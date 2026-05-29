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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class SimulationService {
    private static final String PAYMENT_EVENTS_TOPIC = "payment.events";

    private final SimulationStateGateway stateStore;
    private final ServerIdentity serverIdentity;
    private final TrafficGeneratorClient trafficGeneratorClient;
    private final SimulationInventoryService inventoryService;
    private final WaitingQueueService waitingQueueService;
    private final SeatReservationService seatReservationService;
    private final KafkaTemplate<String, PaymentRequestedEvent> paymentKafkaTemplate;
    private final Random random;
    private final Clock clock;
    private final Duration seatHoldTtl;
    private final Duration seatSelectionTtl;
    private final int admissionBatchSize;
    private final int maxActiveAdmissions;

    @Autowired
    public SimulationService(
            SimulationStateGateway stateStore,
            ServerIdentity serverIdentity,
            ObjectProvider<TrafficGeneratorClient> trafficGeneratorClient,
            ObjectProvider<SimulationInventoryService> inventoryService,
            ObjectProvider<WaitingQueueService> waitingQueueService,
            ObjectProvider<SeatReservationService> seatReservationService,
            ObjectProvider<KafkaTemplate<String, PaymentRequestedEvent>> paymentKafkaTemplate,
            @Value("${payment.hold-ttl-seconds:60}") long seatHoldTtlSeconds,
            @Value("${waiting-queue.admission-batch-size:1}") int admissionBatchSize,
            @Value("${waiting-queue.max-active-admissions:1}") int maxActiveAdmissions,
            @Value("${waiting-queue.selection-ttl-seconds:15}") long seatSelectionTtlSeconds
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
                new Random(),
                Clock.systemUTC(),
                Duration.ofSeconds(seatHoldTtlSeconds),
                Duration.ofSeconds(seatSelectionTtlSeconds),
                admissionBatchSize,
                maxActiveAdmissions
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
                new Random(1),
                Clock.systemUTC(),
                Duration.ofSeconds(60),
                Duration.ofSeconds(15),
                1,
                1
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
        this(
                stateStore,
                serverIdentity,
                trafficGeneratorClient,
                inventoryService,
                waitingQueueService,
                seatReservationService,
                paymentKafkaTemplate,
                random,
                Clock.systemUTC(),
                Duration.ofSeconds(60),
                Duration.ofSeconds(15),
                1,
                1
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
            Random random,
            Clock clock,
            Duration seatHoldTtl
    ) {
        this(
                stateStore,
                serverIdentity,
                trafficGeneratorClient,
                inventoryService,
                waitingQueueService,
                seatReservationService,
                paymentKafkaTemplate,
                random,
                clock,
                seatHoldTtl,
                Duration.ofSeconds(15),
                1,
                1
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
            Random random,
            Clock clock,
            Duration seatHoldTtl,
            Duration seatSelectionTtl,
            int admissionBatchSize,
            int maxActiveAdmissions
    ) {
        this.stateStore = stateStore;
        this.serverIdentity = serverIdentity;
        this.trafficGeneratorClient = trafficGeneratorClient;
        this.inventoryService = inventoryService;
        this.waitingQueueService = waitingQueueService;
        this.seatReservationService = seatReservationService;
        this.paymentKafkaTemplate = paymentKafkaTemplate;
        this.random = random;
        this.clock = clock;
        this.seatHoldTtl = seatHoldTtl;
        this.seatSelectionTtl = seatSelectionTtl;
        this.admissionBatchSize = Math.max(1, admissionBatchSize);
        this.maxActiveAdmissions = Math.max(1, maxActiveAdmissions);
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
        if (waitingQueueService != null) {
            waitingQueueService.clearQueue(simulationId.toString());
        }
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
        expireTimedOutParticipants(simulationId);
        return stateStore.snapshot(simulationId);
    }

    public RunSimulationResponse runSimulation(UUID simulationId, RunSimulationRequest request) {
        stateStore.markRunning(simulationId);
        trafficGeneratorClient.start(simulationId, request);
        return new RunSimulationResponse(simulationId, request.virtualUserCount(), "RUNNING", serverIdentity.id());
    }

    public VirtualUserCommandResponse enterQueue(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = stateStore.participant(simulationId, userId);
        boolean firstQueueEntry = participant.status() == VirtualUserStatus.WAITING_ROOM
                || participant.status() == VirtualUserStatus.PAYMENT_FAILED
                || participant.status() == VirtualUserStatus.FAILED
                || participant.status() == VirtualUserStatus.EXPIRED;
        if (firstQueueEntry && waitingQueueService != null) {
            waitingQueueService.enterQueue(simulationId.toString(), userId.toString());
        }
        if (firstQueueEntry) {
            stateStore.registerQueueEntry(simulationId, userId, serverIdentity.id());
        }
        if (!firstQueueEntry && participant.status() == VirtualUserStatus.QUEUED && admitIfPossible(simulationId, userId)) {
            markAdmittedIfStillQueued(simulationId, userId);
            return new VirtualUserCommandResponse(
                    simulationId,
                    userId,
                    "ADMITTED",
                    serverIdentity.id(),
                    "대기열을 통과했습니다. 좌석을 선택해 주세요.",
                    null
            );
        }
        if (participant.status() == VirtualUserStatus.SELECTING_SEAT) {
            return new VirtualUserCommandResponse(
                    simulationId,
                    userId,
                    "ADMITTED",
                    serverIdentity.id(),
                    "대기열을 통과했습니다. 좌석을 선택해 주세요.",
                    participant.selectedSeatLabel()
            );
        }
        return new VirtualUserCommandResponse(
                simulationId,
                userId,
                "QUEUED",
                serverIdentity.id(),
                "대기열에 진입했습니다. 아직 좌석을 선택할 수 없습니다.",
                null
        );
    }

    public VirtualUserCommandResponse enterParticipantQueue(UUID simulationId, UUID participantId) {
        return enterQueue(simulationId, participantId);
    }

    public VirtualUserCommandResponse holdExplicitSeat(UUID simulationId, UUID participantId, long seatId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = stateStore.participant(simulationId, participantId);
        if (participant.status() == VirtualUserStatus.SEAT_HELD
                || participant.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS
                || participant.status() == VirtualUserStatus.RESERVED) {
            return new VirtualUserCommandResponse(
                    simulationId,
                    participantId,
                    "ALREADY_HOLDING",
                    serverIdentity.id(),
                    "이미 선점한 좌석이 있습니다.",
                    participant.selectedSeatLabel()
            );
        }

        if (participant.status() != VirtualUserStatus.SELECTING_SEAT) {
            if (participant.status() == VirtualUserStatus.QUEUED) {
                stateStore.recordWaiting(simulationId, participantId, serverIdentity.id());
            }
            return new VirtualUserCommandResponse(
                    simulationId,
                    participantId,
                    "WAITING",
                    serverIdentity.id(),
                    "대기열 대기 중입니다. 통과 후 좌석을 선택할 수 있습니다.",
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

        Instant expiresAt = clock.instant().plus(seatHoldTtl);
        stateStore.recordSeatHeldForPayment(simulationId, participantId, seat, result.reservationId(), expiresAt, serverIdentity.id());
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
        expireTimedOutParticipants(simulationId);
        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        VirtualUserView participant = snapshot.users().stream()
                .filter(user -> user.id().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));
        if (participant.selectedSeatLabel() == null) {
            return new VirtualUserCommandResponse(
                    simulationId,
                    participantId,
                    "NO_HELD_SEAT",
                    serverIdentity.id(),
                    "결제할 선점 좌석이 없습니다.",
                    null
            );
        }
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
        expireTimedOutParticipants(simulationId);
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
        markAdmittedIfStillQueued(simulationId, userId);

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

    private void expireTimedOutParticipants(UUID simulationId) {
        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        Instant now = clock.instant();
        for (VirtualUserView user : snapshot.users()) {
            if (user.status() == VirtualUserStatus.SELECTING_SEAT
                    && user.seatHoldExpiresAt() != null
                    && !user.seatHoldExpiresAt().isAfter(now)) {
                stateStore.expireSeatSelection(simulationId, user.id(), serverIdentity.id());
                if (waitingQueueService != null) {
                    waitingQueueService.revokeAdmissionToken(simulationId.toString(), user.id().toString());
                }
                continue;
            }

            if (user.status() != VirtualUserStatus.SEAT_HELD
                    || user.seatHoldExpiresAt() == null
                    || user.seatHoldExpiresAt().isAfter(now)) {
                continue;
            }
            if (seatReservationService != null && user.reservationId() != null && user.selectedSeatLabel() != null) {
                snapshot.seats().stream()
                        .filter(seat -> seat.label().equals(user.selectedSeatLabel()))
                        .findFirst()
                        .ifPresent(seat -> seatReservationService.expireHold(simulationId, user.reservationId(), seat.id()));
            }
            stateStore.expireSeatHold(simulationId, user.id(), serverIdentity.id());
            if (waitingQueueService != null) {
                waitingQueueService.revokeAdmissionToken(simulationId.toString(), user.id().toString());
            }
        }
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

        int availableAdmissionSlots = maxActiveAdmissions - activeAdmissionCount(simulationId);
        if (availableAdmissionSlots <= 0) {
            return false;
        }

        int admissionLimit = Math.min(admissionBatchSize, availableAdmissionSlots);
        List<String> candidates = waitingQueueService.pickAdmissionCandidates(simulationKey, admissionLimit);
        for (String candidate : candidates) {
            waitingQueueService.issueAdmissionToken(simulationKey, candidate);
            waitingQueueService.removeAdmissionCandidate(simulationKey, candidate);
            stateStore.recordAdmitted(simulationId, UUID.fromString(candidate), seatSelectionExpiresAt(), serverIdentity.id());
        }
        return waitingQueueService.hasAdmissionToken(simulationKey, userKey);
    }

    private int activeAdmissionCount(UUID simulationId) {
        Instant now = clock.instant();
        return (int) stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.status() == VirtualUserStatus.SELECTING_SEAT
                        && (user.seatHoldExpiresAt() == null || user.seatHoldExpiresAt().isAfter(now)))
                .count();
    }

    private void markAdmittedIfStillQueued(UUID simulationId, UUID userId) {
        VirtualUserView current = stateStore.participant(simulationId, userId);
        if (current != null && current.status() == VirtualUserStatus.QUEUED) {
            stateStore.recordAdmitted(simulationId, userId, seatSelectionExpiresAt(), serverIdentity.id());
        }
    }

    private Instant seatSelectionExpiresAt() {
        return clock.instant().plus(seatSelectionTtl);
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
