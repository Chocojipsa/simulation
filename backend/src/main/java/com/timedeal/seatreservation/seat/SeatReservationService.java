package com.timedeal.seatreservation.seat;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
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
            where simulation_id = ?
              and seat_id = ?
              and status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED')
            """;

    static final String HOLD_SEAT_SQL = """
            update simulation_seats
            set status = 'HELD', held_by_user_id = ?, updated_at = now()
            where simulation_id = ?
              and seat_id = ?
              and status = 'AVAILABLE'
            """;

    static final String NEXT_RESERVATION_ID_SQL = "select nextval('reservation_id_seq')";

    static final String INSERT_RESERVATION_SQL = """
            insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key)
            values (?, ?, ?, ?, ?, ?)
            """;
    static final String UPDATE_RESERVATION_PAYMENT_RESULT_SQL = """
            update reservations
            set status = ?, updated_at = now()
            where id = ?
              and simulation_id = ?
              and status = 'HELD'
            """;
    static final String UPDATE_SIMULATION_SEAT_PAYMENT_RESULT_SQL = """
            update simulation_seats
            set status = ?, held_by_user_id = ?, updated_at = now()
            where simulation_id = ?
              and seat_id = ?
              and held_by_user_id = ?
            """;
    static final String EXPIRE_HELD_RESERVATION_SQL = """
            update reservations
            set status = 'EXPIRED', updated_at = now()
            where id = ?
              and simulation_id = ?
              and status = 'HELD'
            """;
    static final String RELEASE_HELD_SEAT_SQL = """
            update simulation_seats
            set status = 'AVAILABLE', held_by_user_id = null, updated_at = now()
            where simulation_id = ?
              and seat_id = ?
              and status = 'HELD'
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
        try {
            return transactions.execute(status -> holdSeatInTransaction(simulationId, virtualUserId, seatId, idempotencyKey));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            return new SeatReservationResult(
                    SeatReservationOutcome.ALREADY_HELD,
                    null,
                    seatId,
                    virtualUserId,
                    idempotencyKey
            );
        }
    }

    public void applyPaymentResult(PaymentResultEvent event) {
        transactions.execute(status -> {
            String reservationStatus = event.success() ? "RESERVED" : "FAILED";
            String seatStatus = event.success() ? "RESERVED" : "AVAILABLE";
            UUID heldByUserId = event.success() ? event.virtualUserId() : null;

            jdbc.update(
                    UPDATE_RESERVATION_PAYMENT_RESULT_SQL,
                    reservationStatus,
                    event.reservationId(),
                    event.simulationId()
            );
            jdbc.update(
                    UPDATE_SIMULATION_SEAT_PAYMENT_RESULT_SQL,
                    seatStatus,
                    heldByUserId,
                    event.simulationId(),
                    event.seatId(),
                    event.virtualUserId()
            );
            return null;
        });
    }

    public void expireHold(UUID simulationId, long reservationId, long seatId) {
        transactions.execute(status -> {
            int expired = jdbc.update(EXPIRE_HELD_RESERVATION_SQL, reservationId, simulationId);
            if (expired > 0) {
                jdbc.update(RELEASE_HELD_SEAT_SQL, simulationId, seatId);
            }
            return null;
        });
    }

    private SeatReservationResult holdSeatInTransaction(
            UUID simulationId,
            UUID virtualUserId,
            long seatId,
            String idempotencyKey
    ) {
        SeatReservationResult existing = findExisting(idempotencyKey);
        if (existing != null) {
            return existing;
        }

        Integer activeReservationCount = jdbc.queryForObject(
                ACTIVE_RESERVATION_COUNT_SQL,
                Integer.class,
                simulationId,
                seatId
        );
        if (activeReservationCount != null && activeReservationCount > 0) {
            return new SeatReservationResult(
                    SeatReservationOutcome.ALREADY_HELD,
                    null,
                    seatId,
                    virtualUserId,
                    idempotencyKey
            );
        }

        int updatedSeats = jdbc.update(HOLD_SEAT_SQL, virtualUserId, simulationId, seatId);
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
        jdbc.update(INSERT_RESERVATION_SQL, reservationId, simulationId, seatId, virtualUserId, "HELD", idempotencyKey);

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
