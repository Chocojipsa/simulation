package com.timedeal.seatreservation.metrics;

import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class SystemMetricsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SystemMetricsInterceptor interceptor;

    @Mock
    private LiveEventService liveEventService;

    @Mock
    private SimulationService simulationService;

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Mock
    private AdminClient adminClient;

    private MockedStatic<AdminClient> mockedAdminClient;

    @BeforeEach
    void setUp() {
        mockedAdminClient = mockStatic(AdminClient.class);
        mockedAdminClient.when(() -> AdminClient.create(any(java.util.Map.class))).thenReturn(adminClient);
    }

    @AfterEach
    void tearDown() {
        mockedAdminClient.close();
    }

    @Test
    void shouldReturnCachedMetrics() {
        // Arrange
        given(kafkaAdmin.getConfigurationProperties()).willReturn(java.util.Map.of());
        SystemMetricsService service = new SystemMetricsService(
                redisTemplate, interceptor, liveEventService, simulationService, kafkaAdmin, "test-group"
        );

        // Act
        SystemMetrics metrics = service.getSystemMetrics();

        // Assert
        assertThat(metrics).isNotNull();
        assertThat(metrics.kafkaLag()).isEqualTo(0);
        assertThat(metrics.redisLockCount()).isEqualTo(0);
        assertThat(metrics.tps()).isEqualTo(0.0);
        assertThat(metrics.avgResponseTimeMs()).isEqualTo(0.0);
        assertThat(metrics.serverStats()).isEmpty();
    }

    @Test
    void shouldUpdateMetricsSafelyWhenEventNotFound() throws Exception {
        given(kafkaAdmin.getConfigurationProperties()).willReturn(java.util.Map.of());
        SystemMetricsService service = new SystemMetricsService(
                redisTemplate, interceptor, liveEventService, simulationService, kafkaAdmin, "test-group"
        );

        org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult mockResult = org.mockito.Mockito.mock(org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult.class);
        org.apache.kafka.common.KafkaFuture mockFuture = org.mockito.Mockito.mock(org.apache.kafka.common.KafkaFuture.class);
        given(adminClient.listConsumerGroupOffsets(any(String.class))).willReturn(mockResult);
        given(mockResult.partitionsToOffsetAndMetadata()).willReturn(mockFuture);
        given(mockFuture.get(any(Long.class), any())).willReturn(java.util.Map.of());

        given(liveEventService.activeEvent()).willThrow(new NoSuchElementException());

        // Act
        service.updateMetrics();

        // Assert
        SystemMetrics metrics = service.getSystemMetrics();
        assertThat(metrics.serverStats()).isEmpty();
    }
}
