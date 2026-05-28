package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficGeneratorServiceTest {
    @Test
    void sendsEveryVirtualUserThroughConfiguredTarget() throws Exception {
        List<String> calls = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(3);
        VirtualUserHttpClient client = (baseUrl, simulationId, virtualUserNumber) -> {
            calls.add(baseUrl + "|" + simulationId + "|" + virtualUserNumber);
            completed.countDown();
        };
        TrafficGeneratorService service = new TrafficGeneratorService(client, "http://nginx:8080", 1);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        service.start(simulationId, new RunSimulationRequest(3, 1));

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(calls).containsExactly(
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|1",
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|2",
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|3"
        );
        service.shutdown();
    }

    @Test
    void startReturnsBeforeVirtualUsersFinish() throws Exception {
        CountDownLatch firstUserStarted = new CountDownLatch(1);
        CountDownLatch allowUsersToFinish = new CountDownLatch(1);
        CountDownLatch startReturned = new CountDownLatch(1);
        VirtualUserHttpClient client = (baseUrl, simulationId, virtualUserNumber) -> {
            firstUserStarted.countDown();
            try {
                allowUsersToFinish.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        TrafficGeneratorService service = new TrafficGeneratorService(client, "http://nginx:8080", 1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000005");

        try {
            caller.submit(() -> {
                service.start(simulationId, new RunSimulationRequest(2, 1));
                startReturned.countDown();
            });

            assertThat(firstUserStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(startReturned.await(200, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            allowUsersToFinish.countDown();
            caller.shutdownNow();
            service.shutdown();
        }
    }

    @Test
    void appliesConfiguredDelayBeforeRunningEachVirtualUser() throws Exception {
        CountDownLatch completed = new CountDownLatch(2);
        List<Integer> delays = new ArrayList<>();
        AtomicInteger nextDelay = new AtomicInteger(100);
        VirtualUserHttpClient client = (baseUrl, simulationId, virtualUserNumber) -> completed.countDown();
        TrafficGeneratorService service = new TrafficGeneratorService(
                client,
                "http://nginx:8080",
                2,
                Executors.newCachedThreadPool(),
                () -> nextDelay.getAndAdd(50),
                delays::add
        );
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000006");

        service.start(simulationId, new RunSimulationRequest(2, 2));

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(delays).containsExactlyInAnyOrder(100, 150);
        service.shutdown();
    }
}
