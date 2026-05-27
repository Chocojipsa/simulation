package com.timedeal.seatreservation.queue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class InMemoryAdmissionQueue implements AdmissionQueue {
    private final ConcurrentHashMap<String, LinkedHashSet<String>> queues = new ConcurrentHashMap<>();

    @Override
    public synchronized void enter(String simulationId, String virtualUserId) {
        queues.computeIfAbsent(simulationId, ignored -> new LinkedHashSet<>()).add(virtualUserId);
    }

    @Override
    public synchronized List<String> pick(String simulationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        LinkedHashSet<String> queue = queues.get(simulationId);
        if (queue == null) {
            return List.of();
        }

        return queue.stream()
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized void grant(String simulationId, String virtualUserId) {
        LinkedHashSet<String> queue = queues.get(simulationId);
        if (queue == null) {
            return;
        }

        queue.remove(virtualUserId);
        if (queue.isEmpty()) {
            queues.remove(simulationId);
        }
    }
}
