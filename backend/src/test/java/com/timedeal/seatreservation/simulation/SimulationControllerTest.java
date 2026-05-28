package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.events.SimulationEventHub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    SimulationService simulationService;

    @MockBean
    SimulationEventHub simulationEventHub;

    @Test
    void startSimulationReturnsKoreanMessageAndSimulationId() throws Exception {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(simulationService.createSimulation(any())).thenReturn(new SimulationResponse(
                simulationId,
                "시뮬레이션이 생성되었습니다.",
                100,
                "api-test"
        ));

        mvc.perform(post("/api/simulations")
                        .contentType(APPLICATION_JSON)
                        .content("{\"virtualUserCount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.message").value("시뮬레이션이 생성되었습니다."))
                .andExpect(jsonPath("$.virtualUserCount").value(100))
                .andExpect(jsonPath("$.handledBy").value("api-test"));
    }

    @Test
    void getSimulationReturnsSnapshotWithUserAttemptCounts() throws Exception {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        SimulationSnapshot snapshot = new SimulationSnapshot(
                simulationId,
                List.of(new SeatView(1L, "A-1", SeatStatus.AVAILABLE)),
                List.of(new VirtualUserView(
                        userId,
                        "사용자 1",
                        VirtualUserStatus.QUEUED,
                        null,
                        List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                        3,
                        2
                )),
                new SimulationMetrics(1, 0, 0, 0, 0, 0),
                List.of(new ServerStatsView("api-test", 1, 0, 0)),
                true
        );
        when(simulationService.getSimulation(simulationId)).thenReturn(snapshot);

        mvc.perform(get("/api/simulations/{simulationId}", simulationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.seats[0].label").value("A-1"))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.users[0].displayName").value("사용자 1"))
                .andExpect(jsonPath("$.users[0].seatAttemptCount").value(3))
                .andExpect(jsonPath("$.users[0].conflictCount").value(2))
                .andExpect(jsonPath("$.metrics.queueSize").value(1))
                .andExpect(jsonPath("$.serverStats[0].serverId").value("api-test"))
                .andExpect(jsonPath("$.running").value(true));
    }
}
