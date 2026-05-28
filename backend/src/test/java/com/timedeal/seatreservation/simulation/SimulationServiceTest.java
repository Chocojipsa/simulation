package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {
    private final SimulationStateStore stateStore = new SimulationStateStore();
    private final SimulationService simulationService = new SimulationService(stateStore);

    @Test
    void createSimulationCreatesInitialSnapshot() {
        SimulationResponse response = simulationService.createSimulation(new CreateSimulationRequest(25));

        SimulationSnapshot snapshot = simulationService.getSimulation(response.simulationId());

        assertThat(response.virtualUserCount()).isEqualTo(25);
        assertThat(response.message()).isEqualTo("시뮬레이션이 생성되었습니다.");
        assertThat(response.handledBy()).isEqualTo("api-test");
        assertThat(snapshot.simulationId()).isEqualTo(response.simulationId());
        assertThat(snapshot.seats()).hasSize(120);
        assertThat(snapshot.users()).hasSize(25);
        assertThat(snapshot.seats()).allMatch(seat -> seat.status() == SeatStatus.AVAILABLE);
        assertThat(snapshot.users()).allMatch(user -> user.status() == VirtualUserStatus.QUEUED);
        assertThat(snapshot.metrics().queueSize()).isEqualTo(25);
        assertThat(snapshot.running()).isTrue();
    }
}
