package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;

import java.util.UUID;

public interface TrafficGeneratorClient {
    void start(UUID simulationId, RunSimulationRequest request);
}
