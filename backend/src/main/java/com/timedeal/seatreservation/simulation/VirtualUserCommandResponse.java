package com.timedeal.seatreservation.simulation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VirtualUserCommandResponse(
        UUID simulationId,
        UUID virtualUserId,
        String status,
        String handledBy,
        String message,
        String selectedSeatLabel
) {
}
