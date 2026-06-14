package com.timedeal.seatreservation.metrics;

import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SystemMetricsService {
    private final StringRedisTemplate redisTemplate;
    private final SystemMetricsInterceptor interceptor;
    private final LiveEventService liveEventService;
    private final SimulationService simulationService;

    private volatile SystemMetrics cachedMetrics = new SystemMetrics(0, 0.0, 0.0, List.of());

    public SystemMetricsService(
            StringRedisTemplate redisTemplate,
            SystemMetricsInterceptor interceptor,
            LiveEventService liveEventService,
            SimulationService simulationService
    ) {
        this.redisTemplate = redisTemplate;
        this.interceptor = interceptor;
        this.liveEventService = liveEventService;
        this.simulationService = simulationService;
    }

    public SystemMetrics getSystemMetrics() {
        return cachedMetrics;
    }

    @Scheduled(fixedRate = 2000)
    public void updateMetrics() {
        long redisLockCount = calculateRedisLockCount();
        double tps = interceptor.getTps();
        double avgResponseTimeMs = interceptor.getAvgResponseTimeMs();
        List<ServerStatsView> serverStats = getServerStats();

        this.cachedMetrics = new SystemMetrics(redisLockCount, tps, avgResponseTimeMs, serverStats);
    }

    private long calculateRedisLockCount() {
        try {
            Long count = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                long c = 0;
                long iterations = 0;
                try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("simulation:*:lock").count(100).build())) {
                    while (cursor.hasNext() && iterations < 1000) {
                        cursor.next();
                        c++;
                        iterations++;
                    }
                }
                iterations = 0;
                try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("live-event:*:lock").count(100).build())) {
                    while (cursor.hasNext() && iterations < 1000) {
                        cursor.next();
                        c++;
                        iterations++;
                    }
                }
                return c;
            });
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<ServerStatsView> getServerStats() {
        try {
            UUID eventId = liveEventService.activeEvent().eventId();
            return simulationService.getSimulation(eventId).serverStats();
        } catch (java.util.NoSuchElementException | IllegalStateException | IllegalArgumentException e) {
            return List.of();
        }
    }
}
