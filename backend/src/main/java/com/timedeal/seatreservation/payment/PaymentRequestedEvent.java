package com.timedeal.seatreservation.payment;

import java.util.UUID;

public record PaymentRequestedEvent(
        UUID simulationId,
        UUID virtualUserId,
        long reservationId,
        long seatId,
        String idempotencyKey,
        String handledBy
) {
}
