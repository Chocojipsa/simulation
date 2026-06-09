package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.timedeal.seatreservation.simulation.TimelineEntry;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveEventControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void startsAndResetsLiveEvent() throws Exception {
        LiveEventService service = mock(LiveEventService.class);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(service.startEvent(eventId, null)).thenReturn(new LiveEventResponse(
                eventId,
                "Busan Ticketing",
                "COUNTDOWN",
                1,
                Instant.parse("2026-05-28T12:01:00Z"),
                Instant.parse("2026-05-28T12:06:00Z"),
                120
        ));
        when(service.resetEvent(eventId)).thenReturn(new LiveEventResponse(
                eventId,
                "Busan Ticketing",
                "READY",
                2,
                null,
                null,
                120
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service, new SimulationEventHub(null, null))).build();

        mvc.perform(post("/api/events/{eventId}/start", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COUNTDOWN")))
                .andExpect(jsonPath("$.generation", is(1)));

        mvc.perform(post("/api/events/{eventId}/reset", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.generation", is(2)));
    }

    @Test
    void exposesActiveEventAndJoinEndpoint() throws Exception {
        LiveEventService service = mock(LiveEventService.class);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000700");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(service.activeEvent()).thenReturn(new LiveEventResponse(
                eventId,
                "부산 콘서트 티켓팅",
                "OPEN",
                1,
                Instant.parse("2026-05-28T12:00:00Z"),
                Instant.parse("2026-05-28T12:05:00Z"),
                120
        ));
        when(service.join(eventId, new JoinEventRequest("권"))).thenReturn(new JoinEventResponse(
                eventId,
                participantId,
                "권",
                "WAITING_ROOM",
                "api-test"
        ));
        when(service.enterQueue(eventId, participantId)).thenReturn(new VirtualUserCommandResponse(
                eventId,
                participantId,
                "QUEUED",
                "api-test",
                "대기열에 진입했습니다.",
                null
        ));
        when(service.holdSeat(eventId, participantId, 1L)).thenReturn(new SeatHoldResponse(
                eventId,
                participantId,
                1L,
                "PAYMENT_PENDING",
                "A-1 좌석을 선점했습니다. 결제를 확인해 주세요.",
                "A-1",
                "api-test"
        ));
        when(service.confirmPayment(eventId, participantId)).thenReturn(new PaymentConfirmResponse(
                eventId,
                participantId,
                "PAYMENT_REQUESTED",
                "결제 확인 요청을 보냈습니다.",
                "api-test"
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service, new SimulationEventHub(null, null))).build();

        String activeJson = mvc.perform(get("/api/events/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("부산 콘서트 티켓팅")))
                .andExpect(jsonPath("$.eventId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventIdText = objectMapper.readTree(activeJson).get("eventId").asText();

        mvc.perform(post("/api/events/{eventId}/participants", eventIdText)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"권\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventIdText)))
                .andExpect(jsonPath("$.displayName", is("권")))
                .andExpect(jsonPath("$.status", is("WAITING_ROOM")));

        mvc.perform(post("/api/events/{eventId}/participants/{participantId}/queue", eventId, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("QUEUED")));

        mvc.perform(post("/api/events/{eventId}/participants/{participantId}/seats/1/hold", eventId, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAYMENT_PENDING")))
                .andExpect(jsonPath("$.selectedSeatLabel", is("A-1")));

        mvc.perform(post("/api/events/{eventId}/participants/{participantId}/payment-confirm", eventId, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAYMENT_REQUESTED")));
    }

    @Test
    void verifiesSeatReleaseApi() throws Exception {
        LiveEventService service = mock(LiveEventService.class);
        UUID eventId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service, new SimulationEventHub(null, null))).build();

        mvc.perform(post("/api/events/{eventId}/participants/{participantId}/seats/release", eventId, participantId))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(service).releaseSeat(eventId, participantId);
    }

    @Test
    void startsLiveEventWithConfig() throws Exception {
        LiveEventService service = mock(LiveEventService.class);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        StartEventRequest request = new StartEventRequest(300, 80, "FAST");
        
        when(service.startEvent(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.any(StartEventRequest.class)))
                .thenReturn(new LiveEventResponse(eventId, "Test Event", "COUNTDOWN", 1, Instant.now(), Instant.now().plusSeconds(600), 120));
                
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service, new SimulationEventHub(null, null))).build();
        
        mvc.perform(post("/api/events/{eventId}/start", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COUNTDOWN")));
    }

    @Test
    void getParticipantTimelineReturnsList() throws Exception {
        LiveEventService service = mock(LiveEventService.class);
        UUID eventId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        when(service.getParticipantTimeline(eq(eventId), eq(participantId)))
                .thenReturn(List.of(new TimelineEntry("THINKING", "탐색 중")));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service, new SimulationEventHub(null, null))).build();

        mvc.perform(get("/api/events/" + eventId + "/participants/" + participantId + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("THINKING"))
                .andExpect(jsonPath("$[0].message").value("탐색 중"));
    }
}
