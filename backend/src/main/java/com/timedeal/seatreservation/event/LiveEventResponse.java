package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record LiveEventResponse(
        UUID eventId,
        String title,
        String status,
        int generation,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant opensAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant endsAt,
        int seatCount
) {
}
