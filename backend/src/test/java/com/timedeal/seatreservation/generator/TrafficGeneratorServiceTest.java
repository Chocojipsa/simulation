package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficGeneratorServiceTest {
    @Test
    void sendsEveryVirtualUserThroughConfiguredTarget() {
        List<String> calls = new ArrayList<>();
        VirtualUserHttpClient client = (baseUrl, simulationId, virtualUserNumber) ->
                calls.add(baseUrl + "|" + simulationId + "|" + virtualUserNumber);
        TrafficGeneratorService service = new TrafficGeneratorService(client, "http://nginx:8080", 1);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        service.start(simulationId, new RunSimulationRequest(3, 1));

        assertThat(calls).containsExactly(
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|1",
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|2",
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|3"
        );
    }
}
