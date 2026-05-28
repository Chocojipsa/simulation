package com.timedeal.seatreservation.simulation.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisSimulationStateStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RedisSimulationStateStore store = new RedisSimulationStateStore(redis, objectMapper);

    @Test
    void createsDeterministicVirtualUserIdsThatMatchTrafficGenerator() {
        when(redis.opsForValue()).thenReturn(values);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        SimulationSnapshot snapshot = store.create(simulationId, 2);

        UUID firstExpected = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        UUID secondExpected = UUID.nameUUIDFromBytes((simulationId + ":2").getBytes(StandardCharsets.UTF_8));
        assertThat(snapshot.users()).extracting(user -> user.id()).containsExactly(firstExpected, secondExpected);
    }

    @Test
    void registerQueueEntryAddsUserTimelineAndServerStats() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = store.create(simulationId, 1);

        when(values.get("simulation:00000000-0000-0000-0000-000000000021:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.registerQueueEntry(simulationId, userId, "api-a");

        assertThat(updated.users().get(0).status()).isEqualTo(VirtualUserStatus.QUEUED);
        assertThat(updated.users().get(0).timeline()).anyMatch(entry -> entry.message().equals("대기열에 진입했습니다."));
        assertThat(updated.serverStats()).anyMatch(stats -> stats.serverId().equals("api-a") && stats.requestCount() == 1);
    }

    @Test
    void registerQueueEntryRetriesWhenSnapshotLockIsBusy() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE, Boolean.TRUE);

        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = store.create(simulationId, 1);

        when(values.get("simulation:00000000-0000-0000-0000-000000000022:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));

        SimulationSnapshot updated = store.registerQueueEntry(simulationId, userId, "api-a");

        assertThat(updated.serverStats()).anyMatch(stats -> stats.serverId().equals("api-a"));
    }
}
