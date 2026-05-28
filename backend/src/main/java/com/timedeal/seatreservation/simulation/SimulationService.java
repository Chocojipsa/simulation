package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.generator.TrafficGeneratorClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SimulationService {
    private final SimulationStateGateway stateStore;
    private final ServerIdentity serverIdentity;
    private final TrafficGeneratorClient trafficGeneratorClient;

    @Autowired
    public SimulationService(
            SimulationStateGateway stateStore,
            ServerIdentity serverIdentity,
            ObjectProvider<TrafficGeneratorClient> trafficGeneratorClient
    ) {
        this.stateStore = stateStore;
        this.serverIdentity = serverIdentity;
        this.trafficGeneratorClient = trafficGeneratorClient.getIfAvailable(() -> (simulationId, request) -> {
        });
    }

    SimulationService(SimulationStateGateway stateStore) {
        this.stateStore = stateStore;
        this.serverIdentity = new ServerIdentity("api-test");
        this.trafficGeneratorClient = (simulationId, request) -> {
        };
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

    public RunSimulationResponse runSimulation(UUID simulationId, RunSimulationRequest request) {
        stateStore.markRunning(simulationId);
        trafficGeneratorClient.start(simulationId, request);
        return new RunSimulationResponse(simulationId, request.virtualUserCount(), "RUNNING", serverIdentity.id());
    }

    public VirtualUserCommandResponse enterQueue(UUID simulationId, UUID userId) {
        return new VirtualUserCommandResponse(simulationId, userId, "QUEUED", serverIdentity.id());
    }
}
