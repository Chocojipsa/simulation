package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class LiveEventAiStarter {
    private static final Logger log = LoggerFactory.getLogger(LiveEventAiStarter.class);

    public record AiConfig(int participantCount, int concurrency, String speed) {}

    private final ConcurrentHashMap<UUID, AiConfig> localConfigs = new ConcurrentHashMap<>();
    private final SimulationService simulationService;
    private final int participantCount;
    private final int concurrency;
    private final BatchScheduler scheduler;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public LiveEventAiStarter(
            SimulationService simulationService,
            @Value("${live-event.ai-user-count:150}") int participantCount,
            @Value("${live-event.ai.concurrency:50}") int concurrency,
            ObjectProvider<RedisTemplate<String, String>> redisTemplateProvider,
            ObjectMapper objectMapper
    ) {
        this(
                simulationService,
                participantCount,
                concurrency,
                new ExecutorBatchScheduler(Executors.newSingleThreadScheduledExecutor()),
                redisTemplateProvider.getIfAvailable(),
                objectMapper
        );
    }

    LiveEventAiStarter(
            SimulationService simulationService,
            int participantCount,
            int concurrency,
            BatchScheduler scheduler
    ) {
        this(simulationService, participantCount, concurrency, scheduler, null, new ObjectMapper());
    }

    LiveEventAiStarter(
            SimulationService simulationService,
            int participantCount,
            int concurrency,
            BatchScheduler scheduler,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.simulationService = simulationService;
        this.participantCount = participantCount;
        this.concurrency = concurrency;
        this.scheduler = scheduler;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void start(UUID eventId) {
        AiConfig config = null;
        if (redisTemplate != null) {
            String jsonKey = "live-event:" + eventId + ":ai-config";
            try {
                String json = redisTemplate.opsForValue().get(jsonKey);
                if (json != null) {
                    try {
                        config = objectMapper.readValue(json, AiConfig.class);
                    } finally {
                        redisTemplate.delete(jsonKey);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve or deserialize AI config from Redis for event {}. Falling back to local configs.", eventId, e);
            }
        }
        if (config == null) {
            config = localConfigs.remove(eventId);
        }

        int count = config != null ? config.participantCount() : this.participantCount;
        int maxConcurrency = config != null ? config.concurrency() : this.concurrency;
        String speed = config != null ? config.speed() : "NORMAL";

        Duration interval;
        if ("FAST".equals(speed)) {
            interval = Duration.ofMillis(100);
        } else if ("SLOW".equals(speed)) {
            interval = Duration.ofMillis(1500);
        } else {
            interval = Duration.ofMillis(500);
        }

        AiBatchSchedule schedule = buildCustomSchedule(count, maxConcurrency, interval);
        for (AiBatch batch : schedule.batches()) {
            scheduler.schedule(batch.delay(), () -> simulationService.runSimulation(
                    eventId,
                    new RunSimulationRequest(batch.participantCount(), batch.concurrency(), batch.virtualUserOffset())
            ));
        }
    }

    private AiBatchSchedule buildCustomSchedule(int participantCount, int maxConcurrency, Duration interval) {
        int remaining = Math.max(0, participantCount);
        int normalizedConcurrency = Math.max(1, maxConcurrency);
        double[] batchPercentages = {0.10, 0.15, 0.20, 0.25, 0.30};
        long delayMillis = interval.toMillis();
        java.util.ArrayList<AiBatch> batches = new java.util.ArrayList<>();
        int currentOffset = 0;
        
        for (double pct : batchPercentages) {
            if (remaining <= 0) break;
            int count = (int) Math.round(participantCount * pct);
            count = Math.min(count, remaining);
            if (count <= 0) continue;
            
            int concurrency = Math.min(normalizedConcurrency, count);
            batches.add(new AiBatch(count, concurrency, Duration.ofMillis(delayMillis), currentOffset));
            currentOffset += count;
            remaining -= count;
            delayMillis += interval.toMillis();
        }
        if (remaining > 0) {
            int concurrency = Math.min(normalizedConcurrency, remaining);
            batches.add(new AiBatch(remaining, concurrency, Duration.ofMillis(delayMillis), currentOffset));
        }
        return new AiBatchSchedule(List.copyOf(batches));
    }

    public void configure(UUID eventId, Integer participantCount, Integer concurrency, String speed) {
        int count = participantCount != null ? Math.max(0, Math.min(1000, participantCount)) : this.participantCount;
        int maxConcurrency = concurrency != null ? Math.max(1, Math.min(120, concurrency)) : this.concurrency;
        String normalizedSpeed = speed != null ? speed.toUpperCase() : "NORMAL";
        AiConfig config = new AiConfig(count, maxConcurrency, normalizedSpeed);

        simulationService.saveConcurrency(eventId, maxConcurrency);

        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(config);
                redisTemplate.opsForValue().set("live-event:" + eventId + ":ai-config", json, Duration.ofMinutes(10));
            } catch (Exception e) {
                log.warn("Failed to save AI config to Redis for event {}. Falling back to local cache.", eventId, e);
                localConfigs.put(eventId, config);
            }
        } else {
            localConfigs.put(eventId, config);
        }
    }

    public AiConfig getCachedConfig(UUID eventId) {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get("live-event:" + eventId + ":ai-config");
                if (json != null) {
                    return objectMapper.readValue(json, AiConfig.class);
                }
            } catch (Exception e) {
                log.warn("Failed to get cached AI config from Redis for event {}. Falling back to local cache.", eventId, e);
            }
        }
        return localConfigs.get(eventId);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

    interface BatchScheduler {
        void schedule(Duration delay, Runnable task);

        default void shutdown() {
        }
    }

    static final class ExecutorBatchScheduler implements BatchScheduler {
        private final ScheduledExecutorService executor;

        ExecutorBatchScheduler(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void schedule(Duration delay, Runnable task) {
            executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}
