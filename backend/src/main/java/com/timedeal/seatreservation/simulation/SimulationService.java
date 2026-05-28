package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.generator.TrafficGeneratorClient;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.payment.PaymentRequestedEvent;
import com.timedeal.seatreservation.queue.WaitingQueueService;
import com.timedeal.seatreservation.seat.SeatReservationOutcome;
import com.timedeal.seatreservation.seat.SeatReservationResult;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class SimulationService {
    private static final String PAYMENT_EVENTS_TOPIC = "payment.events";
    private static final int ADMISSION_BATCH_SIZE = 10;

    private final SimulationStateGateway stateStore;
    private final ServerIdentity serverIdentity;
    private final TrafficGeneratorClient trafficGeneratorClient;
    private final SimulationInventoryService inventoryService;
    private final WaitingQueueService waitingQueueService;
    private final SeatReservationService seatReservationService;
    private final KafkaTemplate<String, PaymentRequestedEvent> paymentKafkaTemplate;
    private final Random random;

    @Autowired
    public SimulationService(
            SimulationStateGateway stateStore,
            ServerIdentity serverIdentity,
            ObjectProvider<TrafficGeneratorClient> trafficGeneratorClient,
            ObjectProvider<SimulationInventoryService> inventoryService,
            ObjectProvider<WaitingQueueService> waitingQueueService,
            ObjectProvider<SeatReservationService> seatReservationService,
            ObjectProvider<KafkaTemplate<String, PaymentRequestedEvent>> paymentKafkaTemplate
    ) {
        this(
                stateStore,
                serverIdentity,
                trafficGeneratorClient.getIfAvailable(() -> (simulationId, request) -> {
                }),
                inventoryService.getIfAvailable(),
                waitingQueueService.getIfAvailable(),
                seatReservationService.getIfAvailable(),
                paymentKafkaTemplate.getIfAvailable(),
                new Random()
        );
    }

    SimulationService(SimulationStateGateway stateStore) {
        this(
                stateStore,
                new ServerIdentity("api-test"),
                (simulationId, request) -> {
                },
                null,
                null,
                null,
                null,
                new Random(1)
        );
    }

    SimulationService(
            SimulationStateGateway stateStore,
            ServerIdentity serverIdentity,
            TrafficGeneratorClient trafficGeneratorClient,
            SimulationInventoryService inventoryService,
            WaitingQueueService waitingQueueService,
            SeatReservationService seatReservationService,
            KafkaTemplate<String, PaymentRequestedEvent> paymentKafkaTemplate,
            Random random
    ) {
        this.stateStore = stateStore;
        this.serverIdentity = serverIdentity;
        this.trafficGeneratorClient = trafficGeneratorClient;
        this.inventoryService = inventoryService;
        this.waitingQueueService = waitingQueueService;
        this.seatReservationService = seatReservationService;
        this.paymentKafkaTemplate = paymentKafkaTemplate;
        this.random = random;
    }

    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        SimulationSnapshot snapshot = stateStore.create(simulationId, request.virtualUserCount());
        if (inventoryService != null) {
            inventoryService.initialize(snapshot, request.virtualUserCount());
        }
        return new SimulationResponse(
                simulationId,
                "시뮬레이션이 생성되었습니다.",
                request.virtualUserCount(),
                serverIdentity.id()
        );
    }

    public SimulationSnapshot getSimulation(UUID simulationId) {
        return stateStore.snapshot(simulationId);
    }

    public RunSimulationResponse runSimulation(UUID simulationId, RunSimulationRequest request) {
        stateStore.markRunning(simulationId);
        trafficGeneratorClient.start(simulationId, request);
        return new RunSimulationResponse(simulationId, request.virtualUserCount(), "RUNNING", serverIdentity.id());
    }

    public VirtualUserCommandResponse enterQueue(UUID simulationId, UUID userId) {
        if (waitingQueueService != null) {
            waitingQueueService.enterQueue(simulationId.toString(), userId.toString());
        }
        stateStore.registerQueueEntry(simulationId, userId, serverIdentity.id());
        return new VirtualUserCommandResponse(
                simulationId,
                userId,
                "QUEUED",
                serverIdentity.id(),
                "대기열에 진입했습니다.",
                null
        );
    }

    public VirtualUserCommandResponse attemptSeat(UUID simulationId, UUID userId) {
        if (!admitIfPossible(simulationId, userId)) {
            stateStore.recordWaiting(simulationId, userId, serverIdentity.id());
            return new VirtualUserCommandResponse(
                    simulationId,
                    userId,
                    "WAITING",
                    serverIdentity.id(),
                    "아직 대기 중입니다.",
                    null
            );
        }

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        List<SeatView> availableSeats = snapshot.seats().stream()
                .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
                .toList();
        if (availableSeats.isEmpty()) {
            stateStore.recordNoSeatAvailable(simulationId, userId, serverIdentity.id());
            return new VirtualUserCommandResponse(
                    simulationId,
                    userId,
                    "FAILED",
                    serverIdentity.id(),
                    "선택 가능한 좌석이 없습니다.",
                    null
            );
        }

        SeatView seat = availableSeats.get(random.nextInt(availableSeats.size()));
        if (seatReservationService == null) {
            return recordSeatConflict(simulationId, userId, seat);
        }

        String idempotencyKey = simulationId + ":" + userId + ":" + seat.id();
        SeatReservationResult result = seatReservationService.holdSeat(simulationId, userId, seat.id(), idempotencyKey);
        if (result.outcome() == SeatReservationOutcome.ALREADY_HELD) {
            return recordSeatConflict(simulationId, userId, seat);
        }

        stateStore.recordPaymentRequested(simulationId, userId, seat, serverIdentity.id());
        publishPaymentRequest(simulationId, userId, seat, result);
        return new VirtualUserCommandResponse(
                simulationId,
                userId,
                "PAYMENT_REQUESTED",
                serverIdentity.id(),
                seat.label() + " 좌석을 선택했습니다. 결제를 요청했습니다.",
                seat.label()
        );
    }

    private boolean admitIfPossible(UUID simulationId, UUID userId) {
        if (waitingQueueService == null) {
            return true;
        }
        String simulationKey = simulationId.toString();
        String userKey = userId.toString();
        if (waitingQueueService.hasAdmissionToken(simulationKey, userKey)) {
            return true;
        }

        List<String> candidates = waitingQueueService.pickAdmissionCandidates(simulationKey, ADMISSION_BATCH_SIZE);
        for (String candidate : candidates) {
            waitingQueueService.issueAdmissionToken(simulationKey, candidate);
            waitingQueueService.removeAdmissionCandidate(simulationKey, candidate);
        }
        return waitingQueueService.hasAdmissionToken(simulationKey, userKey);
    }

    private VirtualUserCommandResponse recordSeatConflict(UUID simulationId, UUID userId, SeatView seat) {
        SimulationSnapshot updated = stateStore.recordSeatConflict(simulationId, userId, seat, serverIdentity.id());
        String status = updated.users().stream()
                .filter(user -> user.id().equals(userId))
                .findFirst()
                .filter(user -> user.status() == VirtualUserStatus.FAILED)
                .map(user -> "FAILED")
                .orElse("RETRY");
        return new VirtualUserCommandResponse(
                simulationId,
                userId,
                status,
                serverIdentity.id(),
                "이미 선택된 좌석입니다: " + seat.label(),
                seat.label()
        );
    }

    private void publishPaymentRequest(
            UUID simulationId,
            UUID userId,
            SeatView seat,
            SeatReservationResult result
    ) {
        if (paymentKafkaTemplate == null || result.reservationId() == null) {
            return;
        }
        paymentKafkaTemplate.send(PAYMENT_EVENTS_TOPIC, String.valueOf(result.reservationId()), new PaymentRequestedEvent(
                simulationId,
                userId,
                result.reservationId(),
                seat.id(),
                "payment-" + result.reservationId(),
                serverIdentity.id()
        ));
    }
}
