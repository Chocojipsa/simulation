package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.event.JoinEventRequest;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("!demo")
public class RestVirtualUserCommandClient implements VirtualUserCommandClient {
    private final RestClient restClient;

    public RestVirtualUserCommandClient() {
        this(RestClient.create());
    }

    RestVirtualUserCommandClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName) {
        return restClient.post()
                .uri(baseUrl + "/api/events/{eventId}/participants", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JoinEventRequest(displayName))
                .retrieve()
                .body(JoinEventResponse.class);
    }

    @Override
    public VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId) {
        return restClient.post()
                .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/queue", eventId, participantId)
                .retrieve()
                .body(VirtualUserCommandResponse.class);
    }

    @Override
    public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
        LiveEventSnapshot snapshot = restClient.get()
                .uri(baseUrl + "/api/events/{eventId}/snapshot?participantId={participantId}", eventId, participantId)
                .retrieve()
                .body(LiveEventSnapshot.class);
        List<SeatView> availableSeats = snapshot.seats().stream()
                .filter(candidate -> candidate.status() == SeatStatus.AVAILABLE)
                .toList();
        if (availableSeats.isEmpty()) {
            restClient.post()
                    .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/fail", eventId, participantId)
                    .retrieve()
                    .toBodilessEntity();
            return new SeatHoldResponse(eventId, participantId, 0L, "FAILED", "선택 가능한 좌석이 없습니다.", null, "generator");
        }
        SeatView seat = availableSeats.get(ThreadLocalRandom.current().nextInt(availableSeats.size()));
        return restClient.post()
                .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/seats/{seatId}/hold", eventId, participantId, seat.id())
                .retrieve()
                .body(SeatHoldResponse.class);
    }

    @Override
    public PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId) {
        return restClient.post()
                .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/payment-confirm", eventId, participantId)
                .retrieve()
                .body(PaymentConfirmResponse.class);
    }
}
