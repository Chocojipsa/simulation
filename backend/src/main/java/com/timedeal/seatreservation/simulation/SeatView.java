package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;

public record SeatView(
        long id,
        String label,
        SeatStatus status
) {
}
