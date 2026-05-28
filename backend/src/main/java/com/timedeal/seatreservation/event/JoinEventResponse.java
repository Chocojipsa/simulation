package com.timedeal.seatreservation.event;

import java.util.UUID;

public record JoinEventResponse(
        UUID eventId,
        UUID participantId,
        String displayName,
        String status,
        String handledBy
) {
}
