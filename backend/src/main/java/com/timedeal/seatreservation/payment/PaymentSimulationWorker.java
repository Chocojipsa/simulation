package com.timedeal.seatreservation.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class PaymentSimulationWorker {
    static final String PAYMENT_EVENTS_TOPIC = "payment.events";
    static final String PAYMENT_RESULTS_TOPIC = "payment-results.events";

    private final KafkaTemplate<String, PaymentResultEvent> kafkaTemplate;

    public PaymentSimulationWorker(KafkaTemplate<String, PaymentResultEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = PAYMENT_EVENTS_TOPIC, groupId = "payment-simulation-worker")
    public void handle(PaymentRequestedEvent event) {
        PaymentResultEvent result = simulate(event);
        if (kafkaTemplate != null) {
            kafkaTemplate.send(PAYMENT_RESULTS_TOPIC, String.valueOf(result.reservationId()), result);
        }
    }

    public PaymentResultEvent simulate(PaymentRequestedEvent event) {
        boolean success = event.reservationId() % 5 != 0;
        String message = success ? "결제 성공" : "결제 실패";
        return new PaymentResultEvent(event.reservationId(), event.seatId(), success, message);
    }
}
