package com.timedeal.seatreservation.generator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Profile("generator")
public class HttpVirtualUserHttpClient implements VirtualUserHttpClient {
    private final RestClient restClient = RestClient.create();

    @Override
    public void runUser(String baseUrl, UUID simulationId, int virtualUserNumber) {
        UUID virtualUserId = UUID.nameUUIDFromBytes(
                (simulationId + ":" + virtualUserNumber).getBytes(StandardCharsets.UTF_8)
        );
        restClient.post()
                .uri(baseUrl + "/api/simulations/{simulationId}/users/{userId}/queue", simulationId, virtualUserId)
                .retrieve()
                .toBodilessEntity();
    }
}
