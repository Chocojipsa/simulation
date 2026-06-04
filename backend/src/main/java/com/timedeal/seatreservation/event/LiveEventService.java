package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.queue.WaitingQueueService;
import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.RunSimulationResponse;
import com.timedeal.seatreservation.simulation.SimulationInventoryService;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.SimulationStateGateway;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LiveEventService {
    private static final UUID DEFAULT_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SimulationService simulationService;
    private final SimulationStateGateway stateGateway;
    private final LiveEventStateStore eventStateStore;
    private final SimulationInventoryService inventoryService;
    private final ServerIdentity serverIdentity;
    private final UUID configuredEventId;
    private final String title;
    private final int seatCount;
    private final Duration countdownDuration;
    private final Duration openWindow;
    private final Clock clock;
    private final LiveEventAiStarter aiStarter;
    private final WaitingQueueService waitingQueueService;

    @Autowired
    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            LiveEventStateStore eventStateStore,
            ServerIdentity serverIdentity,
            ObjectProvider<SimulationInventoryService> inventoryService,
            ObjectProvider<LiveEventAiStarter> aiStarter,
            ObjectProvider<WaitingQueueService> waitingQueueService,
            @Value("${live-event.id:00000000-0000-0000-0000-000000000001}") UUID configuredEventId,
            @Value("${live-event.title:Live Ticketing Event}") String title,
            @Value("${live-event.seat-count:120}") int seatCount,
            @Value("${live-event.countdown-seconds:60}") long countdownSeconds,
            @Value("${live-event.open-window-seconds:300}") long openWindowSeconds
    ) {
        this(
                simulationService,
                stateGateway,
                eventStateStore,
                serverIdentity,
                inventoryService.getIfAvailable(),
                configuredEventId,
                title,
                seatCount,
                Duration.ofSeconds(countdownSeconds),
                Duration.ofSeconds(openWindowSeconds),
                Clock.systemUTC(),
                aiStarter.getIfAvailable(),
                waitingQueueService.getIfAvailable()
        );
    }

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            ServerIdentity serverIdentity,
            String title,
            int seatCount
    ) {
        this(
                simulationService,
                stateGateway,
                new InMemoryLiveEventStateStore(),
                serverIdentity,
                null,
                DEFAULT_EVENT_ID,
                title,
                seatCount,
                Duration.ZERO,
                Duration.ofMinutes(5),
                Clock.systemUTC(),
                null
        );
    }

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            ServerIdentity serverIdentity,
            SimulationInventoryService inventoryService,
            String title,
            int seatCount
    ) {
        this(
                simulationService,
                stateGateway,
                new InMemoryLiveEventStateStore(),
                serverIdentity,
                inventoryService,
                DEFAULT_EVENT_ID,
                title,
                seatCount,
                Duration.ZERO,
                Duration.ofMinutes(5),
                Clock.systemUTC(),
                null
        );
    }

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            ServerIdentity serverIdentity,
            SimulationInventoryService inventoryService,
            UUID configuredEventId,
            String title,
            int seatCount
    ) {
        this(
                simulationService,
                stateGateway,
                new InMemoryLiveEventStateStore(),
                serverIdentity,
                inventoryService,
                configuredEventId,
                title,
                seatCount,
                Duration.ZERO,
                Duration.ofMinutes(5),
                Clock.systemUTC(),
                null
        );
    }

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            LiveEventStateStore eventStateStore,
            ServerIdentity serverIdentity,
            SimulationInventoryService inventoryService,
            UUID configuredEventId,
            String title,
            int seatCount,
            Duration countdownDuration,
            Duration openWindow,
            Clock clock
    ) {
        this(
                simulationService,
                stateGateway,
                eventStateStore,
                serverIdentity,
                inventoryService,
                configuredEventId,
                title,
                seatCount,
                countdownDuration,
                openWindow,
                clock,
                null
        );
    }

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            LiveEventStateStore eventStateStore,
            ServerIdentity serverIdentity,
            SimulationInventoryService inventoryService,
            UUID configuredEventId,
            String title,
            int seatCount,
            Duration countdownDuration,
            Duration openWindow,
            Clock clock,
            LiveEventAiStarter aiStarter
    ) {
        this(
                simulationService,
                stateGateway,
                eventStateStore,
                serverIdentity,
                inventoryService,
                configuredEventId,
                title,
                seatCount,
                countdownDuration,
                openWindow,
                clock,
                aiStarter,
                null
        );
    }

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            LiveEventStateStore eventStateStore,
            ServerIdentity serverIdentity,
            SimulationInventoryService inventoryService,
            UUID configuredEventId,
            String title,
            int seatCount,
            Duration countdownDuration,
            Duration openWindow,
            Clock clock,
            LiveEventAiStarter aiStarter,
            WaitingQueueService waitingQueueService
    ) {
        this.simulationService = simulationService;
        this.stateGateway = stateGateway;
        this.eventStateStore = eventStateStore;
        this.serverIdentity = serverIdentity;
        this.inventoryService = inventoryService;
        this.configuredEventId = configuredEventId;
        this.title = title;
        this.seatCount = seatCount;
        this.countdownDuration = countdownDuration;
        this.openWindow = openWindow;
        this.clock = clock;
        this.aiStarter = aiStarter;
        this.waitingQueueService = waitingQueueService;
    }

    public LiveEventResponse activeEvent() {
        ensureSimulationExists();
        LiveEventMetadata metadata = eventStateStore.getOrCreate(configuredEventId, now());
        triggerAiIfOpen(metadata);
        return response(metadata);
    }

    public LiveEventResponse startEvent(UUID eventId) {
        ensureExpectedEvent(eventId);
        ensureSimulationExists();
        LiveEventMetadata metadata = eventStateStore.startCountdown(eventId, now(), countdownDuration, openWindow);
        triggerAiIfOpen(metadata);
        return response(metadata);
    }

    public LiveEventResponse resetEvent(UUID eventId) {
        ensureExpectedEvent(eventId);
        simulationService.resetSimulation(eventId, 0);
        LiveEventMetadata metadata = eventStateStore.reset(eventId, now());
        return response(metadata);
    }

    public JoinEventResponse join(UUID eventId, JoinEventRequest request) {
        ensureExpectedEvent(eventId);
        ensureSimulationExists();
        UUID participantId = UUID.randomUUID();
        String displayName = request.normalizedDisplayName();
        SimulationSnapshot snapshot = stateGateway.registerParticipant(eventId, participantId, displayName, ParticipantType.HUMAN, serverIdentity.id());
        if (inventoryService != null) {
            VirtualUserView participant = snapshot.users().stream()
                    .filter(user -> user.id().equals(participantId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Participant not found after join: " + participantId));
            inventoryService.registerParticipant(snapshot, participant);
        }
        return new JoinEventResponse(eventId, participantId, displayName, "WAITING_ROOM", serverIdentity.id());
    }

    public LiveEventSnapshot snapshot(UUID eventId, UUID myParticipantId) {
        ensureExpectedEvent(eventId);
        ensureSimulationExists();
        LiveEventMetadata metadata = eventStateStore.getOrCreate(eventId, now()).withDerivedStatus(now());
        triggerAiIfOpen(metadata);
        SimulationSnapshot snapshot = simulationService.getSimulation(eventId);
        return new LiveEventSnapshot(
                eventId,
                title,
                metadata.status().name(),
                metadata.generation(),
                metadata.opensAt(),
                metadata.endsAt(),
                snapshot.seats(),
                participantsInQueueOrder(eventId, snapshot.users()),
                snapshot.metrics(),
                snapshot.serverStats(),
                snapshot.running(),
                myParticipantId,
                queuePosition(eventId, myParticipantId)
        );
    }

    public VirtualUserCommandResponse enterQueue(UUID eventId, UUID participantId) {
        ensureExpectedEvent(eventId);
        LiveEventStatus status = currentStatus(eventId);
        if (status == LiveEventStatus.READY) {
            return new VirtualUserCommandResponse(eventId, participantId, "NOT_STARTED", serverIdentity.id(), "이벤트 시작 전입니다.", null);
        }
        if (status == LiveEventStatus.COUNTDOWN) {
            return new VirtualUserCommandResponse(eventId, participantId, "NOT_OPEN", serverIdentity.id(), "예매가 아직 시작되지 않았습니다.", null);
        }
        if (status == LiveEventStatus.ENDED) {
            return new VirtualUserCommandResponse(eventId, participantId, "EVENT_ENDED", serverIdentity.id(), "이벤트가 종료되었습니다.", null);
        }
        
        VirtualUserView participant = stateGateway.participant(eventId, participantId);
        if (participant.status() == com.timedeal.seatreservation.domain.VirtualUserStatus.WAITING_ROOM) {
            return simulationService.enterQueue(eventId, participantId);
        } else {
            return simulationService.postQueue(eventId, participantId);
        }
    }

    public SeatHoldResponse holdSeat(UUID eventId, UUID participantId, long seatId) {
        ensureExpectedEvent(eventId);
        LiveEventStatus eventStatus = currentStatus(eventId);
        if (eventStatus == LiveEventStatus.READY || eventStatus == LiveEventStatus.COUNTDOWN) {
            return new SeatHoldResponse(eventId, participantId, seatId, "NOT_OPEN", "예매가 아직 시작되지 않았습니다.", null, serverIdentity.id());
        }
        if (eventStatus == LiveEventStatus.ENDED) {
            return new SeatHoldResponse(eventId, participantId, seatId, "EVENT_ENDED", "이벤트가 종료되었습니다.", null, serverIdentity.id());
        }
        VirtualUserCommandResponse response = simulationService.holdExplicitSeat(eventId, participantId, seatId);
        return new SeatHoldResponse(
                eventId,
                participantId,
                seatId,
                response.status(),
                response.message(),
                response.selectedSeatLabel(),
                response.handledBy()
        );
    }

    public PaymentConfirmResponse confirmPayment(UUID eventId, UUID participantId) {
        ensureExpectedEvent(eventId);
        LiveEventStatus eventStatus = currentStatus(eventId);
        if (eventStatus != LiveEventStatus.OPEN) {
            String status = eventStatus == LiveEventStatus.ENDED ? "EVENT_ENDED" : "NOT_OPEN";
            String message = eventStatus == LiveEventStatus.ENDED ? "이벤트가 종료되었습니다." : "예매가 아직 시작되지 않았습니다.";
            return new PaymentConfirmResponse(eventId, participantId, status, message, serverIdentity.id());
        }
        return simulationService.confirmPayment(eventId, participantId);
    }

    public RunSimulationResponse startAiParticipants(UUID eventId, StartAiParticipantsRequest request) {
        ensureExpectedEvent(eventId);
        return simulationService.runSimulation(eventId, new RunSimulationRequest(request.participantCount(), request.concurrency()));
    }

    private void ensureSimulationExists() {
        try {
            simulationService.getSimulation(configuredEventId);
        } catch (NoSuchElementException exception) {
            simulationService.createSimulation(configuredEventId, 0);
        }
    }

    private void ensureExpectedEvent(UUID eventId) {
        if (!configuredEventId.equals(eventId)) {
            throw new NoSuchElementException("Live event not found: " + eventId);
        }
    }

    private LiveEventStatus currentStatus(UUID eventId) {
        return eventStateStore.getOrCreate(eventId, now()).statusAt(now());
    }

    private void triggerAiIfOpen(LiveEventMetadata metadata) {
        if (aiStarter == null || metadata.statusAt(now()) != LiveEventStatus.OPEN || metadata.aiStarted()) {
            return;
        }
        if (eventStateStore.claimAiStart(metadata.eventId())) {
            aiStarter.start(metadata.eventId());
        }
    }

    private List<VirtualUserView> participantsInQueueOrder(UUID eventId, List<VirtualUserView> participants) {
        if (waitingQueueService == null) {
            return participants;
        }
        List<String> queuedUserIds = waitingQueueService.queuedUserIds(eventId.toString());
        if (queuedUserIds.isEmpty()) {
            return participants;
        }
        Map<UUID, Integer> queueOrder = new HashMap<>();
        for (int index = 0; index < queuedUserIds.size(); index++) {
            queueOrder.put(UUID.fromString(queuedUserIds.get(index)), index);
        }
        return participants.stream()
                .sorted(Comparator.comparingInt(participant -> queueOrder.getOrDefault(participant.id(), Integer.MAX_VALUE)))
                .toList();
    }

    private Integer queuePosition(UUID eventId, UUID participantId) {
        if (waitingQueueService == null || participantId == null) {
            return null;
        }
        List<String> queuedUserIds = waitingQueueService.queuedUserIds(eventId.toString());
        String participantKey = participantId.toString();
        for (int index = 0; index < queuedUserIds.size(); index++) {
            if (participantKey.equals(queuedUserIds.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private LiveEventResponse response(LiveEventMetadata metadata) {
        LiveEventMetadata derived = metadata.withDerivedStatus(now());
        return new LiveEventResponse(
                derived.eventId(),
                title,
                derived.status().name(),
                derived.generation(),
                derived.opensAt(),
                derived.endsAt(),
                seatCount
        );
    }

    private Instant now() {
        return clock.instant();
    }
}
