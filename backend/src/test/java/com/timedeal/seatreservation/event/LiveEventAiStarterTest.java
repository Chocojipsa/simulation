package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(15, 15));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(23, 23));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(30, 30));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(38, 38));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(44, 44));
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
        org.mockito.Mockito.verify(simService).runSimulation(
                org.mockito.Mockito.eq(eventId),
                org.mockito.Mockito.argThat(req -> req.virtualUserCount() == 100 && req.concurrency() == 100) // 10%
        );
        org.mockito.Mockito.verify(simService).runSimulation(
                org.mockito.Mockito.eq(eventId),
                org.mockito.Mockito.argThat(req -> req.virtualUserCount() == 150 && req.concurrency() == 100) // 15%
        );
        org.mockito.Mockito.verify(simService).runSimulation(
                org.mockito.Mockito.eq(eventId),
                org.mockito.Mockito.argThat(req -> req.virtualUserCount() == 200 && req.concurrency() == 100) // 20%
        );
        org.mockito.Mockito.verify(simService).runSimulation(
                org.mockito.Mockito.eq(eventId),
                org.mockito.Mockito.argThat(req -> req.virtualUserCount() == 250 && req.concurrency() == 100) // 25%
        );
        org.mockito.Mockito.verify(simService).runSimulation(
                org.mockito.Mockito.eq(eventId),
                org.mockito.Mockito.argThat(req -> req.virtualUserCount() == 300 && req.concurrency() == 100) // 30%
        );
    }
}
