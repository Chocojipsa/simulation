package com.timedeal.seatreservation;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private static final long ASYNC_TIMEOUT_MILLIS = 300_000L;
    
    private final com.timedeal.seatreservation.metrics.SystemMetricsInterceptor systemMetricsInterceptor;

    public WebMvcConfig(com.timedeal.seatreservation.metrics.SystemMetricsInterceptor systemMetricsInterceptor) {
        this.systemMetricsInterceptor = systemMetricsInterceptor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(ASYNC_TIMEOUT_MILLIS);
        configurer.setTaskExecutor(mvcAsyncTaskExecutor());
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.core.task.AsyncTaskExecutor mvcAsyncTaskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(systemMetricsInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/system/metrics");
    }
}
