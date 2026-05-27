package com.timedeal.seatreservation.seat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SeatReservationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionOperations transactions = new ImmediateTransactionOperations();
    private final SeatReservationService service = new SeatReservationService(jdbc, transactions);

    @Test
    void holdSeatCreatesHeldReservationWhenSeatIsAvailable() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID virtualUserId = UUID.fromString("00000000-0000-0000-0000-000000000401");

        when(jdbc.queryForObject(eq(SeatReservationService.FIND_BY_IDEMPOTENCY_KEY_SQL), any(RowMapper.class), eq("hold-1")))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(SeatReservationService.ACTIVE_RESERVATION_COUNT_SQL, Integer.class, 10L))
                .thenReturn(0);
        when(jdbc.update(SeatReservationService.HOLD_SEAT_SQL, 10L))
                .thenReturn(1);
        when(jdbc.queryForObject(SeatReservationService.NEXT_RESERVATION_ID_SQL, Long.class))
                .thenReturn(100L);

        SeatReservationResult result = service.holdSeat(simulationId, virtualUserId, 10L, "hold-1");

        assertThat(result.outcome()).isEqualTo(SeatReservationOutcome.HELD);
        assertThat(result.reservationId()).isEqualTo(100L);
        assertThat(result.seatId()).isEqualTo(10L);
        assertThat(result.virtualUserId()).isEqualTo(virtualUserId);
        assertThat(result.idempotencyKey()).isEqualTo("hold-1");
        verify(jdbc).update(
                SeatReservationService.INSERT_RESERVATION_SQL,
                100L,
                10L,
                virtualUserId,
                "HELD",
                "hold-1"
        );
    }

    @Test
    void holdSeatReturnsAlreadyHeldWhenActiveReservationExists() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID virtualUserId = UUID.fromString("00000000-0000-0000-0000-000000000402");

        when(jdbc.queryForObject(eq(SeatReservationService.FIND_BY_IDEMPOTENCY_KEY_SQL), any(RowMapper.class), eq("hold-2")))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(SeatReservationService.ACTIVE_RESERVATION_COUNT_SQL, Integer.class, 10L))
                .thenReturn(1);

        SeatReservationResult result = service.holdSeat(simulationId, virtualUserId, 10L, "hold-2");

        assertThat(result.outcome()).isEqualTo(SeatReservationOutcome.ALREADY_HELD);
        assertThat(result.reservationId()).isNull();
        assertThat(result.seatId()).isEqualTo(10L);
        assertThat(result.virtualUserId()).isEqualTo(virtualUserId);
    }

    @Test
    void holdSeatReturnsIdempotentReplayForDuplicateKey() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        UUID virtualUserId = UUID.fromString("00000000-0000-0000-0000-000000000403");
        SeatReservationResult existing = new SeatReservationResult(
                SeatReservationOutcome.IDEMPOTENT_REPLAY,
                100L,
                10L,
                virtualUserId,
                "hold-3"
        );

        when(jdbc.queryForObject(eq(SeatReservationService.FIND_BY_IDEMPOTENCY_KEY_SQL), any(RowMapper.class), eq("hold-3")))
                .thenReturn(existing);

        SeatReservationResult result = service.holdSeat(simulationId, virtualUserId, 10L, "hold-3");

        assertThat(result).isEqualTo(existing);
    }

    private static final class ImmediateTransactionOperations implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
