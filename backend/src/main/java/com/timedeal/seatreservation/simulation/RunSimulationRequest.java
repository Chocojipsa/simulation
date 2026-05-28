package com.timedeal.seatreservation.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RunSimulationRequest(
        @Min(1) @Max(1000) int virtualUserCount,
        @Min(1) @Max(100) int concurrency
) {
}
