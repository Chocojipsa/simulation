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
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, 3, 0);
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
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, 5, 0);
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
        HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000043");

        client.runUser("http://nginx:8080", eventId, 1);

        assertThat(commandClient.holdAttempts).isEqualTo(60);
    }

    private static final class RecordingEventCommandClient implements VirtualUserCommandClient {
        private final UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        private final List<String> calls = new ArrayList<>();
        private boolean failFirstJoin;
        private boolean failFirstHold;
        private int joinAttempts;
        private int holdAttempts;
        private int waitingHoldAttemptsBeforeSuccess = 1;

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
            calls.add("queue:" + baseUrl + ":" + participantId);
            return new VirtualUserCommandResponse(eventId, participantId, "QUEUED", "api-test", "대기열에 진입했습니다.", null);
        }

        @Override
        public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
            holdAttempts++;
            calls.add("hold:" + baseUrl + ":" + participantId);
            if (failFirstHold && holdAttempts == 1) {
                throw new IllegalStateException("temporary hold failure");
            }
            if (holdAttempts < waitingHoldAttemptsBeforeSuccess) {
                return new SeatHoldResponse(eventId, participantId, 0L, "WAITING", "아직 대기 중입니다.", null, "api-test");
            }
            return new SeatHoldResponse(eventId, participantId, 1L, "PAYMENT_PENDING", "A-1 좌석을 선점했습니다.", "A-1", "api-test");
        }

        @Override
        public PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId) {
            calls.add("confirm:" + baseUrl + ":" + participantId);
            return new PaymentConfirmResponse(eventId, participantId, "PAYMENT_REQUESTED", "결제 확인 요청을 보냈습니다.", "api-test");
        }
    }
}
