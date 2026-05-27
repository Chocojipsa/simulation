package com.timedeal.seatreservation.seat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeatReservationConstraintTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyOneActiveReservationCanExistForOneSeat() {
        jdbc.update("insert into concerts(id, title) values (?, ?)", 1L, "테스트 콘서트");
        jdbc.update("insert into seats(id, concert_id, seat_label) values (?, ?, ?)", 10L, 1L, "A-1");
        jdbc.update("insert into reservations(id, seat_id, status, idempotency_key) values (?, ?, ?, ?)",
                100L, 10L, "RESERVED", "reservation-100");

        assertThatThrownBy(() -> jdbc.update(
                "insert into reservations(id, seat_id, status, idempotency_key) values (?, ?, ?, ?)",
                101L, 10L, "RESERVED", "reservation-101"
        )).hasMessageContaining("active_reservation_per_seat");
    }
}
