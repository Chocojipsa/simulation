package com.timedeal.seatreservation.payment;

import com.timedeal.seatreservation.identity.ServerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

@Component
@Profile("worker")
public class PaymentSimulationWorker {
    static final String PAYMENT_EVENTS_TOPIC = "payment.events";
    static final String PAYMENT_RESULTS_TOPIC = "payment-results.events";

    private final KafkaTemplate<String, PaymentResultEvent> kafkaTemplate;
    private final ServerIdentity serverIdentity;
    private final IntSupplier paymentDelayMillis;
    private final IntConsumer sleeper;

    @Autowired
    public PaymentSimulationWorker(
            KafkaTemplate<String, PaymentResultEvent> kafkaTemplate,
            ServerIdentity serverIdentity,
            @Value("${payment.simulation-delay-min-ms:300}") int paymentDelayMinMillis,
            @Value("${payment.simulation-delay-max-ms:900}") int paymentDelayMaxMillis
    ) {
        this(
                kafkaTemplate,
                serverIdentity,
                randomDelaySupplier(paymentDelayMinMillis, paymentDelayMaxMillis),
                PaymentSimulationWorker::sleep
        );
    }

    PaymentSimulationWorker(KafkaTemplate<String, PaymentResultEvent> kafkaTemplate, ServerIdentity serverIdentity) {
        this(kafkaTemplate, serverIdentity, () -> 0, ignored -> {
        });
    }

    PaymentSimulationWorker(
            KafkaTemplate<String, PaymentResultEvent> kafkaTemplate,
            ServerIdentity serverIdentity,
            IntSupplier paymentDelayMillis,
            IntConsumer sleeper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.serverIdentity = serverIdentity;
        this.paymentDelayMillis = paymentDelayMillis;
        this.sleeper = sleeper;
    }

    @KafkaListener(topics = PAYMENT_EVENTS_TOPIC, groupId = "payment-simulation-worker")
    public void handle(PaymentRequestedEvent event) {
        sleeper.accept(paymentDelayMillis.getAsInt());
        PaymentResultEvent result = simulate(event);
        if (kafkaTemplate != null) {
            kafkaTemplate.send(PAYMENT_RESULTS_TOPIC, String.valueOf(result.reservationId()), result);
        }
    }

    public PaymentResultEvent simulate(PaymentRequestedEvent event) {
        boolean success = event.reservationId() % 5 != 0;
        String message = success ? "결제 성공" : "결제 실패";
        return new PaymentResultEvent(
                event.simulationId(),
                event.virtualUserId(),
                event.reservationId(),
                event.seatId(),
                success,
                message,
                serverIdentity.id()
        );
    }

    private static IntSupplier randomDelaySupplier(int minMillis, int maxMillis) {
        int normalizedMin = Math.max(0, minMillis);
        int normalizedMax = Math.max(normalizedMin, maxMillis);
        Random random = new Random();
        return () -> {
            if (normalizedMin == normalizedMax) {
                return normalizedMin;
            }
            return random.nextInt(normalizedMax - normalizedMin + 1) + normalizedMin;
        };
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
