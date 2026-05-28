package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.TimelineEntry;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEventContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesLiveEventSnapshotForFrontend() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        LiveEventSnapshot snapshot = new LiveEventSnapshot(
                eventId,
                "부산 콘서트 티켓팅",
                "COUNTDOWN",
                1,
                Instant.parse("2026-05-28T12:01:00Z"),
                Instant.parse("2026-05-28T12:06:00Z"),
                List.of(new SeatView(1, "A-1", SeatStatus.AVAILABLE)),
                List.of(new VirtualUserView(
                        participantId,
                        "나",
                        ParticipantType.HUMAN,
                        VirtualUserStatus.WAITING_ROOM,
                        null,
                        List.of(new TimelineEntry("입장", "이벤트에 입장했습니다.")),
                        0,
                        0,
                        0,
                        null
                )),
                new SimulationMetrics(0, 0, 0, 0, 0, 0),
                List.of(new ServerStatsView("api-a", 1, 0, 0)),
                false,
                participantId
        );

        String json = objectMapper.writeValueAsString(snapshot);

        assertThat(json).contains("\"eventId\":\"" + eventId + "\"");
        assertThat(json).contains("\"title\":\"부산 콘서트 티켓팅\"");
        assertThat(json).contains("\"status\":\"COUNTDOWN\"");
        assertThat(json).contains("\"generation\":1");
        assertThat(json).contains("\"opensAt\":\"2026-05-28T12:01:00Z\"");
        assertThat(json).contains("\"endsAt\":\"2026-05-28T12:06:00Z\"");
        assertThat(json).contains("\"type\":\"HUMAN\"");
        assertThat(json).contains("\"myParticipantId\":\"" + participantId + "\"");
    }
}
