package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Profile("generator")
public class TrafficGeneratorService implements TrafficGeneratorClient {
    private static final Logger log = LoggerFactory.getLogger(TrafficGeneratorService.class);

    private final VirtualUserHttpClient client;
    private final String targetBaseUrl;
    private final int fallbackConcurrency;
    private final ExecutorService simulationExecutor;
    private final IntSupplier virtualUserDelayMillis;
    private final IntConsumer sleeper;

    @Autowired
    public TrafficGeneratorService(
            VirtualUserHttpClient client,
            @Value("${traffic-generator.target-base-url:http://localhost:8080}") String targetBaseUrl,
            @Value("${traffic-generator.default-concurrency:20}") int fallbackConcurrency,
            @Value("${traffic-generator.virtual-user-delay-min-ms:80}") int virtualUserDelayMinMillis,
            @Value("${traffic-generator.virtual-user-delay-max-ms:300}") int virtualUserDelayMaxMillis
    ) {
        this(
                client,
                targetBaseUrl,
                fallbackConcurrency,
                Executors.newCachedThreadPool(),
                randomDelaySupplier(virtualUserDelayMinMillis, virtualUserDelayMaxMillis),
                TrafficGeneratorService::sleep
        );
    }

    TrafficGeneratorService(VirtualUserHttpClient client, String targetBaseUrl, int fallbackConcurrency) {
        this(client, targetBaseUrl, fallbackConcurrency, Executors.newCachedThreadPool(), () -> 0, ignored -> {
        });
    }

    TrafficGeneratorService(
            VirtualUserHttpClient client,
            String targetBaseUrl,
            int fallbackConcurrency,
            ExecutorService simulationExecutor,
            IntSupplier virtualUserDelayMillis,
            IntConsumer sleeper
    ) {
        this.client = client;
        this.targetBaseUrl = targetBaseUrl;
        this.fallbackConcurrency = fallbackConcurrency;
        this.simulationExecutor = simulationExecutor;
        this.virtualUserDelayMillis = virtualUserDelayMillis;
        this.sleeper = sleeper;
    }

    @Override
    public void start(UUID simulationId, RunSimulationRequest request) {
        log.info("start simulation request received. simulationId={}, virtualUserCount={}, concurrency={}", 
                simulationId, request.virtualUserCount(), request.concurrency());
        simulationExecutor.submit(() -> runSimulation(simulationId, request));
    }

    private void runSimulation(UUID simulationId, RunSimulationRequest request) {
        int concurrency = Math.max(1, request.concurrency() > 0 ? request.concurrency() : fallbackConcurrency);
        log.info("Running simulation: simulationId={}, virtualUserCount={}, concurrency={}", 
                simulationId, request.virtualUserCount(), concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            for (int number = 1; number <= request.virtualUserCount(); number++) {
                int virtualUserNumber = number;
                executor.submit(() -> runVirtualUser(simulationId, virtualUserNumber));
            }
        } finally {
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log.warn("Simulation executor did not terminate within 1 minute, forcing shutdownNow for simulationId={}", simulationId);
                executor.shutdownNow();
            } else {
                log.info("Simulation executor terminated successfully for simulationId={}", simulationId);
            }
        } catch (InterruptedException exception) {
            log.error("Simulation awaitTermination interrupted for simulationId={}", simulationId, exception);
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void runVirtualUser(UUID simulationId, int virtualUserNumber) {
        sleeper.accept(virtualUserDelayMillis.getAsInt());
        log.debug("Starting virtual user #{} for simulationId={}", virtualUserNumber, simulationId);
        client.runUser(targetBaseUrl, simulationId, virtualUserNumber);
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down TrafficGeneratorService executor");
        simulationExecutor.shutdownNow();
    }

    private static IntSupplier randomDelaySupplier(int minMillis, int maxMillis) {
        int normalizedMin = Math.max(0, minMillis);
        int normalizedMax = Math.max(normalizedMin, maxMillis);
        Random random = new Random();
        return () -> {
            if (normalizedMin == normalizedMax) {
                return normalizedMin;
            }
            return random.nextInt(normalizedMax - normalizedMin + 1) + normalizedMin;
        };
    }

    private static void sleep(int delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
