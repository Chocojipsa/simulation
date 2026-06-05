package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.events.UserActivityPublisher;
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
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationService {
    private static final String PAYMENT_EVENTS_TOPIC = "payment.events";

    private final SimulationStateGateway stateStore;
    private final SimulationEventHub eventHub;
    private final UserActivityPublisher activityPublisher;
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
    private final Map<UUID, Instant> lastExpirationCheck = new ConcurrentHashMap<>();

    @Autowired
    public SimulationService(
            SimulationStateGateway stateStore,
            SimulationEventHub eventHub,
            UserActivityPublisher activityPublisher,
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
                eventHub,
                activityPublisher,
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
                new SimulationEventHub(null, null),
                null,
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
            SimulationEventHub eventHub,
            UserActivityPublisher activityPublisher,
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
                eventHub,
                activityPublisher,
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
            SimulationEventHub eventHub,
            UserActivityPublisher activityPublisher,
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
                eventHub,
                activityPublisher,
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
            SimulationEventHub eventHub,
            UserActivityPublisher activityPublisher,
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
        this.eventHub = eventHub;
        this.activityPublisher = activityPublisher;
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

    public void recordUserActivity(UUID simulationId, UUID userId, String label, String message) {
        stateStore.recordUserActivity(simulationId, userId, label, message);
        if (activityPublisher != null) {
            activityPublisher.publish(new UserActivityEvent(simulationId, userId, label, message));
        }
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
                "시뮬레이션이 초기화되었습니다.",
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
        if (participant.status() != VirtualUserStatus.WAITING_ROOM) {
            return new VirtualUserCommandResponse(simulationId, userId, participant.status().name(), serverIdentity.id(), "이미 대기열에 진입했거나 다른 상태입니다.", null);
        }

        waitingQueueService.enterQueue(simulationId.toString(), userId.toString());
        stateStore.registerQueueEntry(simulationId, userId, serverIdentity.id());
        return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.QUEUED.name(), serverIdentity.id(), "대기열에 진입했습니다.", null);
    }

    public VirtualUserCommandResponse postQueue(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = stateStore.participant(simulationId, userId);

        if (participant.status() == VirtualUserStatus.SELECTING_SEAT || participant.status() == VirtualUserStatus.SEAT_HELD || participant.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS) {
            return new VirtualUserCommandResponse(simulationId, userId, "ADMITTED", serverIdentity.id(), "이미 입장 허가되었습니다.", null);
        }

        if (participant.status() != VirtualUserStatus.QUEUED) {
            return new VirtualUserCommandResponse(simulationId, userId, participant.status().name(), serverIdentity.id(), "대기열에 있지 않습니다.", null);
        }

        if (admitIfPossible(simulationId, userId)) {
            return new VirtualUserCommandResponse(simulationId, userId, "ADMITTED", serverIdentity.id(), "입장 허가되었습니다.", null);
        }

        long position = waitingQueueService.position(simulationId.toString(), userId.toString());
        return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.QUEUED.name(), serverIdentity.id(), "대기 중입니다. 현재 순번: " + position, null);
    }

    public SeatHoldResponse holdRandomSeat(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = stateStore.participant(simulationId, userId);

        if (participant.status() != VirtualUserStatus.SELECTING_SEAT) {
            return new SeatHoldResponse(simulationId, userId, 0L, participant.status().name(), "좌석을 선택할 수 있는 상태가 아닙니다.", null, serverIdentity.id());
        }

        List<SeatView> availableSeats = stateStore.snapshot(simulationId).seats().stream()
                .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
                .toList();

        if (availableSeats.isEmpty()) {
            stateStore.recordNoSeatAvailable(simulationId, userId, serverIdentity.id());
            return new SeatHoldResponse(simulationId, userId, 0L, VirtualUserStatus.FAILED.name(), "선택 가능한 좌석이 없습니다.", null, serverIdentity.id());
        }

        SeatView target = availableSeats.get(random.nextInt(availableSeats.size()));
        SeatReservationResult result = seatReservationService.holdSeat(simulationId, userId, target.id(), "idempotency-" + userId + "-" + target.id());

        if (result.outcome() == SeatReservationOutcome.HELD) {
            Instant expiresAt = now().plus(seatHoldTtl);
            stateStore.recordSeatHeldForPayment(simulationId, userId, target, result.reservationId(), expiresAt, serverIdentity.id());
            return new SeatHoldResponse(simulationId, userId, target.id(), VirtualUserStatus.SEAT_HELD.name(), "좌석을 선점했습니다.", target.label(), serverIdentity.id());
        } else if (result.outcome() == SeatReservationOutcome.ALREADY_HELD || result.outcome() == SeatReservationOutcome.IDEMPOTENT_REPLAY) {
            stateStore.recordSeatConflict(simulationId, userId, target, serverIdentity.id());
            return new SeatHoldResponse(simulationId, userId, target.id(), VirtualUserStatus.SELECTING_SEAT.name(), "이미 선택된 좌석입니다.", target.label(), serverIdentity.id());
        }

        return new SeatHoldResponse(simulationId, userId, target.id(), VirtualUserStatus.SELECTING_SEAT.name(), "좌석 선점에 실패했습니다.", null, serverIdentity.id());
    }

    public PaymentConfirmResponse confirmPayment(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = stateStore.participant(simulationId, userId);

        if (participant.status() != VirtualUserStatus.SEAT_HELD) {
            return null; // Should not happen in normal flow
        }

        SeatView seat = stateStore.snapshot(simulationId).seats().stream()
                .filter(s -> s.label().equals(participant.selectedSeatLabel()))
                .findFirst()
                .orElse(null);

        stateStore.recordPaymentRequested(simulationId, userId, seat, serverIdentity.id());

        paymentKafkaTemplate.send(PAYMENT_EVENTS_TOPIC, userId.toString(), new PaymentRequestedEvent(
                simulationId,
                userId,
                participant.reservationId(),
                seat.id(),
                "payment-" + participant.reservationId(),
                serverIdentity.id()
        ));

        return new PaymentConfirmResponse(simulationId, userId, VirtualUserStatus.PAYMENT_IN_PROGRESS.name(), "결제 요청이 접수되었습니다.", serverIdentity.id());
    }

    // Compatibility methods for LiveEventService and SimulationController
    public VirtualUserCommandResponse enterParticipantQueue(UUID simulationId, UUID userId) {
        return enterQueue(simulationId, userId);
    }

    public VirtualUserCommandResponse attemptSeat(UUID simulationId, UUID userId) {
        SeatHoldResponse hold = holdRandomSeat(simulationId, userId);
        return new VirtualUserCommandResponse(simulationId, userId, hold.status(), serverIdentity.id(), hold.message(), hold.selectedSeatLabel());
    }

    public VirtualUserCommandResponse holdExplicitSeat(UUID simulationId, UUID userId, long seatId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = stateStore.participant(simulationId, userId);

        if (participant.status() != VirtualUserStatus.SELECTING_SEAT) {
            return new VirtualUserCommandResponse(simulationId, userId, participant.status().name(), serverIdentity.id(), "좌석을 선택할 수 있는 상태가 아닙니다.", null);
        }

        SeatView target = stateStore.snapshot(simulationId).seats().stream()
                .filter(s -> s.id() == seatId)
                .findFirst()
                .orElse(null);

        if (target == null || target.status() != SeatStatus.AVAILABLE) {
            stateStore.recordSeatConflict(simulationId, userId, target, serverIdentity.id());
            return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.SELECTING_SEAT.name(), serverIdentity.id(), "이미 선택된 좌석입니다.", null);
        }

        SeatReservationResult result = seatReservationService.holdSeat(simulationId, userId, target.id(), "idempotency-" + userId + "-" + target.id());

        if (result.outcome() == SeatReservationOutcome.HELD) {
            Instant expiresAt = now().plus(seatHoldTtl);
            stateStore.recordSeatHeldForPayment(simulationId, userId, target, result.reservationId(), expiresAt, serverIdentity.id());
            return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.SEAT_HELD.name(), serverIdentity.id(), "좌석을 선점했습니다.", target.label());
        }

        return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.SELECTING_SEAT.name(), serverIdentity.id(), "좌석 선점에 실패했습니다.", null);
    }

    private boolean admitIfPossible(UUID simulationId, UUID userId) {
        long activeCount = stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.status() == VirtualUserStatus.SELECTING_SEAT
                        || user.status() == VirtualUserStatus.SEAT_HELD
                        || user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS)
                .count();

        if (activeCount < maxActiveAdmissions) {
            waitingQueueService.issueAdmissionToken(simulationId.toString(), userId.toString());
            Instant expiresAt = now().plus(seatSelectionTtl);
            stateStore.recordAdmitted(simulationId, userId, expiresAt, serverIdentity.id());
            return true;
        }
        return false;
    }

    private void expireTimedOutParticipants(UUID simulationId) {
        Instant now = now();
        Instant lastCheck = lastExpirationCheck.get(simulationId);
        if (lastCheck != null && Duration.between(lastCheck, now).toMillis() < 1500) {
            return;
        }
        lastExpirationCheck.put(simulationId, now);

        stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.seatHoldExpiresAt() != null && user.seatHoldExpiresAt().isBefore(now))
                .forEach(user -> {
                    if (user.status() == VirtualUserStatus.SEAT_HELD || user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS) {
                        stateStore.expireSeatHold(simulationId, user.id(), serverIdentity.id());
                    } else if (user.status() == VirtualUserStatus.SELECTING_SEAT) {
                        stateStore.expireSeatSelection(simulationId, user.id(), serverIdentity.id());
                    }
                });
    }

    private Instant now() {
        return clock.instant();
    }
}
