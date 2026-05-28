package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SimulationInventoryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SimulationInventoryService service = new SimulationInventoryService(jdbc);

    @Test
    void initializesConcertSeatsSessionVirtualUsersAndSimulationSeats() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000110");
        SimulationSnapshot snapshot = new SimulationSnapshot(
                simulationId,
                List.of(new SeatView(1L, "A-1", SeatStatus.AVAILABLE)),
                List.of(new VirtualUserView(
                        userId,
                        "사용자 1",
                        ParticipantType.AI,
                        VirtualUserStatus.QUEUED,
                        null,
                        List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                        0,
                        0,
                        0,
                        null
                )),
                new SimulationMetrics(1, 0, 0, 0, 0, 0),
                List.of(),
                false
        );

        service.initialize(snapshot, 1);

        verify(jdbc).update("insert into concerts(id, title) values (1, ?) on conflict (id) do nothing", "분산 좌석 예매 콘서트");
        verify(jdbc).update("insert into seats(id, concert_id, seat_label, status) values (?, 1, ?, 'AVAILABLE') on conflict (id) do nothing", 1L, "A-1");
        verify(jdbc).update("insert into simulation_sessions(id, concert_id, requested_users, status) values (?, 1, ?, 'CREATED')", simulationId, 1);
        verify(jdbc).update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?) on conflict (id) do nothing", userId, simulationId, "사용자 1", "QUEUED");
        verify(jdbc).update("insert into simulation_seats(simulation_id, seat_id, seat_label, status) values (?, ?, ?, 'AVAILABLE') on conflict (simulation_id, seat_id) do nothing", simulationId, 1L, "A-1");
    }

    @Test
    void registersLiveEventParticipantInSimulationInventory() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000120");
        SimulationSnapshot snapshot = new SimulationSnapshot(
                simulationId,
                List.of(),
                List.of(),
                new SimulationMetrics(0, 0, 0, 0, 0, 0),
                List.of(),
                false
        );
        VirtualUserView participant = new VirtualUserView(
                participantId,
                "권",
                ParticipantType.HUMAN,
                VirtualUserStatus.WAITING_ROOM,
                null,
                List.of(new TimelineEntry("입장", "이벤트에 입장했습니다.")),
                0,
                0,
                0,
                null
        );

        service.registerParticipant(snapshot, participant);

        verify(jdbc).update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?) on conflict (id) do nothing", participantId, simulationId, "권", "WAITING_ROOM");
    }
}
