package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VirtualUserView(
        UUID id,
        String displayName,
        ParticipantType type,
        VirtualUserStatus status,
        String selectedSeatLabel,
        List<TimelineEntry> timeline,
        int seatAttemptCount,
        int conflictCount,
        int paymentAttemptCount,
        Long reservationId,
        Instant seatHoldExpiresAt
) {
}
