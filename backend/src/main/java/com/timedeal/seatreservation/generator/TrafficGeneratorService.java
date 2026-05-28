package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Profile("generator")
public class TrafficGeneratorService implements TrafficGeneratorClient {
    private final VirtualUserHttpClient client;
    private final String targetBaseUrl;
    private final int fallbackConcurrency;

    public TrafficGeneratorService(
            VirtualUserHttpClient client,
            @Value("${traffic-generator.target-base-url:http://localhost:8080}") String targetBaseUrl,
            @Value("${traffic-generator.default-concurrency:20}") int fallbackConcurrency
    ) {
        this.client = client;
        this.targetBaseUrl = targetBaseUrl;
        this.fallbackConcurrency = fallbackConcurrency;
    }

    @Override
    public void start(UUID simulationId, RunSimulationRequest request) {
        int concurrency = Math.max(1, request.concurrency() > 0 ? request.concurrency() : fallbackConcurrency);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            for (int number = 1; number <= request.virtualUserCount(); number++) {
                int virtualUserNumber = number;
                executor.submit(() -> client.runUser(targetBaseUrl, simulationId, virtualUserNumber));
            }
        } finally {
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
