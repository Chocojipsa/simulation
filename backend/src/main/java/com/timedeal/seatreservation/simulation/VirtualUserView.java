package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.VirtualUserStatus;

import java.util.List;
import java.util.UUID;

public record VirtualUserView(
        UUID id,
        String displayName,
        VirtualUserStatus status,
        String selectedSeatLabel,
        List<TimelineEntry> timeline
) {
}
