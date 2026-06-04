package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.events.UserActivityPublisher;
import com.timedeal.seatreservation.queue.AdmissionQueue;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@Profile("demo")
public class SimulationRunner {
    private static final int ACTIVE_SELECTION_LIMIT = 30;

    private final SimulationStateStore stateStore;
    private final SimulationEventHub eventHub;
    private final UserActivityPublisher activityPublisher;
    private final AdmissionQueue admissionQueue;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public SimulationRunner(
            SimulationStateStore stateStore,
            SimulationEventHub eventHub,
            UserActivityPublisher activityPublisher,
            AdmissionQueue admissionQueue
    ) {
        this.stateStore = stateStore;
        this.eventHub = eventHub;
        this.activityPublisher = activityPublisher;
        this.admissionQueue = admissionQueue;
        this.executor = Executors.newScheduledThreadPool(2);
    }

    public void start(UUID simulationId) {
        jobs.computeIfAbsent(simulationId, id -> executor.scheduleAtFixedRate(
                () -> runScheduledTick(id),
                0L,
                250L,
                TimeUnit.MILLISECONDS
        ));
    }

    public SimulationSnapshot tick(UUID simulationId) {
        SimulationStateStore.MutableSimulationState state = stateStore.state(simulationId);
        synchronized (state) {
            if (state.running) {
                state.tick++;
                seedAdmissionQueue(state);
                completePayments(state);
                moveHeldSeatsToPayment(state);
                attemptSeatSelection(state);
                admitQueuedUsers(state);
                expireUsersWhenSoldOut(state);
                state.running = hasActiveUsers(state);
            }
        }

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        eventHub.publish(snapshot);
        return snapshot;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void runScheduledTick(UUID simulationId) {
        try {
            SimulationSnapshot snapshot = tick(simulationId);
            if (!snapshot.running()) {
                stop(simulationId);
            }
        } catch (RuntimeException exception) {
            stop(simulationId);
        }
    }

    private void stop(UUID simulationId) {
        ScheduledFuture<?> job = jobs.remove(simulationId);
        if (job != null) {
            job.cancel(false);
        }
    }

    private void admitQueuedUsers(SimulationStateStore.MutableSimulationState state) {
        int openSlots = ACTIVE_SELECTION_LIMIT - activeUserCount(state);
        if (openSlots <= 0) {
            return;
        }

        List<String> candidateIds = admissionQueue.pick(state.simulationId.toString(), openSlots);
        for (String candidateId : candidateIds) {
            state.users.stream()
                    .filter(user -> user.id.toString().equals(candidateId))
                    .filter(user -> user.status == VirtualUserStatus.QUEUED)
                    .findFirst()
                    .ifPresent(user -> {
                        admissionQueue.grant(state.simulationId.toString(), candidateId);
                        user.status = VirtualUserStatus.SELECTING_SEAT;
                        user.timeline.add(new TimelineEntry("입장", "입장이 허용되었습니다."));
                        user.timeline.add(new TimelineEntry("SUCCESS", "대기열을 통과했습니다! 좌석을 선택합니다."));
                        publishActivity(state.simulationId, user.id, "SUCCESS", "대기열을 통과했습니다! 좌석을 선택합니다.");
                    });
        }
    }

    private void seedAdmissionQueue(SimulationStateStore.MutableSimulationState state) {
        if (state.queueSeeded) {
            return;
        }

        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (user.status == VirtualUserStatus.QUEUED) {
                admissionQueue.enter(state.simulationId.toString(), user.id.toString());
            }
        }
        state.queueSeeded = true;
    }

    private int activeUserCount(SimulationStateStore.MutableSimulationState state) {
        int count = 0;
        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (user.status == VirtualUserStatus.SELECTING_SEAT
                    || user.status == VirtualUserStatus.SEAT_HELD
                    || user.status == VirtualUserStatus.PAYMENT_IN_PROGRESS) {
                count++;
            }
        }
        return count;
    }

    private void attemptSeatSelection(SimulationStateStore.MutableSimulationState state) {
        List<SimulationStateStore.MutableVirtualUser> selectingUsers = state.users.stream()
                .filter(user -> user.status == VirtualUserStatus.SELECTING_SEAT)
                .toList();
        if (selectingUsers.isEmpty()) {
            return;
        }

        List<SimulationStateStore.MutableSeat> availableSeats = availableSeats(state);
        if (availableSeats.isEmpty()) {
            return;
        }

        LinkedHashMap<SimulationStateStore.MutableSeat, List<SimulationStateStore.MutableVirtualUser>> attempts = new LinkedHashMap<>();
        for (SimulationStateStore.MutableVirtualUser user : selectingUsers) {
            user.timeline.add(new TimelineEntry("THINKING", "비어있는 좌석을 탐색 중입니다..."));
            publishActivity(state.simulationId, user.id, "THINKING", "비어있는 좌석을 탐색 중입니다...");
            SimulationStateStore.MutableSeat seat = chooseRandomSeat(state, user, availableSeats);
            user.selectedSeatLabel = seat.label;
            user.timeline.add(new TimelineEntry("좌석 선택", seat.label + " 좌석을 선택했습니다."));
            user.timeline.add(new TimelineEntry("ACTION", seat.label + " 좌석 선점을 시도합니다."));
            publishActivity(state.simulationId, user.id, "ACTION", seat.label + " 좌석 선점을 시도합니다.");
            attempts.computeIfAbsent(seat, ignored -> new ArrayList<>()).add(user);
        }

        for (var entry : attempts.entrySet()) {
            SimulationStateStore.MutableSeat seat = entry.getKey();
            List<SimulationStateStore.MutableVirtualUser> contenders = entry.getValue();
            SimulationStateStore.MutableVirtualUser winner = contenders.get(0);
            holdSeat(state, winner, seat);
            for (int index = 1; index < contenders.size(); index++) {
                retrySeatSelection(state, contenders.get(index));
            }
        }
    }

    private List<SimulationStateStore.MutableSeat> availableSeats(SimulationStateStore.MutableSimulationState state) {
        return state.seats.stream()
                .filter(seat -> seat.status == SeatStatus.AVAILABLE)
                .toList();
    }

    private SimulationStateStore.MutableSeat chooseRandomSeat(
            SimulationStateStore.MutableSimulationState state,
            SimulationStateStore.MutableVirtualUser user,
            List<SimulationStateStore.MutableSeat> availableSeats
    ) {
        long seed = state.simulationId.getMostSignificantBits()
                ^ state.simulationId.getLeastSignificantBits()
                ^ user.id.getMostSignificantBits()
                ^ user.id.getLeastSignificantBits()
                ^ state.tick;
        Random random = new Random(seed);
        return availableSeats.get(random.nextInt(availableSeats.size()));
    }

    private void holdSeat(SimulationStateStore.MutableSimulationState state, SimulationStateStore.MutableVirtualUser user, SimulationStateStore.MutableSeat seat) {
        seat.status = SeatStatus.HELD;
        user.status = VirtualUserStatus.SEAT_HELD;
        user.selectedSeatLabel = seat.label;
        user.timeline.add(new TimelineEntry("좌석 선점", seat.label + " 좌석을 임시 선점했습니다."));
        user.timeline.add(new TimelineEntry("SUCCESS", seat.label + " 좌석을 발견했습니다! 결제를 진행합니다."));
        publishActivity(state.simulationId, user.id, "SUCCESS", seat.label + " 좌석을 발견했습니다! 결제를 진행합니다.");
    }

    private void retrySeatSelection(SimulationStateStore.MutableSimulationState state, SimulationStateStore.MutableVirtualUser user) {
        user.status = VirtualUserStatus.SELECTING_SEAT;
        user.timeline.add(new TimelineEntry("좌석 선택 실패", "이미 선택된 좌석입니다."));
        user.timeline.add(new TimelineEntry("RETRY", "좌석 선점에 실패했습니다. 다른 좌석을 찾아볼게요."));
        publishActivity(state.simulationId, user.id, "RETRY", "좌석 선점에 실패했습니다. 다른 좌석을 찾아볼게요.");
    }

    private void publishActivity(UUID simulationId, UUID userId, String label, String message) {
        if (activityPublisher != null) {
            activityPublisher.publish(new UserActivityEvent(simulationId, userId, label, message));
        }
    }

    private void moveHeldSeatsToPayment(SimulationStateStore.MutableSimulationState state) {
        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (user.status != VirtualUserStatus.SEAT_HELD) {
                continue;
            }

            findSelectedSeat(state, user).ifPresent(seat -> seat.status = SeatStatus.PAYMENT_IN_PROGRESS);
            user.status = VirtualUserStatus.PAYMENT_IN_PROGRESS;
            user.timeline.add(new TimelineEntry("결제", "결제를 진행 중입니다."));
        }
    }

    private void completePayments(SimulationStateStore.MutableSimulationState state) {
        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (user.status != VirtualUserStatus.PAYMENT_IN_PROGRESS) {
                continue;
            }

            Optional<SimulationStateStore.MutableSeat> selectedSeat = findSelectedSeat(state, user);
            selectedSeat.ifPresent(seat -> seat.status = SeatStatus.RESERVED);
            user.status = VirtualUserStatus.RESERVED;
            user.timeline.add(new TimelineEntry("예약 완료", "예약이 완료되었습니다."));
        }
    }

    private void expireUsersWhenSoldOut(SimulationStateStore.MutableSimulationState state) {
        if (!availableSeats(state).isEmpty()) {
            return;
        }
        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (user.status == VirtualUserStatus.QUEUED || user.status == VirtualUserStatus.SELECTING_SEAT) {
                user.status = VirtualUserStatus.FAILED;
                user.timeline.add(new TimelineEntry("좌석 선택", "남은 좌석 선택을 시도했습니다."));
                user.timeline.add(new TimelineEntry("좌석 선택 실패", "선택 가능한 좌석이 없습니다."));
            }
        }
    }

    private Optional<SimulationStateStore.MutableSeat> findSelectedSeat(
            SimulationStateStore.MutableSimulationState state,
            SimulationStateStore.MutableVirtualUser user
    ) {
        return state.seats.stream()
                .filter(seat -> seat.label.equals(user.selectedSeatLabel))
                .findFirst();
    }

    private boolean hasActiveUsers(SimulationStateStore.MutableSimulationState state) {
        return state.users.stream().anyMatch(user ->
                user.status == VirtualUserStatus.QUEUED
                        || user.status == VirtualUserStatus.SELECTING_SEAT
                        || user.status == VirtualUserStatus.SEAT_HELD
                        || user.status == VirtualUserStatus.PAYMENT_IN_PROGRESS
        );
    }
}
