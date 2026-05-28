package com.timedeal.seatreservation.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiBatchScheduleTest {
    @Test
    void splitsParticipantsIntoStaggeredBatches() {
        AiBatchSchedule schedule = AiBatchSchedule.defaultSchedule(150, 50);

        assertThat(schedule.batches()).containsExactly(
                new AiBatch(10, 10, Duration.ZERO),
                new AiBatch(20, 20, Duration.ofMillis(100)),
                new AiBatch(30, 30, Duration.ofMillis(300)),
                new AiBatch(40, 40, Duration.ofMillis(700)),
                new AiBatch(50, 50, Duration.ofMillis(1200))
        );
    }
}
