package com.timedeal.seatreservation.event;

import jakarta.validation.constraints.Size;

public record JoinEventRequest(
        @Size(max = 30) String displayName
) {
    public String normalizedDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return "나";
        }
        return displayName.trim();
    }
}
