package com.timedeal.seatreservation.simulation.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.payment.PaymentResultEvent;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.SimulationStateGateway;
import com.timedeal.seatreservation.simulation.TimelineEntry;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

@Component
@Profile("!demo")
public class RedisSimulationStateStore implements SimulationStateGateway {
    private static final Duration TTL = Duration.ofHours(3);
    private static final Duration LOCK_TTL = Duration.ofSeconds(2);
    private static final Duration LOCK_RETRY_DELAY = Duration.ofMillis(20);
    private static final int LOCK_ATTEMPTS = 250;
    private static final int MAX_SEAT_ATTEMPTS = 30;
    private static final int ROW_COUNT = 10;
    private static final int SEATS_PER_ROW = 12;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSimulationStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SimulationSnapshot create(UUID simulationId, int virtualUserCount) {
        SimulationSnapshot snapshot = new SimulationSnapshot(
                simulationId,
                createSeats(),
                createUsers(simulationId, virtualUserCount),
                new SimulationMetrics(virtualUserCount, 0, 0, 0, 0, 0),
                List.of(),
                false
        );
        save(snapshot);
        return snapshot;
    }

    @Override
    public SimulationSnapshot snapshot(UUID simulationId) {
        SimulationRedisKeys keys = new SimulationRedisKeys(simulationId);
        String json = redisTemplate.opsForValue().get(keys.snapshot());
        if (json == null) {
            throw new NoSuchElementException("Simulation not found: " + simulationId);
        }
        try {
            return objectMapper.readValue(json, SimulationSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read simulation snapshot: " + simulationId, exception);
        }
    }

    @Override
    public SimulationSnapshot markRunning(UUID simulationId) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                current.users(),
                current.metrics(),
                current.serverStats(),
                true
        ));
    }

    @Override
    public SimulationSnapshot registerParticipant(
            UUID simulationId,
            UUID participantId,
            String displayName,
            ParticipantType type,
            String handledBy
    ) {
        return mutate(simulationId, current -> {
            List<VirtualUserView> users = new ArrayList<>(current.users());
            users.add(new VirtualUserView(
                    participantId,
                    displayName,
                    type,
                    VirtualUserStatus.WAITING_ROOM,
                    null,
                    List.of(new TimelineEntry("입장", "이벤트에 입장했습니다.")),
                    0,
                    0,
                    0,
                    null,
                    null
            ));
            return new SimulationSnapshot(
                    current.simulationId(),
                    current.seats(),
                    users,
                    current.metrics(),
                    incrementServerStats(current.serverStats(), handledBy, false, false),
                    current.running()
            );
        });
    }

    @Override
    public SimulationSnapshot registerQueueEntry(UUID simulationId, UUID virtualUserId, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> replaceUser(
                        user,
                        VirtualUserStatus.QUEUED,
                        null,
                        appendEntry(user.timeline(), "대기열", "대기열에 진입했습니다."),
                        user.seatAttemptCount(),
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        null,
                        null
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot recordAdmitted(UUID simulationId, UUID virtualUserId, Instant selectionExpiresAt, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> replaceUser(
                        user,
                        VirtualUserStatus.SELECTING_SEAT,
                        null,
                        appendEntry(user.timeline(), "대기열 통과", "대기열을 통과했습니다. 좌석을 선택해 주세요."),
                        user.seatAttemptCount(),
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        null,
                        selectionExpiresAt
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot recordWaiting(UUID simulationId, UUID virtualUserId, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> appendTimeline(
                        user,
                        VirtualUserStatus.QUEUED,
                        user.selectedSeatLabel(),
                        "대기 중",
                        "아직 대기 중입니다.",
                        0,
                        0
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot recordSeatSelectionWaiting(UUID simulationId, UUID virtualUserId, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> appendTimeline(
                        user,
                        VirtualUserStatus.SELECTING_SEAT,
                        user.selectedSeatLabel(),
                        "좌석 선택 대기",
                        "결제 결과를 기다린 뒤 다시 좌석을 선택합니다.",
                        0,
                        0
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot recordSeatConflict(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> appendTimeline(
                        user,
                        seatAttemptStatusAfter(user),
                        seat.label(),
                        "좌석 선택 실패",
                        "이미 선택된 좌석입니다: " + seat.label(),
                        1,
                        1
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, true, false),
                current.running()
        ));
    }

    private VirtualUserStatus seatAttemptStatusAfter(VirtualUserView user) {
        if (user.seatAttemptCount() + 1 >= MAX_SEAT_ATTEMPTS) {
            return VirtualUserStatus.FAILED;
        }
        return VirtualUserStatus.SELECTING_SEAT;
    }

    @Override
    public SimulationSnapshot recordNoSeatAvailable(UUID simulationId, UUID virtualUserId, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> appendTimeline(
                        user,
                        VirtualUserStatus.FAILED,
                        user.selectedSeatLabel(),
                        "좌석 선택 실패",
                        "선택 가능한 좌석이 없습니다.",
                        1,
                        1
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, true, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot recordPaymentRequested(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                updateSeat(current.seats(), seat.id(), SeatStatus.PAYMENT_IN_PROGRESS),
                updateUser(current.users(), virtualUserId, user -> appendTimeline(
                        user,
                        VirtualUserStatus.PAYMENT_IN_PROGRESS,
                        seat.label(),
                        "좌석 선택",
                        seat.label() + " 좌석을 선택했습니다. 결제를 요청했습니다.",
                        1,
                        0
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, true),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot recordSeatHeldForPayment(
            UUID simulationId,
            UUID virtualUserId,
            SeatView seat,
            Long reservationId,
            Instant expiresAt,
            String handledBy
    ) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                updateSeat(current.seats(), seat.id(), SeatStatus.HELD),
                updateUser(current.users(), virtualUserId, user -> replaceUser(
                        user,
                        VirtualUserStatus.SEAT_HELD,
                        seat.label(),
                        appendEntry(user.timeline(), "좌석 선점", seat.label() + " 좌석을 선점했습니다. 결제를 확인해 주세요."),
                        user.seatAttemptCount() + 1,
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        reservationId,
                        expiresAt
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, true),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot expireSeatHold(UUID simulationId, UUID virtualUserId, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                updateSelectedSeat(current.seats(), current.users(), virtualUserId, SeatStatus.AVAILABLE),
                updateUser(current.users(), virtualUserId, user -> replaceUser(
                        user,
                        VirtualUserStatus.EXPIRED,
                        null,
                        appendEntry(user.timeline(), "좌석 선점 만료", "결제 제한 시간이 지나 좌석 선점이 해제되었습니다."),
                        user.seatAttemptCount(),
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        null,
                        null
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot expireSeatSelection(UUID simulationId, UUID virtualUserId, String handledBy) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), virtualUserId, user -> replaceUser(
                        user,
                        VirtualUserStatus.EXPIRED,
                        null,
                        appendEntry(user.timeline(), "좌석 선택 만료", "좌석 선택 제한 시간이 지나 다시 예약하기가 필요합니다."),
                        user.seatAttemptCount(),
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        null,
                        null
                )),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, false),
                current.running()
        ));
    }

    @Override
    public SimulationSnapshot expireTimedOutParticipants(
            UUID simulationId,
            List<UUID> seatHoldExpiredIds,
            List<UUID> seatSelectionExpiredIds,
            String handledBy
    ) {
        if (seatHoldExpiredIds.isEmpty() && seatSelectionExpiredIds.isEmpty()) {
            return snapshot(simulationId);
        }
        return mutate(simulationId, current -> {
            List<SeatView> updatedSeats = current.seats();
            List<VirtualUserView> updatedUsers = current.users();

            for (UUID userId : seatHoldExpiredIds) {
                updatedSeats = updateSelectedSeat(updatedSeats, updatedUsers, userId, SeatStatus.AVAILABLE);
                updatedUsers = updateUser(updatedUsers, userId, user -> replaceUser(
                        user,
                        VirtualUserStatus.EXPIRED,
                        null,
                        appendEntry(user.timeline(), "좌석 선점 만료", "결제 제한 시간이 지나 좌석 선점이 해제되었습니다."),
                        user.seatAttemptCount(),
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        null,
                        null
                ));
            }

            for (UUID userId : seatSelectionExpiredIds) {
                updatedUsers = updateUser(updatedUsers, userId, user -> replaceUser(
                        user,
                        VirtualUserStatus.EXPIRED,
                        null,
                        appendEntry(user.timeline(), "좌석 선택 만료", "좌석 선택 제한 시간이 지나 다시 예약하기가 필요합니다."),
                        user.seatAttemptCount(),
                        user.conflictCount(),
                        user.paymentAttemptCount(),
                        null,
                        null
                ));
            }

            return new SimulationSnapshot(
                    current.simulationId(),
                    updatedSeats,
                    updatedUsers,
                    current.metrics(),
                    incrementServerStats(current.serverStats(), handledBy, false, false),
                    current.running()
            );
        });
    }

    @Override
    public Long markPaymentRequestedByParticipant(UUID simulationId, UUID virtualUserId, String handledBy) {
        AtomicReference<Long> reservationId = new AtomicReference<>();
        mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                updateSelectedSeat(current.seats(), current.users(), virtualUserId, SeatStatus.PAYMENT_IN_PROGRESS),
                updateUser(current.users(), virtualUserId, user -> {
                    reservationId.set(user.reservationId());
                    return replaceUser(
                            user,
                            VirtualUserStatus.PAYMENT_IN_PROGRESS,
                            user.selectedSeatLabel(),
                            appendEntry(user.timeline(), "결제 확인", "결제 확인 요청을 보냈습니다."),
                            user.seatAttemptCount(),
                            user.conflictCount(),
                            user.paymentAttemptCount() + 1,
                            user.reservationId(),
                            null
                    );
                }),
                current.metrics(),
                incrementServerStats(current.serverStats(), handledBy, false, true),
                current.running()
        ));
        return reservationId.get();
    }

    @Override
    public SimulationSnapshot applyPaymentResult(PaymentResultEvent event) {
        return mutate(event.simulationId(), current -> {
            SeatStatus seatStatus = event.success() ? SeatStatus.RESERVED : SeatStatus.AVAILABLE;
            VirtualUserStatus userStatus = event.success() ? VirtualUserStatus.RESERVED : VirtualUserStatus.FAILED;
            String label = event.success() ? "결제 성공" : "결제 실패";
            String message = event.success() ? "결제 성공" : "결제 실패";

            return new SimulationSnapshot(
                    current.simulationId(),
                    updateSeat(current.seats(), event.seatId(), seatStatus),
                    updateUser(current.users(), event.virtualUserId(), user -> appendTimeline(
                            user,
                            userStatus,
                            user.selectedSeatLabel(),
                            label,
                            message,
                            0,
                            0
                    )),
                    current.metrics(),
                    incrementServerStats(current.serverStats(), event.handledBy(), false, event.success()),
                    current.running()
            );
        });
    }

    @Override
    public SimulationSnapshot recordUserActivity(UUID simulationId, UUID userId, String label, String message) {
        return mutate(simulationId, current -> new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                updateUser(current.users(), userId, user -> appendTimeline(
                        user,
                        user.status(),
                        user.selectedSeatLabel(),
                        label,
                        message,
                        0,
                        0
                )),
                current.metrics(),
                current.serverStats(),
                current.running()
        ));
    }

    private SimulationSnapshot mutate(UUID simulationId, UnaryOperator<SimulationSnapshot> mutator) {
        String lockKey = "simulation:%s:lock".formatted(simulationId);
        acquireLock(simulationId, lockKey);
        try {
            SimulationSnapshot updated = withMetrics(mutator.apply(snapshot(simulationId)));
            save(updated);
            return updated;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void acquireLock(UUID simulationId, String lockKey) {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                return;
            }
            sleepBeforeLockRetry(simulationId);
        }
        throw new IllegalStateException("Simulation snapshot is busy: " + simulationId);
    }

    private void sleepBeforeLockRetry(UUID simulationId) {
        try {
            Thread.sleep(LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for simulation snapshot lock: " + simulationId, exception);
        }
    }

    private void save(SimulationSnapshot snapshot) {
        SimulationRedisKeys keys = new SimulationRedisKeys(snapshot.simulationId());
        try {
            redisTemplate.opsForValue().set(keys.snapshot(), objectMapper.writeValueAsString(snapshot), TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save simulation snapshot: " + snapshot.simulationId(), exception);
        }
    }

    private SimulationSnapshot withMetrics(SimulationSnapshot snapshot) {
        int queueSize = 0;
        int admittedCount = 0;
        int failedCount = 0;
        for (VirtualUserView user : snapshot.users()) {
            if (user.status() == VirtualUserStatus.QUEUED) {
                queueSize++;
            }
            if (user.status() != VirtualUserStatus.QUEUED && user.status() != VirtualUserStatus.FAILED) {
                admittedCount++;
            }
            failedCount += user.conflictCount();
            if (user.status() == VirtualUserStatus.FAILED) {
                failedCount++;
            }
        }

        int heldCount = 0;
        int paymentInProgressCount = 0;
        int reservedCount = 0;
        for (SeatView seat : snapshot.seats()) {
            if (seat.status() == SeatStatus.HELD) {
                heldCount++;
            }
            if (seat.status() == SeatStatus.PAYMENT_IN_PROGRESS) {
                paymentInProgressCount++;
            }
            if (seat.status() == SeatStatus.RESERVED) {
                reservedCount++;
            }
        }

        return new SimulationSnapshot(
                snapshot.simulationId(),
                snapshot.seats(),
                snapshot.users(),
                new SimulationMetrics(queueSize, admittedCount, heldCount, paymentInProgressCount, reservedCount, failedCount),
                snapshot.serverStats(),
                snapshot.running() && hasActiveUsers(snapshot.users())
        );
    }

    private boolean hasActiveUsers(List<VirtualUserView> users) {
        return users.stream().anyMatch(user ->
                user.status() == VirtualUserStatus.QUEUED
                        || user.status() == VirtualUserStatus.SELECTING_SEAT
                        || user.status() == VirtualUserStatus.SEAT_HELD
                        || user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS
        );
    }

    private List<VirtualUserView> updateUser(
            List<VirtualUserView> users,
            UUID userId,
            UnaryOperator<VirtualUserView> updater
    ) {
        return users.stream()
                .map(user -> user.id().equals(userId) ? updater.apply(user) : user)
                .toList();
    }

    private VirtualUserView appendTimeline(
            VirtualUserView user,
            VirtualUserStatus status,
            String selectedSeatLabel,
            String label,
            String message,
            int seatAttemptIncrement,
            int conflictIncrement
    ) {
        List<TimelineEntry> timeline = new ArrayList<>(user.timeline());
        timeline.add(new TimelineEntry(label, message));
        if (timeline.size() > 20) {
            timeline.remove(0);
        }
        return replaceUser(
                user,
                status,
                selectedSeatLabel,
                timeline,
                user.seatAttemptCount() + seatAttemptIncrement,
                user.conflictCount() + conflictIncrement,
                user.paymentAttemptCount(),
                user.reservationId(),
                user.seatHoldExpiresAt()
        );
    }

    private VirtualUserView replaceUser(
            VirtualUserView user,
            VirtualUserStatus status,
            String selectedSeatLabel,
            List<TimelineEntry> timeline,
            int seatAttemptCount,
            int conflictCount,
            int paymentAttemptCount,
            Long reservationId,
            Instant seatHoldExpiresAt
    ) {
        return new VirtualUserView(
                user.id(),
                user.displayName(),
                user.type(),
                status,
                selectedSeatLabel,
                timeline,
                seatAttemptCount,
                conflictCount,
                paymentAttemptCount,
                reservationId,
                seatHoldExpiresAt
        );
    }

    private List<TimelineEntry> appendEntry(List<TimelineEntry> timeline, String label, String message) {
        List<TimelineEntry> updated = new ArrayList<>(timeline);
        updated.add(new TimelineEntry(label, message));
        return updated;
    }

    private List<SeatView> updateSeat(List<SeatView> seats, long seatId, SeatStatus status) {
        return seats.stream()
                .map(seat -> seat.id() == seatId ? new SeatView(seat.id(), seat.label(), status) : seat)
                .toList();
    }

    private List<SeatView> updateSelectedSeat(
            List<SeatView> seats,
            List<VirtualUserView> users,
            UUID virtualUserId,
            SeatStatus status
    ) {
        return users.stream()
                .filter(user -> user.id().equals(virtualUserId))
                .findFirst()
                .map(VirtualUserView::selectedSeatLabel)
                .map(label -> seats.stream()
                        .map(seat -> seat.label().equals(label) ? new SeatView(seat.id(), seat.label(), status) : seat)
                        .toList())
                .orElse(seats);
    }

    private List<ServerStatsView> incrementServerStats(
            List<ServerStatsView> stats,
            String serverId,
            boolean conflict,
            boolean success
    ) {
        List<ServerStatsView> updated = new ArrayList<>();
        boolean found = false;
        for (ServerStatsView current : stats) {
            if (current.serverId().equals(serverId)) {
                found = true;
                updated.add(new ServerStatsView(
                        serverId,
                        current.requestCount() + 1,
                        current.conflictCount() + (conflict ? 1 : 0),
                        current.successCount() + (success ? 1 : 0)
                ));
            } else {
                updated.add(current);
            }
        }
        if (!found) {
            updated.add(new ServerStatsView(serverId, 1, conflict ? 1 : 0, success ? 1 : 0));
        }
        return updated;
    }

    private List<SeatView> createSeats() {
        List<SeatView> seats = new ArrayList<>(ROW_COUNT * SEATS_PER_ROW);
        long id = 1L;
        for (int row = 0; row < ROW_COUNT; row++) {
            String rowName = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= SEATS_PER_ROW; number++) {
                seats.add(new SeatView(id, rowName + "-" + number, SeatStatus.AVAILABLE));
                id++;
            }
        }
        return seats;
    }

    private List<VirtualUserView> createUsers(UUID simulationId, int virtualUserCount) {
        List<VirtualUserView> users = new ArrayList<>(virtualUserCount);
        for (int index = 1; index <= virtualUserCount; index++) {
            UUID userId = UUID.nameUUIDFromBytes((simulationId + ":" + index).getBytes(StandardCharsets.UTF_8));
            users.add(new VirtualUserView(
                    userId,
                    "사용자 " + index,
                    ParticipantType.AI,
                    VirtualUserStatus.QUEUED,
                    null,
                    List.of(new TimelineEntry("생성", "가상 사용자가 생성되었습니다.")),
                    0,
                    0,
                    0,
                    null,
                    null
            ));
        }
        return users;
    }
}
