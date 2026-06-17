package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Primary
public class DirectVirtualUserCommandClient implements VirtualUserCommandClient {
    private final ObjectProvider<LiveEventService> liveEventServiceProvider;

    public DirectVirtualUserCommandClient(ObjectProvider<LiveEventService> liveEventServiceProvider) {
        this.liveEventServiceProvider = liveEventServiceProvider;
    }

    private LiveEventService getService() {
        return liveEventServiceProvider.getObject();
    }

    @Override
    public JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName) {
        return getService().join(eventId, new com.timedeal.seatreservation.event.JoinEventRequest(displayName));
    }

    @Override
    public VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId) {
        return getService().enterQueue(eventId, participantId);
    }

    @Override
    public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
        LiveEventSnapshot snapshot = getService().snapshot(eventId, participantId);
        List<SeatView> availableSeats = snapshot.seats().stream()
                .filter(candidate -> candidate.status() == SeatStatus.AVAILABLE)
                .toList();
        
        if (availableSeats.isEmpty()) {
            getService().failParticipant(eventId, participantId);
            return new SeatHoldResponse(eventId, participantId, 0L, "FAILED", "선택 가능한 좌석이 없습니다.", null, "generator");
        }
        
        SeatView seat = availableSeats.get(ThreadLocalRandom.current().nextInt(availableSeats.size()));
        return getService().holdSeat(eventId, participantId, seat.id());
    }

    @Override
    public PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId) {
        return getService().confirmPayment(eventId, participantId);
    }
}
