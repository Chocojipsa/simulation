package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import com.timedeal.seatreservation.event.ParticipantType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SimulationStateGateway {
    SimulationSnapshot create(UUID simulationId, int virtualUserCount);

    SimulationSnapshot snapshot(UUID simulationId);

    default VirtualUserView participant(UUID simulationId, UUID participantId) {
        return snapshot(simulationId).users().stream()
                .filter(user -> user.id().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));
    }

    SimulationSnapshot markRunning(UUID simulationId);

    SimulationSnapshot registerParticipant(UUID simulationId, UUID participantId, String displayName, ParticipantType type, String handledBy);

    SimulationSnapshot registerQueueEntry(UUID simulationId, UUID virtualUserId, String handledBy);

    default SimulationSnapshot recordAdmitted(UUID simulationId, UUID virtualUserId, String handledBy) {
        return recordAdmitted(simulationId, virtualUserId, null, handledBy);
    }

    SimulationSnapshot recordAdmitted(UUID simulationId, UUID virtualUserId, Instant selectionExpiresAt, String handledBy);

    SimulationSnapshot recordWaiting(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordSeatSelectionWaiting(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordSeatConflict(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy);

    SimulationSnapshot recordNoSeatAvailable(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordSeatHeldForPayment(
            UUID simulationId,
            UUID virtualUserId,
            SeatView seat,
            Long reservationId,
            Instant expiresAt,
            String handledBy
    );

    SimulationSnapshot expireSeatHold(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot expireSeatSelection(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot expireTimedOutParticipants(
            UUID simulationId,
            List<UUID> seatHoldExpiredIds,
            List<UUID> seatSelectionExpiredIds,
            String handledBy
    );

    Long markPaymentRequestedByParticipant(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordPaymentRequested(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy);

    SimulationSnapshot applyPaymentResult(PaymentResultEvent event);

    SimulationSnapshot releaseSeat(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordUserActivity(UUID simulationId, UUID userId, String label, String message);

    SimulationSnapshot updateParticipantName(UUID simulationId, UUID participantId, String displayName);
}
