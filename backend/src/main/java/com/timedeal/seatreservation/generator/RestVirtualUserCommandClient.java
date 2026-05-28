package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@Profile("generator")
public class RestVirtualUserCommandClient implements VirtualUserCommandClient {
    private final RestClient restClient;

    public RestVirtualUserCommandClient() {
        this(RestClient.create());
    }

    RestVirtualUserCommandClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public VirtualUserCommandResponse postQueue(String baseUrl, UUID simulationId, UUID virtualUserId) {
        return restClient.post()
                .uri(baseUrl + "/api/simulations/{simulationId}/users/{userId}/queue", simulationId, virtualUserId)
                .retrieve()
                .body(VirtualUserCommandResponse.class);
    }

    @Override
    public VirtualUserCommandResponse postSeatAttempt(String baseUrl, UUID simulationId, UUID virtualUserId) {
        return restClient.post()
                .uri(baseUrl + "/api/simulations/{simulationId}/users/{userId}/seat-attempt", simulationId, virtualUserId)
                .retrieve()
                .body(VirtualUserCommandResponse.class);
    }
}
