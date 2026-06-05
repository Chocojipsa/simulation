package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpVirtualUserHttpClientTest {
    @Test
    void aiParticipantUsesEventEndpoints() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 3, 0);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000040");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.calls).containsExactly(
                "join:http://nginx:8080:00000000-0000-0000-0000-000000000040:AI-1",
                "queue:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "hold:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "confirm:http://nginx:8080:00000000-0000-0000-0000-000000000041"
        );
    }

    @Test
    void retriesTransientCommandFailuresForTheSameParticipant() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.failFirstJoin = true;
        commandClient.failFirstHold = true;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 5, 0);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000042");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.calls).containsExactly(
                "join:http://nginx:8080:00000000-0000-0000-0000-000000000042:AI-1",
                "join:http://nginx:8080:00000000-0000-0000-0000-000000000042:AI-1",
                "queue:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "hold:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "hold:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "confirm:http://nginx:8080:00000000-0000-0000-0000-000000000041"
        );
    }

    @Test
    void defaultRetryBudgetKeepsAiAliveLongEnoughForPaymentFailureResale() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.waitingHoldAttemptsBeforeSuccess = 60;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 300, 0, () -> 0, ignored -> {
        });
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000043");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.holdAttempts).isEqualTo(60);
    }

    @Test
    void waitsBeforeTryingToSelectASeat() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        List<Integer> seatClickDelays = new ArrayList<>();
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 3, 0, () -> 250, seatClickDelays::add);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000044");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(seatClickDelays).containsExactly(250);
    }

    @Test
    void checksQueueUntilAdmittedBeforeTryingToSelectASeat() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.queueResponsesBeforeAdmitted = 2;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 5, 0, () -> 0, ignored -> {
        });
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000045");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.calls).containsExactly(
                "join:http://nginx:8080:00000000-0000-0000-0000-000000000045:AI-1",
                "queue:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "queue:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "hold:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "confirm:http://nginx:8080:00000000-0000-0000-0000-000000000041"
        );
    }

    @Test
    void keepsWaitingWhenQueueCommandExhaustsOneRetryBudget() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.queueFailuresBeforeSuccess = 6;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 5, 0, () -> 0, ignored -> {
        });
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000046");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.holdAttempts).isEqualTo(1);
    }

    @Test
    void keepsTryingSeatsWhenHoldCommandExhaustsOneRetryBudget() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.holdFailuresBeforeSuccess = 6;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 5, 0, () -> 0, ignored -> {
        });
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000047");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.holdAttempts).isEqualTo(7);
        assertThat(commandClient.calls).contains("confirm:http://nginx:8080:00000000-0000-0000-0000-000000000041");
    }

    @Test
    void keepsConfirmingPaymentWhenPaymentCommandExhaustsOneRetryBudget() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.confirmFailuresBeforeSuccess = 6;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 5, 0, () -> 0, ignored -> {
        });
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000048");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.confirmAttempts).isEqualTo(7);
    }

    @Test
    void confirmsPaymentWhenParticipantAlreadyHasHeldSeat() {
        RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
        commandClient.alreadyHoldingSeat = true;
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, "http://localhost:8080", 3, 0, () -> 0, ignored -> {
        });
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000049");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.calls).containsExactly(
                "join:http://nginx:8080:00000000-0000-0000-0000-000000000049:AI-1",
                "queue:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "hold:http://nginx:8080:00000000-0000-0000-0000-000000000041",
                "confirm:http://nginx:8080:00000000-0000-0000-0000-000000000041"
        );
    }

    private static final class RecordingEventCommandClient implements VirtualUserCommandClient {
        private final UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        private final List<String> calls = new ArrayList<>();
        private boolean failFirstJoin;
        private boolean failFirstHold;
        private boolean alreadyHoldingSeat;
        private int joinAttempts;
        private int queueAttempts;
        private int holdAttempts;
        private int confirmAttempts;
        private int queueResponsesBeforeAdmitted = 1;
        private int waitingHoldAttemptsBeforeSuccess = 1;
        private int queueFailuresBeforeSuccess;
        private int holdFailuresBeforeSuccess;
        private int confirmFailuresBeforeSuccess;

        @Override
        public JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName) {
            joinAttempts++;
            calls.add("join:" + baseUrl + ":" + eventId + ":" + displayName);
            if (failFirstJoin && joinAttempts == 1) {
                throw new IllegalStateException("temporary join failure");
            }
            return new JoinEventResponse(eventId, participantId, displayName, "WAITING_ROOM", "api-test");
        }

        @Override
        public VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId) {
            queueAttempts++;
            calls.add("queue:" + baseUrl + ":" + participantId);
            if (queueAttempts <= queueFailuresBeforeSuccess) {
                throw new IllegalStateException("temporary queue failure");
            }
            String status = queueAttempts >= queueResponsesBeforeAdmitted ? "ADMITTED" : "QUEUED";
            return new VirtualUserCommandResponse(eventId, participantId, status, "api-test", "대기열 상태 확인", null);
        }

        @Override
        public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
            holdAttempts++;
            calls.add("hold:" + baseUrl + ":" + participantId);
            if (failFirstHold && holdAttempts == 1) {
                throw new IllegalStateException("temporary hold failure");
            }
            if (holdAttempts <= holdFailuresBeforeSuccess) {
                throw new IllegalStateException("temporary hold failure");
            }
            if (holdAttempts < waitingHoldAttemptsBeforeSuccess) {
                return new SeatHoldResponse(eventId, participantId, 0L, "WAITING", "아직 대기 중입니다.", null, "api-test");
            }
            if (alreadyHoldingSeat) {
                return new SeatHoldResponse(eventId, participantId, 1L, "ALREADY_HOLDING", "Already holding a seat.", "A-1", "api-test");
            }
            return new SeatHoldResponse(eventId, participantId, 1L, "PAYMENT_PENDING", "A-1 좌석을 선점했습니다.", "A-1", "api-test");
        }

        @Override
        public PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId) {
            confirmAttempts++;
            calls.add("confirm:" + baseUrl + ":" + participantId);
            if (confirmAttempts <= confirmFailuresBeforeSuccess) {
                throw new IllegalStateException("temporary payment failure");
            }
            return new PaymentConfirmResponse(eventId, participantId, "PAYMENT_REQUESTED", "결제 확인 요청을 보냈습니다.", "api-test");
        }
    }
}
