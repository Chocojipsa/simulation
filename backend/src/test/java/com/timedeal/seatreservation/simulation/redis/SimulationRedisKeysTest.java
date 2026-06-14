package com.timedeal.seatreservation.simulation.redis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRedisKeysTest {
    @Test
    void keysAreScopedBySimulationId() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SimulationRedisKeys keys = new SimulationRedisKeys(simulationId);

        assertThat(keys.queue()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:queue");
        assertThat(keys.users()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:users");
        assertThat(keys.snapshot()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:snapshot");
        assertThat(keys.serverStats()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:server-stats");
        assertThat(keys.events()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:events");
    }
}
