package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SnapshotSubscriber {
    private final SimulationEventHub eventHub;
    private final ObjectMapper objectMapper;

    public SnapshotSubscriber(SimulationEventHub eventHub, ObjectMapper objectMapper) {
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String message) {
        try {
            SimulationSnapshot snapshot = objectMapper.readValue(message, SimulationSnapshot.class);
            eventHub.publishLocally(snapshot);
        } catch (IOException e) {
            // Ignore
        }
    }
}
