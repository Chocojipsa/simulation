package com.timedeal.seatreservation.metrics;

import com.timedeal.seatreservation.simulation.ServerStatsView;
import java.util.List;

public record SystemMetrics(
        long kafkaLag,
        long redisLockCount,
        double tps,
        double avgResponseTimeMs,
        List<ServerStatsView> serverStats
) {}
