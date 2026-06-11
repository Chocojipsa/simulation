package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LiveEventAiStarterTest {
    @Test
    void schedulesEachBatchWithDelay() {
        SimulationService simulationService = mock(SimulationService.class);
        List<Duration> delays = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        LiveEventAiStarter starter = new LiveEventAiStarter(
                simulationService,
                150,
                50,
                (delay, task) -> {
                    delays.add(delay);
                    tasks.add(task);
                }
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        starter.start(eventId);
        tasks.forEach(Runnable::run);

        assertThat(delays).containsExactly(
                Duration.ofMillis(500),
                Duration.ofMillis(1000),
                Duration.ofMillis(1500),
                Duration.ofMillis(2000),
                Duration.ofMillis(2500)
        );
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(15, 15, 0));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(23, 23, 15));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(30, 30, 38));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(38, 38, 68));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(44, 44, 106));
    }

    @Test
    void appliesConfiguredProportionalScheduleAndSpeeds() {
        SimulationService simService = mock(SimulationService.class);
        LiveEventAiStarter.BatchScheduler scheduler = (delay, task) -> {
            task.run();
        };
        
        LiveEventAiStarter starter = new LiveEventAiStarter(simService, 150, 50, scheduler);
        
        UUID eventId = UUID.randomUUID();
        starter.configure(eventId, 1000, 100, "FAST");
        
        starter.start(eventId);
        
        // Verify simulationService was called with proportional numbers
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 100 && req.concurrency() == 100) // 10%
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 150 && req.concurrency() == 100) // 15%
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 200 && req.concurrency() == 100) // 20%
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 250 && req.concurrency() == 100) // 25%
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 300 && req.concurrency() == 100) // 30%
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesRedisWhenAvailable() throws Exception {
        SimulationService simService = mock(SimulationService.class);
        LiveEventAiStarter.BatchScheduler scheduler = (delay, task) -> task.run();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectMapper mapper = new ObjectMapper();

        LiveEventAiStarter starter = new LiveEventAiStarter(
                simService, 150, 50, scheduler, redisTemplate, mapper
        );

        UUID eventId = UUID.randomUUID();
        String expectedKey = "live-event:" + eventId + ":ai-config";

        // 1. Test configure writes to Redis
        starter.configure(eventId, 200, 40, "FAST");
        verify(valueOperations).set(eq(expectedKey), contains("\"participantCount\":200"), eq(Duration.ofMinutes(10)));

        // 2. Test getCachedConfig reads from Redis
        String json = mapper.writeValueAsString(new LiveEventAiStarter.AiConfig(200, 40, "FAST"));
        when(valueOperations.get(expectedKey)).thenReturn(json);

        LiveEventAiStarter.AiConfig cached = starter.getCachedConfig(eventId);
        assertThat(cached).isNotNull();
        assertThat(cached.participantCount()).isEqualTo(200);
        assertThat(cached.concurrency()).isEqualTo(40);
        assertThat(cached.speed()).isEqualTo("FAST");

        // 3. Test start reads from Redis and deletes, then triggers simulation batches
        starter.start(eventId);
        verify(redisTemplate).delete(expectedKey);
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 20 && req.concurrency() == 20) // 10% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 30 && req.concurrency() == 30) // 15% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 40 && req.concurrency() == 40) // 20% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 50 && req.concurrency() == 40) // 25% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 60 && req.concurrency() == 40) // 30% batch
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToLocalConfigWhenRedisThrows() {
        SimulationService simService = mock(SimulationService.class);
        LiveEventAiStarter.BatchScheduler scheduler = (delay, task) -> task.run();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis connection error")).when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection error"));

        LiveEventAiStarter starter = new LiveEventAiStarter(
                simService, 150, 50, scheduler, redisTemplate, new ObjectMapper()
        );

        UUID eventId = UUID.randomUUID();

        // 1. Configure should catch exception and write to localConfig
        starter.configure(eventId, 300, 60, "SLOW");

        // 2. GetCachedConfig should handle Redis throw and read from localConfig
        LiveEventAiStarter.AiConfig cached = starter.getCachedConfig(eventId);
        assertThat(cached).isNotNull();
        assertThat(cached.participantCount()).isEqualTo(300);
        assertThat(cached.concurrency()).isEqualTo(60);
        assertThat(cached.speed()).isEqualTo("SLOW");

        // 3. Start should handle Redis throw, read from localConfig, and trigger simulation
        starter.start(eventId);
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 30 && req.concurrency() == 30) // 10% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 45 && req.concurrency() == 45) // 15% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 60 && req.concurrency() == 60) // 20% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 75 && req.concurrency() == 60) // 25% batch
        );
        verify(simService).runSimulation(
                eq(eventId),
                argThat(req -> req.virtualUserCount() == 90 && req.concurrency() == 60) // 30% batch
        );
    }
}
