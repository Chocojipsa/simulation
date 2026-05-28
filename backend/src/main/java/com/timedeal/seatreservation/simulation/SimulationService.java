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
        return createSimulation(simulationId, request.virtualUserCount());
    }

    public SimulationResponse createSimulation(UUID simulationId, int virtualUserCount) {
        SimulationSnapshot snapshot = stateStore.create(simulationId, virtualUserCount);
        if (inventoryService != null) {
            inventoryService.initialize(snapshot, virtualUserCount);
        }
        return new SimulationResponse(
                simulationId,
                "시뮬레이션이 생성되었습니다.",
                virtualUserCount,
                serverIdentity.id()
        );
    }

    public SimulationResponse resetSimulation(UUID simulationId, int virtualUserCount) {
        if (inventoryService != null) {
            inventoryService.resetSimulation(simulationId);
        }
        SimulationSnapshot snapshot = stateStore.create(simulationId, virtualUserCount);
        if (inventoryService != null) {
            inventoryService.initialize(snapshot, virtualUserCount);
        }
        return new SimulationResponse(
                simulationId,
                "?쒕??덉씠?섏씠 珥덇린?붾릺?덉뒿?덈떎.",
                virtualUserCount,
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

    public VirtualUserCommandResponse enterParticipantQueue(UUID simulationId, UUID participantId) {
        return enterQueue(simulationId, participantId);
    }

    public VirtualUserCommandResponse holdExplicitSeat(UUID simulationId, UUID participantId, long seatId) {
        if (!admitIfPossible(simulationId, participantId)) {
            stateStore.recordWaiting(simulationId, participantId, serverIdentity.id());
            return new VirtualUserCommandResponse(
                    simulationId,
                    participantId,
                    "WAITING",
                    serverIdentity.id(),
                    "아직 대기 중입니다.",
                    null
            );
        }

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        SeatView seat = snapshot.seats().stream()
                .filter(candidate -> candidate.id() == seatId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        if (seatReservationService == null) {
            return recordSeatConflict(simulationId, participantId, seat);
        }

        String idempotencyKey = simulationId + ":" + participantId + ":" + seat.id();
        SeatReservationResult result = seatReservationService.holdSeat(simulationId, participantId, seat.id(), idempotencyKey);
        if (result.outcome() == SeatReservationOutcome.ALREADY_HELD) {
            return recordSeatConflict(simulationId, participantId, seat);
        }

        stateStore.recordSeatHeldForPayment(simulationId, participantId, seat, result.reservationId(), serverIdentity.id());
        return new VirtualUserCommandResponse(
                simulationId,
                participantId,
                "PAYMENT_PENDING",
                serverIdentity.id(),
                seat.label() + " 좌석을 선점했습니다. 결제를 확인해 주세요.",
                seat.label()
        );
    }

    public VirtualUserCommandResponse confirmPayment(UUID simulationId, UUID participantId) {
        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        VirtualUserView participant = snapshot.users().stream()
                .filter(user -> user.id().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));
        SeatView seat = snapshot.seats().stream()
                .filter(candidate -> candidate.label().equals(participant.selectedSeatLabel()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected seat not found for participant: " + participantId));

        Long reservationId = stateStore.markPaymentRequestedByParticipant(simulationId, participantId, serverIdentity.id());
        if (paymentKafkaTemplate != null && reservationId != null) {
            publishPaymentRequest(simulationId, participantId, seat, new SeatReservationResult(
                    SeatReservationOutcome.HELD,
                    reservationId,
                    seat.id(),
                    participantId,
                    simulationId + ":" + participantId + ":" + seat.id()
            ));
        }
        return new VirtualUserCommandResponse(
                simulationId,
                participantId,
                "PAYMENT_REQUESTED",
                serverIdentity.id(),
                "결제 확인 요청을 보냈습니다.",
                seat.label()
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
            if (hasPendingPaymentSeats(snapshot)) {
                stateStore.recordSeatSelectionWaiting(simulationId, userId, serverIdentity.id());
                return new VirtualUserCommandResponse(
                        simulationId,
                        userId,
                        "WAITING",
                        serverIdentity.id(),
                        "결제 결과를 기다린 뒤 다시 좌석을 선택합니다.",
                        null
                );
            }
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

    private boolean hasPendingPaymentSeats(SimulationSnapshot snapshot) {
        return snapshot.seats().stream().anyMatch(seat ->
                seat.status() == SeatStatus.HELD || seat.status() == SeatStatus.PAYMENT_IN_PROGRESS
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
