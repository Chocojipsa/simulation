package com.timedeal.seatreservation.simulation;

import java.time.Instant;
import java.util.UUID;

public record UserActivityEvent(
        UUID simulationId,
        UUID userId,
        String label,
        String message,
        Instant timestamp
) {
    public UserActivityEvent(UUID simulationId, UUID userId, String label, String message) {
        this(simulationId, userId, label, message, Instant.now());
    }
}

