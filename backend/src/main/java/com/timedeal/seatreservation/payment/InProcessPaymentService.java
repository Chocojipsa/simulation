package com.timedeal.seatreservation.payment;

import com.timedeal.seatreservation.simulation.PaymentResultListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Profile("!demo")
public class InProcessPaymentService {
    private final PaymentSimulationWorker worker;
    private final ObjectProvider<PaymentResultListener> resultListener;

    public InProcessPaymentService(
            PaymentSimulationWorker worker,
            ObjectProvider<PaymentResultListener> resultListener
    ) {
        this.worker = worker;
        this.resultListener = resultListener;
    }

    @Async
    public void processPaymentAsync(PaymentRequestedEvent event) {
        PaymentResultEvent result = worker.processPayment(event);
        PaymentResultListener listener = resultListener.getIfAvailable();
        if (listener != null) {
            listener.handle(result);
        }
    }
}
