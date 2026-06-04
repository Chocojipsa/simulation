package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Component
@Profile("!demo")
public class RedisLiveEventStateStore implements LiveEventStateStore {
    private static final Duration TTL = Duration.ofHours(3);
    private static final Duration LOCK_TTL = Duration.ofSeconds(2);
    private static final Duration LOCK_RETRY_DELAY = Duration.ofMillis(20);
    private static final int LOCK_ATTEMPTS = 250;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisLiveEventStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public LiveEventMetadata getOrCreate(UUID eventId, Instant now) {
        return mutate(eventId, current -> current == null ? ready(eventId, 1, now) : current.withDerivedStatus(now));
    }

    @Override
    public LiveEventMetadata startCountdown(UUID eventId, Instant now, Duration countdownDuration, Duration openWindow) {
        return mutate(eventId, current -> {
            LiveEventMetadata metadata = current == null ? ready(eventId, 1, now) : current.withDerivedStatus(now);
            if (metadata.status() != LiveEventStatus.READY) {
                return metadata;
            }
            Instant opensAt = now.plus(countdownDuration);
            return new LiveEventMetadata(eventId, metadata.generation(), LiveEventStatus.COUNTDOWN, metadata.createdAt(), opensAt, opensAt.plus(openWindow), false);
        });
    }

    @Override
    public boolean claimAiStart(UUID eventId) {
        String lockKey = key(eventId) + ":lock";
        acquireLock(eventId, lockKey);
        try {
            LiveEventMetadata current = read(eventId);
            if (current == null || current.aiStarted()) {
                return false;
            }
            LiveEventMetadata updated = new LiveEventMetadata(
                    current.eventId(),
                    current.generation(),
                    current.status(),
                    current.createdAt(),
                    current.opensAt(),
                    current.endsAt(),
                    true
            );
            redisTemplate.opsForValue().set(key(eventId), write(updated), TTL);
            return true;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public LiveEventMetadata reset(UUID eventId, Instant now) {
        return mutate(eventId, current -> {
            int nextGeneration = current == null ? 1 : current.generation() + 1;
            return ready(eventId, nextGeneration, now);
        });
    }

    private LiveEventMetadata mutate(UUID eventId, UnaryOperator<LiveEventMetadata> mutator) {
        String lockKey = key(eventId) + ":lock";
        acquireLock(eventId, lockKey);
        try {
            LiveEventMetadata updated = mutator.apply(read(eventId));
            if (updated != null) {
                redisTemplate.opsForValue().set(key(eventId), write(updated), TTL);
            }
            return updated;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private LiveEventMetadata read(UUID eventId) {
        String json = redisTemplate.opsForValue().get(key(eventId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LiveEventMetadata.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read live event metadata: " + eventId, exception);
        }
    }

    private String write(LiveEventMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write live event metadata: " + metadata.eventId(), exception);
        }
    }

    private void acquireLock(UUID eventId, String lockKey) {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                return;
            }
            sleepBeforeRetry(eventId);
        }
        throw new IllegalStateException("Live event metadata is busy: " + eventId);
    }

    private void sleepBeforeRetry(UUID eventId) {
        try {
            Thread.sleep(LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for live event lock: " + eventId, exception);
        }
    }

    private String key(UUID eventId) {
        return "live-event:%s:metadata".formatted(eventId);
    }

    private LiveEventMetadata ready(UUID eventId, int generation, Instant now) {
        return new LiveEventMetadata(eventId, generation, LiveEventStatus.READY, now, null, null, false);
    }
}
