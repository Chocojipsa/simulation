package com.timedeal.seatreservation.metrics;

import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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

    @Test
    void shouldReturnCachedMetrics() {
        // Arrange
        SystemMetricsService service = new SystemMetricsService(
                redisTemplate, interceptor, liveEventService, simulationService
        );

        // Act
        SystemMetrics metrics = service.getSystemMetrics();

        // Assert
        assertThat(metrics).isNotNull();
        assertThat(metrics.redisLockCount()).isEqualTo(0);
        assertThat(metrics.tps()).isEqualTo(0.0);
        assertThat(metrics.avgResponseTimeMs()).isEqualTo(0.0);
        assertThat(metrics.serverStats()).isEmpty();
    }

    @Test
    void shouldUpdateMetricsSafelyWhenEventNotFound() throws Exception {
        // Arrange
        SystemMetricsService service = new SystemMetricsService(
                redisTemplate, interceptor, liveEventService, simulationService
        );

        given(liveEventService.activeEvent()).willThrow(new NoSuchElementException());

        // Act
        service.updateMetrics();

        // Assert
        SystemMetrics metrics = service.getSystemMetrics();
        assertThat(metrics.serverStats()).isEmpty();
    }
}
