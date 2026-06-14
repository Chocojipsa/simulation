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
import com.timedeal.seatreservation.payment.InProcessPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationService {

    private final SimulationStateGateway stateStore;
    private final SimulationEventHub eventHub;
    private final UserActivityPublisher activityPublisher;
    private final ServerIdentity serverIdentity;
    private final TrafficGeneratorClient trafficGeneratorClient;
    private final SimulationInventoryService inventoryService;
    private final WaitingQueueService waitingQueueService;
    private final SeatReservationService seatReservationService;
    private final InProcessPaymentService paymentService;
    private final Random random;
    private final Clock clock;
    private final Duration seatHoldTtl;
    private final Duration seatSelectionTtl;
    private final int admissionBatchSize;
    private final int maxActiveAdmissions;
    private final Map<UUID, Instant> lastExpirationCheck = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> localConcurrencies = new ConcurrentHashMap<>();

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
            ObjectProvider<InProcessPaymentService> paymentService,
            @Value("${payment.hold-ttl-seconds:60}") long seatHoldTtlSeconds,
            @Value("${waiting-queue.admission-batch-size:1}") int admissionBatchSize,
            @Value("${waiting-queue.max-active-admissions:1}") int maxActiveAdmissions,
            @Value("${waiting-queue.selection-ttl-seconds:60}") long seatSelectionTtlSeconds
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
                paymentService.getIfAvailable(),
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
            InProcessPaymentService paymentService,
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
                paymentService,
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
            InProcessPaymentService paymentService,
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
                paymentService,
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
            InProcessPaymentService paymentService,
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
        this.paymentService = paymentService;
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

    public void publishUserActivityDirectly(UUID simulationId, UUID userId, String label, String message) {
        if (activityPublisher != null) {
            activityPublisher.publish(new UserActivityEvent(simulationId, userId, label, message));
        }
    }

    public void publishQueuePositionsBatch(com.timedeal.seatreservation.queue.QueuePositionsBatchEvent event) {
        if (activityPublisher != null) {
            activityPublisher.publishBatch(event);
        }
    }

    public SimulationSnapshot updateParticipantName(UUID simulationId, UUID participantId, String displayName) {
        SimulationSnapshot updated = stateStore.updateParticipantName(simulationId, participantId, displayName);
        if (eventHub != null) {
            eventHub.publish(updated);
        }
        return updated;
    }

    public void saveConcurrency(UUID simulationId, int concurrency) {
        if (waitingQueueService != null) {
            try {
                waitingQueueService.saveConcurrency(simulationId.toString(), concurrency);
            } catch (Exception e) {
                localConcurrencies.put(simulationId, concurrency);
            }
        } else {
            localConcurrencies.put(simulationId, concurrency);
        }
    }

    public int getMaxActiveAdmissions(UUID simulationId) {
        if (waitingQueueService != null) {
            try {
                Integer concurrency = waitingQueueService.getConcurrency(simulationId.toString());
                if (concurrency != null && concurrency > 0) {
                    return concurrency;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return localConcurrencies.getOrDefault(simulationId, this.maxActiveAdmissions);
    }

    @Deprecated
    public int getMaxActiveAdmissions() {
        return this.maxActiveAdmissions;
    }

    public int getAdmissionsAvailable(UUID simulationId) {
        long currentActiveCount = stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.status() == VirtualUserStatus.SELECTING_SEAT
                        || user.status() == VirtualUserStatus.SEAT_HELD
                        || user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS)
                .count();
        return Math.max(0, getMaxActiveAdmissions(simulationId) - (int) currentActiveCount);
    }

    public void admitParticipant(UUID simulationId, UUID userId) {
        // Only admit if user is still in QUEUED status (prevents re-admitting expired/completed users)
        try {
            VirtualUserView participant = getParticipant(simulationId, userId);
            if (participant.status() != VirtualUserStatus.QUEUED) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        if (waitingQueueService != null) {
            // Always remove from Redis queue first to prevent phantom entries
            waitingQueueService.removeAdmissionCandidate(simulationId.toString(), userId.toString());
            waitingQueueService.issueAdmissionToken(simulationId.toString(), userId.toString());
        }
        Instant expiresAt = now().plus(seatSelectionTtl);
        SimulationSnapshot updated = stateStore.recordAdmitted(simulationId, userId, expiresAt, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updated);
        }
        publishUserActivityDirectly(simulationId, userId, "queue_admitted", "대기열을 통과했습니다! 좌석을 선택합니다.");
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
        saveConcurrency(simulationId, request.concurrency());
        stateStore.markRunning(simulationId);
        trafficGeneratorClient.start(simulationId, request);
        return new RunSimulationResponse(simulationId, request.virtualUserCount(), "RUNNING", serverIdentity.id());
    }

    public VirtualUserCommandResponse enterQueue(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = getParticipant(simulationId, userId);
        if (participant.status() != VirtualUserStatus.WAITING_ROOM) {
            return new VirtualUserCommandResponse(simulationId, userId, participant.status().name(), serverIdentity.id(), "이미 대기열에 진입했거나 다른 상태입니다.", null);
        }

        if (waitingQueueService != null) {
            waitingQueueService.enterQueue(simulationId.toString(), userId.toString());
        }
        SimulationSnapshot updated = stateStore.registerQueueEntry(simulationId, userId, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updated);
        }
        return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.QUEUED.name(), serverIdentity.id(), "대기열에 진입했습니다.", null);
    }

    public VirtualUserCommandResponse postQueue(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = getParticipant(simulationId, userId);

        if (participant.status() == VirtualUserStatus.SELECTING_SEAT || participant.status() == VirtualUserStatus.SEAT_HELD || participant.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS) {
            return new VirtualUserCommandResponse(simulationId, userId, "ADMITTED", serverIdentity.id(), "이미 입장 허가되었습니다.", null);
        }

        if (participant.status() != VirtualUserStatus.QUEUED) {
            return new VirtualUserCommandResponse(simulationId, userId, participant.status().name(), serverIdentity.id(), "대기열에 있지 않습니다.", null);
        }

        if (admitIfPossible(simulationId, userId)) {
            return new VirtualUserCommandResponse(simulationId, userId, "ADMITTED", serverIdentity.id(), "대기열을 통과했습니다. 좌석을 선택해 주세요.", null);
        }

        long position = waitingQueueService != null ? waitingQueueService.position(simulationId.toString(), userId.toString()) : 0L;
        return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.QUEUED.name(), serverIdentity.id(), "대기 중입니다. 현재 순번: " + position, null);
    }

    public SeatHoldResponse holdRandomSeat(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = getParticipant(simulationId, userId);

        if (participant.status() != VirtualUserStatus.SELECTING_SEAT) {
            return new SeatHoldResponse(simulationId, userId, 0L, participant.status().name(), "좌석을 선택할 수 있는 상태가 아닙니다.", null, serverIdentity.id());
        }

        List<SeatView> availableSeats = stateStore.snapshot(simulationId).seats().stream()
                .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
                .toList();

        if (availableSeats.isEmpty()) {
            boolean paymentsInProgress = stateStore.snapshot(simulationId).seats().stream()
                    .anyMatch(s -> s.status() == SeatStatus.HELD || s.status() == SeatStatus.PAYMENT_IN_PROGRESS);
            if (paymentsInProgress) {
                stateStore.recordSeatSelectionWaiting(simulationId, userId, serverIdentity.id());
                return new SeatHoldResponse(simulationId, userId, 0L, VirtualUserStatus.SELECTING_SEAT.name(), "결제 결과를 기다린 뒤 다시 좌석을 선택합니다.", null, serverIdentity.id());
            }
            stateStore.recordNoSeatAvailable(simulationId, userId, serverIdentity.id());
            return new SeatHoldResponse(simulationId, userId, 0L, VirtualUserStatus.FAILED.name(), "선택 가능한 좌석이 없습니다.", null, serverIdentity.id());
        }

        SeatView target = availableSeats.get(random.nextInt(availableSeats.size()));
        SeatReservationResult result = seatReservationService.holdSeat(simulationId, userId, target.id(), "idempotency-" + userId + "-" + target.id());

        if (result.outcome() == SeatReservationOutcome.HELD) {
            Instant expiresAt = now().plus(seatHoldTtl);
            SimulationSnapshot updated = stateStore.recordSeatHeldForPayment(simulationId, userId, target, result.reservationId(), expiresAt, serverIdentity.id());
            if (eventHub != null) {
                eventHub.publish(updated);
            }
            return new SeatHoldResponse(simulationId, userId, target.id(), VirtualUserStatus.SEAT_HELD.name(), "좌석을 선점했습니다.", target.label(), serverIdentity.id());
        } else if (result.outcome() == SeatReservationOutcome.ALREADY_HELD || result.outcome() == SeatReservationOutcome.IDEMPOTENT_REPLAY) {
            SimulationSnapshot updated = stateStore.recordSeatConflict(simulationId, userId, target, serverIdentity.id());
            if (eventHub != null) {
                eventHub.publish(updated);
            }
            VirtualUserView updatedUser = updated.users().stream()
                    .filter(u -> u.id().equals(userId))
                    .findFirst()
                    .orElse(null);
            String status = updatedUser != null ? updatedUser.status().name() : VirtualUserStatus.SELECTING_SEAT.name();
            return new SeatHoldResponse(simulationId, userId, target.id(), status, "이미 선택된 좌석입니다.", target.label(), serverIdentity.id());
        }

        return new SeatHoldResponse(simulationId, userId, target.id(), VirtualUserStatus.SELECTING_SEAT.name(), "좌석 선점에 실패했습니다.", null, serverIdentity.id());
    }

    public PaymentConfirmResponse confirmPayment(UUID simulationId, UUID userId) {
        expireTimedOutParticipants(simulationId);
        VirtualUserView participant = getParticipant(simulationId, userId);

        if (participant.status() != VirtualUserStatus.SEAT_HELD) {
            return null; // Should not happen in normal flow
        }

        SeatView seat = stateStore.snapshot(simulationId).seats().stream()
                .filter(s -> s.label().equals(participant.selectedSeatLabel()))
                .findFirst()
                .orElse(null);

        SimulationSnapshot updated = stateStore.recordPaymentRequested(simulationId, userId, seat, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updated);
        }

        if (paymentService != null) {
            paymentService.processPaymentAsync(new PaymentRequestedEvent(
                    simulationId,
                    userId,
                    participant.reservationId(),
                    seat.id(),
                    "payment-" + participant.reservationId(),
                    serverIdentity.id()
            ));
        }

        return new PaymentConfirmResponse(simulationId, userId, VirtualUserStatus.PAYMENT_IN_PROGRESS.name(), "결제 요청이 접수되었습니다.", serverIdentity.id());
    }

    public void releaseSeat(UUID simulationId, UUID userId) {
        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        VirtualUserView participant = snapshot.users().stream()
                .filter(user -> user.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + userId));
        if (participant.reservationId() != null && participant.selectedSeatLabel() != null) {
            SeatView seat = snapshot.seats().stream()
                    .filter(s -> s.label().equals(participant.selectedSeatLabel()))
                    .findFirst()
                    .orElse(null);
            if (seat != null && seatReservationService != null) {
                seatReservationService.expireHold(simulationId, participant.reservationId(), seat.id());
            }
        }
        SimulationSnapshot updatedSnapshot = stateStore.releaseSeat(simulationId, userId, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updatedSnapshot);
        }
    }

    public void failParticipant(UUID simulationId, UUID userId) {
        SimulationSnapshot updated = stateStore.recordNoSeatAvailable(simulationId, userId, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updated);
        }
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
        VirtualUserView participant = getParticipant(simulationId, userId);

        if (participant.status() != VirtualUserStatus.SELECTING_SEAT) {
            return new VirtualUserCommandResponse(simulationId, userId, participant.status().name(), serverIdentity.id(), "좌석을 선택할 수 있는 상태가 아닙니다.", null);
        }

        SeatView target = stateStore.snapshot(simulationId).seats().stream()
                .filter(s -> s.id() == seatId)
                .findFirst()
                .orElse(null);

        if (target == null || target.status() != SeatStatus.AVAILABLE) {
            SimulationSnapshot updated = stateStore.recordSeatConflict(simulationId, userId, target, serverIdentity.id());
            if (eventHub != null) {
                eventHub.publish(updated);
            }
            VirtualUserView updatedUser = updated.users().stream()
                    .filter(u -> u.id().equals(userId))
                    .findFirst()
                    .orElse(null);
            String status = updatedUser != null ? updatedUser.status().name() : VirtualUserStatus.SELECTING_SEAT.name();
            return new VirtualUserCommandResponse(simulationId, userId, status, serverIdentity.id(), "이미 선택된 좌석입니다.", null);
        }

        SeatReservationResult result = seatReservationService.holdSeat(simulationId, userId, target.id(), "idempotency-" + userId + "-" + target.id());

        if (result.outcome() == SeatReservationOutcome.HELD) {
            Instant expiresAt = now().plus(seatHoldTtl);
            SimulationSnapshot updated = stateStore.recordSeatHeldForPayment(simulationId, userId, target, result.reservationId(), expiresAt, serverIdentity.id());
            if (eventHub != null) {
                eventHub.publish(updated);
            }
            return new VirtualUserCommandResponse(simulationId, userId, VirtualUserStatus.SEAT_HELD.name(), serverIdentity.id(), "좌석을 선점했습니다.", target.label());
        }

        SimulationSnapshot updated = stateStore.recordSeatConflict(simulationId, userId, target, serverIdentity.id());
        if (eventHub != null) {
            eventHub.publish(updated);
        }
        VirtualUserView updatedUser = updated.users().stream()
                .filter(u -> u.id().equals(userId))
                .findFirst()
                .orElse(null);
        String status = updatedUser != null ? updatedUser.status().name() : VirtualUserStatus.SELECTING_SEAT.name();
        return new VirtualUserCommandResponse(simulationId, userId, status, serverIdentity.id(), "좌석 선점에 실패했습니다.", null);
    }

    private boolean admitIfPossible(UUID simulationId, UUID userId) {
        long activeCount = stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.status() == VirtualUserStatus.SELECTING_SEAT)
                .count();

        int maxActiveAdmissionsVal = getMaxActiveAdmissions(simulationId);
        if (activeCount < maxActiveAdmissionsVal) {
            if (waitingQueueService != null) {
                List<String> candidates = waitingQueueService.pickAdmissionCandidates(simulationId.toString(), 1);
                if (!candidates.isEmpty() && !candidates.get(0).equals(userId.toString())) {
                    UUID frontUserId = UUID.fromString(candidates.get(0));
                    admitParticipant(simulationId, frontUserId);
                    return false;
                }
                waitingQueueService.issueAdmissionToken(simulationId.toString(), userId.toString());
                waitingQueueService.removeAdmissionCandidate(simulationId.toString(), userId.toString());
            }
            Instant expiresAt = now().plus(seatSelectionTtl);
            SimulationSnapshot updated = stateStore.recordAdmitted(simulationId, userId, expiresAt, serverIdentity.id());
            if (eventHub != null) {
                eventHub.publish(updated);
            }
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

        List<UUID> seatHoldExpiredIds = new ArrayList<>();
        List<UUID> seatSelectionExpiredIds = new ArrayList<>();

        stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.seatHoldExpiresAt() != null && user.seatHoldExpiresAt().isBefore(now))
                .forEach(user -> {
                    if (user.status() == VirtualUserStatus.SEAT_HELD || user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS) {
                        seatHoldExpiredIds.add(user.id());
                    } else if (user.status() == VirtualUserStatus.SELECTING_SEAT) {
                        seatSelectionExpiredIds.add(user.id());
                    }
                });

        if (!seatHoldExpiredIds.isEmpty() || !seatSelectionExpiredIds.isEmpty()) {
            if (seatReservationService != null && !seatHoldExpiredIds.isEmpty()) {
                SimulationSnapshot currentSnapshot = stateStore.snapshot(simulationId);
                for (UUID userId : seatHoldExpiredIds) {
                    currentSnapshot.users().stream()
                            .filter(user -> user.id().equals(userId))
                            .findFirst()
                            .ifPresent(user -> {
                                if (user.reservationId() != null && user.selectedSeatLabel() != null) {
                                    currentSnapshot.seats().stream()
                                            .filter(seat -> seat.label().equals(user.selectedSeatLabel()))
                                            .findFirst()
                                            .ifPresent(seat -> {
                                                seatReservationService.expireHold(simulationId, user.reservationId(), seat.id());
                                            });
                                }
                            });
                }
            }
            SimulationSnapshot updated = stateStore.expireTimedOutParticipants(simulationId, seatHoldExpiredIds, seatSelectionExpiredIds, serverIdentity.id());
            if (eventHub != null) {
                eventHub.publish(updated);
            }
            // Clean up expired users from Redis queue to prevent phantom entries
            if (waitingQueueService != null) {
                for (UUID userId : seatHoldExpiredIds) {
                    waitingQueueService.removeAdmissionCandidate(simulationId.toString(), userId.toString());
                }
                for (UUID userId : seatSelectionExpiredIds) {
                    waitingQueueService.removeAdmissionCandidate(simulationId.toString(), userId.toString());
                }
            }
        }
    }

    private Instant now() {
        return clock.instant();
    }

    private VirtualUserView getParticipant(UUID simulationId, UUID userId) {
        try {
            VirtualUserView p = stateStore.participant(simulationId, userId);
            if (p != null) {
                return p;
            }
        } catch (Exception ignored) {
        }
        return stateStore.snapshot(simulationId).users().stream()
                .filter(user -> user.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + userId));
    }
}
