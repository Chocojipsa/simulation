package com.timedeal.seatreservation.event;

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
        Instant opensAt,
        List<SeatView> seats,
        List<VirtualUserView> participants,
        SimulationMetrics metrics,
        List<ServerStatsView> serverStats,
        boolean running,
        UUID myParticipantId
) {
}
