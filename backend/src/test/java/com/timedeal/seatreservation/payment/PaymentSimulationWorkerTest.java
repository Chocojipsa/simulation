package com.timedeal.seatreservation.payment;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentSimulationWorkerTest {
    @Test
    void deterministicPaymentRuleSucceedsWhenReservationIdIsNotMultipleOfFive() {
        PaymentSimulationWorker worker = new PaymentSimulationWorker(null);

        PaymentResultEvent result = worker.simulate(new PaymentRequestedEvent(201L, 10L, "payment-201"));

        assertThat(result.reservationId()).isEqualTo(201L);
        assertThat(result.seatId()).isEqualTo(10L);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("결제 성공");
    }

    @Test
    void deterministicPaymentRuleFailsWhenReservationIdIsMultipleOfFive() {
        PaymentSimulationWorker worker = new PaymentSimulationWorker(null);

        PaymentResultEvent result = worker.simulate(new PaymentRequestedEvent(205L, 10L, "payment-205"));

        assertThat(result.reservationId()).isEqualTo(205L);
        assertThat(result.seatId()).isEqualTo(10L);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("결제 실패");
    }

    @Test
    void handlePublishesPaymentResultEvent() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PaymentResultEvent> kafkaTemplate = mock(KafkaTemplate.class);
        PaymentSimulationWorker worker = new PaymentSimulationWorker(kafkaTemplate);

        worker.handle(new PaymentRequestedEvent(201L, 10L, "payment-201"));

        verify(kafkaTemplate).send(
                "payment-results.events",
                "201",
                new PaymentResultEvent(201L, 10L, true, "결제 성공")
        );
    }
}
