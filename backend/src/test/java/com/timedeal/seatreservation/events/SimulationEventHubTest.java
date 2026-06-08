package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
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

    @Test
    void openHandlesSendFailureAndCleansUp() throws Exception {
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection reset")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        SimulationEventHub hub = new SimulationEventHub(publisher, mapper) {
            @Override
            protected SseEmitter createEmitter(long timeout) {
                return mockEmitter;
            }
        };

        UUID simulationId = UUID.randomUUID();
        try {
            hub.open(simulationId);
            verify(mockEmitter).complete();
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void openUserStreamHandlesSendFailureAndCleansUp() throws Exception {
        SnapshotPublisher publisher = mock(SnapshotPublisher.class);
        ObjectMapper mapper = new ObjectMapper();
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection reset")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        SimulationEventHub hub = new SimulationEventHub(publisher, mapper) {
            @Override
            protected SseEmitter createEmitter(long timeout) {
                return mockEmitter;
            }
        };

        try {
            assertEquals(0, hub.getActiveUserConnectionCount());
            hub.openUserStream(UUID.randomUUID());
            assertEquals(0, hub.getActiveUserConnectionCount());
            verify(mockEmitter).complete();
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void openUserStreamCallbacksCleanUp() throws Exception {
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
            hub.openUserStream(participantId);
            
            org.mockito.ArgumentCaptor<Runnable> completionCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(mockEmitter).onCompletion(completionCaptor.capture());
            
            // Invoke onCompletion callback manually
            assertEquals(1, hub.getActiveUserConnectionCount());
            completionCaptor.getValue().run();
            assertEquals(0, hub.getActiveUserConnectionCount());
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void openUserStreamTimeoutCleanUp() throws Exception {
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
            hub.openUserStream(participantId);
            
            org.mockito.ArgumentCaptor<Runnable> timeoutCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(mockEmitter).onTimeout(timeoutCaptor.capture());
            
            assertEquals(1, hub.getActiveUserConnectionCount());
            timeoutCaptor.getValue().run();
            assertEquals(0, hub.getActiveUserConnectionCount());
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void openUserStreamErrorCleanUp() throws Exception {
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
            hub.openUserStream(participantId);
            
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<java.util.function.Consumer<Throwable>> errorCaptor = 
                org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);
            verify(mockEmitter).onError(errorCaptor.capture());
            
            assertEquals(1, hub.getActiveUserConnectionCount());
            errorCaptor.getValue().accept(new RuntimeException("Test error"));
            assertEquals(0, hub.getActiveUserConnectionCount());
        } finally {
            hub.shutdown();
        }
    }
}
