package com.timedeal.seatreservation.payment;

import com.timedeal.seatreservation.identity.ServerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

@Component
public class PaymentSimulationWorker {

    private final ServerIdentity serverIdentity;
    private final IntSupplier paymentDelayMillis;
    private final IntConsumer sleeper;
    private final int failureRatePercent;

    @Autowired
    public PaymentSimulationWorker(
            ServerIdentity serverIdentity,
            @Value("${payment.simulation-delay-min-ms:300}") int paymentDelayMinMillis,
            @Value("${payment.simulation-delay-max-ms:900}") int paymentDelayMaxMillis,
            @Value("${payment.failure-rate-percent:5}") int failureRatePercent
    ) {
        this(
                serverIdentity,
                randomDelaySupplier(paymentDelayMinMillis, paymentDelayMaxMillis),
                PaymentSimulationWorker::sleep,
                failureRatePercent
        );
    }

    PaymentSimulationWorker(ServerIdentity serverIdentity) {
        this(serverIdentity, () -> 0, ignored -> {}, 5);
    }

    PaymentSimulationWorker(
            ServerIdentity serverIdentity,
            IntSupplier paymentDelayMillis,
            IntConsumer sleeper,
            int failureRatePercent
    ) {
        this.serverIdentity = serverIdentity;
        this.paymentDelayMillis = paymentDelayMillis;
        this.sleeper = sleeper;
        this.failureRatePercent = Math.max(0, Math.min(100, failureRatePercent));
    }

    public PaymentResultEvent processPayment(PaymentRequestedEvent event) {
        sleeper.accept(paymentDelayMillis.getAsInt());
        return simulate(event);
    }

    public PaymentResultEvent simulate(PaymentRequestedEvent event) {
        boolean success = event.reservationId() % 100 >= failureRatePercent;
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
