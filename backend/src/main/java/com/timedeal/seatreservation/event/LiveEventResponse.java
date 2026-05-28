package com.timedeal.seatreservation.event;

import java.time.Instant;
import java.util.UUID;

public record LiveEventResponse(
        UUID eventId,
        String title,
        String status,
        Instant opensAt,
        int seatCount
) {
}
