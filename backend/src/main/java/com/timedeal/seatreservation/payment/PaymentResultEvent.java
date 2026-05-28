package com.timedeal.seatreservation.payment;

import java.util.UUID;

public record PaymentResultEvent(
        UUID simulationId,
        UUID virtualUserId,
        long reservationId,
        long seatId,
        boolean success,
        String message,
        String handledBy
) {
}
