package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpVirtualUserHttpClientTest {
    @Test
    void queuesUserThenRetriesSeatAttemptsUntilPaymentIsRequested() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000040");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        List<String> calls = new ArrayList<>();
        VirtualUserCommandClient commandClient = new VirtualUserCommandClient() {
            private int attempts;

            @Override
            public VirtualUserCommandResponse postQueue(String baseUrl, UUID simulationId, UUID virtualUserId) {
                calls.add("queue:" + baseUrl + ":" + virtualUserId);
                return new VirtualUserCommandResponse(simulationId, virtualUserId, "QUEUED", "api-a", "대기열에 진입했습니다.", null);
            }

            @Override
            public VirtualUserCommandResponse postSeatAttempt(String baseUrl, UUID simulationId, UUID virtualUserId) {
                attempts++;
                calls.add("seat-attempt-" + attempts + ":" + baseUrl + ":" + virtualUserId);
                String status = attempts == 1 ? "RETRY" : "PAYMENT_REQUESTED";
                return new VirtualUserCommandResponse(simulationId, virtualUserId, status, "api-b", "message", "A-1");
            }
        };
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, 5, 0);

        client.runUser("http://nginx:8080", simulationId, 1);

        assertThat(calls).containsExactly(
                "queue:http://nginx:8080:" + userId,
                "seat-attempt-1:http://nginx:8080:" + userId,
                "seat-attempt-2:http://nginx:8080:" + userId
        );
    }

    @Test
    void retriesTransientCommandFailuresForTheSameVirtualUser() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        List<String> calls = new ArrayList<>();
        VirtualUserCommandClient commandClient = new VirtualUserCommandClient() {
            private int queueAttempts;
            private int seatAttempts;

            @Override
            public VirtualUserCommandResponse postQueue(String baseUrl, UUID simulationId, UUID virtualUserId) {
                queueAttempts++;
                calls.add("queue-" + queueAttempts + ":" + virtualUserId);
                if (queueAttempts == 1) {
                    throw new IllegalStateException("temporary queue failure");
                }
                return new VirtualUserCommandResponse(simulationId, virtualUserId, "QUEUED", "api-a", "queued", null);
            }

            @Override
            public VirtualUserCommandResponse postSeatAttempt(String baseUrl, UUID simulationId, UUID virtualUserId) {
                seatAttempts++;
                calls.add("seat-" + seatAttempts + ":" + virtualUserId);
                if (seatAttempts == 1) {
                    throw new IllegalStateException("temporary seat failure");
                }
                return new VirtualUserCommandResponse(
                        simulationId,
                        virtualUserId,
                        "PAYMENT_REQUESTED",
                        "api-b",
                        "payment requested",
                        "A-1"
                );
            }
        };
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, 5, 0);

        client.runUser("http://nginx:8080", simulationId, 1);

        assertThat(calls).containsExactly(
                "queue-1:" + userId,
                "queue-2:" + userId,
                "seat-1:" + userId,
                "seat-2:" + userId
        );
    }

    @Test
    void defaultRetryBudgetKeepsUserAliveLongEnoughForPaymentFailureResale() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        VirtualUserCommandClient commandClient = new VirtualUserCommandClient() {
            private int seatAttempts;

            @Override
            public VirtualUserCommandResponse postQueue(String baseUrl, UUID simulationId, UUID virtualUserId) {
                return new VirtualUserCommandResponse(simulationId, virtualUserId, "QUEUED", "api-a", "queued", null);
            }

            @Override
            public VirtualUserCommandResponse postSeatAttempt(String baseUrl, UUID simulationId, UUID virtualUserId) {
                seatAttempts++;
                if (seatAttempts < 60) {
                    return new VirtualUserCommandResponse(simulationId, virtualUserId, "WAITING", "api-b", "waiting", null);
                }
                return new VirtualUserCommandResponse(
                        simulationId,
                        virtualUserId,
                        "PAYMENT_REQUESTED",
                        "api-b",
                        "payment requested",
                        "A-1"
                );
            }
        };
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient);

        client.runUser("http://nginx:8080", simulationId, 1);

        assertThat(commandClient).extracting("seatAttempts").isEqualTo(60);
    }
}
