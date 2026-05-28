package com.timedeal.seatreservation.event;

import java.util.UUID;

public record SeatHoldResponse(
        UUID eventId,
        UUID participantId,
        long seatId,
        String status,
        String message,
        String selectedSeatLabel,
        String handledBy
) {
}
