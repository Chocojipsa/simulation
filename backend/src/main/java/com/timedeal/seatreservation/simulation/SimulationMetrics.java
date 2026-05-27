package com.timedeal.seatreservation.simulation;

public record SimulationMetrics(
        int queueSize,
        int admittedCount,
        int heldCount,
        int paymentInProgressCount,
        int reservedCount,
        int failedCount
) {
}
