package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class SimulationStateStore implements SimulationStateGateway {
    private static final int ROW_COUNT = 10;
    private static final int SEATS_PER_ROW = 12;

    private final ConcurrentHashMap<UUID, MutableSimulationState> simulations = new ConcurrentHashMap<>();

    @Override
    public SimulationSnapshot create(UUID simulationId, int virtualUserCount) {
        MutableSimulationState state = new MutableSimulationState(simulationId, createSeats(), createUsers(virtualUserCount));
        simulations.put(simulationId, state);
        return snapshot(simulationId);
    }

    @Override
    public SimulationSnapshot snapshot(UUID simulationId) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            List<SeatView> seats = state.seats.stream()
                    .map(seat -> new SeatView(seat.id, seat.label, seat.status))
                    .toList();
            List<VirtualUserView> users = state.users.stream()
                    .map(user -> new VirtualUserView(
                            user.id,
                            user.displayName,
                            user.status,
                            user.selectedSeatLabel,
                            List.copyOf(user.timeline),
                            countTimelineEntries(user, "좌석 선택"),
                            countTimelineEntries(user, "좌석 선택 실패")
                    ))
                    .toList();

            return new SimulationSnapshot(
                    simulationId,
                    seats,
                    users,
                    metrics(state),
                    List.of(),
                    state.running
            );
        }
    }

    @Override
    public SimulationSnapshot markRunning(UUID simulationId) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            state.running = true;
        }
        return snapshot(simulationId);
    }

    MutableSimulationState state(UUID simulationId) {
        MutableSimulationState state = simulations.get(simulationId);
        if (state == null) {
            throw new NoSuchElementException("Simulation not found: " + simulationId);
        }
        return state;
    }

    private int countTimelineEntries(MutableVirtualUser user, String label) {
        return (int) user.timeline.stream()
                .filter(entry -> label.equals(entry.label()))
                .count();
    }

    private List<MutableSeat> createSeats() {
        List<MutableSeat> seats = new ArrayList<>(ROW_COUNT * SEATS_PER_ROW);
        long id = 1L;
        for (int row = 0; row < ROW_COUNT; row++) {
            String rowName = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= SEATS_PER_ROW; number++) {
                seats.add(new MutableSeat(id, rowName + "-" + number));
                id++;
            }
        }
        return seats;
    }

    private List<MutableVirtualUser> createUsers(int virtualUserCount) {
        List<MutableVirtualUser> users = new ArrayList<>(virtualUserCount);
        for (int index = 1; index <= virtualUserCount; index++) {
            MutableVirtualUser user = new MutableVirtualUser(UUID.randomUUID(), "사용자 " + index);
            user.timeline.add(new TimelineEntry("대기열", "대기열에 진입했습니다."));
            users.add(user);
        }
        return users;
    }

    private SimulationMetrics metrics(MutableSimulationState state) {
        int queueSize = 0;
        int admittedCount = 0;
        int failedCount = 0;
        for (MutableVirtualUser user : state.users) {
            if (user.status == VirtualUserStatus.QUEUED) {
                queueSize++;
            }
            if (user.status != VirtualUserStatus.QUEUED && user.status != VirtualUserStatus.FAILED) {
                admittedCount++;
            }
            failedCount += (int) user.timeline.stream()
                    .filter(entry -> "좌석 선택 실패".equals(entry.label()))
                    .count();
        }

        int heldCount = 0;
        int paymentInProgressCount = 0;
        int reservedCount = 0;
        for (MutableSeat seat : state.seats) {
            if (seat.status == SeatStatus.HELD) {
                heldCount++;
            }
            if (seat.status == SeatStatus.PAYMENT_IN_PROGRESS) {
                paymentInProgressCount++;
            }
            if (seat.status == SeatStatus.RESERVED) {
                reservedCount++;
            }
        }

        return new SimulationMetrics(
                queueSize,
                admittedCount,
                heldCount,
                paymentInProgressCount,
                reservedCount,
                failedCount
        );
    }

    static final class MutableSimulationState {
        final UUID simulationId;
        final List<MutableSeat> seats;
        final List<MutableVirtualUser> users;
        long tick;
        boolean queueSeeded;
        boolean running;

        MutableSimulationState(UUID simulationId, List<MutableSeat> seats, List<MutableVirtualUser> users) {
            this.simulationId = simulationId;
            this.seats = seats;
            this.users = users;
        }
    }

    static final class MutableSeat {
        final long id;
        final String label;
        SeatStatus status = SeatStatus.AVAILABLE;

        MutableSeat(long id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    static final class MutableVirtualUser {
        final UUID id;
        final String displayName;
        final List<TimelineEntry> timeline = new ArrayList<>();
        VirtualUserStatus status = VirtualUserStatus.QUEUED;
        String selectedSeatLabel;

        MutableVirtualUser(UUID id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }
}
