package com.timedeal.seatreservation.simulation.redis;

import java.util.UUID;

public final class SimulationRedisKeys {
    private final String prefix;

    public SimulationRedisKeys(UUID simulationId) {
        this.prefix = "simulation:" + simulationId;
    }

    public String queue() {
        return prefix + ":queue";
    }

    public String users() {
        return prefix + ":users";
    }

    public String snapshot() {
        return prefix + ":snapshot";
    }

    public String serverStats() {
        return prefix + ":server-stats";
    }

    public String events() {
        return prefix + ":events";
    }

}
