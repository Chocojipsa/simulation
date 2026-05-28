package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveEventControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesActiveEventAndJoinEndpoint() throws Exception {
        LiveEventService service = mock(LiveEventService.class);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000700");
        UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(service.activeEvent()).thenReturn(new LiveEventResponse(
                eventId,
                "부산 콘서트 티켓팅",
                "OPEN",
                Instant.parse("2026-05-28T12:00:00Z"),
                120
        ));
        when(service.join(eventId, new JoinEventRequest("권"))).thenReturn(new JoinEventResponse(
                eventId,
                participantId,
                "권",
                "WAITING_ROOM",
                "api-test"
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service)).build();

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
    }
}
