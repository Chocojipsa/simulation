package com.timedeal.seatreservation.queue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAdmissionQueueTest {
    private final InMemoryAdmissionQueue queue = new InMemoryAdmissionQueue();

    @Test
    void picksUsersInEnteredOrderAndRemovesGrantedUsers() {
        queue.enter("sim-1", "user-1");
        queue.enter("sim-1", "user-2");
        queue.enter("sim-1", "user-3");

        List<String> firstPick = queue.pick("sim-1", 2);

        assertThat(firstPick).containsExactly("user-1", "user-2");

        queue.grant("sim-1", "user-1");

        assertThat(queue.pick("sim-1", 2)).containsExactly("user-2", "user-3");
    }

    @Test
    void pickReturnsEmptyForNonPositiveLimit() {
        queue.enter("sim-1", "user-1");

        assertThat(queue.pick("sim-1", 0)).isEmpty();
    }
}
