package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationEventHubTest {

    @SuppressWarnings("unchecked")
    private Set<Object> getEarlySendAttempts(SseEmitter emitter) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField("earlySendAttempts");
        field.setAccessible(true);
        return (Set<Object>) field.get(emitter);
    }

    private Object getDataFieldValue(Object dataWithMediaType) throws Exception {
        Field field = dataWithMediaType.getClass().getDeclaredField("data");
        field.setAccessible(true);
        return field.get(dataWithMediaType);
    }

    @Test
    void openFlushesConnectEvent() throws Exception {
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        SimulationEventHub hub = new SimulationEventHub(publisher, mapper);

        UUID simulationId = UUID.randomUUID();
        try {
            SseEmitter emitter = hub.open(simulationId);
            Set<Object> attempts = getEarlySendAttempts(emitter);
            assertNotNull(attempts);
            assertTrue(attempts.size() > 0, "Should have early send attempts");

            boolean foundConnect = false;
            for (Object attempt : attempts) {
                Object data = getDataFieldValue(attempt);
                if (data instanceof String && ((String) data).contains("connect")) {
                    foundConnect = true;
                }
            }
            assertTrue(foundConnect, "Should have sent a connect event");
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void openUserStreamFlushesConnectEvent() throws Exception {
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        SimulationEventHub hub = new SimulationEventHub(publisher, mapper);

        UUID participantId = UUID.randomUUID();
        try {
            SseEmitter emitter = hub.openUserStream(participantId);
            Set<Object> attempts = getEarlySendAttempts(emitter);
            assertNotNull(attempts);
            assertTrue(attempts.size() > 0, "Should have early send attempts");

            boolean foundConnect = false;
            for (Object attempt : attempts) {
                Object data = getDataFieldValue(attempt);
                if (data instanceof String && ((String) data).contains("connect")) {
                    foundConnect = true;
                }
            }
            assertTrue(foundConnect, "Should have sent a connect event");
        } finally {
            hub.shutdown();
        }
    }
}
