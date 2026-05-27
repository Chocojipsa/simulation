package com.timedeal.seatreservation.queue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitingQueueServiceTest {
    @Test
    void usersAreAdmittedByQueueScoreOrder() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneOffset.UTC);
        WaitingQueueService service = new WaitingQueueService(redis, clock);

        when(redis.opsForValue()).thenReturn(values);

        List<String> admitted = service.pickAdmissionCandidates(List.of("user-1", "user-2", "user-3"), 2);

        assertThat(admitted).containsExactly("user-1", "user-2");
    }
}
