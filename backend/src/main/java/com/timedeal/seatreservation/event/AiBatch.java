package com.timedeal.seatreservation.event;

import java.time.Duration;

public record AiBatch(
        int participantCount,
        int concurrency,
        Duration delay
) {
}
