package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.simulation.RunSimulationResponse;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class LiveEventController {
    private final LiveEventService liveEventService;

    public LiveEventController(LiveEventService liveEventService) {
        this.liveEventService = liveEventService;
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
    public LiveEventResponse startEvent(@PathVariable UUID eventId) {
        return liveEventService.startEvent(eventId);
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

    @PostMapping("/{eventId}/ai/start")
    public RunSimulationResponse startAiParticipants(
            @PathVariable UUID eventId,
            @Valid @RequestBody StartAiParticipantsRequest request
    ) {
        return liveEventService.startAiParticipants(eventId, request);
    }
}
