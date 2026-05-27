package com.timedeal.seatreservation.simulation;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SimulationService {
    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        return new SimulationResponse(simulationId, "시뮬레이션이 시작되었습니다.", request.virtualUserCount());
    }
}
