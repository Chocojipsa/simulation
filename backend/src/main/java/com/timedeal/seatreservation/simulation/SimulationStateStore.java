package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.payment.PaymentResultEvent;
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
    private static final int MAX_SEAT_ATTEMPTS = 30;
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

    @Override
    public SimulationSnapshot registerQueueEntry(UUID simulationId, UUID virtualUserId, String handledBy) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            MutableVirtualUser user = user(state, virtualUserId);
            user.status = VirtualUserStatus.QUEUED;
            user.timeline.add(new TimelineEntry("대기열", "대기열에 진입했습니다."));
        }
        return snapshot(simulationId);
    }

    @Override
    public SimulationSnapshot recordWaiting(UUID simulationId, UUID virtualUserId, String handledBy) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            MutableVirtualUser user = user(state, virtualUserId);
            user.timeline.add(new TimelineEntry("대기 중", "아직 대기 중입니다."));
        }
        return snapshot(simulationId);
    }

    @Override
    public SimulationSnapshot recordSeatSelectionWaiting(UUID simulationId, UUID virtualUserId, String handledBy) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            MutableVirtualUser user = user(state, virtualUserId);
            user.status = VirtualUserStatus.SELECTING_SEAT;
            user.timeline.add(new TimelineEntry("좌석 선택 대기", "결제 결과를 기다린 뒤 다시 좌석을 선택합니다."));
        }
        return snapshot(simulationId);
    }

    @Override
    public SimulationSnapshot recordSeatConflict(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            MutableVirtualUser user = user(state, virtualUserId);
            user.status = nextSeatAttemptStatus(user);
            user.selectedSeatLabel = seat.label();
            user.timeline.add(new TimelineEntry("좌석 선택 실패", "이미 선택된 좌석입니다: " + seat.label()));
        }
        return snapshot(simulationId);
    }

    private VirtualUserStatus nextSeatAttemptStatus(MutableVirtualUser user) {
        if (countTimelineEntries(user, "좌석 선택 실패") + 1 >= MAX_SEAT_ATTEMPTS) {
            return VirtualUserStatus.FAILED;
        }
        return VirtualUserStatus.SELECTING_SEAT;
    }

    @Override
    public SimulationSnapshot recordNoSeatAvailable(UUID simulationId, UUID virtualUserId, String handledBy) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            MutableVirtualUser user = user(state, virtualUserId);
            user.status = VirtualUserStatus.FAILED;
            user.timeline.add(new TimelineEntry("좌석 선택 실패", "선택 가능한 좌석이 없습니다."));
        }
        return snapshot(simulationId);
    }

    @Override
    public SimulationSnapshot recordPaymentRequested(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy) {
        MutableSimulationState state = state(simulationId);
        synchronized (state) {
            MutableVirtualUser user = user(state, virtualUserId);
            user.status = VirtualUserStatus.PAYMENT_IN_PROGRESS;
            user.selectedSeatLabel = seat.label();
            user.timeline.add(new TimelineEntry("좌석 선택", seat.label() + " 좌석을 선택했습니다. 결제를 요청했습니다."));
            state.seats.stream()
                    .filter(candidate -> candidate.id == seat.id())
                    .findFirst()
                    .ifPresent(candidate -> candidate.status = SeatStatus.PAYMENT_IN_PROGRESS);
        }
        return snapshot(simulationId);
    }

    @Override
    public SimulationSnapshot applyPaymentResult(PaymentResultEvent event) {
        MutableSimulationState state = state(event.simulationId());
        synchronized (state) {
            MutableVirtualUser user = user(state, event.virtualUserId());
            user.status = event.success() ? VirtualUserStatus.RESERVED : VirtualUserStatus.FAILED;
            user.timeline.add(new TimelineEntry(event.success() ? "결제 성공" : "결제 실패", event.success() ? "결제 성공" : "결제 실패"));
            state.seats.stream()
                    .filter(candidate -> candidate.id == event.seatId())
                    .findFirst()
                    .ifPresent(candidate -> candidate.status = event.success() ? SeatStatus.RESERVED : SeatStatus.AVAILABLE);
        }
        return snapshot(event.simulationId());
    }

    MutableSimulationState state(UUID simulationId) {
        MutableSimulationState state = simulations.get(simulationId);
        if (state == null) {
            throw new NoSuchElementException("Simulation not found: " + simulationId);
        }
        return state;
    }

    private MutableVirtualUser user(MutableSimulationState state, UUID virtualUserId) {
        return state.users.stream()
                .filter(user -> user.id.equals(virtualUserId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Virtual user not found: " + virtualUserId));
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
