package com.timedeal.seatreservation.queue;

import java.util.List;
import java.util.UUID;

public record QueuePositionsBatchEvent(
        UUID eventId,
        List<UserQueuePosition> positions
) {}
