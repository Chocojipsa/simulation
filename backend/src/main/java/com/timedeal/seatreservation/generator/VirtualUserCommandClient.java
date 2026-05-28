package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;

import java.util.UUID;

public interface VirtualUserCommandClient {
    VirtualUserCommandResponse postQueue(String baseUrl, UUID simulationId, UUID virtualUserId);

    VirtualUserCommandResponse postSeatAttempt(String baseUrl, UUID simulationId, UUID virtualUserId);
}
