package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.event.JoinEventRequest;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.InMemoryLiveEventStateStore;
import com.timedeal.seatreservation.event.LiveEventAiStarter;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.payment.PaymentRequestedEvent;
import com.timedeal.seatreservation.queue.WaitingQueueService;
import com.timedeal.seatreservation.seat.SeatReservationOutcome;
import com.timedeal.seatreservation.seat.SeatReservationResult;
import com.timedeal.seatreservation.seat.SeatReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveEventServiceTest {
    @Test
    void visitorStartsCountdownAndResetCreatesReadyNextGeneration() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                eventStateStore,
                new ServerIdentity("api-test"),
                null,
                eventId,
                "Busan Ticketing",
                120,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)
        );

        LiveEventResponse active = service.activeEvent();
        LiveEventResponse countdown = service.startEvent(eventId);
        LiveEventSnapshot snapshot = service.snapshot(eventId, null);
        LiveEventResponse reset = service.resetEvent(eventId);

        assertThat(active.status()).isEqualTo("READY");
        assertThat(countdown.status()).isEqualTo("COUNTDOWN");
        assertThat(countdown.opensAt()).isEqualTo(Instant.parse("2026-05-28T12:01:00Z"));
        assertThat(countdown.endsAt()).isEqualTo(Instant.parse("2026-05-28T12:06:00Z"));
        assertThat(snapshot.generation()).isEqualTo(1);
        assertThat(reset.status()).isEqualTo("READY");
        assertThat(reset.generation()).isEqualTo(2);
    }

    @Test
    void rejectsSeatHoldBeforeOpenAndAfterEndedWithDomainStatus() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
        LiveEventService readyService = new LiveEventService(
                simulationService,
                stateStore,
                eventStateStore,
                new ServerIdentity("api-test"),
                null,
                eventId,
                "Busan Ticketing",
                120,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)
        );

        readyService.activeEvent();
        JoinEventResponse joined = readyService.join(eventId, new JoinEventRequest("Kwon"));
        SeatHoldResponse readyHold = readyService.holdSeat(eventId, joined.participantId(), 1L);

        assertThat(readyHold.status()).isEqualTo("NOT_OPEN");

        eventStateStore.startCountdown(eventId, Instant.parse("2026-05-28T12:00:00Z"), Duration.ofSeconds(60), Duration.ofMinutes(5));
        LiveEventService endedService = new LiveEventService(
                simulationService,
                stateStore,
                eventStateStore,
                new ServerIdentity("api-test"),
                null,
                eventId,
                "Busan Ticketing",
                120,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-28T12:07:00Z"), ZoneOffset.UTC)
        );

        SeatHoldResponse endedHold = endedService.holdSeat(eventId, joined.participantId(), 1L);

        assertThat(endedHold.status()).isEqualTo("EVENT_ENDED");
    }

    @Test
    void rejectsQueueEntryDuringCountdown() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                eventStateStore,
                new ServerIdentity("api-test"),
                null,
                eventId,
                "Busan Ticketing",
                120,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)
        );

        service.activeEvent();
        service.startEvent(eventId);
        JoinEventResponse joined = service.join(eventId, new JoinEventRequest("Kwon"));
        VirtualUserCommandResponse queued = service.enterQueue(eventId, joined.participantId());

        assertThat(queued.status()).isEqualTo("NOT_OPEN");
    }

    @Test
    void startsAiOnceWhenEventIsOpen() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
        LiveEventAiStarter aiStarter = mock(LiveEventAiStarter.class);
        Instant start = Instant.parse("2026-05-28T12:00:00Z");
        eventStateStore.startCountdown(eventId, start, Duration.ofSeconds(60), Duration.ofMinutes(5));
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                eventStateStore,
                new ServerIdentity("api-test"),
                null,
                eventId,
                "Busan Ticketing",
                120,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Clock.fixed(start.plusSeconds(61), ZoneOffset.UTC),
                aiStarter
        );

        service.snapshot(eventId, null);
        service.snapshot(eventId, null);

        verify(aiStarter, times(1)).start(eventId);
    }

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
    void activeEventUsesConfiguredSharedIdAcrossServiceInstances() {
        UUID sharedEventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        LiveEventService apiA = new LiveEventService(
                simulationService,
                stateStore,
                new ServerIdentity("api-a"),
                (SimulationInventoryService) null,
                sharedEventId,
                "부산 콘서트 티켓팅",
                120
        );
        LiveEventService apiB = new LiveEventService(
                simulationService,
                stateStore,
                new ServerIdentity("api-b"),
                (SimulationInventoryService) null,
                sharedEventId,
                "부산 콘서트 티켓팅",
                120
        );

        LiveEventResponse first = apiA.activeEvent();
        LiveEventResponse second = apiB.activeEvent();

        assertThat(first.eventId()).isEqualTo(sharedEventId);
        assertThat(second.eventId()).isEqualTo(sharedEventId);
        assertThat(apiB.snapshot(sharedEventId, null).seats()).hasSize(120);
    }

    @Test
    void persistsJoinedHumanParticipantForPostgresSeatHolds() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        SimulationInventoryService inventoryService = mock(SimulationInventoryService.class);
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                new ServerIdentity("api-test"),
                inventoryService,
                "부산 콘서트 티켓팅",
                120
        );

        LiveEventResponse active = service.activeEvent();
        JoinEventResponse joined = service.join(active.eventId(), new JoinEventRequest("권"));

        verify(inventoryService).registerParticipant(
                any(SimulationSnapshot.class),
                argThat(participant ->
                        participant.id().equals(joined.participantId())
                                && participant.displayName().equals("권")
                                && participant.type() == ParticipantType.HUMAN
                                && participant.status().name().equals("WAITING_ROOM")
                )
        );
    }

    @Test
    void humanCanQueueHoldSeatAndConfirmPayment() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SeatReservationService seatReservationService = mock(SeatReservationService.class);
        KafkaTemplate<String, PaymentRequestedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        SimulationService simulationService = new SimulationService(
                stateStore,
                new SimulationEventHub(null, null),
                null,
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
        service.startEvent(active.eventId());
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
        VirtualUserCommandResponse admitted = service.enterQueue(active.eventId(), joined.participantId());
        var hold = service.holdSeat(active.eventId(), joined.participantId(), 1L);
        var confirm = service.confirmPayment(active.eventId(), joined.participantId());

        assertThat(queue.status()).isEqualTo("QUEUED");
        assertThat(admitted.status()).isEqualTo("ADMITTED");
        assertThat(hold.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(hold.selectedSeatLabel()).isEqualTo("A-1");
        assertThat(confirm.status()).isEqualTo("PAYMENT_REQUESTED");
        verify(kafkaTemplate).send(eq("payment.events"), eq("101"), any(PaymentRequestedEvent.class));
    }

    @Test
    void snapshotOrdersQueuedParticipantsByRedisQueueOrder() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        WaitingQueueService waitingQueueService = mock(WaitingQueueService.class);
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                new InMemoryLiveEventStateStore(),
                new ServerIdentity("api-test"),
                null,
                eventId,
                "Busan Ticketing",
                120,
                Duration.ZERO,
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC),
                null,
                waitingQueueService
        );

        service.activeEvent();
        JoinEventResponse firstJoined = service.join(eventId, new JoinEventRequest("첫 화면 사용자"));
        JoinEventResponse secondJoined = service.join(eventId, new JoinEventRequest("실제 앞사람"));
        stateStore.registerQueueEntry(eventId, firstJoined.participantId(), "api-test");
        stateStore.registerQueueEntry(eventId, secondJoined.participantId(), "api-test");
        when(waitingQueueService.queuedUserIds(eventId.toString()))
                .thenReturn(List.of(secondJoined.participantId().toString(), firstJoined.participantId().toString()));

        LiveEventSnapshot snapshot = service.snapshot(eventId, firstJoined.participantId());

        assertThat(snapshot.participants().stream()
                .filter(participant -> participant.status().name().equals("QUEUED"))
                .map(VirtualUserView::id)
                .toList())
                .containsExactly(secondJoined.participantId(), firstJoined.participantId());
        assertThat(snapshot.myQueuePosition()).isEqualTo(2);
    }
}
