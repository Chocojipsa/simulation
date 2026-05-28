package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo & !worker & !generator")
public class PaymentResultListener {
    private final SimulationStateGateway stateStore;
    private final SeatReservationService seatReservationService;

    public PaymentResultListener(SimulationStateGateway stateStore, SeatReservationService seatReservationService) {
        this.stateStore = stateStore;
        this.seatReservationService = seatReservationService;
    }

    @KafkaListener(topics = "payment-results.events", groupId = "payment-result-applier")
    public void handle(PaymentResultEvent event) {
        seatReservationService.applyPaymentResult(event);
        stateStore.applyPaymentResult(event);
    }
}
