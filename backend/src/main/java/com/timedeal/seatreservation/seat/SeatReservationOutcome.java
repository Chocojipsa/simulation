package com.timedeal.seatreservation.seat;

public enum SeatReservationOutcome {
    HELD,
    IDEMPOTENT_REPLAY,
    ALREADY_HELD
}
