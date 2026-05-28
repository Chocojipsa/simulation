package com.timedeal.seatreservation.generator;

import java.util.UUID;

@FunctionalInterface
public interface VirtualUserHttpClient {
    void runUser(String baseUrl, UUID simulationId, int virtualUserNumber);
}
