package com.timedeal.seatreservation.simulation;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!demo")
public class SimulationInventoryService {
    private static final String CONCERT_TITLE = "분산 좌석 예매 콘서트";

    private final JdbcTemplate jdbc;

    public SimulationInventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize(SimulationSnapshot snapshot, int requestedUsers) {
        jdbc.update("insert into concerts(id, title) values (1, ?) on conflict (id) do nothing", CONCERT_TITLE);

        for (SeatView seat : snapshot.seats()) {
            jdbc.update(
                    "insert into seats(id, concert_id, seat_label, status) values (?, 1, ?, 'AVAILABLE') on conflict (id) do nothing",
                    seat.id(),
                    seat.label()
            );
        }

        jdbc.update(
                "insert into simulation_sessions(id, concert_id, requested_users, status) values (?, 1, ?, 'CREATED')",
                snapshot.simulationId(),
                requestedUsers
        );

        for (VirtualUserView user : snapshot.users()) {
            jdbc.update(
                    "insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?) on conflict (id) do nothing",
                    user.id(),
                    snapshot.simulationId(),
                    user.displayName(),
                    user.status().name()
            );
        }

        for (SeatView seat : snapshot.seats()) {
            jdbc.update(
                    "insert into simulation_seats(simulation_id, seat_id, seat_label, status) values (?, ?, ?, 'AVAILABLE') on conflict (simulation_id, seat_id) do nothing",
                    snapshot.simulationId(),
                    seat.id(),
                    seat.label()
            );
        }
    }

    public void registerParticipant(SimulationSnapshot snapshot, VirtualUserView participant) {
        jdbc.update(
                "insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?) on conflict (id) do nothing",
                participant.id(),
                snapshot.simulationId(),
                participant.displayName(),
                participant.status().name()
        );
    }
}
