package com.timedeal.seatreservation.simulation.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.payment.PaymentResultEvent;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.TimelineEntry;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisSimulationStateStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RedisSimulationStateStore store = new RedisSimulationStateStore(redis, objectMapper);

    @Test
    void createsDeterministicVirtualUserIdsThatMatchTrafficGenerator() {
        when(redis.opsForValue()).thenReturn(values);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        SimulationSnapshot snapshot = store.create(simulationId, 2);

        UUID firstExpected = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        UUID secondExpected = UUID.nameUUIDFromBytes((simulationId + ":2").getBytes(StandardCharsets.UTF_8));
        assertThat(snapshot.users()).extracting(user -> user.id()).containsExactly(firstExpected, secondExpected);
    }

    @Test
    void registerQueueEntryAddsUserTimelineAndServerStats() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = store.create(simulationId, 1);

        when(values.get("simulation:00000000-0000-0000-0000-000000000021:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.registerQueueEntry(simulationId, userId, "api-a");

        assertThat(updated.users().get(0).status()).isEqualTo(VirtualUserStatus.QUEUED);
        assertThat(updated.users().get(0).timeline()).anyMatch(entry -> entry.message().equals("대기열에 진입했습니다."));
        assertThat(updated.serverStats()).anyMatch(stats -> stats.serverId().equals("api-a") && stats.requestCount() == 1);
    }

    @Test
    void registerQueueEntryRetriesWhenSnapshotLockIsBusy() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE, Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = store.create(simulationId, 1);

        when(values.get("simulation:00000000-0000-0000-0000-000000000022:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.registerQueueEntry(simulationId, userId, "api-a");

        assertThat(updated.serverStats()).anyMatch(stats -> stats.serverId().equals("api-a"));
    }

    @Test
    void recordSeatConflictMarksUserFailedAtAttemptLimit() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000023");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.AVAILABLE);
        SimulationSnapshot initial = new SimulationSnapshot(
                simulationId,
                List.of(seat),
                List.of(new VirtualUserView(
                        userId,
                        "user 1",
                        ParticipantType.AI,
                        VirtualUserStatus.SELECTING_SEAT,
                        "A-1",
                        List.of(new TimelineEntry("attempt", "attempt")),
                        29,
                        29,
                        0,
                        null
                )),
                new SimulationMetrics(0, 1, 0, 0, 0, 29),
                List.of(new ServerStatsView("api-a", 29, 29, 0)),
                true
        );

        when(values.get("simulation:00000000-0000-0000-0000-000000000023:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.recordSeatConflict(simulationId, userId, seat, "api-a");

        assertThat(updated.users().get(0).status()).isEqualTo(VirtualUserStatus.FAILED);
        assertThat(updated.users().get(0).seatAttemptCount()).isEqualTo(30);
        assertThat(updated.users().get(0).conflictCount()).isEqualTo(30);
    }

    @Test
    void applyPaymentResultStopsRunningWhenNoUsersAreActive() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000024");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = new SimulationSnapshot(
                simulationId,
                List.of(new SeatView(1L, "A-1", SeatStatus.PAYMENT_IN_PROGRESS)),
                List.of(new VirtualUserView(
                        userId,
                        "user 1",
                        ParticipantType.AI,
                        VirtualUserStatus.PAYMENT_IN_PROGRESS,
                        "A-1",
                        List.of(new TimelineEntry("결제", "결제를 진행 중입니다.")),
                        1,
                        0,
                        0,
                        null
                )),
                new SimulationMetrics(0, 1, 0, 1, 0, 0),
                List.of(new ServerStatsView("api-a", 1, 0, 1)),
                true
        );

        when(values.get("simulation:00000000-0000-0000-0000-000000000024:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.applyPaymentResult(new PaymentResultEvent(
                simulationId,
                userId,
                1L,
                1L,
                true,
                "결제 성공",
                "worker"
        ));

        assertThat(updated.running()).isFalse();
    }

    @Test
    void failedPaymentReopensSeatForResaleAndKeepsSimulationRunningWhenUsersAreQueued() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000025");
        UUID payingUserId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        UUID queuedUserId = UUID.nameUUIDFromBytes((simulationId + ":2").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = new SimulationSnapshot(
                simulationId,
                List.of(new SeatView(1L, "A-1", SeatStatus.PAYMENT_IN_PROGRESS)),
                List.of(
                        new VirtualUserView(
                                payingUserId,
                                "user 1",
                                ParticipantType.AI,
                                VirtualUserStatus.PAYMENT_IN_PROGRESS,
                                "A-1",
                                List.of(new TimelineEntry("결제", "결제를 진행 중입니다.")),
                                1,
                                0,
                                0,
                                null
                        ),
                        new VirtualUserView(
                                queuedUserId,
                                "user 2",
                                ParticipantType.AI,
                                VirtualUserStatus.QUEUED,
                                null,
                                List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                                0,
                                0,
                                0,
                                null
                        )
                ),
                new SimulationMetrics(1, 1, 0, 1, 0, 0),
                List.of(new ServerStatsView("api-a", 1, 0, 1)),
                true
        );

        when(values.get("simulation:00000000-0000-0000-0000-000000000025:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.applyPaymentResult(new PaymentResultEvent(
                simulationId,
                payingUserId,
                1L,
                1L,
                false,
                "결제 실패",
                "worker"
        ));

        assertThat(updated.seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(updated.users().get(0).status()).isEqualTo(VirtualUserStatus.FAILED);
        assertThat(updated.running()).isTrue();
    }
}
