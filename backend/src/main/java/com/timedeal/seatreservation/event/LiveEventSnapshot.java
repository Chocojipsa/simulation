package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.VirtualUserView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LiveEventSnapshot(
        UUID eventId,
        String title,
        String status,
        int generation,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant opensAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant endsAt,
        List<SeatView> seats,
        List<VirtualUserView> participants,
        SimulationMetrics metrics,
        List<ServerStatsView> serverStats,
        boolean running,
        UUID myParticipantId,
        Integer myQueuePosition
) {
}
