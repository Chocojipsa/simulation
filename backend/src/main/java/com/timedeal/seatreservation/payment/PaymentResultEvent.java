package com.timedeal.seatreservation.payment;

public record PaymentResultEvent(
        long reservationId,
        long seatId,
        boolean success,
        String message
) {
}
