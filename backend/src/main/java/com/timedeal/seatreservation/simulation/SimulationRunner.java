package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.events.SimulationEventHub;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SimulationRunner {
    private static final int ADMISSION_BATCH_SIZE = 10;

    private final SimulationStateStore stateStore;
    private final SimulationEventHub eventHub;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public SimulationRunner(SimulationStateStore stateStore, SimulationEventHub eventHub) {
        this.stateStore = stateStore;
        this.eventHub = eventHub;
        this.executor = Executors.newScheduledThreadPool(2);
    }

    public void start(UUID simulationId) {
        jobs.computeIfAbsent(simulationId, id -> executor.scheduleAtFixedRate(
                () -> runScheduledTick(id),
                0L,
                200L,
                TimeUnit.MILLISECONDS
        ));
    }

    public SimulationSnapshot tick(UUID simulationId) {
        SimulationStateStore.MutableSimulationState state = stateStore.state(simulationId);
        synchronized (state) {
            if (state.running) {
                completePayments(state);
                moveHeldSeatsToPayment(state);
                holdSeatsForAdmittedUsers(state);
                admitQueuedUsers(state);
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
        int admitted = 0;
        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (admitted >= ADMISSION_BATCH_SIZE) {
                return;
            }
            if (user.status == VirtualUserStatus.QUEUED) {
                user.status = VirtualUserStatus.ADMITTED;
                user.timeline.add(new TimelineEntry("입장", "입장이 허용되었습니다."));
                admitted++;
            }
        }
    }

    private void holdSeatsForAdmittedUsers(SimulationStateStore.MutableSimulationState state) {
        for (SimulationStateStore.MutableVirtualUser user : state.users) {
            if (user.status != VirtualUserStatus.ADMITTED) {
                continue;
            }

            Optional<SimulationStateStore.MutableSeat> seat = firstAvailableSeat(state);
            if (seat.isEmpty()) {
                user.status = VirtualUserStatus.FAILED;
                user.timeline.add(new TimelineEntry("좌석 선택", "선택 가능한 좌석이 없습니다."));
                continue;
            }

            SimulationStateStore.MutableSeat selectedSeat = seat.get();
            selectedSeat.status = SeatStatus.HELD;
            user.status = VirtualUserStatus.SEAT_HELD;
            user.selectedSeatLabel = selectedSeat.label;
            user.timeline.add(new TimelineEntry("좌석 선택", selectedSeat.label + " 좌석을 임시 선점했습니다."));
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
            if (paymentSucceeds(user)) {
                selectedSeat.ifPresent(seat -> seat.status = SeatStatus.RESERVED);
                user.status = VirtualUserStatus.RESERVED;
                user.timeline.add(new TimelineEntry("예약 완료", "예약이 완료되었습니다."));
            } else {
                selectedSeat.ifPresent(seat -> seat.status = SeatStatus.AVAILABLE);
                user.status = VirtualUserStatus.FAILED;
                user.timeline.add(new TimelineEntry("결제 실패", "결제에 실패해 좌석이 해제되었습니다."));
            }
        }
    }

    private Optional<SimulationStateStore.MutableSeat> firstAvailableSeat(SimulationStateStore.MutableSimulationState state) {
        return state.seats.stream()
                .filter(seat -> seat.status == SeatStatus.AVAILABLE)
                .findFirst();
    }

    private Optional<SimulationStateStore.MutableSeat> findSelectedSeat(
            SimulationStateStore.MutableSimulationState state,
            SimulationStateStore.MutableVirtualUser user
    ) {
        return state.seats.stream()
                .filter(seat -> seat.label.equals(user.selectedSeatLabel))
                .findFirst();
    }

    private boolean paymentSucceeds(SimulationStateStore.MutableVirtualUser user) {
        int sequenceNumber = Integer.parseInt(user.displayName.replace("사용자 ", ""));
        return sequenceNumber % 5 != 0;
    }

    private boolean hasActiveUsers(SimulationStateStore.MutableSimulationState state) {
        return state.users.stream().anyMatch(user ->
                user.status == VirtualUserStatus.QUEUED
                        || user.status == VirtualUserStatus.ADMITTED
                        || user.status == VirtualUserStatus.SEAT_HELD
                        || user.status == VirtualUserStatus.PAYMENT_IN_PROGRESS
        );
    }
}
