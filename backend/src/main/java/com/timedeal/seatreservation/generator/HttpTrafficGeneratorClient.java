package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@Profile("!demo & !generator")
public class HttpTrafficGeneratorClient implements TrafficGeneratorClient {
    private final RestClient restClient;

    public HttpTrafficGeneratorClient(
            @Value("${traffic-generator.control-base-url:http://localhost:8080}") String controlBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(controlBaseUrl)
                .build();
    }

    @Override
    public void start(UUID simulationId, RunSimulationRequest request) {
        restClient.post()
                .uri("/internal/traffic-generator/simulations/{simulationId}/run", simulationId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
