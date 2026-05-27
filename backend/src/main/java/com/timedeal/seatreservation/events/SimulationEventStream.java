package com.timedeal.seatreservation.events;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class SimulationEventStream {
    public static final long TIMEOUT_MILLIS = 60_000L;

    private SimulationEventStream() {
    }

    public static SseEmitter open(String simulationId) {
        return new SseEmitter(TIMEOUT_MILLIS);
    }
}
