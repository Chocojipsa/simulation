package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.events.UserActivityPublisher;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("generator")
public class HttpVirtualUserHttpClient implements VirtualUserHttpClient {
    private static final Logger log = LoggerFactory.getLogger(HttpVirtualUserHttpClient.class);

    @Autowired(required = false)
    private UserActivityPublisher activityPublisher;

    private static final int DEFAULT_MAX_SEAT_ATTEMPTS = 3000;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 100L;
    private static final int COMMAND_RETRY_ATTEMPTS = 5;

    private final VirtualUserCommandClient commandClient;
    private final RestClient restClient;
    private final String controlBaseUrl;
    private final int maxSeatAttempts;
    private final long retryDelayMillis;
    private final IntSupplier seatClickDelayMillis;
    private final IntConsumer sleeper;

    @Autowired
    public HttpVirtualUserHttpClient(
            VirtualUserCommandClient commandClient,
            @Value("${traffic-generator.control-base-url:http://localhost:8080}") String controlBaseUrl
    ) {
        this(commandClient, controlBaseUrl, DEFAULT_MAX_SEAT_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS);
    }

    HttpVirtualUserHttpClient(
            VirtualUserCommandClient commandClient,
            String controlBaseUrl,
            int maxSeatAttempts,
            long retryDelayMillis
    ) {
        this(commandClient, controlBaseUrl, maxSeatAttempts, retryDelayMillis, randomDelaySupplier(200, 700), HttpVirtualUserHttpClient::sleep);
    }

    HttpVirtualUserHttpClient(
            VirtualUserCommandClient commandClient,
            String controlBaseUrl,
            int maxSeatAttempts,
            long retryDelayMillis,
            IntSupplier seatClickDelayMillis,
            IntConsumer sleeper
    ) {
        this.commandClient = commandClient;
        this.controlBaseUrl = controlBaseUrl;
        this.restClient = RestClient.create();
        this.maxSeatAttempts = maxSeatAttempts;
        this.retryDelayMillis = retryDelayMillis;
        this.seatClickDelayMillis = seatClickDelayMillis;
        this.sleeper = sleeper;
    }

    void setActivityPublisher(UserActivityPublisher activityPublisher) {
        this.activityPublisher = activityPublisher;
    }

    @Override
    public void runUser(String baseUrl, UUID simulationId, int virtualUserNumber) {
        String displayName = "AI-" + virtualUserNumber;
        log.info("Starting virtual user displayName={} for simulationId={}", displayName, simulationId);
        JoinEventResponse joined = runWithTransientRetry(() -> commandClient.joinEvent(baseUrl, simulationId, displayName));
        UUID participantId = joined.participantId();
        logActivity(simulationId, participantId, "INTENT", "이벤트 입장을 시도합니다.");

        if (!waitUntilAdmitted(baseUrl, simulationId, participantId)) {
            logActivity(simulationId, participantId, "FAILED", "입장에 실패했습니다.");
            log.warn("Virtual user displayName={} failed to admit to event for simulationId={}", displayName, simulationId);
            return;
        }
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            sleeper.accept(seatClickDelayMillis.getAsInt());
            logActivity(simulationId, participantId, "THINKING", "비어있는 좌석을 탐색 중입니다...");
            SeatHoldResponse hold = runWithTransientRetryOrNull(() -> commandClient.holdRandomSeat(baseUrl, simulationId, participantId));
            if (hold == null) {
                logActivity(simulationId, participantId, "RETRY", "좌석 탐색 중 오류가 발생했습니다. 다시 시도합니다.");
                log.warn("Virtual user displayName={} holdRandomSeat returned null at attempt={}", displayName, attempt);
                sleepBriefly();
                continue;
            }
            if (requiresPaymentConfirmation(hold)) {
                logActivity(simulationId, participantId, "ACTION", hold.selectedSeatLabel() + " 좌석을 발견했습니다! 결제를 진행합니다.");
                log.info("Virtual user displayName={} held seat={} successfully in simulationId={}. Proceeding to payment...", 
                        displayName, hold.selectedSeatLabel(), simulationId);
                confirmPaymentUntilAccepted(baseUrl, simulationId, participantId);
                return;
            }
            if (isTerminalStatus(hold.status())) {
                logActivity(simulationId, participantId, "FAILED", "예약 가능 좌석이 없어 중단합니다.");
                log.info("Virtual user displayName={} stopped due to terminal status={} in simulationId={}", 
                        displayName, hold.status(), simulationId);
                return;
            }
            logActivity(simulationId, participantId, "RETRY", "좌석 선점에 실패했습니다. 다른 좌석을 찾아볼게요.");
            sleepBriefly();
        }
    }

    private boolean waitUntilAdmitted(String baseUrl, UUID simulationId, UUID participantId) {
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            VirtualUserCommandResponse response = runWithTransientRetryOrNull(() -> commandClient.postQueue(baseUrl, simulationId, participantId));
            if (response == null) {
                log.debug("postQueue returned null for participantId={} at attempt={}", participantId, attempt);
                sleepBriefly();
                continue;
            }
            if ("ADMITTED".equals(response.status())) {
                logActivity(simulationId, participantId, "SUCCESS", "대기열을 통과했습니다! 좌석을 선택합니다.");
                log.info("ParticipantId={} admitted to event in simulationId={}", participantId, simulationId);
                return true;
            }
            if (isTerminalStatus(response.status())) {
                log.warn("ParticipantId={} queue terminal status={} in simulationId={}", participantId, response.status(), simulationId);
                return false;
            }
            logActivity(simulationId, participantId, "WAITING", "대기열에서 차례를 기다리는 중입니다...");
            log.debug("ParticipantId={} still waiting in queue, status={} in simulationId={}", participantId, response.status(), simulationId);
            sleepBriefly();
        }
        return false;
    }

    private void confirmPaymentUntilAccepted(String baseUrl, UUID simulationId, UUID participantId) {
        for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
            PaymentConfirmResponse response = runWithTransientRetryOrNull(() -> commandClient.confirmPayment(baseUrl, simulationId, participantId));
            if (response != null || Thread.currentThread().isInterrupted()) {
                logActivity(simulationId, participantId, "SUCCESS", "결제가 완료되었습니다! 예약을 마칩니다.");
                log.info("Payment confirmed successfully for participantId={} in simulationId={}", participantId, simulationId);
                return;
            }
            logActivity(simulationId, participantId, "WAITING", "결제 승인을 기다리고 있습니다...");
            log.debug("Payment pending/failed for participantId={}, retrying... in simulationId={}", participantId, simulationId);
            sleepBriefly();
        }
    }

    private void logActivity(UUID simulationId, UUID userId, String label, String message) {
        if (activityPublisher != null) {
            try {
                activityPublisher.publish(new UserActivityEvent(simulationId, userId, label, message));
            } catch (Exception e) {
                log.warn("Failed to publish direct user activity", e);
            }
            return;
        }

        try {
            restClient.post()
                    .uri(controlBaseUrl + "/internal/traffic-generator/simulations/{simulationId}/users/{userId}/activity", simulationId, userId)
                    .body(new UserActivityEvent(simulationId, userId, label, message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Logging failure should not break the simulation
        }
    }

    private boolean isTerminalStatus(String status) {
        return "FAILED".equals(status) || "EVENT_ENDED".equals(status);
    }

    private boolean requiresPaymentConfirmation(SeatHoldResponse hold) {
        if ("PAYMENT_PENDING".equals(hold.status()) || "PAYMENT_REQUESTED".equals(hold.status()) || "SEAT_HELD".equals(hold.status())) {
            return true;
        }
        return "ALREADY_HOLDING".equals(hold.status()) && hold.selectedSeatLabel() != null;
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
