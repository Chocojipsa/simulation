package com.timedeal.seatreservation.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/metrics")
public class SystemMetricsController {

    private final SystemMetricsService systemMetricsService;

    public SystemMetricsController(SystemMetricsService systemMetricsService) {
        this.systemMetricsService = systemMetricsService;
    }

    @GetMapping
    public SystemMetrics getMetrics() {
        return systemMetricsService.getSystemMetrics();
    }
}
