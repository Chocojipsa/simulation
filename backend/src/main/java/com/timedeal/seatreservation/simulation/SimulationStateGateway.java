package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public interface SimulationStateGateway {
    SimulationSnapshot create(UUID simulationId, int virtualUserCount);

    SimulationSnapshot snapshot(UUID simulationId);

    SimulationSnapshot markRunning(UUID simulationId);
}
