package com.timedeal.seatreservation.queue;

import java.util.List;

public interface AdmissionQueue {
    void enter(String simulationId, String virtualUserId);

    List<String> pick(String simulationId, int limit);

    void grant(String simulationId, String virtualUserId);
}
