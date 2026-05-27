package com.timedeal.seatreservation.simulation;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SimulationService {
    private final SimulationStateStore stateStore;
    private final SimulationRunner simulationRunner;

    public SimulationService(SimulationStateStore stateStore, SimulationRunner simulationRunner) {
        this.stateStore = stateStore;
        this.simulationRunner = simulationRunner;
    }

    SimulationService(SimulationStateStore stateStore) {
        this.stateStore = stateStore;
        this.simulationRunner = null;
    }

    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        stateStore.create(simulationId, request.virtualUserCount());
        if (simulationRunner != null) {
            simulationRunner.start(simulationId);
        }
        return new SimulationResponse(simulationId, "시뮬레이션이 시작되었습니다.", request.virtualUserCount());
    }

    public SimulationSnapshot getSimulation(UUID simulationId) {
        return stateStore.snapshot(simulationId);
    }
}
