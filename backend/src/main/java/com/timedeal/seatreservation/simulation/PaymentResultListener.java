package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class PaymentResultListener {
    private final SimulationStateGateway stateStore;

    public PaymentResultListener(SimulationStateGateway stateStore) {
        this.stateStore = stateStore;
    }

    @KafkaListener(topics = "payment-results.events", groupId = "payment-result-applier")
    public void handle(PaymentResultEvent event) {
        stateStore.applyPaymentResult(event);
    }
}
