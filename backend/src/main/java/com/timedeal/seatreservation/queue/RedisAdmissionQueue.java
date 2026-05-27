package com.timedeal.seatreservation.queue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!demo")
public class RedisAdmissionQueue implements AdmissionQueue {
    private final WaitingQueueService waitingQueueService;

    public RedisAdmissionQueue(WaitingQueueService waitingQueueService) {
        this.waitingQueueService = waitingQueueService;
    }

    @Override
    public void enter(String simulationId, String virtualUserId) {
        waitingQueueService.enterQueue(simulationId, virtualUserId);
    }

    @Override
    public List<String> pick(String simulationId, int limit) {
        return waitingQueueService.pickAdmissionCandidates(simulationId, limit);
    }

    @Override
    public void grant(String simulationId, String virtualUserId) {
        waitingQueueService.issueAdmissionToken(simulationId, virtualUserId);
        waitingQueueService.removeAdmissionCandidate(simulationId, virtualUserId);
    }
}
