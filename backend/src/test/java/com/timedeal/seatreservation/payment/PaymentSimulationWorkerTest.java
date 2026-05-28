package com.timedeal.seatreservation.payment;

import com.timedeal.seatreservation.identity.ServerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentSimulationWorkerTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000106");

    @Test
    void deterministicPaymentRuleSucceedsWhenReservationIdIsNotMultipleOfFive() {
        PaymentSimulationWorker worker = new PaymentSimulationWorker(null, new ServerIdentity("worker-test"));

        PaymentResultEvent result = worker.simulate(new PaymentRequestedEvent(
                SIMULATION_ID,
                USER_ID,
                201L,
                10L,
                "payment-201",
                "api-a"
        ));

        assertThat(result.simulationId()).isEqualTo(SIMULATION_ID);
        assertThat(result.virtualUserId()).isEqualTo(USER_ID);
        assertThat(result.reservationId()).isEqualTo(201L);
        assertThat(result.seatId()).isEqualTo(10L);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("결제 성공");
        assertThat(result.handledBy()).isEqualTo("worker-test");
    }

    @Test
    void deterministicPaymentRuleFailsWhenReservationIdIsMultipleOfFive() {
        PaymentSimulationWorker worker = new PaymentSimulationWorker(null, new ServerIdentity("worker-test"));

        PaymentResultEvent result = worker.simulate(new PaymentRequestedEvent(
                SIMULATION_ID,
                USER_ID,
                205L,
                10L,
                "payment-205",
                "api-a"
        ));

        assertThat(result.simulationId()).isEqualTo(SIMULATION_ID);
        assertThat(result.virtualUserId()).isEqualTo(USER_ID);
        assertThat(result.reservationId()).isEqualTo(205L);
        assertThat(result.seatId()).isEqualTo(10L);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("결제 실패");
        assertThat(result.handledBy()).isEqualTo("worker-test");
    }

    @Test
    void handlePublishesPaymentResultEvent() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PaymentResultEvent> kafkaTemplate = mock(KafkaTemplate.class);
        PaymentSimulationWorker worker = new PaymentSimulationWorker(kafkaTemplate, new ServerIdentity("worker-test"));

        worker.handle(new PaymentRequestedEvent(SIMULATION_ID, USER_ID, 201L, 10L, "payment-201", "api-a"));

        verify(kafkaTemplate).send(
                "payment-results.events",
                "201",
                new PaymentResultEvent(SIMULATION_ID, USER_ID, 201L, 10L, true, "결제 성공", "worker-test")
        );
    }

    @Test
    void handleAppliesConfiguredDelayBeforePublishingResult() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PaymentResultEvent> kafkaTemplate = mock(KafkaTemplate.class);
        List<Integer> delays = new ArrayList<>();
        PaymentSimulationWorker worker = new PaymentSimulationWorker(
                kafkaTemplate,
                new ServerIdentity("worker-test"),
                () -> 700,
                delays::add
        );

        worker.handle(new PaymentRequestedEvent(SIMULATION_ID, USER_ID, 201L, 10L, "payment-201", "api-a"));

        assertThat(delays).containsExactly(700);
        verify(kafkaTemplate).send(
                "payment-results.events",
                "201",
                new PaymentResultEvent(SIMULATION_ID, USER_ID, 201L, 10L, true, "결제 성공", "worker-test")
        );
    }
}
