package com.timedeal.seatreservation.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SystemMetricsInterceptorTest {

    @Test
    void shouldRecordMetricsAndDecay() throws Exception {
        SystemMetricsInterceptor interceptor = new SystemMetricsInterceptor();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Perform first request
        interceptor.preHandle(request, response, new Object());
        // Sleep a bit to simulate duration
        Thread.sleep(50);
        interceptor.afterCompletion(request, response, new Object(), null);

        // Wait for window rollover (1000ms)
        Thread.sleep(1200);

        // Perform second request to trigger calculate
        interceptor.preHandle(request, response, new Object());
        Thread.sleep(50);
        interceptor.afterCompletion(request, response, new Object(), null);

        // Now TPS and AvgResponse should be calculated from the first window
        assertThat(interceptor.getTps()).isGreaterThan(0);
        assertThat(interceptor.getAvgResponseTimeMs()).isGreaterThan(0);

        // Wait for decay (2000ms)
        Thread.sleep(2500);

        // Should be decayed to 0
        assertThat(interceptor.getTps()).isEqualTo(0.0);
        assertThat(interceptor.getAvgResponseTimeMs()).isEqualTo(0.0);
    }
}
