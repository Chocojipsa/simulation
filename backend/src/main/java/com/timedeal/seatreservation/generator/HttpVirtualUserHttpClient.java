package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@Profile("generator")
public class HttpVirtualUserHttpClient implements VirtualUserHttpClient {
    private static final int DEFAULT_MAX_SEAT_ATTEMPTS = 300;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 100L;
    private static final int COMMAND_RETRY_ATTEMPTS = 5;

    private final VirtualUserCommandClient commandClient;
    private final int maxSeatAttempts;
    private final long retryDelayMillis;

    @Autowired
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

        runWithTransientRetry(() -> commandClient.postQueue(baseUrl, simulationId, virtualUserId));
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            VirtualUserCommandResponse response = runWithTransientRetry(
                    () -> commandClient.postSeatAttempt(baseUrl, simulationId, virtualUserId)
            );
            if (isTerminal(response.status())) {
                return;
            }
            sleepBriefly();
        }
    }

    private VirtualUserCommandResponse runWithTransientRetry(Supplier<VirtualUserCommandResponse> command) {
        RuntimeException lastException = null;
        for (int attempt = 0; attempt < COMMAND_RETRY_ATTEMPTS; attempt++) {
            try {
                return command.get();
            } catch (RuntimeException exception) {
                lastException = exception;
                sleepBriefly();
            }
        }
        throw lastException;
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
