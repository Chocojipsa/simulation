package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.generator.TrafficGeneratorClient;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.payment.PaymentRequestedEvent;
import com.timedeal.seatreservation.queue.WaitingQueueService;
import com.timedeal.seatreservation.seat.SeatReservationOutcome;
import com.timedeal.seatreservation.seat.SeatReservationResult;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        SimulationService service = service(stateStore, waitingQueue, null, null);

        VirtualUserCommandResponse response = service.enterQueue(simulationId, userId);

        verify(waitingQueue).enterQueue(simulationId.toString(), userId.toString());
        verify(stateStore).registerQueueEntry(simulationId, userId, "api-test");
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.message()).isEqualTo("대기열에 진입했습니다.");
    }

    @Test
    void seatAttemptReturnsWaitingWhenAdmissionTokenIsMissing() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        WaitingQueueService waitingQueue = mock(WaitingQueueService.class);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000131");
        when(waitingQueue.hasAdmissionToken(simulationId.toString(), userId.toString())).thenReturn(false);
        when(waitingQueue.pickAdmissionCandidates(simulationId.toString(), 10)).thenReturn(List.of());
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
                new ServerIdentity("api-test"),
                trafficGeneratorClient,
                null,
                waitingQueue,
                seatReservationService,
                kafkaTemplate,
                new Random(1)
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
                        null
                )),
                new SimulationMetrics(1, 0, 0, 0, 0, 0),
                List.of(),
                true
        );
    }
}
