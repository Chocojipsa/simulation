package com.timedeal.seatreservation.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StartAiParticipantsRequest(
        @Min(1) @Max(1000) int participantCount,
        @Min(1) @Max(100) int concurrency
) {
}
