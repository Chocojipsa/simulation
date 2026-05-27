package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.events.SimulationEventHub;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRunnerTest {
    private final SimulationStateStore stateStore = new SimulationStateStore();
    private final SimulationRunner runner = new SimulationRunner(stateStore, new SimulationEventHub());

    @Test
    void oneTickAdmitsQueuedUsers() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        stateStore.create(simulationId, 25);

        SimulationSnapshot snapshot = runner.tick(simulationId);

        assertThat(snapshot.metrics().queueSize()).isEqualTo(15);
        assertThat(snapshot.metrics().admittedCount()).isEqualTo(10);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.ADMITTED)
                .hasSize(10);
    }

    @Test
    void usersEventuallyReserveOrFailPaymentWithoutOverbooking() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        stateStore.create(simulationId, 30);

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        for (int index = 0; index < 20 && snapshot.running(); index++) {
            snapshot = runner.tick(simulationId);
        }

        assertThat(snapshot.metrics().reservedCount()).isGreaterThan(0);
        assertThat(snapshot.metrics().failedCount()).isGreaterThan(0);
        assertThat(snapshot.metrics().reservedCount()).isLessThanOrEqualTo(120);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.SEAT_HELD)
                .isEmpty();
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS)
                .isEmpty();
    }
}
