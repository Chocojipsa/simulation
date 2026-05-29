package com.timedeal.seatreservation.queue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class WaitingQueueServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneOffset.UTC);
    private final WaitingQueueService service = new WaitingQueueService(redis, clock);

    @Test
    void usersAreAdmittedByQueueScoreOrder() {
        ZSetOperations<String, String> zSets = mock(ZSetOperations.class);

        when(redis.opsForZSet()).thenReturn(zSets);
        when(zSets.range("simulation:sim-1:queue", 0, 1))
                .thenReturn(new LinkedHashSet<>(List.of("user-1", "user-2")));

        List<String> admitted = service.pickAdmissionCandidates("sim-1", 2);

        assertThat(admitted).containsExactly("user-1", "user-2");
        verify(zSets).range("simulation:sim-1:queue", 0, 1);
    }

    @Test
    void waitingQueueServiceIsRegisteredAsSpringService() {
        assertThat(WaitingQueueService.class).hasAnnotation(Service.class);
    }

    @Test
    void enterQueueAddsUsersWithMonotonicSequenceScore() {
        ZSetOperations<String, String> zSets = mock(ZSetOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);

        when(redis.opsForZSet()).thenReturn(zSets);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("simulation:sim-1:queue-sequence")).thenReturn(1L, 2L);

        service.enterQueue("sim-1", "user-1");
        service.enterQueue("sim-1", "user-2");

        verify(zSets).add("simulation:sim-1:queue", "user-1", 1D);
        verify(zSets).add("simulation:sim-1:queue", "user-2", 2D);
    }

    @Test
    void issueAdmissionTokenStoresGrantedTokenWithTtl() {
        ValueOperations<String, String> values = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(values);

        service.issueAdmissionToken("sim-1", "user-1");

        verify(values).set("simulation:sim-1:admission:user-1", "granted", Duration.ofSeconds(60));
    }

    @Test
    void removeAdmissionCandidateRemovesUserFromQueue() {
        ZSetOperations<String, String> zSets = mock(ZSetOperations.class);

        when(redis.opsForZSet()).thenReturn(zSets);

        service.removeAdmissionCandidate("sim-1", "user-1");

        verify(zSets).remove("simulation:sim-1:queue", "user-1");
    }

    @Test
    void hasAdmissionTokenReturnsTrueOnlyWhenRedisReturnsTrue() {
        when(redis.hasKey("simulation:sim-1:admission:user-1"))
                .thenReturn(Boolean.TRUE, Boolean.FALSE, null);

        assertThat(service.hasAdmissionToken("sim-1", "user-1")).isTrue();
        assertThat(service.hasAdmissionToken("sim-1", "user-1")).isFalse();
        assertThat(service.hasAdmissionToken("sim-1", "user-1")).isFalse();
    }

    @Test
    void pickAdmissionCandidatesReturnsEmptyForNonPositiveLimitWithoutQueryingRedisRange() {
        List<String> admitted = service.pickAdmissionCandidates("sim-1", 0);

        assertThat(admitted).isEmpty();
        verify(redis, never()).opsForZSet();
    }

    @Test
    void pickAdmissionCandidatesReturnsEmptyWhenRedisRangeReturnsNull() {
        ZSetOperations<String, String> zSets = mock(ZSetOperations.class);

        when(redis.opsForZSet()).thenReturn(zSets);
        when(zSets.range("simulation:sim-1:queue", 0, 1)).thenReturn(null);

        List<String> admitted = service.pickAdmissionCandidates("sim-1", 2);

        assertThat(admitted).isEmpty();
        verify(zSets).range("simulation:sim-1:queue", 0, 1);
    }

    @Test
    void queuedUserIdsReturnsRedisQueueOrder() {
        ZSetOperations<String, String> zSets = mock(ZSetOperations.class);

        when(redis.opsForZSet()).thenReturn(zSets);
        when(zSets.range("simulation:sim-1:queue", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("user-2", "user-1")));

        List<String> queued = service.queuedUserIds("sim-1");

        assertThat(queued).containsExactly("user-2", "user-1");
    }

    @Test
    void clearQueueRemovesQueueAndSequenceKeys() {
        service.clearQueue("sim-1");

        verify(redis).delete("simulation:sim-1:queue");
        verify(redis).delete("simulation:sim-1:queue-sequence");
    }
}
