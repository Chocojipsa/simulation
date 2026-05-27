package com.timedeal.seatreservation.queue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAdmissionQueueTest {
    private final WaitingQueueService waitingQueueService = mock(WaitingQueueService.class);
    private final RedisAdmissionQueue queue = new RedisAdmissionQueue(waitingQueueService);

    @Test
    void delegatesEnterAndPickToWaitingQueueService() {
        when(waitingQueueService.pickAdmissionCandidates("sim-1", 2))
                .thenReturn(List.of("user-1", "user-2"));

        queue.enter("sim-1", "user-1");
        List<String> candidates = queue.pick("sim-1", 2);

        verify(waitingQueueService).enterQueue("sim-1", "user-1");
        verify(waitingQueueService).pickAdmissionCandidates("sim-1", 2);
        assertThat(candidates).containsExactly("user-1", "user-2");
    }

    @Test
    void grantIssuesAdmissionTokenAndRemovesCandidate() {
        queue.grant("sim-1", "user-1");

        verify(waitingQueueService).issueAdmissionToken("sim-1", "user-1");
        verify(waitingQueueService).removeAdmissionCandidate("sim-1", "user-1");
    }
}
