package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class LiveEventAiStarter {
    private final SimulationService simulationService;
    private final int participantCount;
    private final int concurrency;
    private final BatchScheduler scheduler;

    @Autowired
    public LiveEventAiStarter(
            SimulationService simulationService,
            @Value("${live-event.ai-user-count:150}") int participantCount,
            @Value("${live-event.ai.concurrency:50}") int concurrency
    ) {
        this(
                simulationService,
                participantCount,
                concurrency,
                new ExecutorBatchScheduler(Executors.newSingleThreadScheduledExecutor())
        );
    }

    LiveEventAiStarter(SimulationService simulationService, int participantCount, int concurrency, BatchScheduler scheduler) {
        this.simulationService = simulationService;
        this.participantCount = participantCount;
        this.concurrency = concurrency;
        this.scheduler = scheduler;
    }

    public void start(UUID eventId) {
        AiBatchSchedule schedule = AiBatchSchedule.defaultSchedule(participantCount, concurrency);
        for (AiBatch batch : schedule.batches()) {
            scheduler.schedule(batch.delay(), () -> simulationService.runSimulation(
                    eventId,
                    new RunSimulationRequest(batch.participantCount(), batch.concurrency())
            ));
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

    interface BatchScheduler {
        void schedule(Duration delay, Runnable task);

        default void shutdown() {
        }
    }

    static final class ExecutorBatchScheduler implements BatchScheduler {
        private final ScheduledExecutorService executor;

        ExecutorBatchScheduler(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void schedule(Duration delay, Runnable task) {
            executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}
