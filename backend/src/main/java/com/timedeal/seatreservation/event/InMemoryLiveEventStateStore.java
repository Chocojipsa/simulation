package com.timedeal.seatreservation.event;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class InMemoryLiveEventStateStore implements LiveEventStateStore {
    private final ConcurrentHashMap<UUID, LiveEventMetadata> events = new ConcurrentHashMap<>();

    @Override
    public LiveEventMetadata getOrCreate(UUID eventId, Instant now) {
        return events.computeIfAbsent(eventId, id -> ready(id, 1, now));
    }

    @Override
    public LiveEventMetadata startCountdown(UUID eventId, Instant now, Duration countdownDuration, Duration openWindow) {
        return events.compute(eventId, (id, current) -> {
            LiveEventMetadata metadata = current == null ? ready(id, 1, now) : current.withDerivedStatus(now);
            if (metadata.status() != LiveEventStatus.READY) {
                return metadata;
            }
            Instant opensAt = now.plus(countdownDuration);
            return new LiveEventMetadata(id, metadata.generation(), LiveEventStatus.COUNTDOWN, metadata.createdAt(), opensAt, opensAt.plus(openWindow), false);
        });
    }

    @Override
    public boolean claimAiStart(UUID eventId) {
        LiveEventMetadata before = events.get(eventId);
        if (before == null || before.aiStarted()) {
            return false;
        }
        LiveEventMetadata after = new LiveEventMetadata(
                before.eventId(),
                before.generation(),
                before.status(),
                before.createdAt(),
                before.opensAt(),
                before.endsAt(),
                true
        );
        return events.replace(eventId, before, after);
    }

    @Override
    public LiveEventMetadata reset(UUID eventId, Instant now) {
        return events.compute(eventId, (id, current) -> {
            int nextGeneration = current == null ? 1 : current.generation() + 1;
            return ready(id, nextGeneration, now);
        });
    }

    private LiveEventMetadata ready(UUID eventId, int generation, Instant now) {
        return new LiveEventMetadata(eventId, generation, LiveEventStatus.READY, now, null, null, false);
    }
}
