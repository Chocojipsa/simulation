package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SimulationEventHub {
    private static final Logger log = LoggerFactory.getLogger(SimulationEventHub.class);
    private static final long TIMEOUT_MILLIS = 300_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SimulationSnapshot> latestSnapshots = new ConcurrentHashMap<>();
    private final SnapshotPublisher snapshotPublisher;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService heartbeatScheduler;

    public SimulationEventHub(SnapshotPublisher snapshotPublisher, ObjectMapper objectMapper) {
        this.snapshotPublisher = snapshotPublisher;
        this.objectMapper = objectMapper;
        this.heartbeatScheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeatScheduler.scheduleAtFixedRate(this::broadcastLatestSnapshots, 500, 500, TimeUnit.MILLISECONDS);
    }

    protected SseEmitter createEmitter(long timeout) {
        return new SseEmitter(timeout);
    }

    public SseEmitter open(UUID simulationId) {
        SseEmitter emitter = createEmitter(TIMEOUT_MILLIS);

        emitters.compute(simulationId, (key, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
            }
            list.add(emitter);
            return list;
        });

        emitter.onCompletion(() -> {
            synchronized (emitter) {
                cancelHeartbeat(emitter);
                remove(simulationId, emitter);
            }
        });
        emitter.onTimeout(() -> {
            synchronized (emitter) {
                cancelHeartbeat(emitter);
                remove(simulationId, emitter);
            }
        });
        emitter.onError(ignored -> {
            synchronized (emitter) {
                cancelHeartbeat(emitter);
                remove(simulationId, emitter);
            }
        });

        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("connect").data("connected"));
            }
        } catch (IOException | IllegalStateException e) {
            synchronized (emitter) {
                remove(simulationId, emitter);
                completeQuietly(emitter);
            }
            return emitter;
        }

        // Only schedule heartbeat if client hasn't disconnected concurrently
        synchronized (emitter) {
            List<SseEmitter> active = emitters.get(simulationId);
            if (active != null && active.contains(emitter)) {
                scheduleHeartbeat(emitter);
            }
        }
        return emitter;
    }

    public SseEmitter openUserStream(UUID participantId) {
        SseEmitter emitter = createEmitter(TIMEOUT_MILLIS);

        userEmitters.compute(participantId, (key, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
            }
            list.add(emitter);
            return list;
        });

        emitter.onCompletion(() -> {
            synchronized (emitter) {
                cancelHeartbeat(emitter);
                removeUserEmitter(participantId, emitter);
            }
        });
        emitter.onTimeout(() -> {
            synchronized (emitter) {
                cancelHeartbeat(emitter);
                removeUserEmitter(participantId, emitter);
            }
        });
        emitter.onError(ignored -> {
            synchronized (emitter) {
                cancelHeartbeat(emitter);
                removeUserEmitter(participantId, emitter);
            }
        });

        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("connect").data("connected"));
            }
        } catch (IOException | IllegalStateException e) {
            synchronized (emitter) {
                removeUserEmitter(participantId, emitter);
                completeQuietly(emitter);
            }
            return emitter;
        }

        // Only schedule heartbeat if client hasn't disconnected concurrently
        synchronized (emitter) {
            List<SseEmitter> active = userEmitters.get(participantId);
            if (active != null && active.contains(emitter)) {
                scheduleHeartbeat(emitter);
            }
        }
        return emitter;
    }

    public int getActiveUserConnectionCount() {
        return userEmitters.values().stream().mapToInt(List::size).sum();
    }

    public void publish(SimulationSnapshot snapshot) {
        if (snapshotPublisher != null) {
            snapshotPublisher.publish(snapshot);
        }
    }

    public void publishLocally(SimulationSnapshot snapshot) {
        latestSnapshots.put(snapshot.simulationId(), snapshot);
    }

    private void broadcastLatestSnapshots() {
        latestSnapshots.forEach((simulationId, snapshot) -> {
            SimulationSnapshot currentSnapshot = latestSnapshots.remove(simulationId);
            if (currentSnapshot != null) {
                sendSnapshotLocally(currentSnapshot);
            }
        });
    }

    private void sendSnapshotLocally(SimulationSnapshot snapshot) {
        List<SseEmitter> simulationEmitters = emitters.get(snapshot.simulationId());
        if (simulationEmitters == null || simulationEmitters.isEmpty()) {
            return;
        }

        // Pre-serialize once to avoid N serializations for N clients
        List<VirtualUserView> strippedUsers = snapshot.users().stream()
                .map(u -> new VirtualUserView(
                        u.id(), u.displayName(), u.type(), u.status(), u.selectedSeatLabel(),
                        List.of(), // strip timeline!
                        u.seatAttemptCount(), u.conflictCount(), u.paymentAttemptCount(),
                        u.reservationId(), u.seatHoldExpiresAt()
                )).toList();
        SimulationSnapshot strippedSnapshot = new SimulationSnapshot(
                snapshot.simulationId(),
                snapshot.seats(),
                strippedUsers,
                snapshot.metrics(),
                snapshot.serverStats(),
                snapshot.running()
        );
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(strippedSnapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize snapshot", e);
            return;
        }

        for (SseEmitter emitter : simulationEmitters) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event()
                            .name("snapshot")
                            .data(jsonData, MediaType.APPLICATION_JSON));
                }
            } catch (IOException | IllegalStateException exception) {
                synchronized (emitter) {
                    cancelHeartbeat(emitter);
                    remove(snapshot.simulationId(), emitter);
                    completeQuietly(emitter);
                }
            }
        }
    }

    public void publishUserActivity(UserActivityEvent event) {
        List<SseEmitter> specificUserEmitters = userEmitters.get(event.userId());
        if (specificUserEmitters == null || specificUserEmitters.isEmpty()) {
            return;
        }

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user activity event", e);
            return;
        }

        for (SseEmitter emitter : specificUserEmitters) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event()
                            .name("activity")
                            .data(jsonData, MediaType.APPLICATION_JSON));
                }
            } catch (IOException | IllegalStateException exception) {
                synchronized (emitter) {
                    cancelHeartbeat(emitter);
                    removeUserEmitter(event.userId(), emitter);
                    completeQuietly(emitter);
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdownNow();
        emitters.values().forEach(list -> list.forEach(this::completeQuietly));
        userEmitters.values().forEach(list -> list.forEach(this::completeQuietly));
    }

    private void scheduleHeartbeat(SseEmitter emitter) {
        synchronized (emitter) {
            ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    }
                } catch (IOException | IllegalStateException e) {
                    cancelHeartbeat(emitter);
                    completeQuietly(emitter);
                }
            }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
            heartbeats.put(emitter, future);
        }
    }

    private void cancelHeartbeat(SseEmitter emitter) {
        ScheduledFuture<?> future = heartbeats.remove(emitter);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void remove(UUID simulationId, SseEmitter emitter) {
        emitters.computeIfPresent(simulationId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    private void removeUserEmitter(UUID userId, SseEmitter emitter) {
        userEmitters.computeIfPresent(userId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Emitter already completed or connection broken — safe to ignore
        }
    }
}
