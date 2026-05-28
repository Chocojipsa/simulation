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
                Duration.ZERO,
                Duration.ofMillis(100),
                Duration.ofMillis(300),
                Duration.ofMillis(700),
                Duration.ofMillis(1200)
        );
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(10, 10));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(20, 20));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(30, 30));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(40, 40));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(50, 50));
    }
}
