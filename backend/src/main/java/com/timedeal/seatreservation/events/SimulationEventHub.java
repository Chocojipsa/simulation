package com.timedeal.seatreservation.events;

import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SimulationEventHub {
    private static final long TIMEOUT_MILLIS = 60_000L;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter open(UUID simulationId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(simulationId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(simulationId, emitter));
        emitter.onTimeout(() -> remove(simulationId, emitter));
        emitter.onError(ignored -> remove(simulationId, emitter));
        return emitter;
    }

    public void publish(SimulationSnapshot snapshot) {
        List<SseEmitter> simulationEmitters = emitters.get(snapshot.simulationId());
        if (simulationEmitters == null) {
            return;
        }

        for (SseEmitter emitter : simulationEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(snapshot));
            } catch (IOException | IllegalStateException exception) {
                remove(snapshot.simulationId(), emitter);
            }
        }
    }

    private void remove(UUID simulationId, SseEmitter emitter) {
        List<SseEmitter> simulationEmitters = emitters.get(simulationId);
        if (simulationEmitters == null) {
            return;
        }
        simulationEmitters.remove(emitter);
        if (simulationEmitters.isEmpty()) {
            emitters.remove(simulationId);
        }
    }
}
