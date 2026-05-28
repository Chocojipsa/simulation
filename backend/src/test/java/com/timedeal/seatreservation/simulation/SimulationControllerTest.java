package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.events.SimulationEventHub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                        ParticipantType.AI,
                        VirtualUserStatus.QUEUED,
                        null,
                        List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                        3,
                        2,
                        0,
                        null
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

    @Test
    void runSimulationStartsTrafficGenerator() throws Exception {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(simulationService.runSimulation(eq(simulationId), any())).thenReturn(new RunSimulationResponse(
                simulationId,
                150,
                "RUNNING",
                "api-test"
        ));

        mvc.perform(post("/api/simulations/{simulationId}/run", simulationId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"virtualUserCount\":150,\"concurrency\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.virtualUserCount").value(150))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.handledBy").value("api-test"));
    }

    @Test
    void virtualUserEnterQueueReturnsHandlingServer() throws Exception {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000105");
        when(simulationService.enterQueue(eq(simulationId), eq(userId))).thenReturn(new VirtualUserCommandResponse(
                simulationId,
                userId,
                "QUEUED",
                "api-test",
                "대기열에 진입했습니다.",
                null
        ));

        mvc.perform(post("/api/simulations/{simulationId}/users/{userId}/queue", simulationId, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.virtualUserId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.handledBy").value("api-test"))
                .andExpect(jsonPath("$.message").value("대기열에 진입했습니다."))
                .andExpect(jsonPath("$.selectedSeatLabel").doesNotExist());
    }

    @Test
    void virtualUserSeatAttemptReturnsHandlingServerAndSelectedSeat() throws Exception {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000106");
        when(simulationService.attemptSeat(eq(simulationId), eq(userId))).thenReturn(new VirtualUserCommandResponse(
                simulationId,
                userId,
                "PAYMENT_REQUESTED",
                "api-test",
                "A-1 좌석을 선택했습니다. 결제를 요청했습니다.",
                "A-1"
        ));

        mvc.perform(post("/api/simulations/{simulationId}/users/{userId}/seat-attempt", simulationId, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.virtualUserId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_REQUESTED"))
                .andExpect(jsonPath("$.handledBy").value("api-test"))
                .andExpect(jsonPath("$.message").value("A-1 좌석을 선택했습니다. 결제를 요청했습니다."))
                .andExpect(jsonPath("$.selectedSeatLabel").value("A-1"));
    }
}
