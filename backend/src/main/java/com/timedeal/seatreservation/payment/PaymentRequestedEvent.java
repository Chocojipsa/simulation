package com.timedeal.seatreservation.payment;

public record PaymentRequestedEvent(
        long reservationId,
        long seatId,
        String idempotencyKey
) {
}
