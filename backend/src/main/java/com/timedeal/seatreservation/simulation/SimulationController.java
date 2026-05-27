package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.events.SimulationEventHub;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/simulations")
public class SimulationController {
    private final SimulationService simulationService;
    private final SimulationEventHub simulationEventHub;

    public SimulationController(SimulationService simulationService, SimulationEventHub simulationEventHub) {
        this.simulationService = simulationService;
        this.simulationEventHub = simulationEventHub;
    }

    @PostMapping
    public SimulationResponse createSimulation(@Valid @RequestBody CreateSimulationRequest request) {
        return simulationService.createSimulation(request);
    }

    @GetMapping("/{simulationId}")
    public SimulationSnapshot getSimulation(@PathVariable UUID simulationId) {
        return simulationService.getSimulation(simulationId);
    }

    @GetMapping(path = "/{simulationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID simulationId) {
        return simulationEventHub.open(simulationId);
    }
}
