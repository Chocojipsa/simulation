package com.timedeal.seatreservation.queue;

import java.util.UUID;

public record UserQueuePosition(
        UUID userId,
        int position,
        double estimatedWaitSeconds
) {}
