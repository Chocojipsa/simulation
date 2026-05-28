package com.timedeal.seatreservation.event;

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

    @PostMapping("/{eventId}/participants")
    public JoinEventResponse join(
            @PathVariable UUID eventId,
            @Valid @RequestBody JoinEventRequest request
    ) {
        return liveEventService.join(eventId, request);
    }
}
