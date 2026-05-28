package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.identity.ServerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SimulationService {
    private final SimulationStateStore stateStore;
    private final SimulationRunner simulationRunner;
    private final ServerIdentity serverIdentity;

    @Autowired
    public SimulationService(
            SimulationStateStore stateStore,
            SimulationRunner simulationRunner,
            ServerIdentity serverIdentity
    ) {
        this.stateStore = stateStore;
        this.simulationRunner = simulationRunner;
        this.serverIdentity = serverIdentity;
    }

    SimulationService(SimulationStateStore stateStore) {
        this.stateStore = stateStore;
        this.simulationRunner = null;
        this.serverIdentity = new ServerIdentity("api-test");
    }

    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        stateStore.create(simulationId, request.virtualUserCount());
        return new SimulationResponse(
                simulationId,
                "시뮬레이션이 생성되었습니다.",
                request.virtualUserCount(),
                serverIdentity.id()
        );
    }

    public SimulationSnapshot getSimulation(UUID simulationId) {
        return stateStore.snapshot(simulationId);
    }
}
