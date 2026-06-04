package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.RunSimulationResponse;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("generator")
@RequestMapping("/internal/traffic-generator/simulations")
public class TrafficGeneratorController {
    private final TrafficGeneratorService trafficGeneratorService;
    private final SimulationService simulationService;
    private final ServerIdentity serverIdentity;

    public TrafficGeneratorController(
            TrafficGeneratorService trafficGeneratorService,
            SimulationService simulationService,
            ServerIdentity serverIdentity
    ) {
        this.trafficGeneratorService = trafficGeneratorService;
        this.simulationService = simulationService;
        this.serverIdentity = serverIdentity;
    }

    @PostMapping("/{simulationId}/run")
    public RunSimulationResponse run(
            @PathVariable UUID simulationId,
            @Valid @RequestBody RunSimulationRequest request
    ) {
        trafficGeneratorService.start(simulationId, request);
        return new RunSimulationResponse(simulationId, request.virtualUserCount(), "RUNNING", serverIdentity.id());
    }

    @PostMapping("/{simulationId}/users/{userId}/activity")
    public void recordActivity(
            @PathVariable UUID simulationId,
            @PathVariable UUID userId,
            @RequestBody UserActivityEvent event
    ) {
        simulationService.recordUserActivity(simulationId, userId, event.label(), event.message());
    }
}
