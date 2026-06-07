package com.timedeal.seatreservation.queue;

import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class LiveEventQueueScheduler {
    private static final Logger log = LoggerFactory.getLogger(LiveEventQueueScheduler.class);

    private final LiveEventService liveEventService;
    private final SimulationService simulationService;
    private final WaitingQueueService waitingQueueService;
    private int tickCounter = 0;

    public LiveEventQueueScheduler(
            LiveEventService liveEventService,
            SimulationService simulationService,
            WaitingQueueService waitingQueueService
    ) {
        this.liveEventService = liveEventService;
        this.simulationService = simulationService;
        this.waitingQueueService = waitingQueueService;
    }

    @Scheduled(fixedRate = 1000)
    public void processQueue() {
        try {
            LiveEventResponse activeEvent = liveEventService.activeEvent();
            if (activeEvent == null || !"OPEN".equals(activeEvent.status())) {
                return;
            }

            UUID eventId = activeEvent.eventId();
            SimulationSnapshot snapshot = simulationService.getSimulation(eventId);
            if (snapshot == null) {
                return;
            }

            long activeCount = snapshot.users().stream()
                    .filter(user -> user.status() == VirtualUserStatus.SELECTING_SEAT
                            || user.status() == VirtualUserStatus.SEAT_HELD
                            || user.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS)
                    .count();

            int maxActiveAdmissions = simulationService.getMaxActiveAdmissions();
            if (activeCount < maxActiveAdmissions) {
                int openSlots = maxActiveAdmissions - (int) activeCount;
                List<String> candidates = waitingQueueService.pickAdmissionCandidates(eventId.toString(), openSlots);
                for (String userIdStr : candidates) {
                    UUID userId = UUID.fromString(userIdStr);
                    simulationService.admitParticipant(eventId, userId);
                    log.info("Admitted user {} to event {}", userId, eventId);
                }
            }

            // Update remaining users position (throttled to every 3 seconds and limited to top 100 users)
            tickCounter++;
            if (tickCounter % 3 == 0) {
                List<String> remainingUserIds = waitingQueueService.queuedUserIds(eventId.toString());
                int limit = Math.min(remainingUserIds.size(), 100);
                for (int i = 0; i < limit; i++) {
                    String userIdStr = remainingUserIds.get(i);
                    UUID userId = UUID.fromString(userIdStr);
                    int position = i + 1;
                    double estimatedWait = position * 0.5;
                    String message = String.format(Locale.US, "{\"position\":%d,\"estimatedWaitSeconds\":%.1f}", position, estimatedWait);
                    simulationService.publishUserActivityDirectly(eventId, userId, "queue_position_update", message);
                }
            }
        } catch (Exception e) {
            log.error("Error processing queue in scheduler", e);
        }
    }
}
