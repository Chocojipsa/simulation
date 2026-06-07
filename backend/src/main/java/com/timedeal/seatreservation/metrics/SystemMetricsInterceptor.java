package com.timedeal.seatreservation.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class SystemMetricsInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MS = 1000L;

    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private volatile long windowStartMs = System.currentTimeMillis();

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

        if (now - windowStartMs >= WINDOW_MS) {
            synchronized (this) {
                if (now - windowStartMs >= WINDOW_MS) {
                    long count = requestCount.getAndSet(0);
                    long total = totalDurationMs.getAndSet(0);
                    
                    if (count > 0) {
                        this.lastTps = (double) count / (WINDOW_MS / 1000.0);
                        this.lastAvgResponseTimeMs = (double) total / count;
                    } else {
                        this.lastTps = 0.0;
                        this.lastAvgResponseTimeMs = 0.0;
                    }
                    this.windowStartMs = now;
                }
            }
        }

        requestCount.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
    }

    public double getTps() {
        if (System.currentTimeMillis() - windowStartMs >= WINDOW_MS * 2) {
            return 0.0;
        }
        return lastTps;
    }

    public double getAvgResponseTimeMs() {
        if (System.currentTimeMillis() - windowStartMs >= WINDOW_MS * 2) {
            return 0.0;
        }
        return lastAvgResponseTimeMs;
    }
}

