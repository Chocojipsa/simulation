package com.timedeal.seatreservation.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateSimulationRequest(
        @Min(1) @Max(1000) int virtualUserCount
) {
}
