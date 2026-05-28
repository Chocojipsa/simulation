package com.timedeal.seatreservation.simulation;

public record ServerStatsView(
        String serverId,
        long requestCount,
        long conflictCount,
        long successCount
) {
}
