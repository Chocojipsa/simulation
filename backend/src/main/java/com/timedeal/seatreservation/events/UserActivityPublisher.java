package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserActivityPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserActivityPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(UserActivityEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(UserActivityBroadcastConfig.ACTIVITY_CHANNEL, message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize UserActivityEvent", e);
        }
    }

    public void publishBatch(com.timedeal.seatreservation.queue.QueuePositionsBatchEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(UserActivityBroadcastConfig.BATCH_ACTIVITY_CHANNEL, message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize QueuePositionsBatchEvent", e);
        }
    }
}
