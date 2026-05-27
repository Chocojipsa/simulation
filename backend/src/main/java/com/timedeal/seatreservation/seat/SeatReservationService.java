package com.timedeal.seatreservation.seat;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Service
@Profile("!demo")
public class SeatReservationService {
    static final String FIND_BY_IDEMPOTENCY_KEY_SQL = """
            select id, seat_id, virtual_user_id, idempotency_key
            from reservations
            where idempotency_key = ?
            """;

    static final String ACTIVE_RESERVATION_COUNT_SQL = """
            select count(*)
            from reservations
            where seat_id = ?
              and status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED')
            """;

    static final String HOLD_SEAT_SQL = """
            update seats
            set status = 'HELD', updated_at = now()
            where id = ?
              and status = 'AVAILABLE'
            """;

    static final String NEXT_RESERVATION_ID_SQL = "select nextval('reservation_id_seq')";

    static final String INSERT_RESERVATION_SQL = """
            insert into reservations(id, seat_id, virtual_user_id, status, idempotency_key)
            values (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;

    public SeatReservationService(JdbcTemplate jdbc, TransactionOperations transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public SeatReservationResult holdSeat(
            UUID simulationId,
            UUID virtualUserId,
            long seatId,
            String idempotencyKey
    ) {
        return transactions.execute(status -> holdSeatInTransaction(virtualUserId, seatId, idempotencyKey));
    }

    private SeatReservationResult holdSeatInTransaction(UUID virtualUserId, long seatId, String idempotencyKey) {
        SeatReservationResult existing = findExisting(idempotencyKey);
        if (existing != null) {
            return existing;
        }

        Integer activeReservationCount = jdbc.queryForObject(ACTIVE_RESERVATION_COUNT_SQL, Integer.class, seatId);
        if (activeReservationCount != null && activeReservationCount > 0) {
            return new SeatReservationResult(
                    SeatReservationOutcome.ALREADY_HELD,
                    null,
                    seatId,
                    virtualUserId,
                    idempotencyKey
            );
        }

        int updatedSeats = jdbc.update(HOLD_SEAT_SQL, seatId);
        if (updatedSeats == 0) {
            return new SeatReservationResult(
                    SeatReservationOutcome.ALREADY_HELD,
                    null,
                    seatId,
                    virtualUserId,
                    idempotencyKey
            );
        }

        Long reservationId = jdbc.queryForObject(NEXT_RESERVATION_ID_SQL, Long.class);
        jdbc.update(INSERT_RESERVATION_SQL, reservationId, seatId, virtualUserId, "HELD", idempotencyKey);

        return new SeatReservationResult(
                SeatReservationOutcome.HELD,
                reservationId,
                seatId,
                virtualUserId,
                idempotencyKey
        );
    }

    private SeatReservationResult findExisting(String idempotencyKey) {
        try {
            return jdbc.queryForObject(
                    FIND_BY_IDEMPOTENCY_KEY_SQL,
                    (rs, rowNum) -> new SeatReservationResult(
                            SeatReservationOutcome.IDEMPOTENT_REPLAY,
                            rs.getLong("id"),
                            rs.getLong("seat_id"),
                            UUID.fromString(rs.getString("virtual_user_id")),
                            rs.getString("idempotency_key")
                    ),
                    idempotencyKey
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
