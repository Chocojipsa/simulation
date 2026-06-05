package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.queue.InMemoryAdmissionQueue;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRunnerTest {
    private final SimulationStateStore stateStore = new SimulationStateStore();
    private final InMemoryAdmissionQueue admissionQueue = new InMemoryAdmissionQueue();
    private final SimulationRunner runner = new SimulationRunner(stateStore, new SimulationEventHub(null, null), null, admissionQueue);

    @Test
    void oneTickAdmitsQueuedUsersIntoSeatSelection() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        stateStore.create(simulationId, 40);
        stateStore.markRunning(simulationId);

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
        stateStore.markRunning(simulationId);

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
        stateStore.markRunning(simulationId);

        runner.tick(simulationId);
        SimulationSnapshot snapshot = runner.tick(simulationId);

        assertThat(snapshot.metrics().heldCount()).isGreaterThan(0);
        assertThat(snapshot.metrics().failedCount()).isGreaterThan(0);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.SELECTING_SEAT)
                .anySatisfy(user -> {
                    assertThat(user.seatAttemptCount()).isGreaterThan(0);
                    assertThat(user.conflictCount()).isGreaterThan(0);
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
        stateStore.markRunning(simulationId);

        SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
        for (int index = 0; index < 8; index++) {
            snapshot = runner.tick(simulationId);
        }

        assertThat(snapshot.metrics().queueSize()).isLessThan(15);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.RESERVED)
                .isNotEmpty();
    }

    @Test
    void soldOutUsersStillCountAsSeatAttempt() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000205");
        stateStore.create(simulationId, 1);
        stateStore.markRunning(simulationId);
        SimulationStateStore.MutableSimulationState state = stateStore.state(simulationId);
        state.seats.forEach(seat -> seat.status = SeatStatus.RESERVED);
        state.users.get(0).status = VirtualUserStatus.SELECTING_SEAT;

        SimulationSnapshot snapshot = runner.tick(simulationId);

        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.metrics().reservedCount()).isEqualTo(120);
        assertThat(snapshot.users())
                .filteredOn(user -> user.status() == VirtualUserStatus.FAILED)
                .allSatisfy(user -> {
                    assertThat(user.seatAttemptCount()).isGreaterThan(0);
                    assertThat(user.timeline())
                            .anySatisfy(entry -> assertThat(entry.message()).contains("선택 가능한 좌석이 없습니다."));
                });
    }
}
