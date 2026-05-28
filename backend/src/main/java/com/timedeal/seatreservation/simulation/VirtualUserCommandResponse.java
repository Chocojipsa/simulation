package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record VirtualUserCommandResponse(
        UUID simulationId,
        UUID virtualUserId,
        String status,
        String handledBy
) {
}
