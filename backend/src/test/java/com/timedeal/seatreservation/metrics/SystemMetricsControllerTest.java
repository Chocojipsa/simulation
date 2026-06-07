package com.timedeal.seatreservation.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemMetricsController.class)
class SystemMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemMetricsService systemMetricsService;

    @MockBean
    private SystemMetricsInterceptor systemMetricsInterceptor;

    @Test
    void shouldReturnSystemMetrics() throws Exception {
        SystemMetrics metrics = new SystemMetrics(12, 3, 45.2, 23.0, List.of());
        given(systemMetricsService.getSystemMetrics()).willReturn(metrics);

        mockMvc.perform(get("/api/system/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kafkaLag").value(12))
                .andExpect(jsonPath("$.redisLockCount").value(3))
                .andExpect(jsonPath("$.tps").value(45.2))
                .andExpect(jsonPath("$.avgResponseTimeMs").value(23.0))
                .andExpect(jsonPath("$.serverStats").isArray());
    }
}
