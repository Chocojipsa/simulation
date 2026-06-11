package com.timedeal.seatreservation.event;

import java.time.Duration;

public record AiBatch(
        int participantCount,
        int concurrency,
        Duration delay,
        int virtualUserOffset
) {
    public AiBatch(int participantCount, int concurrency, Duration delay) {
        this(participantCount, concurrency, delay, 0);
    }
}
