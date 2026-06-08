package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationEventHubTest {

    @Test
    void openFlushesConnectEvent() throws Exception {
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        SseEmitter mockEmitter = mock(SseEmitter.class);
        
        SimulationEventHub hub = new SimulationEventHub(publisher, mapper) {
            @Override
            protected SseEmitter createEmitter(long timeout) {
                return mockEmitter;
            }
        };

        UUID simulationId = UUID.randomUUID();
        try {
            SseEmitter emitter = hub.open(simulationId);
            assertSame(mockEmitter, emitter);
            verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void openUserStreamFlushesConnectEvent() throws Exception {
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        SseEmitter mockEmitter = mock(SseEmitter.class);
        
        SimulationEventHub hub = new SimulationEventHub(publisher, mapper) {
            @Override
            protected SseEmitter createEmitter(long timeout) {
                return mockEmitter;
            }
        };

        UUID participantId = UUID.randomUUID();
        try {
            SseEmitter emitter = hub.openUserStream(participantId);
            assertSame(mockEmitter, emitter);
            verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        } finally {
            hub.shutdown();
        }
    }
}
