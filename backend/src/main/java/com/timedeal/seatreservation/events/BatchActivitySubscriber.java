package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.queue.QueuePositionsBatchEvent;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Locale;

@Component
public class BatchActivitySubscriber {
    private final SimulationEventHub eventHub;
    private final ObjectMapper objectMapper;

    public BatchActivitySubscriber(SimulationEventHub eventHub, ObjectMapper objectMapper) {
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String message) {
        try {
            QueuePositionsBatchEvent batchEvent = objectMapper.readValue(message, QueuePositionsBatchEvent.class);
            batchEvent.positions().forEach(pos -> {
                String jsonMsg = String.format(Locale.US, "{\"position\":%d,\"estimatedWaitSeconds\":%.1f}", pos.position(), pos.estimatedWaitSeconds());
                UserActivityEvent event = new UserActivityEvent(batchEvent.eventId(), pos.userId(), "queue_position_update", jsonMsg);
                eventHub.publishUserActivity(event);
            });
        } catch (IOException e) {
            // Ignore
        }
    }
}
