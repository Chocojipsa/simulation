package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.event.JoinEventRequest;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.payment.PaymentRequestedEvent;
import com.timedeal.seatreservation.seat.SeatReservationOutcome;
import com.timedeal.seatreservation.seat.SeatReservationResult;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveEventServiceTest {
    @Test
    void createsActiveEventOnceAndLetsHumanJoinWaitingRoom() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                new ServerIdentity("api-test"),
                "부산 콘서트 티켓팅",
                120
        );

        LiveEventResponse active = service.activeEvent();
        JoinEventResponse joined = service.join(active.eventId(), new JoinEventRequest("권"));
        LiveEventSnapshot snapshot = service.snapshot(active.eventId(), joined.participantId());

        assertThat(joined.displayName()).isEqualTo("권");
        assertThat(joined.status()).isEqualTo("WAITING_ROOM");
        assertThat(snapshot.participants())
                .anySatisfy(participant -> {
                    assertThat(participant.id()).isEqualTo(joined.participantId());
                    assertThat(participant.type()).isEqualTo(ParticipantType.HUMAN);
                    assertThat(participant.status().name()).isEqualTo("WAITING_ROOM");
                });
    }

    @Test
    void humanCanQueueHoldSeatAndConfirmPayment() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        KafkaTemplate<String, PaymentRequestedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        SimulationService simulationService = new SimulationService(
                stateStore,
                new ServerIdentity("api-test"),
                (simulationId, request) -> {
                },
                null,
                null,
                seatReservationService,
                kafkaTemplate,
                new Random(1)
        );
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                new ServerIdentity("api-test"),
                "부산 콘서트 티켓팅",
                120
        );

        LiveEventResponse active = service.activeEvent();
        JoinEventResponse joined = service.join(active.eventId(), new JoinEventRequest("권"));
        when(seatReservationService.holdSeat(eq(active.eventId()), eq(joined.participantId()), eq(1L), any()))
                .thenReturn(new SeatReservationResult(
                        SeatReservationOutcome.HELD,
                        101L,
                        1L,
                        joined.participantId(),
                        "hold"
                ));

        VirtualUserCommandResponse queue = service.enterQueue(active.eventId(), joined.participantId());
        var hold = service.holdSeat(active.eventId(), joined.participantId(), 1L);
        var confirm = service.confirmPayment(active.eventId(), joined.participantId());

        assertThat(queue.status()).isEqualTo("QUEUED");
        assertThat(hold.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(hold.selectedSeatLabel()).isEqualTo("A-1");
        assertThat(confirm.status()).isEqualTo("PAYMENT_REQUESTED");
        verify(kafkaTemplate).send(eq("payment.events"), eq("101"), any(PaymentRequestedEvent.class));
    }
}
