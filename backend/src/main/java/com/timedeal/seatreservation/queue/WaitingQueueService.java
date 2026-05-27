package com.timedeal.seatreservation.queue;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class WaitingQueueService {
    private final StringRedisTemplate redis;
    private final Clock clock;

    public WaitingQueueService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void enterQueue(String simulationId, String virtualUserId) {
        redis.opsForZSet().add(queueKey(simulationId), virtualUserId, clock.millis());
    }

    public List<String> pickAdmissionCandidates(List<String> queuedUserIds, int limit) {
        return queuedUserIds.stream().limit(limit).toList();
    }

    public void issueAdmissionToken(String simulationId, String virtualUserId) {
        redis.opsForValue().set(tokenKey(simulationId, virtualUserId), "granted", Duration.ofSeconds(60));
    }

    public boolean hasAdmissionToken(String simulationId, String virtualUserId) {
        return Boolean.TRUE.equals(redis.hasKey(tokenKey(simulationId, virtualUserId)));
    }

    private String queueKey(String simulationId) {
        return "simulation:%s:queue".formatted(simulationId);
    }

    private String tokenKey(String simulationId, String virtualUserId) {
        return "simulation:%s:admission:%s".formatted(simulationId, virtualUserId);
    }
}
