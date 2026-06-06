package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.generator.TrafficGeneratorClient;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.payment.PaymentRequestedEvent;
import com.timedeal.seatreservation.queue.WaitingQueueService;
import com.timedeal.seatreservation.seat.SeatReservationOutcome;
import com.timedeal.seatreservation.seat.SeatReservationResult;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationServiceTest {
    private final SimulationStateGateway stateStore = new SimulationStateStore();
    private final SimulationService simulationService = new SimulationService(stateStore);

    @Test
    void createSimulationCreatesInitialSnapshot() {
        SimulationResponse response = simulationService.createSimulation(new CreateSimulationRequest(25));

        SimulationSnapshot snapshot = simulationService.getSimulation(response.simulationId());

        assertThat(response.virtualUserCount()).isEqualTo(25);
        assertThat(response.message()).isEqualTo("시뮬레이션이 생성되었습니다.");
        assertThat(response.handledBy()).isEqualTo("api-test");
        assertThat(snapshot.simulationId()).isEqualTo(response.simulationId());
        assertThat(snapshot.seats()).hasSize(120);
        assertThat(snapshot.users()).hasSize(25);
        assertThat(snapshot.seats()).allMatch(seat -> seat.status() == SeatStatus.AVAILABLE);
        assertThat(snapshot.users()).allMatch(user -> user.status() == VirtualUserStatus.QUEUED);
        assertThat(snapshot.metrics().queueSize()).isEqualTo(25);
        assertThat(snapshot.running()).isFalse();
    }

    @Test
    void enterQueueRegistersUserInRedisQueueAndSnapshot() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000130");
        when(stateStore.snapshot(simulationId)).thenReturn(new SimulationSnapshot(
                simulationId,
                List.of(),
                List.of(),
                new SimulationMetrics(0, 0, 0, 0, 0, 0),
                List.of(),
                true
        ));
        when(stateStore.participant(simulationId, userId)).thenReturn(new VirtualUserView(
                userId,
                "사용자 1",
                ParticipantType.HUMAN,
                VirtualUserStatus.WAITING_ROOM,
                null,
                List.of(),
                0,
                0,
                0,
                null,
                null
        ));
        SimulationService service = service(stateStore, waitingQueue, null, null);

        VirtualUserCommandResponse response = service.enterQueue(simulationId, userId);

        verify(waitingQueue).enterQueue(simulationId.toString(), userId.toString());
        verify(stateStore).registerQueueEntry(simulationId, userId, "api-test");
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.message()).isEqualTo("대기열에 진입했습니다. 아직 좌석을 선택할 수 없습니다.");
    }

    @Test
    void queuedParticipantCanBeAdmittedByQueueStatusCheck() {
        SimulationStateGateway stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000037");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000137");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, userId, "Kwon", ParticipantType.HUMAN, "api-test");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString()))
                .thenReturn(false, true);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 1))
                .thenReturn(List.of(userId.toString()));
        SimulationService service = service(stateStore, waitingQueue, null, null);

        VirtualUserCommandResponse queued = service.enterQueue(simulationId, userId);
        VirtualUserCommandResponse admitted = service.enterQueue(simulationId, userId);

        assertThat(queued.status()).isEqualTo("QUEUED");
        assertThat(admitted.status()).isEqualTo("ADMITTED");
        assertThat(admitted.message()).isEqualTo("대기열을 통과했습니다. 좌석을 선택해 주세요.");
        assertThat(stateStore.participant(simulationId, userId).status()).isEqualTo(VirtualUserStatus.SELECTING_SEAT);
        verify(waitingQueue, times(1)).enterQueue(simulationId.toString(), userId.toString());
    }

    @Test
    void seatHoldDoesNotAdmitQueuedParticipantImplicitly() {
        SimulationStateGateway stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000038");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000138");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, participantId, "Kwon", ParticipantType.HUMAN, "api-test");
        stateStore.registerQueueEntry(simulationId, participantId, "api-test");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), participantId.toString()))
                .thenReturn(false, true);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 1))
                .thenReturn(List.of(participantId.toString()));
        when(seatReservationService.holdSeat(eq(simulationId), eq(participantId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.HELD, 102L, 1L, participantId, "hold"));
        SimulationService service = service(stateStore, waitingQueue, seatReservationService, null);

        VirtualUserCommandResponse response = service.holdExplicitSeat(simulationId, participantId, 1L);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.message()).isEqualTo("대기열 대기 중입니다. 통과 후 좌석을 선택할 수 있습니다.");
        verify(seatReservationService, times(0)).holdSeat(any(), any(), anyLong(), any());
    }

    @Test
    void queuedParticipantWaitsWhenActiveAdmissionSlotIsFull() {
        SimulationStateGateway stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000040");
        UUID activeUserId = UUID.fromString("00000000-0000-0000-0000-000000000140");
        UUID queuedUserId = UUID.fromString("00000000-0000-0000-0000-000000000141");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, activeUserId, "앞사람", ParticipantType.HUMAN, "api-test");
        stateStore.registerParticipant(simulationId, queuedUserId, "뒷사람", ParticipantType.HUMAN, "api-test");
        stateStore.registerQueueEntry(simulationId, queuedUserId, "api-test");
        stateStore.recordAdmitted(simulationId, activeUserId, "api-test");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), queuedUserId.toString())).thenReturn(false);
        SimulationService service = service(stateStore, waitingQueue, null, null, 1, 1);

        VirtualUserCommandResponse response = service.enterQueue(simulationId, queuedUserId);

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(stateStore.participant(simulationId, queuedUserId).status()).isEqualTo(VirtualUserStatus.QUEUED);
        verify(waitingQueue, times(0)).pickAdmissionCandidates(simulationId.toString(), 1);
    }

    @Test
    void queuedParticipantIsAdmittedWhenActiveAdmissionSlotIsAvailable() {
        SimulationStateGateway stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        UUID queuedUserId = UUID.fromString("00000000-0000-0000-0000-000000000142");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, queuedUserId, "뒷사람", ParticipantType.HUMAN, "api-test");
        stateStore.registerQueueEntry(simulationId, queuedUserId, "api-test");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), queuedUserId.toString()))
                .thenReturn(false, true);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 1))
                .thenReturn(List.of(queuedUserId.toString()));
        SimulationService service = service(stateStore, waitingQueue, null, null, 1, 1);

        VirtualUserCommandResponse response = service.enterQueue(simulationId, queuedUserId);

        assertThat(response.status()).isEqualTo("ADMITTED");
        assertThat(stateStore.participant(simulationId, queuedUserId).status()).isEqualTo(VirtualUserStatus.SELECTING_SEAT);
    }

    @Test
    void queuePollMarksFrontParticipantActiveEvenWhenCallerIsBehind() {
        SimulationStateGateway stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        UUID frontUserId = UUID.fromString("00000000-0000-0000-0000-000000000143");
        UUID behindUserId = UUID.fromString("00000000-0000-0000-0000-000000000144");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, frontUserId, "앞사람", ParticipantType.HUMAN, "api-test");
        stateStore.registerParticipant(simulationId, behindUserId, "뒷사람", ParticipantType.AI, "api-test");
        stateStore.registerQueueEntry(simulationId, frontUserId, "api-test");
        stateStore.registerQueueEntry(simulationId, behindUserId, "api-test");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), behindUserId.toString())).thenReturn(false);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 1))
                .thenReturn(List.of(frontUserId.toString()));
        SimulationService service = service(stateStore, waitingQueue, null, null, 1, 1);

        VirtualUserCommandResponse response = service.enterQueue(simulationId, behindUserId);

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(stateStore.participant(simulationId, frontUserId).status()).isEqualTo(VirtualUserStatus.SELECTING_SEAT);
        assertThat(stateStore.participant(simulationId, behindUserId).status()).isEqualTo(VirtualUserStatus.QUEUED);
    }

    @Test
    void seatAttemptReturnsWaitingWhenAdmissionTokenIsMissing() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000131");
        when(stateStore.snapshot(simulationId)).thenReturn(snapshot(simulationId, userId, new SeatView(1L, "A-1", SeatStatus.AVAILABLE)));
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(false);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 1)).thenReturn(List.of());
        SimulationService service = service(stateStore, waitingQueue, null, null);

        VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

        verify(stateStore).recordWaiting(simulationId, userId, "api-test");
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.message()).isEqualTo("아직 대기 중입니다.");
    }

    @Test
    void seatAttemptRecordsConflictWhenPostgresHoldLosesRace() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000032");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000132");
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.AVAILABLE);
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(true);
        when(stateStore.snapshot(simulationId)).thenReturn(snapshot(simulationId, userId, seat));
        when(seatReservationService.holdSeat(eq(simulationId), eq(userId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.ALREADY_HELD, null, 1L, userId, "hold"));
        when(stateStore.recordSeatConflict(simulationId, userId, seat, "api-test"))
                .thenReturn(snapshot(simulationId, userId, seat));
        SimulationService service = service(stateStore, waitingQueue, seatReservationService, null);

        VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

        verify(stateStore).recordSeatConflict(simulationId, userId, seat, "api-test");
        assertThat(response.status()).isEqualTo("RETRY");
        assertThat(response.message()).isEqualTo("이미 선택된 좌석입니다: A-1");
    }

    @Test
    void seatAttemptReturnsFailedWhenConflictReachesAttemptLimit() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000034");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000134");
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.AVAILABLE);
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(true);
        when(stateStore.snapshot(simulationId)).thenReturn(snapshot(simulationId, userId, seat));
        when(seatReservationService.holdSeat(eq(simulationId), eq(userId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.ALREADY_HELD, null, 1L, userId, "hold"));
        when(stateStore.recordSeatConflict(simulationId, userId, seat, "api-test")).thenReturn(new SimulationSnapshot(
                simulationId,
                List.of(seat),
                List.of(new VirtualUserView(
                        userId,
                        "user 1",
                        ParticipantType.AI,
                        VirtualUserStatus.FAILED,
                        "A-1",
                        List.of(new TimelineEntry("attempt", "attempt")),
                        30,
                        30,
                        0,
                        null,
                        null
                )),
                new SimulationMetrics(0, 0, 0, 0, 0, 30),
                List.of(),
                true
        ));
        SimulationService service = service(stateStore, waitingQueue, seatReservationService, null);

        VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

        assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    void seatAttemptReturnsFailedWhenNoSeatIsAvailable() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000035");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000135");
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.RESERVED);
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(true);
        when(stateStore.snapshot(simulationId)).thenReturn(snapshot(simulationId, userId, seat));
        SimulationService service = service(stateStore, waitingQueue, null, null);

        VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

        verify(stateStore).recordNoSeatAvailable(simulationId, userId, "api-test");
        assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    void seatAttemptWaitsWhenNoSeatIsAvailableButPaymentsAreStillInProgress() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000036");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000136");
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.PAYMENT_IN_PROGRESS);
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(true);
        when(stateStore.snapshot(simulationId)).thenReturn(snapshot(simulationId, userId, seat));
        SimulationService service = service(stateStore, waitingQueue, null, null);

        VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

        verify(stateStore).recordSeatSelectionWaiting(simulationId, userId, "api-test");
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.message()).isEqualTo("결제 결과를 기다린 뒤 다시 좌석을 선택합니다.");
    }

    @Test
    void seatAttemptPublishesPaymentRequestAfterSuccessfulHold() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        KafkaTemplate<String, PaymentRequestedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000033");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000133");
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.AVAILABLE);
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(true);
        when(stateStore.snapshot(simulationId)).thenReturn(snapshot(simulationId, userId, seat));
        when(seatReservationService.holdSeat(eq(simulationId), eq(userId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.HELD, 101L, 1L, userId, "hold"));
        SimulationService service = service(stateStore, waitingQueue, seatReservationService, kafkaTemplate);

        VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

        verify(stateStore).recordPaymentRequested(simulationId, userId, seat, "api-test");
        verify(kafkaTemplate).send(eq("payment.events"), eq("101"), any(PaymentRequestedEvent.class));
        assertThat(response.status()).isEqualTo("PAYMENT_REQUESTED");
        assertThat(response.selectedSeatLabel()).isEqualTo("A-1");
    }

    @Test
    void participantCannotHoldMultipleSeats() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, participantId, "Kwon", ParticipantType.HUMAN, "api-test");
        stateStore.registerQueueEntry(simulationId, participantId, "api-test");
        stateStore.recordAdmitted(simulationId, participantId, "api-test");
        when(seatReservationService.holdSeat(eq(simulationId), eq(participantId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.HELD, 100L, 1L, participantId, "hold-1"));
        SimulationService service = new SimulationService(
                stateStore,
                new SimulationEventHub(null, null),
                null,
                new ServerIdentity("api-test"),
                (id, request) -> {
                },
                null,
                null,
                seatReservationService,
                null,
                new Random(1)
        );

        VirtualUserCommandResponse first = service.holdExplicitSeat(simulationId, participantId, 1L);
        VirtualUserCommandResponse second = service.holdExplicitSeat(simulationId, participantId, 2L);

        assertThat(first.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(second.status()).isEqualTo("ALREADY_HOLDING");
        assertThat(second.selectedSeatLabel()).isEqualTo("A-1");
        verify(seatReservationService, times(1)).holdSeat(eq(simulationId), eq(participantId), anyLong(), any());
    }

    @Test
    void heldSeatExpiresWhenPaymentIsNotConfirmedInTime() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000039");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000139");
        Instant openedAt = Instant.parse("2026-05-28T12:00:00Z");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, participantId, "Kwon", ParticipantType.HUMAN, "api-test");
        stateStore.registerQueueEntry(simulationId, participantId, "api-test");
        stateStore.recordAdmitted(simulationId, participantId, "api-test");
        when(seatReservationService.holdSeat(eq(simulationId), eq(participantId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.HELD, 103L, 1L, participantId, "hold"));
        SimulationService service = service(
                stateStore,
                null,
                seatReservationService,
                null,
                Clock.fixed(openedAt, ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        service.holdExplicitSeat(simulationId, participantId, 1L);

        SimulationService afterExpiry = service(
                stateStore,
                null,
                seatReservationService,
                null,
                Clock.fixed(openedAt.plusSeconds(61), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );
        SimulationSnapshot expired = afterExpiry.getSimulation(simulationId);

        assertThat(expired.users().get(0).status()).isEqualTo(VirtualUserStatus.EXPIRED);
        assertThat(expired.users().get(0).selectedSeatLabel()).isNull();
        assertThat(expired.seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
        verify(seatReservationService).expireHold(simulationId, 103L, 1L);
    }

    @Test
    void admittedParticipantExpiresWhenSeatIsNotSelectedInTime() {
        SimulationStateStore stateStore = new SimulationStateStore();
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000043");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000145");
        Instant openedAt = Instant.parse("2026-05-28T12:00:00Z");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, participantId, "Kwon", ParticipantType.HUMAN, "api-test");
        SimulationService service = service(
                stateStore,
                null,
                null,
                null,
                Clock.fixed(openedAt, ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );
        service.enterQueue(simulationId, participantId);
        service.enterQueue(simulationId, participantId);

        SimulationService afterExpiry = service(
                stateStore,
                null,
                null,
                null,
                Clock.fixed(openedAt.plusSeconds(16), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );
        SimulationSnapshot expired = afterExpiry.getSimulation(simulationId);

        assertThat(expired.users().get(0).status()).isEqualTo(VirtualUserStatus.EXPIRED);
        assertThat(expired.users().get(0).seatHoldExpiresAt()).isNull();
    }

    @Test
    void heldSeatDoesNotBlockNextQueuedParticipantAdmission() {
        SimulationStateStore stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000044");
        UUID holdingUserId = UUID.fromString("00000000-0000-0000-0000-000000000146");
        UUID queuedUserId = UUID.fromString("00000000-0000-0000-0000-000000000147");
        stateStore.create(simulationId, 0);
        stateStore.registerParticipant(simulationId, holdingUserId, "선점자", ParticipantType.HUMAN, "api-test");
        stateStore.registerParticipant(simulationId, queuedUserId, "다음 사람", ParticipantType.HUMAN, "api-test");
        stateStore.registerQueueEntry(simulationId, holdingUserId, "api-test");
        stateStore.recordAdmitted(simulationId, holdingUserId, "api-test");
        when(seatReservationService.holdSeat(eq(simulationId), eq(holdingUserId), eq(1L), any()))
                .thenReturn(new SeatReservationResult(SeatReservationOutcome.HELD, 104L, 1L, holdingUserId, "hold"));
        stateStore.registerQueueEntry(simulationId, queuedUserId, "api-test");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), queuedUserId.toString()))
                .thenReturn(false, true);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 1))
                .thenReturn(List.of(queuedUserId.toString()));
        SimulationService service = service(stateStore, waitingQueue, seatReservationService, null, 1, 1);

        VirtualUserCommandResponse hold = service.holdExplicitSeat(simulationId, holdingUserId, 1L);
        VirtualUserCommandResponse admitted = service.enterQueue(simulationId, queuedUserId);

        assertThat(hold.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(admitted.status()).isEqualTo("ADMITTED");
        assertThat(stateStore.participant(simulationId, queuedUserId).status()).isEqualTo(VirtualUserStatus.SELECTING_SEAT);
    }

    @Test
    void resetSimulationClearsRedisWaitingQueue() {
        SimulationStateStore stateStore = new SimulationStateStore();
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000045");
        SimulationService service = service(stateStore, waitingQueue, null, null);

        service.resetSimulation(simulationId, 0);

        verify(waitingQueue).clearQueue(simulationId.toString());
    }

    @Test
    void releaseSeatInvokesExpireHoldAndPublishesSnapshot() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        SimulationEventHub eventHub = mock(SimulationEventHub.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000050");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000150");
        SeatView seat = new SeatView(1L, "A-1", SeatStatus.HELD);
        SimulationSnapshot initial = new SimulationSnapshot(
                simulationId,
                List.of(seat),
                List.of(new VirtualUserView(
                        userId,
                        "user 1",
                        ParticipantType.AI,
                        VirtualUserStatus.SEAT_HELD,
                        "A-1",
                        List.of(),
                        1,
                        0,
                        0,
                        103L,
                        null
                )),
                new SimulationMetrics(0, 1, 1, 0, 0, 0),
                List.of(),
                true
        );
        SimulationSnapshot updated = new SimulationSnapshot(
                simulationId,
                List.of(new SeatView(1L, "A-1", SeatStatus.AVAILABLE)),
                List.of(new VirtualUserView(
                        userId,
                        "user 1",
                        ParticipantType.AI,
                        VirtualUserStatus.SELECTING_SEAT,
                        null,
                        List.of(),
                        1,
                        0,
                        0,
                        null,
                        null
                )),
                new SimulationMetrics(0, 1, 0, 0, 0, 0),
                List.of(),
                true
        );

        when(stateStore.snapshot(simulationId)).thenReturn(initial);
        when(stateStore.releaseSeat(simulationId, userId, "api-test")).thenReturn(updated);

        SimulationService service = new SimulationService(
                stateStore,
                eventHub,
                null,
                new ServerIdentity("api-test"),
                (simId, req) -> {},
                null,
                null,
                seatReservationService,
                null,
                new java.util.Random(1),
                java.time.Clock.systemUTC(),
                java.time.Duration.ofSeconds(60),
                java.time.Duration.ofSeconds(15),
                1,
                1
        );

        service.releaseSeat(simulationId, userId);

        verify(seatReservationService).expireHold(simulationId, 103L, 1L);
        verify(stateStore).releaseSeat(simulationId, userId, "api-test");
        verify(eventHub).publish(updated);
    }

    private SimulationService service(
            SimulationStateGateway stateStore,
            WaitingQueueService waitingQueue,
            SeatReservationService seatReservationService,
            KafkaTemplate<String, PaymentRequestedEvent> kafkaTemplate
    ) {
        TrafficGeneratorClient trafficGeneratorClient = (simulationId, request) -> {
        };
        return new SimulationService(
                stateStore,
                new SimulationEventHub(null, null),
                null,
                new ServerIdentity("api-test"),
                trafficGeneratorClient,
                null,
                waitingQueue,
                seatReservationService,
                kafkaTemplate,
                new Random(1)
        );
    }

    private SimulationService service(
            SimulationStateGateway stateStore,
            WaitingQueueService waitingQueue,
            SeatReservationService seatReservationService,
            KafkaTemplate<String, PaymentRequestedEvent> kafkaTemplate,
            int admissionBatchSize,
            int maxActiveAdmissions
    ) {
        TrafficGeneratorClient trafficGeneratorClient = (simulationId, request) -> {
        };
        return new SimulationService(
                stateStore,
                new SimulationEventHub(null, null),
                null,
                new ServerIdentity("api-test"),
                trafficGeneratorClient,
                null,
                waitingQueue,
                seatReservationService,
                kafkaTemplate,
                new Random(1),
                Clock.systemUTC(),
                Duration.ofSeconds(60),
                Duration.ofSeconds(15),
                admissionBatchSize,
                maxActiveAdmissions
        );
    }

    private SimulationService service(
            SimulationStateGateway stateStore,
            WaitingQueueService waitingQueue,
            SeatReservationService seatReservationService,
            KafkaTemplate<String, PaymentRequestedEvent> kafkaTemplate,
            Clock clock,
            Duration seatHoldTtl
    ) {
        TrafficGeneratorClient trafficGeneratorClient = (simulationId, request) -> {
        };
        return new SimulationService(
                stateStore,
                new SimulationEventHub(null, null),
                null,
                new ServerIdentity("api-test"),
                trafficGeneratorClient,
                null,
                waitingQueue,
                seatReservationService,
                kafkaTemplate,
                new Random(1),
                clock,
                seatHoldTtl
        );
    }

    private SimulationSnapshot snapshot(UUID simulationId, UUID userId, SeatView seat) {
        return new SimulationSnapshot(
                simulationId,
                List.of(seat),
                List.of(new VirtualUserView(
                        userId,
                        "사용자 1",
                        ParticipantType.AI,
                        VirtualUserStatus.QUEUED,
                        null,
                        List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                        0,
                        0,
                        0,
                        null,
                        null
                )),
                new SimulationMetrics(1, 0, 0, 0, 0, 0),
                List.of(),
                true
        );
    }
}
