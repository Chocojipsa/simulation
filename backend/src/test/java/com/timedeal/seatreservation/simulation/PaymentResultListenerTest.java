package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentResultListenerTest {
    @Test
    void runsOnlyOnApiProcesses() {
        Profile profile = PaymentResultListener.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("!demo & !worker & !generator");
    }

    @Test
    void appliesPaymentResultToStateStore() {
        SimulationStateGateway stateStore = mock(SimulationStateGateway.class);
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        com.timedeal.seatreservation.events.SimulationEventHub eventHub = mock(com.timedeal.seatreservation.events.SimulationEventHub.class);
        PaymentResultListener listener = new PaymentResultListener(stateStore, seatReservationService, eventHub);
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

        verify(seatReservationService).applyPaymentResult(event);
        verify(stateStore).applyPaymentResult(event);
    }
}
