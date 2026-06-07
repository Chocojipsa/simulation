package com.timedeal.seatreservation.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SystemMetricsInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MS = 1000L;

    // TPS calculation
    private final AtomicReference<WindowData> currentWindow = new AtomicReference<>(new WindowData(System.currentTimeMillis(), 0, 0));
    
    // Smooth averages
    private volatile double lastTps = 0.0;
    private volatile double lastAvgResponseTimeMs = 0.0;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("startTime");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequest(duration);
        }
    }

    private void recordRequest(long durationMs) {
        long now = System.currentTimeMillis();
        WindowData current;
        WindowData next;

        do {
            current = currentWindow.get();
            if (now - current.startTimeMs >= WINDOW_MS) {
                // rollover window
                calculateMetrics(current);
                next = new WindowData(now, 1, durationMs);
            } else {
                next = new WindowData(current.startTimeMs, current.count + 1, current.totalDurationMs + durationMs);
            }
        } while (!currentWindow.compareAndSet(current, next));
        
        // Also trigger calculation if time has passed without new requests
        if (now - current.startTimeMs >= WINDOW_MS && current.count > 0) {
            calculateMetrics(current);
        }
    }

    private synchronized void calculateMetrics(WindowData windowData) {
        if (windowData.count > 0) {
            this.lastTps = (double) windowData.count / (WINDOW_MS / 1000.0);
            this.lastAvgResponseTimeMs = (double) windowData.totalDurationMs / windowData.count;
        } else {
            this.lastTps = 0.0;
            this.lastAvgResponseTimeMs = 0.0;
        }
    }

    // Refresh if idle
    public void refreshIdle() {
        long now = System.currentTimeMillis();
        WindowData current = currentWindow.get();
        if (now - current.startTimeMs >= WINDOW_MS * 2) {
             this.lastTps = 0.0;
        }
    }

    public double getTps() {
        refreshIdle();
        return lastTps;
    }

    public double getAvgResponseTimeMs() {
        return lastAvgResponseTimeMs;
    }

    private static class WindowData {
        final long startTimeMs;
        final long count;
        final long totalDurationMs;

        WindowData(long startTimeMs, long count, long totalDurationMs) {
            this.startTimeMs = startTimeMs;
            this.count = count;
            this.totalDurationMs = totalDurationMs;
        }
    }
}
