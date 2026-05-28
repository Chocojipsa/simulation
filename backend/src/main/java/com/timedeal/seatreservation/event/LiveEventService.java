package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.identity.ServerIdentity;
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

    @Autowired
    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            LiveEventStateStore eventStateStore,
            ServerIdentity serverIdentity,
            ObjectProvider<SimulationInventoryService> inventoryService,
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
                Clock.systemUTC()
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
                Clock.systemUTC()
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
                Clock.systemUTC()
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
                Clock.systemUTC()
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
    }

    public LiveEventResponse activeEvent() {
        ensureSimulationExists();
        LiveEventMetadata metadata = eventStateStore.getOrCreate(configuredEventId, now());
        return response(metadata);
    }

    public LiveEventResponse startEvent(UUID eventId) {
        ensureExpectedEvent(eventId);
        ensureSimulationExists();
        LiveEventMetadata metadata = eventStateStore.startCountdown(eventId, now(), countdownDuration, openWindow);
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
        SimulationSnapshot snapshot = simulationService.getSimulation(eventId);
        return new LiveEventSnapshot(
                eventId,
                title,
                metadata.status().name(),
                metadata.generation(),
                metadata.opensAt(),
                metadata.endsAt(),
                snapshot.seats(),
                snapshot.users(),
                snapshot.metrics(),
                snapshot.serverStats(),
                snapshot.running(),
                myParticipantId
        );
    }

    public VirtualUserCommandResponse enterQueue(UUID eventId, UUID participantId) {
        ensureExpectedEvent(eventId);
        return simulationService.enterParticipantQueue(eventId, participantId);
    }

    public SeatHoldResponse holdSeat(UUID eventId, UUID participantId, long seatId) {
        ensureExpectedEvent(eventId);
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
        VirtualUserCommandResponse response = simulationService.confirmPayment(eventId, participantId);
        return new PaymentConfirmResponse(
                eventId,
                participantId,
                response.status(),
                response.message(),
                response.handledBy()
        );
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
