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
    void oneTickAdmitsQueuedUsersIntoSeatSelection() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        stateStore.create(simulationId, 40);

        SimulationSnapshot snapshot = runner.tick(simulationId);

        assertThat(snapshot.metrics().queueSize()).isEqualTo(10);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.SELECTING_SEAT)
                .hasSize(30);
    }

    @Test
    void usersEventuallyReserveWithoutOverbooking() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        stateStore.create(simulationId, 30);

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        for (int index = 0; index < 80 && snapshot.running(); index++) {
            snapshot = runner.tick(simulationId);
        }

        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.metrics().reservedCount()).isEqualTo(30);
        assertThat(snapshot.metrics().reservedCount()).isLessThanOrEqualTo(120);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.SEAT_HELD)
                .isEmpty();
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS)
                .isEmpty();
    }

    @Test
    void concurrentSeatSelectionRecordsTriedSeatAndKeepsConflictedUsersRetrying() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000203");
        stateStore.create(simulationId, 90);

        runner.tick(simulationId);
        SimulationSnapshot snapshot = runner.tick(simulationId);

        assertThat(snapshot.metrics().heldCount()).isGreaterThan(0);
        assertThat(snapshot.metrics().failedCount()).isGreaterThan(0);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.SELECTING_SEAT)
                .anySatisfy(user -> {
                    assertThat(user.timeline())
                            .anySatisfy(entry -> assertThat(entry.message()).contains("좌석을 선택했습니다."));
                    assertThat(user.timeline())
                            .anySatisfy(entry -> assertThat(entry.message()).contains("이미 선택된 좌석입니다."));
                });
    }

    @Test
    void successfulReservationsOpenAdmissionSlotsForQueuedUsers() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000204");
        stateStore.create(simulationId, 45);

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        for (int index = 0; index < 8; index++) {
            snapshot = runner.tick(simulationId);
        }

        assertThat(snapshot.metrics().queueSize()).isLessThan(15);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.RESERVED)
                .isNotEmpty();
    }
}
