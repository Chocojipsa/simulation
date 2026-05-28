package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record RunSimulationResponse(
        UUID simulationId,
        int virtualUserCount,
        String status,
        String handledBy
) {
}
