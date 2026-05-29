package com.timedeal.seatreservation.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record AiBatchSchedule(List<AiBatch> batches) {
    public static AiBatchSchedule defaultSchedule(int participantCount, int maxConcurrency) {
        int remaining = Math.max(0, participantCount);
        int normalizedConcurrency = Math.max(1, maxConcurrency);
        int[] batchSizes = {10, 15, 20, 25, 30};
        long delayMillis = 500;
        List<AiBatch> batches = new ArrayList<>();
        for (int batchSize : batchSizes) {
            if (remaining <= 0) {
                break;
            }
            int count = Math.min(batchSize, remaining);
            int concurrency = Math.min(normalizedConcurrency, count);
            batches.add(new AiBatch(count, concurrency, Duration.ofMillis(delayMillis)));
            remaining -= count;
            delayMillis += 500;
        }
        if (remaining > 0) {
            int concurrency = Math.min(normalizedConcurrency, remaining);
            batches.add(new AiBatch(remaining, concurrency, Duration.ofMillis(delayMillis)));
        }
        return new AiBatchSchedule(List.copyOf(batches));
    }
}
