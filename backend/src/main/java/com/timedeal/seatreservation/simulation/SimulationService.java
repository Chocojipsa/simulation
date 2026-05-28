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
    private final SimulationInventoryService inventoryService;

    @Autowired
    public SimulationService(
            SimulationStateGateway stateStore,
            ServerIdentity serverIdentity,
            ObjectProvider<TrafficGeneratorClient> trafficGeneratorClient,
            ObjectProvider<SimulationInventoryService> inventoryService
    ) {
        this.stateStore = stateStore;
        this.serverIdentity = serverIdentity;
        this.trafficGeneratorClient = trafficGeneratorClient.getIfAvailable(() -> (simulationId, request) -> {
        });
        this.inventoryService = inventoryService.getIfAvailable();
    }

    SimulationService(SimulationStateGateway stateStore) {
        this.stateStore = stateStore;
        this.serverIdentity = new ServerIdentity("api-test");
        this.trafficGeneratorClient = (simulationId, request) -> {
        };
        this.inventoryService = null;
    }

    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        SimulationSnapshot snapshot = stateStore.create(simulationId, request.virtualUserCount());
        if (inventoryService != null) {
            inventoryService.initialize(snapshot, request.virtualUserCount());
        }
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
        return new VirtualUserCommandResponse(
                simulationId,
                userId,
                "QUEUED",
                serverIdentity.id(),
                "대기열에 진입했습니다.",
                null
        );
    }

    public VirtualUserCommandResponse attemptSeat(UUID simulationId, UUID userId) {
        return new VirtualUserCommandResponse(
                simulationId,
                userId,
                "WAITING",
                serverIdentity.id(),
                "아직 대기 중입니다.",
                null
        );
    }
}
