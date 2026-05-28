package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.simulation.CreateSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.SimulationStateGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LiveEventService {
    private final SimulationService simulationService;
    private final SimulationStateGateway stateGateway;
    private final ServerIdentity serverIdentity;
    private final String title;
    private final int seatCount;
    private final AtomicReference<UUID> activeEventId = new AtomicReference<>();

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            ServerIdentity serverIdentity,
            @Value("${live-event.title:Live Ticketing Event}") String title,
            @Value("${live-event.seat-count:120}") int seatCount
    ) {
        this.simulationService = simulationService;
        this.stateGateway = stateGateway;
        this.serverIdentity = serverIdentity;
        this.title = title;
        this.seatCount = seatCount;
    }

    public LiveEventResponse activeEvent() {
        UUID eventId = activeEventId.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            return simulationService.createSimulation(new CreateSimulationRequest(0)).simulationId();
        });
        return new LiveEventResponse(eventId, title, "OPEN", Instant.now(Clock.systemUTC()), seatCount);
    }

    public JoinEventResponse join(UUID eventId, JoinEventRequest request) {
        UUID participantId = UUID.randomUUID();
        String displayName = request.normalizedDisplayName();
        stateGateway.registerParticipant(eventId, participantId, displayName, ParticipantType.HUMAN, serverIdentity.id());
        return new JoinEventResponse(eventId, participantId, displayName, "WAITING_ROOM", serverIdentity.id());
    }

    public LiveEventSnapshot snapshot(UUID eventId, UUID myParticipantId) {
        SimulationSnapshot snapshot = simulationService.getSimulation(eventId);
        return new LiveEventSnapshot(
                eventId,
                title,
                status(snapshot),
                Instant.now(Clock.systemUTC()),
                snapshot.seats(),
                snapshot.users(),
                snapshot.metrics(),
                snapshot.serverStats(),
                snapshot.running(),
                myParticipantId
        );
    }

    private String status(SimulationSnapshot snapshot) {
        return snapshot.running() ? "OPEN" : "OPEN";
    }
}
