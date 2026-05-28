package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import com.timedeal.seatreservation.event.ParticipantType;

import java.util.UUID;

public interface SimulationStateGateway {
    SimulationSnapshot create(UUID simulationId, int virtualUserCount);

    SimulationSnapshot snapshot(UUID simulationId);

    SimulationSnapshot markRunning(UUID simulationId);

    SimulationSnapshot registerParticipant(UUID simulationId, UUID participantId, String displayName, ParticipantType type, String handledBy);

    SimulationSnapshot registerQueueEntry(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordWaiting(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordSeatSelectionWaiting(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordSeatConflict(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy);

    SimulationSnapshot recordNoSeatAvailable(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordPaymentRequested(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy);

    SimulationSnapshot applyPaymentResult(PaymentResultEvent event);
}
