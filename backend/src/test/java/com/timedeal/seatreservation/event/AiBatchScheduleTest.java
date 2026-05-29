package com.timedeal.seatreservation.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiBatchScheduleTest {
    @Test
    void splitsParticipantsIntoStaggeredBatches() {
        AiBatchSchedule schedule = AiBatchSchedule.defaultSchedule(150, 50);

        assertThat(schedule.batches()).containsExactly(
                new AiBatch(10, 10, Duration.ofMillis(500)),
                new AiBatch(15, 15, Duration.ofMillis(1000)),
                new AiBatch(20, 20, Duration.ofMillis(1500)),
                new AiBatch(25, 25, Duration.ofMillis(2000)),
                new AiBatch(30, 30, Duration.ofMillis(2500)),
                new AiBatch(50, 50, Duration.ofMillis(3000))
        );
    }
}
