package com.timedeal.seatreservation.simulation;

import java.util.List;
import java.util.UUID;

public record SimulationSnapshot(
        UUID simulationId,
        List<SeatView> seats,
        List<VirtualUserView> users,
        SimulationMetrics metrics,
        List<ServerStatsView> serverStats,
        boolean running
) {
}
