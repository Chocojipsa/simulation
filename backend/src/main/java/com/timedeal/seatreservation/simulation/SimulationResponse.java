package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record SimulationResponse(
        UUID simulationId,
        String message,
        int virtualUserCount,
        String handledBy
) {
}
