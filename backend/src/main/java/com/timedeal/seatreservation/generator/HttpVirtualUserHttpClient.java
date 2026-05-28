package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Profile("generator")
public class HttpVirtualUserHttpClient implements VirtualUserHttpClient {
    private static final int DEFAULT_MAX_SEAT_ATTEMPTS = 30;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 100L;

    private final VirtualUserCommandClient commandClient;
    private final int maxSeatAttempts;
    private final long retryDelayMillis;

    public HttpVirtualUserHttpClient(VirtualUserCommandClient commandClient) {
        this(commandClient, DEFAULT_MAX_SEAT_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS);
    }

    HttpVirtualUserHttpClient(
            VirtualUserCommandClient commandClient,
            int maxSeatAttempts,
            long retryDelayMillis
    ) {
        this.commandClient = commandClient;
        this.maxSeatAttempts = maxSeatAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public void runUser(String baseUrl, UUID simulationId, int virtualUserNumber) {
        UUID virtualUserId = UUID.nameUUIDFromBytes(
                (simulationId + ":" + virtualUserNumber).getBytes(StandardCharsets.UTF_8)
        );

        commandClient.postQueue(baseUrl, simulationId, virtualUserId);
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            VirtualUserCommandResponse response = commandClient.postSeatAttempt(baseUrl, simulationId, virtualUserId);
            if (isTerminal(response.status())) {
                return;
            }
            sleepBriefly();
        }
    }

    private boolean isTerminal(String status) {
        return "PAYMENT_REQUESTED".equals(status)
                || "COMPLETED".equals(status)
                || "FAILED".equals(status);
    }

    private void sleepBriefly() {
        if (retryDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
