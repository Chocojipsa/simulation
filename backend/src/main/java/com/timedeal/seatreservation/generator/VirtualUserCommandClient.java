package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;

import java.util.UUID;

public interface VirtualUserCommandClient {
    JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName);

    VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId);

    SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId);

    PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId);
}
