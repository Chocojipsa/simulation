package com.timedeal.seatreservation.seat;

import java.util.UUID;

public record SeatReservationResult(
        SeatReservationOutcome outcome,
        Long reservationId,
        long seatId,
        UUID virtualUserId,
        String idempotencyKey
) {
}
