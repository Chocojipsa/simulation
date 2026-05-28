package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
        String displayName = "AI-" + virtualUserNumber;
        JoinEventResponse joined = runWithTransientRetry(() -> commandClient.joinEvent(baseUrl, simulationId, displayName));
        UUID participantId = joined.participantId();

        runWithTransientRetry(() -> commandClient.postQueue(baseUrl, simulationId, participantId));
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            SeatHoldResponse hold = runWithTransientRetry(() -> commandClient.holdRandomSeat(baseUrl, simulationId, participantId));
            if ("PAYMENT_PENDING".equals(hold.status()) || "PAYMENT_REQUESTED".equals(hold.status())) {
                runWithTransientRetry(() -> commandClient.confirmPayment(baseUrl, simulationId, participantId));
                return;
            }
            if ("FAILED".equals(hold.status())) {
                return;
            }
            sleepBriefly();
        }
    }

    private <T> T runWithTransientRetry(Supplier<T> command) {
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
