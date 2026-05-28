package com.timedeal.seatreservation.seat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeatReservationConstraintTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sameSeatCanBeReservedIndependentlyPerSimulation() {
        UUID simulationA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID simulationB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID userA = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID userB = UUID.fromString("00000000-0000-0000-0000-000000000102");
        insertSimulationFixture(simulationA, userA, "사용자 1");
        insertSimulationFixture(simulationB, userB, "사용자 2");

        jdbc.update("""
                        insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                100L, simulationA, 10L, userA, "RESERVED", "reservation-a");
        jdbc.update("""
                        insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                101L, simulationB, 10L, userB, "RESERVED", "reservation-b");

        Integer count = jdbc.queryForObject("select count(*) from reservations", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void onlyOneActiveReservationCanExistForOneSimulationSeat() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID userA = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID userB = UUID.fromString("00000000-0000-0000-0000-000000000202");
        insertSimulationFixture(simulationId, userA, "사용자 1");
        jdbc.update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?)",
                userB, simulationId, "사용자 2", "QUEUED");

        jdbc.update("""
                        insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                200L, simulationId, 10L, userA, "RESERVED", "reservation-200");

        assertThatThrownBy(() -> jdbc.update("""
                                insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key)
                                values (?, ?, ?, ?, ?, ?)
                                """,
                        201L, simulationId, 10L, userB, "RESERVED", "reservation-201"
                ))
                .hasMessageContaining("active_reservation_per_simulation_seat");
    }

    private void insertSimulationFixture(UUID simulationId, UUID userId, String displayName) {
        jdbc.update("insert into concerts(id, title) values (?, ?) on conflict (id) do nothing", 1L, "테스트 콘서트");
        jdbc.update("insert into seats(id, concert_id, seat_label) values (?, ?, ?) on conflict (id) do nothing",
                10L, 1L, "A-1");
        jdbc.update("insert into simulation_sessions(id, concert_id, requested_users, status) values (?, ?, ?, ?)",
                simulationId, 1L, 1, "CREATED");
        jdbc.update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?)",
                userId, simulationId, displayName, "QUEUED");
        jdbc.update("""
                        insert into simulation_seats(simulation_id, seat_id, seat_label, status)
                        values (?, ?, ?, ?)
                        """,
                simulationId, 10L, "A-1", "AVAILABLE");
    }
}
