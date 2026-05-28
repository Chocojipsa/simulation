package com.timedeal.seatreservation.event;

import java.time.Instant;
import java.util.UUID;

public record LiveEventMetadata(
        UUID eventId,
        int generation,
        LiveEventStatus status,
        Instant createdAt,
        Instant opensAt,
        Instant endsAt,
        boolean aiStarted
) {
    public LiveEventStatus statusAt(Instant now) {
        if (status == LiveEventStatus.COUNTDOWN && opensAt != null && !now.isBefore(opensAt)) {
            if (endsAt != null && !now.isBefore(endsAt)) {
                return LiveEventStatus.ENDED;
            }
            return LiveEventStatus.OPEN;
        }
        if (status == LiveEventStatus.OPEN && endsAt != null && !now.isBefore(endsAt)) {
            return LiveEventStatus.ENDED;
        }
        return status;
    }

    public LiveEventMetadata withDerivedStatus(Instant now) {
        return new LiveEventMetadata(eventId, generation, statusAt(now), createdAt, opensAt, endsAt, aiStarted);
    }
}
