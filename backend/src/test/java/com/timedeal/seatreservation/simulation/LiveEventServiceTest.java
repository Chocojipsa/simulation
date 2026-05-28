package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.event.JoinEventRequest;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.identity.ServerIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
