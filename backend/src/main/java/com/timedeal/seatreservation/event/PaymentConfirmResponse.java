package com.timedeal.seatreservation.event;

import java.util.UUID;

public record PaymentConfirmResponse(
        UUID eventId,
        UUID participantId,
        String status,
        String message,
        String handledBy
) {
}
