package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SnapshotPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SnapshotPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(SimulationSnapshot snapshot) {
        try {
            String message = objectMapper.writeValueAsString(snapshot);
            redisTemplate.convertAndSend(UserActivityBroadcastConfig.SNAPSHOT_CHANNEL, message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize SimulationSnapshot", e);
        }
    }
}
