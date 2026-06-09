package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.events.SimulationEventHub;
import com.timedeal.seatreservation.simulation.RunSimulationResponse;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import com.timedeal.seatreservation.simulation.TimelineEntry;

@RestController
@RequestMapping("/api/events")
public class LiveEventController {
    private final LiveEventService liveEventService;
    private final SimulationEventHub simulationEventHub;

    public LiveEventController(LiveEventService liveEventService, SimulationEventHub simulationEventHub) {
        this.liveEventService = liveEventService;
        this.simulationEventHub = simulationEventHub;
    }

    @GetMapping("/active")
    public LiveEventResponse activeEvent() {
        return liveEventService.activeEvent();
    }

    @GetMapping("/{eventId}/snapshot")
    public LiveEventSnapshot snapshot(
            @PathVariable UUID eventId,
            @RequestParam(required = false) UUID participantId
    ) {
        return liveEventService.snapshot(eventId, participantId);
    }

    @PostMapping("/{eventId}/start")
    public LiveEventResponse startEvent(
            @PathVariable UUID eventId,
            @RequestBody(required = false) StartEventRequest request
    ) {
        return liveEventService.startEvent(eventId, request);
    }

    @PostMapping("/{eventId}/reset")
    public LiveEventResponse resetEvent(@PathVariable UUID eventId) {
        return liveEventService.resetEvent(eventId);
    }

    @PostMapping("/{eventId}/participants")
    public JoinEventResponse join(
            @PathVariable UUID eventId,
            @Valid @RequestBody JoinEventRequest request
    ) {
        return liveEventService.join(eventId, request);
    }

    @PostMapping("/{eventId}/participants/{participantId}/queue")
    public VirtualUserCommandResponse enterQueue(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        return liveEventService.enterQueue(eventId, participantId);
    }

    @PostMapping("/{eventId}/participants/{participantId}/seats/{seatId}/hold")
    public SeatHoldResponse holdSeat(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId,
            @PathVariable long seatId
    ) {
        return liveEventService.holdSeat(eventId, participantId, seatId);
    }

    @PostMapping("/{eventId}/participants/{participantId}/payment-confirm")
    public PaymentConfirmResponse confirmPayment(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        return liveEventService.confirmPayment(eventId, participantId);
    }

    @PostMapping("/{eventId}/participants/{participantId}/seats/release")
    public void releaseSeat(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        liveEventService.releaseSeat(eventId, participantId);
    }

    @PostMapping("/{eventId}/ai/start")
    public RunSimulationResponse startAiParticipants(
            @PathVariable UUID eventId,
            @Valid @RequestBody StartAiParticipantsRequest request
    ) {
        return liveEventService.startAiParticipants(eventId, request);
    }
 
    @GetMapping(path = "/{eventId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventStream(@PathVariable UUID eventId) {
        return simulationEventHub.open(eventId);
    }
 
    @GetMapping(path = "/{eventId}/participants/{participantId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter participantStream(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        return simulationEventHub.openUserStream(participantId);
    }

    @PostMapping("/{eventId}/participants/{participantId}/name")
    public VirtualUserCommandResponse updateParticipantName(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId,
            @RequestBody UpdateNameRequest request
    ) {
        liveEventService.updateParticipantName(eventId, participantId, request.displayName());
        return new VirtualUserCommandResponse(eventId, participantId, "SUCCESS", "api", "이름이 변경되었습니다.", null);
    }

    @GetMapping("/{eventId}/participants/{participantId}/timeline")
    public List<TimelineEntry> getParticipantTimeline(
            @PathVariable UUID eventId,
            @PathVariable UUID participantId
    ) {
        return liveEventService.getParticipantTimeline(eventId, participantId);
    }
}
