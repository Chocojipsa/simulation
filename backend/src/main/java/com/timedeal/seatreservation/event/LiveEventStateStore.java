package com.timedeal.seatreservation.event;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface LiveEventStateStore {
    LiveEventMetadata getOrCreate(UUID eventId, Instant now);

    LiveEventMetadata startCountdown(UUID eventId, Instant now, Duration countdownDuration, Duration openWindow);

    boolean claimAiStart(UUID eventId);

    LiveEventMetadata reset(UUID eventId, Instant now);
}
