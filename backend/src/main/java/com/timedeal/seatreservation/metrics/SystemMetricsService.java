package com.timedeal.seatreservation.metrics;

import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
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

    public SystemMetricsService(
            StringRedisTemplate redisTemplate,
            SystemMetricsInterceptor interceptor,
            LiveEventService liveEventService,
            SimulationService simulationService,
            KafkaAdmin kafkaAdmin
    ) {
        this.redisTemplate = redisTemplate;
        this.interceptor = interceptor;
        this.liveEventService = liveEventService;
        this.simulationService = simulationService;
        this.adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    public SystemMetrics getSystemMetrics() {
        long kafkaLag = calculateKafkaLag();
        long redisLockCount = calculateRedisLockCount();
        double tps = interceptor.getTps();
        double avgResponseTimeMs = interceptor.getAvgResponseTimeMs();
        List<ServerStatsView> serverStats = getServerStats();

        return new SystemMetrics(kafkaLag, redisLockCount, tps, avgResponseTimeMs, serverStats);
    }

    private long calculateKafkaLag() {
        try {
            String groupId = "payment-result-applier";
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> consumerOffsets = offsetsResult.partitionsToOffsetAndMetadata().get();
            
            if (consumerOffsets == null || consumerOffsets.isEmpty()) {
                return 0;
            }

            Map<TopicPartition, OffsetSpec> requestOffsets = consumerOffsets.keySet().stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(requestOffsets).all().get();

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
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    private long calculateRedisLockCount() {
        Long count = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
            long c = 0;
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("simulation:*:lock").count(100).build())) {
                while (cursor.hasNext()) {
                    cursor.next();
                    c++;
                }
            }
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match("live-event:*:lock").count(100).build())) {
                while (cursor.hasNext()) {
                    cursor.next();
                    c++;
                }
            }
            return c;
        });
        return count != null ? count : 0L;
    }

    private List<ServerStatsView> getServerStats() {
        try {
            UUID eventId = liveEventService.activeEvent().eventId();
            return simulationService.getSimulation(eventId).serverStats();
        } catch (Exception e) {
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
