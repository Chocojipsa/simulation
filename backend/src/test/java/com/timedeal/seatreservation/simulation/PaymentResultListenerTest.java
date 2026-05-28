package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentResultListenerTest {
    @Test
    void appliesPaymentResultToStateStore() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        PaymentResultListener listener = new PaymentResultListener(stateStore);
        PaymentResultEvent event = new PaymentResultEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000060"),
                UUID.fromString("00000000-0000-0000-0000-000000000160"),
                101L,
                7L,
                true,
                "결제 성공",
                "worker"
        );

        listener.handle(event);

        verify(stateStore).applyPaymentResult(event);
    }
}
