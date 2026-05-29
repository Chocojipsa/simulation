package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Component
@Profile("generator")
public class HttpVirtualUserHttpClient implements VirtualUserHttpClient {
    private static final int DEFAULT_MAX_SEAT_ATTEMPTS = 3000;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 100L;
    private static final int COMMAND_RETRY_ATTEMPTS = 5;

    private final VirtualUserCommandClient commandClient;
    private final int maxSeatAttempts;
    private final long retryDelayMillis;
    private final IntSupplier seatClickDelayMillis;
    private final IntConsumer sleeper;

    @Autowired
    public HttpVirtualUserHttpClient(VirtualUserCommandClient commandClient) {
        this(commandClient, DEFAULT_MAX_SEAT_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS);
    }

    HttpVirtualUserHttpClient(
            VirtualUserCommandClient commandClient,
            int maxSeatAttempts,
            long retryDelayMillis
    ) {
        this(commandClient, maxSeatAttempts, retryDelayMillis, randomDelaySupplier(200, 700), HttpVirtualUserHttpClient::sleep);
    }

    HttpVirtualUserHttpClient(
            VirtualUserCommandClient commandClient,
            int maxSeatAttempts,
            long retryDelayMillis,
            IntSupplier seatClickDelayMillis,
            IntConsumer sleeper
    ) {
        this.commandClient = commandClient;
        this.maxSeatAttempts = maxSeatAttempts;
        this.retryDelayMillis = retryDelayMillis;
        this.seatClickDelayMillis = seatClickDelayMillis;
        this.sleeper = sleeper;
    }

    @Override
    public void runUser(String baseUrl, UUID simulationId, int virtualUserNumber) {
        String displayName = "AI-" + virtualUserNumber;
        JoinEventResponse joined = runWithTransientRetry(() -> commandClient.joinEvent(baseUrl, simulationId, displayName));
        UUID participantId = joined.participantId();

        if (!waitUntilAdmitted(baseUrl, simulationId, participantId)) {
            return;
        }
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            sleeper.accept(seatClickDelayMillis.getAsInt());
            SeatHoldResponse hold = runWithTransientRetryOrNull(() -> commandClient.holdRandomSeat(baseUrl, simulationId, participantId));
            if (hold == null) {
                sleepBriefly();
                continue;
            }
            if ("PAYMENT_PENDING".equals(hold.status()) || "PAYMENT_REQUESTED".equals(hold.status())) {
                confirmPaymentUntilAccepted(baseUrl, simulationId, participantId);
                return;
            }
            if (isTerminalStatus(hold.status())) {
                return;
            }
            sleepBriefly();
        }
    }

    private boolean waitUntilAdmitted(String baseUrl, UUID simulationId, UUID participantId) {
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            VirtualUserCommandResponse response = runWithTransientRetryOrNull(() -> commandClient.postQueue(baseUrl, simulationId, participantId));
            if (response == null) {
                sleepBriefly();
                continue;
            }
            if ("ADMITTED".equals(response.status())) {
                return true;
            }
            if (isTerminalStatus(response.status())) {
                return false;
            }
            sleepBriefly();
        }
        return false;
    }

    private void confirmPaymentUntilAccepted(String baseUrl, UUID simulationId, UUID participantId) {
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            PaymentConfirmResponse response = runWithTransientRetryOrNull(() -> commandClient.confirmPayment(baseUrl, simulationId, participantId));
            if (response != null || Thread.currentThread().isInterrupted()) {
                return;
            }
            sleepBriefly();
        }
    }

    private boolean isTerminalStatus(String status) {
        return "FAILED".equals(status) || "EVENT_ENDED".equals(status);
    }

    private <T> T runWithTransientRetryOrNull(Supplier<T> command) {
        try {
            return runWithTransientRetry(command);
        } catch (RuntimeException exception) {
            return null;
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
        sleep((int) retryDelayMillis);
    }

    private static IntSupplier randomDelaySupplier(int minMillis, int maxMillis) {
        int normalizedMin = Math.max(0, minMillis);
        int normalizedMax = Math.max(normalizedMin, maxMillis);
        return () -> ThreadLocalRandom.current().nextInt(normalizedMin, normalizedMax + 1);
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
