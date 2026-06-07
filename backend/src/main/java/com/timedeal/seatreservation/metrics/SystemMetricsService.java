package com.timedeal.seatreservation.metrics;

import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class SystemMetricsService {
    private final StringRedisTemplate redisTemplate;
    private final SystemMetricsInterceptor interceptor;
    private final LiveEventService liveEventService;
    private final SimulationService simulationService;
    private final AdminClient adminClient;
    private final String consumerGroupId;

    private volatile SystemMetrics cachedMetrics = new SystemMetrics(0, 0, 0.0, 0.0, List.of());

    public SystemMetricsService(
            StringRedisTemplate redisTemplate,
            SystemMetricsInterceptor interceptor,
            LiveEventService liveEventService,
            SimulationService simulationService,
            KafkaAdmin kafkaAdmin,
            @Value("${spring.kafka.consumer.group-id:payment-result-applier}") String consumerGroupId
    ) {
        this.redisTemplate = redisTemplate;
        this.interceptor = interceptor;
        this.liveEventService = liveEventService;
        this.simulationService = simulationService;
        this.adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
        this.consumerGroupId = consumerGroupId;
    }

    public SystemMetrics getSystemMetrics() {
        return cachedMetrics;
    }

    @Scheduled(fixedRate = 2000)
    public void updateMetrics() {
        long kafkaLag = calculateKafkaLag();
        long redisLockCount = calculateRedisLockCount();
        double tps = interceptor.getTps();
        double avgResponseTimeMs = interceptor.getAvgResponseTimeMs();
        List<ServerStatsView> serverStats = getServerStats();

        this.cachedMetrics = new SystemMetrics(kafkaLag, redisLockCount, tps, avgResponseTimeMs, serverStats);
    }

    private long calculateKafkaLag() {
        try {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
            Map<TopicPartition, OffsetAndMetadata> consumerOffsets = offsetsResult.partitionsToOffsetAndMetadata().get(2, java.util.concurrent.TimeUnit.SECONDS);
            
            if (consumerOffsets == null || consumerOffsets.isEmpty()) {
                return 0;
            }

            Map<TopicPartition, OffsetSpec> requestOffsets = consumerOffsets.keySet().stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(requestOffsets).all().get(2, java.util.concurrent.TimeUnit.SECONDS);

            long lag = 0;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : consumerOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                if (entry.getValue() != null) {
                    long consumerOffset = entry.getValue().offset();
                    if (endOffsets.containsKey(tp)) {
                        long endOffset = endOffsets.get(tp).offset();
                        lag += Math.max(0, endOffset - consumerOffset);
                    }
                }
            }
            return lag;
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            return 0;
        }
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

    @PreDestroy
    public void close() {
        if (adminClient != null) {
            adminClient.close();
        }
    }
}
