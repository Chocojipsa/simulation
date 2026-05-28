package com.timedeal.seatreservation.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record AiBatchSchedule(List<AiBatch> batches) {
    public static AiBatchSchedule defaultSchedule(int participantCount, int maxConcurrency) {
        int remaining = Math.max(0, participantCount);
        int[] weights = {10, 20, 30, 40, 50};
        long[] delays = {0, 100, 300, 700, 1200};
        List<AiBatch> batches = new ArrayList<>();
        for (int index = 0; index < weights.length && remaining > 0; index++) {
            int count = Math.min(weights[index], remaining);
            int concurrency = Math.max(1, Math.min(maxConcurrency, count));
            batches.add(new AiBatch(count, concurrency, Duration.ofMillis(delays[index])));
            remaining -= count;
        }
        if (remaining > 0) {
            int concurrency = Math.max(1, Math.min(maxConcurrency, remaining));
            batches.add(new AiBatch(remaining, concurrency, Duration.ofMillis(1800)));
        }
        return new AiBatchSchedule(List.copyOf(batches));
    }
}
