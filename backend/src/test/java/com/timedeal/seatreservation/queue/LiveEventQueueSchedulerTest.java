package com.timedeal.seatreservation.queue;

import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveEventQueueSchedulerTest {

    private LiveEventService liveEventService;
    private SimulationService simulationService;
    private WaitingQueueService waitingQueueService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LiveEventQueueScheduler scheduler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        liveEventService = mock(LiveEventService.class);
        simulationService = mock(SimulationService.class);
        waitingQueueService = mock(WaitingQueueService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        scheduler = new LiveEventQueueScheduler(
                liveEventService,
                simulationService,
                waitingQueueService,
                redisTemplate
        );
    }

    @Test
    void processQueueSkipsExecutionIfLockNotAcquired() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        scheduler.processQueue();

        verifyNoInteractions(liveEventService);
        verifyNoInteractions(simulationService);
        verifyNoInteractions(waitingQueueService);
    }

    @Test
    void processQueueExecutesAndBatchesPositionsWhenLockAcquired() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        UUID eventId = UUID.randomUUID();
        LiveEventResponse activeEvent = new LiveEventResponse(
                eventId,
                "Test Event",
                "OPEN",
                1,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                100
        );
        when(liveEventService.activeEvent()).thenReturn(activeEvent);

        SimulationSnapshot snapshot = new SimulationSnapshot(
                eventId,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                true
        );
        when(simulationService.getSimulation(eventId)).thenReturn(snapshot);
        when(simulationService.getMaxActiveAdmissions()).thenReturn(10);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        when(waitingQueueService.queuedUserIds(eventId.toString()))
                .thenReturn(List.of(user1.toString(), user2.toString()));

        scheduler.processQueue();

        ArgumentCaptor<QueuePositionsBatchEvent> batchCaptor = ArgumentCaptor.forClass(QueuePositionsBatchEvent.class);
        verify(simulationService).publishQueuePositionsBatch(batchCaptor.capture());

        QueuePositionsBatchEvent batchEvent = batchCaptor.getValue();
        assertEquals(eventId, batchEvent.eventId());
        assertEquals(2, batchEvent.positions().size());

        UserQueuePosition pos1 = batchEvent.positions().get(0);
        assertEquals(user1, pos1.userId());
        assertEquals(1, pos1.position());
        assertEquals(0.5, pos1.estimatedWaitSeconds());

        UserQueuePosition pos2 = batchEvent.positions().get(1);
        assertEquals(user2, pos2.userId());
        assertEquals(2, pos2.position());
        assertEquals(1.0, pos2.estimatedWaitSeconds());
    }
}
