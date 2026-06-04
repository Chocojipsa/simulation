package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UserActivitySubscriber {
    private final SimulationEventHub eventHub;
    private final ObjectMapper objectMapper;

    public UserActivitySubscriber(SimulationEventHub eventHub, ObjectMapper objectMapper) {
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String message) {
        try {
            UserActivityEvent event = objectMapper.readValue(message, UserActivityEvent.class);
            eventHub.publishUserActivity(event);
        } catch (IOException e) {
            // Ignore
        }
    }
}
