package com.timedeal.seatreservation.simulation.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.SimulationStateGateway;
import com.timedeal.seatreservation.simulation.TimelineEntry;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Component
@Profile("!demo")
public class RedisSimulationStateStore implements SimulationStateGateway {
    private static final Duration TTL = Duration.ofHours(3);
    private static final int ROW_COUNT = 10;
    private static final int SEATS_PER_ROW = 12;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSimulationStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SimulationSnapshot create(UUID simulationId, int virtualUserCount) {
        SimulationSnapshot snapshot = new SimulationSnapshot(
                simulationId,
                createSeats(),
                createUsers(virtualUserCount),
                new SimulationMetrics(virtualUserCount, 0, 0, 0, 0, 0),
                List.of(),
                false
        );
        save(snapshot);
        return snapshot;
    }

    @Override
    public SimulationSnapshot snapshot(UUID simulationId) {
        SimulationRedisKeys keys = new SimulationRedisKeys(simulationId);
        String json = redisTemplate.opsForValue().get(keys.snapshot());
        if (json == null) {
            throw new NoSuchElementException("Simulation not found: " + simulationId);
        }
        try {
            return objectMapper.readValue(json, SimulationSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read simulation snapshot: " + simulationId, exception);
        }
    }

    @Override
    public SimulationSnapshot markRunning(UUID simulationId) {
        SimulationSnapshot current = snapshot(simulationId);
        SimulationSnapshot updated = new SimulationSnapshot(
                current.simulationId(),
                current.seats(),
                current.users(),
                current.metrics(),
                current.serverStats(),
                true
        );
        save(updated);
        return updated;
    }

    private void save(SimulationSnapshot snapshot) {
        SimulationRedisKeys keys = new SimulationRedisKeys(snapshot.simulationId());
        try {
            redisTemplate.opsForValue().set(keys.snapshot(), objectMapper.writeValueAsString(snapshot), TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save simulation snapshot: " + snapshot.simulationId(), exception);
        }
    }

    private List<SeatView> createSeats() {
        List<SeatView> seats = new ArrayList<>(ROW_COUNT * SEATS_PER_ROW);
        long id = 1L;
        for (int row = 0; row < ROW_COUNT; row++) {
            String rowName = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= SEATS_PER_ROW; number++) {
                seats.add(new SeatView(id, rowName + "-" + number, SeatStatus.AVAILABLE));
                id++;
            }
        }
        return seats;
    }

    private List<VirtualUserView> createUsers(int virtualUserCount) {
        List<VirtualUserView> users = new ArrayList<>(virtualUserCount);
        for (int index = 1; index <= virtualUserCount; index++) {
            users.add(new VirtualUserView(
                    UUID.randomUUID(),
                    "사용자 " + index,
                    VirtualUserStatus.QUEUED,
                    null,
                    List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                    0,
                    0
            ));
        }
        return users;
    }
}
