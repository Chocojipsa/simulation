package com.timedeal.seatreservation.queue;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class WaitingQueueService {
    private final StringRedisTemplate redis;
    private final Clock clock;

    public WaitingQueueService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void enterQueue(String simulationId, String virtualUserId) {
        Long sequence = redis.opsForValue().increment(sequenceKey(simulationId));
        double score = sequence != null ? sequence.doubleValue() : clock.millis();
        redis.opsForZSet().add(queueKey(simulationId), virtualUserId, score);
    }

    public List<String> pickAdmissionCandidates(String simulationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Set<String> candidates = redis.opsForZSet().range(queueKey(simulationId), 0, limit - 1);
        if (candidates == null) {
            return List.of();
        }

        return new ArrayList<>(candidates);
    }

    public List<String> queuedUserIds(String simulationId) {
        Set<String> candidates = redis.opsForZSet().range(queueKey(simulationId), 0, -1);
        if (candidates == null) {
            return List.of();
        }
        return new ArrayList<>(candidates);
    }

    public void issueAdmissionToken(String simulationId, String virtualUserId) {
        redis.opsForValue().set(tokenKey(simulationId, virtualUserId), "granted", Duration.ofSeconds(60));
    }

    public void removeAdmissionCandidate(String simulationId, String virtualUserId) {
        redis.opsForZSet().remove(queueKey(simulationId), virtualUserId);
    }

    public boolean hasAdmissionToken(String simulationId, String virtualUserId) {
        return Boolean.TRUE.equals(redis.hasKey(tokenKey(simulationId, virtualUserId)));
    }

    public void revokeAdmissionToken(String simulationId, String virtualUserId) {
        redis.delete(tokenKey(simulationId, virtualUserId));
    }

    public void clearQueue(String simulationId) {
        redis.delete(queueKey(simulationId));
        redis.delete(sequenceKey(simulationId));
    }

    private String queueKey(String simulationId) {
        return "simulation:%s:queue".formatted(simulationId);
    }

    private String sequenceKey(String simulationId) {
        return "simulation:%s:queue-sequence".formatted(simulationId);
    }

    private String tokenKey(String simulationId, String virtualUserId) {
        return "simulation:%s:admission:%s".formatted(simulationId, virtualUserId);
    }
}
