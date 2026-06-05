package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SnapshotPublisher {
    private static final long DEBOUNCE_MILLIS = 200L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, SimulationSnapshot> pendingSnapshots = new ConcurrentHashMap<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService broadcastScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "snapshot-debounce");
        t.setDaemon(true);
        return t;
    });

    public SnapshotPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(SimulationSnapshot snapshot) {
        pendingSnapshots.put(snapshot.simulationId(), snapshot);
        if (flushScheduled.compareAndSet(false, true)) {
            broadcastScheduler.schedule(this::flush, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void flush() {
        flushScheduled.set(false);
        pendingSnapshots.forEach((simulationId, snapshot) -> {
            pendingSnapshots.remove(simulationId);
            try {
                String message = objectMapper.writeValueAsString(snapshot);
                redisTemplate.convertAndSend(UserActivityBroadcastConfig.SNAPSHOT_CHANNEL, message);
            } catch (JsonProcessingException e) {
                // Log and continue to avoid blocking other simulations
            }
        });
    }

    @PreDestroy
    void shutdown() {
        broadcastScheduler.shutdownNow();
        flush();
    }
}
