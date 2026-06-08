package com.timedeal.seatreservation.queue;

import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class LiveEventQueueScheduler {
    private static final Logger log = LoggerFactory.getLogger(LiveEventQueueScheduler.class);
    private static final String LOCK_KEY = "lock:scheduler:queue-process";

    private final LiveEventService liveEventService;
    private final SimulationService simulationService;
    private final WaitingQueueService waitingQueueService;
    private final StringRedisTemplate redisTemplate;

    public LiveEventQueueScheduler(
            LiveEventService liveEventService,
            SimulationService simulationService,
            WaitingQueueService waitingQueueService,
            StringRedisTemplate redisTemplate
    ) {
        this.liveEventService = liveEventService;
        this.simulationService = simulationService;
        this.waitingQueueService = waitingQueueService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRate = 1000)
    public void processQueue() {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "locked", Duration.ofMillis(900));
        if (acquired == null || !acquired) {
            return;
        }

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

            // Update remaining users position (limited to top 100 users) in a batch
            List<String> remainingUserIds = waitingQueueService.queuedUserIds(eventId.toString());
            int limit = Math.min(remainingUserIds.size(), 100);
            List<UserQueuePosition> batchPositions = new java.util.ArrayList<>();
            for (int i = 0; i < limit; i++) {
                String userIdStr = remainingUserIds.get(i);
                UUID userId = UUID.fromString(userIdStr);
                int position = i + 1;
                double estimatedWait = position * 0.5;
                batchPositions.add(new UserQueuePosition(userId, position, estimatedWait));
            }
            if (!batchPositions.isEmpty()) {
                simulationService.publishQueuePositionsBatch(new QueuePositionsBatchEvent(eventId, batchPositions));
            }
        } catch (Exception e) {
            log.error("Error processing queue in scheduler", e);
        }
    }
}
