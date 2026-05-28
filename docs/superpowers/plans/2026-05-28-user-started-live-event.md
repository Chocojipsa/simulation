# User-Started Live Event Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a visitor-started ticketing lifecycle with 60 second countdown, 5 minute open window, visible queue, staggered AI entry, and single-seat hold protection.

**Architecture:** Store event lifecycle metadata outside API process memory so api-a/api-b agree through Redis/local state store. Keep PostgreSQL as seat-hold authority, Redis as live read model/queue state, Kafka as payment boundary, and traffic-generator as the AI runner. Gate every event command by lifecycle status and surface normal race conditions as domain messages instead of 500s.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Data Redis, Spring Kafka, PostgreSQL/Flyway, React 18, Vite, TypeScript, Vitest, Docker Compose, nginx.

---

## File Structure

Backend lifecycle contracts:

- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStatus.java`: `READY`, `COUNTDOWN`, `OPEN`, `ENDED`.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventMetadata.java`: shared event lifecycle metadata with derived status helper.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStateStore.java`: lifecycle store interface.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/InMemoryLiveEventStateStore.java`: demo/test implementation.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/RedisLiveEventStateStore.java`: local/prod Redis implementation with lock.
- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventResponse.java`: add `generation`, `endsAt`.
- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventSnapshot.java`: add `generation`, `endsAt`.
- Modify `backend/src/main/resources/application-local.yml`: add lifecycle and AI defaults.
- Modify `backend/src/main/resources/application-prod.yml`: add lifecycle and AI defaults.

Backend lifecycle service/API:

- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`: use lifecycle store, start/reset commands, state-aware snapshot.
- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`: add `POST /start`, `POST /reset`.
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`: add `resetSimulation(UUID, int)` and single-seat hold guard.
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationInventoryService.java`: add database reset for a simulation id.

Backend AI staggering:

- Create `backend/src/main/java/com/timedeal/seatreservation/event/AiBatch.java`: one AI batch definition.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/AiBatchSchedule.java`: deterministic batch schedule generator.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`: schedules AI batches after event opens.

Frontend:

- Modify `frontend/src/api/liveEventApi.ts`: add lifecycle fields and `startEvent`, `resetEvent`.
- Modify `frontend/src/hooks/useLiveEventRoom.ts`: expose `start`, `reset`, command message, lifecycle-aware actions.
- Modify `frontend/src/domain/liveEventSelectors.ts`: add status labels, timers, queue rank, seat click rules.
- Modify `frontend/src/components/EventHeader.tsx`: countdown/open remaining time.
- Modify `frontend/src/components/MyTicketPanel.tsx`: lifecycle-aware button text/state.
- Modify `frontend/src/components/EventActivityPanel.tsx`: show `이벤트 시작하기`/`새 이벤트 시작`, hide AI manual button.
- Create `frontend/src/components/QueuePanel.tsx`: queue size, my queue state, approximate rank.
- Modify `frontend/src/components/SeatMap.tsx`: disabled reason and single-hold UX.
- Modify `frontend/src/App.tsx`: wire queue panel and new commands.
- Modify `frontend/src/styles.css`: queue panel, timer, disabled seat states.

Tests:

- Create `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventStateStoreTest.java`.
- Modify `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventContractTest.java`.
- Modify `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`.
- Modify `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`.
- Modify `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`.
- Modify `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationInventoryServiceTest.java`.
- Create `backend/src/test/java/com/timedeal/seatreservation/event/AiBatchScheduleTest.java`.
- Create `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`.
- Modify `frontend/src/api/liveEventApi.test.ts`.
- Modify `frontend/src/hooks/useLiveEventRoom.test.tsx`.
- Modify `frontend/src/domain/liveEventSelectors.test.ts`.
- Modify `frontend/src/App.test.tsx`.
- Create `frontend/src/components/QueuePanel.test.tsx`.
- Create or modify `frontend/src/components/SeatMap.test.tsx` if absent.

---

## Task 1: Add Lifecycle Contracts And State Store

**Files:**

- Create: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStatus.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventMetadata.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStateStore.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/InMemoryLiveEventStateStore.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/RedisLiveEventStateStore.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventStateStoreTest.java`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: Write lifecycle state store tests**

Create `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventStateStoreTest.java`:

```java
package com.timedeal.seatreservation.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEventStateStoreTest {
    private final UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void createsReadyMetadataAndStartsCountdownOnce() {
        InMemoryLiveEventStateStore store = new InMemoryLiveEventStateStore();
        Instant now = Instant.parse("2026-05-28T12:00:00Z");

        LiveEventMetadata ready = store.getOrCreate(eventId, now);
        LiveEventMetadata countdown = store.startCountdown(eventId, now, Duration.ofSeconds(60), Duration.ofMinutes(5));
        LiveEventMetadata duplicate = store.startCountdown(eventId, now.plusSeconds(10), Duration.ofSeconds(60), Duration.ofMinutes(5));

        assertThat(ready.statusAt(now)).isEqualTo(LiveEventStatus.READY);
        assertThat(countdown.statusAt(now)).isEqualTo(LiveEventStatus.COUNTDOWN);
        assertThat(countdown.opensAt()).isEqualTo(now.plusSeconds(60));
        assertThat(countdown.endsAt()).isEqualTo(now.plusSeconds(360));
        assertThat(duplicate.opensAt()).isEqualTo(countdown.opensAt());
        assertThat(duplicate.generation()).isEqualTo(1);
    }

    @Test
    void derivesOpenAndEndedFromClock() {
        InMemoryLiveEventStateStore store = new InMemoryLiveEventStateStore();
        Instant now = Instant.parse("2026-05-28T12:00:00Z");

        LiveEventMetadata metadata = store.startCountdown(eventId, now, Duration.ofSeconds(60), Duration.ofMinutes(5));

        assertThat(metadata.statusAt(now.plusSeconds(61))).isEqualTo(LiveEventStatus.OPEN);
        assertThat(metadata.statusAt(now.plusSeconds(361))).isEqualTo(LiveEventStatus.ENDED);
    }

    @Test
    void resetAfterEndedIncrementsGenerationAndClearsAiFlag() {
        InMemoryLiveEventStateStore store = new InMemoryLiveEventStateStore();
        Instant now = Instant.parse("2026-05-28T12:00:00Z");
        store.startCountdown(eventId, now, Duration.ofSeconds(60), Duration.ofMinutes(5));
        assertThat(store.claimAiStart(eventId)).isTrue();
        assertThat(store.claimAiStart(eventId)).isFalse();

        LiveEventMetadata reset = store.reset(eventId, now.plusSeconds(400));

        assertThat(reset.generation()).isEqualTo(2);
        assertThat(reset.statusAt(now.plusSeconds(400))).isEqualTo(LiveEventStatus.READY);
        assertThat(reset.aiStarted()).isFalse();
        assertThat(reset.opensAt()).isNull();
        assertThat(reset.endsAt()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventStateStoreTest"
```

Expected:

```text
Compilation failed
cannot find symbol class LiveEventMetadata
```

- [ ] **Step 3: Add lifecycle status enum**

Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStatus.java`:

```java
package com.timedeal.seatreservation.event;

public enum LiveEventStatus {
    READY,
    COUNTDOWN,
    OPEN,
    ENDED
}
```

- [ ] **Step 4: Add lifecycle metadata record**

Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventMetadata.java`:

```java
package com.timedeal.seatreservation.event;

import java.time.Instant;
import java.util.UUID;

public record LiveEventMetadata(
        UUID eventId,
        int generation,
        LiveEventStatus status,
        Instant createdAt,
        Instant opensAt,
        Instant endsAt,
        boolean aiStarted
) {
    public LiveEventStatus statusAt(Instant now) {
        if (status == LiveEventStatus.COUNTDOWN && opensAt != null && !now.isBefore(opensAt)) {
            if (endsAt != null && !now.isBefore(endsAt)) {
                return LiveEventStatus.ENDED;
            }
            return LiveEventStatus.OPEN;
        }
        if (status == LiveEventStatus.OPEN && endsAt != null && !now.isBefore(endsAt)) {
            return LiveEventStatus.ENDED;
        }
        return status;
    }

    public LiveEventMetadata withDerivedStatus(Instant now) {
        return new LiveEventMetadata(eventId, generation, statusAt(now), createdAt, opensAt, endsAt, aiStarted);
    }

}
```

- [ ] **Step 5: Add lifecycle state store interface**

Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStateStore.java`:

```java
package com.timedeal.seatreservation.event;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface LiveEventStateStore {
    LiveEventMetadata getOrCreate(UUID eventId, Instant now);

    LiveEventMetadata startCountdown(UUID eventId, Instant now, Duration countdownDuration, Duration openWindow);

    boolean claimAiStart(UUID eventId);

    LiveEventMetadata reset(UUID eventId, Instant now);
}
```

- [ ] **Step 6: Add in-memory store**

Create `backend/src/main/java/com/timedeal/seatreservation/event/InMemoryLiveEventStateStore.java`:

```java
package com.timedeal.seatreservation.event;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class InMemoryLiveEventStateStore implements LiveEventStateStore {
    private final ConcurrentHashMap<UUID, LiveEventMetadata> events = new ConcurrentHashMap<>();

    @Override
    public LiveEventMetadata getOrCreate(UUID eventId, Instant now) {
        return events.computeIfAbsent(eventId, id -> ready(id, 1, now));
    }

    @Override
    public LiveEventMetadata startCountdown(UUID eventId, Instant now, Duration countdownDuration, Duration openWindow) {
        return events.compute(eventId, (id, current) -> {
            LiveEventMetadata metadata = current == null ? ready(id, 1, now) : current.withDerivedStatus(now);
            if (metadata.status() != LiveEventStatus.READY) {
                return metadata;
            }
            Instant opensAt = now.plus(countdownDuration);
            return new LiveEventMetadata(id, metadata.generation(), LiveEventStatus.COUNTDOWN, metadata.createdAt(), opensAt, opensAt.plus(openWindow), false);
        });
    }

    @Override
    public boolean claimAiStart(UUID eventId) {
        LiveEventMetadata before = events.get(eventId);
        if (before == null || before.aiStarted()) {
            return false;
        }
        LiveEventMetadata after = new LiveEventMetadata(
                before.eventId(),
                before.generation(),
                before.status(),
                before.createdAt(),
                before.opensAt(),
                before.endsAt(),
                true
        );
        return events.replace(eventId, before, after);
    }

    @Override
    public LiveEventMetadata reset(UUID eventId, Instant now) {
        return events.compute(eventId, (id, current) -> {
            int nextGeneration = current == null ? 1 : current.generation() + 1;
            return ready(id, nextGeneration, now);
        });
    }

    private LiveEventMetadata ready(UUID eventId, int generation, Instant now) {
        return new LiveEventMetadata(eventId, generation, LiveEventStatus.READY, now, null, null, false);
    }
}
```

- [ ] **Step 7: Add Redis store**

Create `backend/src/main/java/com/timedeal/seatreservation/event/RedisLiveEventStateStore.java`:

```java
package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Component
@Profile("!demo")
public class RedisLiveEventStateStore implements LiveEventStateStore {
    private static final Duration TTL = Duration.ofHours(3);
    private static final Duration LOCK_TTL = Duration.ofSeconds(2);
    private static final Duration LOCK_RETRY_DELAY = Duration.ofMillis(20);
    private static final int LOCK_ATTEMPTS = 250;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisLiveEventStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public LiveEventMetadata getOrCreate(UUID eventId, Instant now) {
        return mutate(eventId, current -> current == null ? ready(eventId, 1, now) : current.withDerivedStatus(now));
    }

    @Override
    public LiveEventMetadata startCountdown(UUID eventId, Instant now, Duration countdownDuration, Duration openWindow) {
        return mutate(eventId, current -> {
            LiveEventMetadata metadata = current == null ? ready(eventId, 1, now) : current.withDerivedStatus(now);
            if (metadata.status() != LiveEventStatus.READY) {
                return metadata;
            }
            Instant opensAt = now.plus(countdownDuration);
            return new LiveEventMetadata(eventId, metadata.generation(), LiveEventStatus.COUNTDOWN, metadata.createdAt(), opensAt, opensAt.plus(openWindow), false);
        });
    }

    @Override
    public boolean claimAiStart(UUID eventId) {
        String lockKey = key(eventId) + ":lock";
        acquireLock(eventId, lockKey);
        try {
            LiveEventMetadata current = read(eventId);
            if (current == null || current.aiStarted()) {
                return false;
            }
            LiveEventMetadata updated = new LiveEventMetadata(
                    current.eventId(),
                    current.generation(),
                    current.status(),
                    current.createdAt(),
                    current.opensAt(),
                    current.endsAt(),
                    true
            );
            redisTemplate.opsForValue().set(key(eventId), write(updated), TTL);
            return true;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public LiveEventMetadata reset(UUID eventId, Instant now) {
        return mutate(eventId, current -> {
            int nextGeneration = current == null ? 1 : current.generation() + 1;
            return ready(eventId, nextGeneration, now);
        });
    }

    private LiveEventMetadata mutate(UUID eventId, UnaryOperator<LiveEventMetadata> mutator) {
        String lockKey = key(eventId) + ":lock";
        acquireLock(eventId, lockKey);
        try {
            LiveEventMetadata updated = mutator.apply(read(eventId));
            if (updated != null) {
                redisTemplate.opsForValue().set(key(eventId), write(updated), TTL);
            }
            return updated;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private LiveEventMetadata read(UUID eventId) {
        String json = redisTemplate.opsForValue().get(key(eventId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LiveEventMetadata.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read live event metadata: " + eventId, exception);
        }
    }

    private String write(LiveEventMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write live event metadata: " + metadata.eventId(), exception);
        }
    }

    private void acquireLock(UUID eventId, String lockKey) {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                return;
            }
            sleepBeforeRetry(eventId);
        }
        throw new IllegalStateException("Live event metadata is busy: " + eventId);
    }

    private void sleepBeforeRetry(UUID eventId) {
        try {
            Thread.sleep(LOCK_RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for live event lock: " + eventId, exception);
        }
    }

    private String key(UUID eventId) {
        return "live-event:%s:metadata".formatted(eventId);
    }

    private LiveEventMetadata ready(UUID eventId, int generation, Instant now) {
        return new LiveEventMetadata(eventId, generation, LiveEventStatus.READY, now, null, null, false);
    }
}
```

- [ ] **Step 8: Add lifecycle configuration defaults**

Modify `backend/src/main/resources/application-local.yml` under existing `live-event`:

```yaml
live-event:
  id: ${LIVE_EVENT_ID:00000000-0000-0000-0000-000000000001}
  title: ${LIVE_EVENT_TITLE:부산 콘서트 티켓팅}
  seat-count: ${LIVE_EVENT_SEAT_COUNT:120}
  countdown-seconds: ${LIVE_EVENT_COUNTDOWN_SECONDS:60}
  open-window-seconds: ${LIVE_EVENT_OPEN_WINDOW_SECONDS:300}
  ai:
    participant-count: ${LIVE_EVENT_AI_PARTICIPANT_COUNT:150}
    concurrency: ${LIVE_EVENT_AI_CONCURRENCY:50}
```

Modify `backend/src/main/resources/application-prod.yml` with the same `live-event` block.

- [ ] **Step 9: Run lifecycle tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventStateStoreTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 10: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStatus.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventMetadata.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventStateStore.java backend/src/main/java/com/timedeal/seatreservation/event/InMemoryLiveEventStateStore.java backend/src/main/java/com/timedeal/seatreservation/event/RedisLiveEventStateStore.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventStateStoreTest.java backend/src/main/resources/application-local.yml backend/src/main/resources/application-prod.yml
git commit -m "feat: add live event lifecycle state store"
```

---

## Task 2: Add Start/Reset API And Lifecycle Response Fields

**Files:**

- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventResponse.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventSnapshot.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationInventoryService.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventContractTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationInventoryServiceTest.java`

- [ ] **Step 1: Update contract test for lifecycle fields**

Modify `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventContractTest.java` so the `LiveEventSnapshot` constructor includes `generation` and `endsAt`:

```java
LiveEventSnapshot snapshot = new LiveEventSnapshot(
        eventId,
        "부산 콘서트 티켓팅",
        "COUNTDOWN",
        1,
        Instant.parse("2026-05-28T12:01:00Z"),
        Instant.parse("2026-05-28T12:06:00Z"),
        List.of(new SeatView(1, "A-1", SeatStatus.AVAILABLE)),
        List.of(new VirtualUserView(
                participantId,
                "나",
                ParticipantType.HUMAN,
                VirtualUserStatus.WAITING_ROOM,
                null,
                List.of(new TimelineEntry("입장", "이벤트에 입장했습니다.")),
                0,
                0,
                0,
                null
        )),
        new SimulationMetrics(0, 0, 0, 0, 0, 0),
        List.of(new ServerStatsView("api-a", 1, 0, 0)),
        false,
        participantId
);

String json = objectMapper.writeValueAsString(snapshot);

assertThat(json).contains("\"status\":\"COUNTDOWN\"");
assertThat(json).contains("\"generation\":1");
assertThat(json).contains("\"opensAt\":\"2026-05-28T12:01:00Z\"");
assertThat(json).contains("\"endsAt\":\"2026-05-28T12:06:00Z\"");
```

- [ ] **Step 2: Add service tests for start and reset**

Add to `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`:

```java
@Test
void visitorStartsCountdownAndResetCreatesReadyNextGeneration() {
    UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
    SimulationStateStore stateStore = new SimulationStateStore();
    SimulationService simulationService = new SimulationService(stateStore);
    InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
    LiveEventService service = new LiveEventService(
            simulationService,
            stateStore,
            eventStateStore,
            new ServerIdentity("api-test"),
            null,
            eventId,
            "부산 콘서트 티켓팅",
            120,
            Duration.ofSeconds(60),
            Duration.ofMinutes(5),
            Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC),
            null
    );

    LiveEventResponse active = service.activeEvent();
    LiveEventResponse countdown = service.startEvent(eventId);
    LiveEventSnapshot snapshot = service.snapshot(eventId, null);
    LiveEventResponse reset = service.resetEvent(eventId);

    assertThat(active.status()).isEqualTo("READY");
    assertThat(countdown.status()).isEqualTo("COUNTDOWN");
    assertThat(countdown.opensAt()).isEqualTo(Instant.parse("2026-05-28T12:01:00Z"));
    assertThat(countdown.endsAt()).isEqualTo(Instant.parse("2026-05-28T12:06:00Z"));
    assertThat(snapshot.generation()).isEqualTo(1);
    assertThat(reset.status()).isEqualTo("READY");
    assertThat(reset.generation()).isEqualTo(2);
}
```

Add imports:

```java
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventContractTest" --tests "*LiveEventServiceTest"
```

Expected:

```text
Compilation failed
cannot find symbol method startEvent
```

- [ ] **Step 4: Update response records**

Replace `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventResponse.java`:

```java
package com.timedeal.seatreservation.event;

import java.time.Instant;
import java.util.UUID;

public record LiveEventResponse(
        UUID eventId,
        String title,
        String status,
        int generation,
        Instant opensAt,
        Instant endsAt,
        int seatCount
) {
}
```

Replace `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventSnapshot.java`:

```java
package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.VirtualUserView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LiveEventSnapshot(
        UUID eventId,
        String title,
        String status,
        int generation,
        Instant opensAt,
        Instant endsAt,
        List<SeatView> seats,
        List<VirtualUserView> participants,
        SimulationMetrics metrics,
        List<ServerStatsView> serverStats,
        boolean running,
        UUID myParticipantId
) {
}
```

- [ ] **Step 5: Add reset support to simulation service and inventory**

Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`:

```java
public SimulationResponse resetSimulation(UUID simulationId, int virtualUserCount) {
    if (inventoryService != null) {
        inventoryService.resetSimulation(simulationId);
    }
    SimulationSnapshot snapshot = stateStore.create(simulationId, virtualUserCount);
    if (inventoryService != null) {
        inventoryService.initialize(snapshot, virtualUserCount);
    }
    return new SimulationResponse(
            simulationId,
            "시뮬레이션이 초기화되었습니다.",
            virtualUserCount,
            serverIdentity.id()
    );
}
```

Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationInventoryService.java`:

```java
public void resetSimulation(UUID simulationId) {
    jdbc.update("delete from payments where simulation_id = ?", simulationId);
    jdbc.update("delete from reservations where simulation_id = ?", simulationId);
    jdbc.update("delete from simulation_seats where simulation_id = ?", simulationId);
    jdbc.update("delete from virtual_users where simulation_id = ?", simulationId);
    jdbc.update("delete from simulation_sessions where id = ?", simulationId);
}
```

Add `import java.util.UUID;`.

- [ ] **Step 6: Replace LiveEventService constructor and lifecycle methods**

Update `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java` so it has these fields:

```java
private final SimulationService simulationService;
private final SimulationStateGateway stateGateway;
private final LiveEventStateStore eventStateStore;
private final SimulationInventoryService inventoryService;
private final ServerIdentity serverIdentity;
private final UUID configuredEventId;
private final String title;
private final int seatCount;
private final Duration countdownDuration;
private final Duration openWindow;
private final Clock clock;
private final LiveEventAiStarter aiStarter;
```

Add this main constructor:

```java
@Autowired
public LiveEventService(
        SimulationService simulationService,
        SimulationStateGateway stateGateway,
        LiveEventStateStore eventStateStore,
        ServerIdentity serverIdentity,
        ObjectProvider<SimulationInventoryService> inventoryService,
        ObjectProvider<LiveEventAiStarter> aiStarter,
        @Value("${live-event.id:00000000-0000-0000-0000-000000000001}") UUID configuredEventId,
        @Value("${live-event.title:Live Ticketing Event}") String title,
        @Value("${live-event.seat-count:120}") int seatCount,
        @Value("${live-event.countdown-seconds:60}") long countdownSeconds,
        @Value("${live-event.open-window-seconds:300}") long openWindowSeconds
) {
    this(
            simulationService,
            stateGateway,
            eventStateStore,
            serverIdentity,
            inventoryService.getIfAvailable(),
            configuredEventId,
            title,
            seatCount,
            Duration.ofSeconds(countdownSeconds),
            Duration.ofSeconds(openWindowSeconds),
            Clock.systemUTC(),
            aiStarter.getIfAvailable()
    );
}
```

Add this test constructor:

```java
public LiveEventService(
        SimulationService simulationService,
        SimulationStateGateway stateGateway,
        LiveEventStateStore eventStateStore,
        ServerIdentity serverIdentity,
        SimulationInventoryService inventoryService,
        UUID configuredEventId,
        String title,
        int seatCount,
        Duration countdownDuration,
        Duration openWindow,
        Clock clock,
        LiveEventAiStarter aiStarter
) {
    this.simulationService = simulationService;
    this.stateGateway = stateGateway;
    this.eventStateStore = eventStateStore;
    this.serverIdentity = serverIdentity;
    this.inventoryService = inventoryService;
    this.configuredEventId = configuredEventId;
    this.title = title;
    this.seatCount = seatCount;
    this.countdownDuration = countdownDuration;
    this.openWindow = openWindow;
    this.clock = clock;
    this.aiStarter = aiStarter;
}
```

Replace `activeEvent`, add `startEvent`, add `resetEvent`, and update `snapshot`:

```java
public LiveEventResponse activeEvent() {
    ensureSimulationExists();
    LiveEventMetadata metadata = eventStateStore.getOrCreate(configuredEventId, now()).withDerivedStatus(now());
    triggerAiIfOpen(metadata);
    return response(metadata);
}

public LiveEventResponse startEvent(UUID eventId) {
    ensureExpectedEvent(eventId);
    ensureSimulationExists();
    LiveEventMetadata metadata = eventStateStore.startCountdown(eventId, now(), countdownDuration, openWindow).withDerivedStatus(now());
    triggerAiIfOpen(metadata);
    return response(metadata);
}

public LiveEventResponse resetEvent(UUID eventId) {
    ensureExpectedEvent(eventId);
    simulationService.resetSimulation(eventId, 0);
    LiveEventMetadata metadata = eventStateStore.reset(eventId, now());
    return response(metadata);
}

public LiveEventSnapshot snapshot(UUID eventId, UUID myParticipantId) {
    ensureExpectedEvent(eventId);
    ensureSimulationExists();
    LiveEventMetadata metadata = eventStateStore.getOrCreate(eventId, now()).withDerivedStatus(now());
    triggerAiIfOpen(metadata);
    SimulationSnapshot snapshot = simulationService.getSimulation(eventId);
    return new LiveEventSnapshot(
            eventId,
            title,
            metadata.statusAt(now()).name(),
            metadata.generation(),
            metadata.opensAt(),
            metadata.endsAt(),
            snapshot.seats(),
            snapshot.users(),
            snapshot.metrics(),
            snapshot.serverStats(),
            snapshot.running(),
            myParticipantId
    );
}
```

Add helpers:

```java
private void ensureSimulationExists() {
    try {
        simulationService.getSimulation(configuredEventId);
    } catch (NoSuchElementException exception) {
        simulationService.createSimulation(configuredEventId, 0);
    }
}

private void ensureExpectedEvent(UUID eventId) {
    if (!configuredEventId.equals(eventId)) {
        throw new NoSuchElementException("Live event not found: " + eventId);
    }
}

private void triggerAiIfOpen(LiveEventMetadata metadata) {
    if (aiStarter == null || metadata.statusAt(now()) != LiveEventStatus.OPEN || metadata.aiStarted()) {
        return;
    }
    if (eventStateStore.claimAiStart(metadata.eventId())) {
        aiStarter.start(metadata.eventId());
    }
}

private LiveEventResponse response(LiveEventMetadata metadata) {
    LiveEventMetadata derived = metadata.withDerivedStatus(now());
    return new LiveEventResponse(
            derived.eventId(),
            title,
            derived.status().name(),
            derived.generation(),
            derived.opensAt(),
            derived.endsAt(),
            seatCount
    );
}

private Instant now() {
    return clock.instant();
}
```

- [ ] **Step 7: Add controller endpoints**

Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`:

```java
@PostMapping("/{eventId}/start")
public LiveEventResponse startEvent(@PathVariable UUID eventId) {
    return liveEventService.startEvent(eventId);
}

@PostMapping("/{eventId}/reset")
public LiveEventResponse resetEvent(@PathVariable UUID eventId) {
    return liveEventService.resetEvent(eventId);
}
```

- [ ] **Step 8: Update controller test**

Add to `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`:

```java
@Test
void startsAndResetsLiveEvent() throws Exception {
    UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    when(liveEventService.startEvent(eventId)).thenReturn(new LiveEventResponse(
            eventId,
            "부산 콘서트 티켓팅",
            "COUNTDOWN",
            1,
            Instant.parse("2026-05-28T12:01:00Z"),
            Instant.parse("2026-05-28T12:06:00Z"),
            120
    ));
    when(liveEventService.resetEvent(eventId)).thenReturn(new LiveEventResponse(
            eventId,
            "부산 콘서트 티켓팅",
            "READY",
            2,
            null,
            null,
            120
    ));

    mockMvc.perform(post("/api/events/{eventId}/start", eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COUNTDOWN"))
            .andExpect(jsonPath("$.generation").value(1));

    mockMvc.perform(post("/api/events/{eventId}/reset", eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.generation").value(2));
}
```

- [ ] **Step 9: Update inventory reset test**

Add to `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationInventoryServiceTest.java`:

```java
@Test
void resetsSimulationInventoryInForeignKeyOrder() {
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000010");

    service.resetSimulation(simulationId);

    InOrder inOrder = inOrder(jdbc);
    inOrder.verify(jdbc).update("delete from payments where simulation_id = ?", simulationId);
    inOrder.verify(jdbc).update("delete from reservations where simulation_id = ?", simulationId);
    inOrder.verify(jdbc).update("delete from simulation_seats where simulation_id = ?", simulationId);
    inOrder.verify(jdbc).update("delete from virtual_users where simulation_id = ?", simulationId);
    inOrder.verify(jdbc).update("delete from simulation_sessions where id = ?", simulationId);
}
```

Add imports:

```java
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
```

- [ ] **Step 10: Run focused tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventContractTest" --tests "*LiveEventServiceTest" --tests "*LiveEventControllerTest" --tests "*SimulationInventoryServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 11: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationInventoryService.java backend/src/test/java/com/timedeal/seatreservation/event backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationInventoryServiceTest.java
git commit -m "feat: add user-started live event lifecycle"
```

---

## Task 3: Gate Event Commands And Prevent Multiple Seat Holds

**Files:**

- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStoreTest.java`

- [ ] **Step 1: Add service tests for lifecycle command gating**

Add to `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`:

```java
@Test
void rejectsSeatHoldBeforeOpenAndAfterEndedWithDomainMessage() {
    UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
    SimulationStateStore stateStore = new SimulationStateStore();
    SimulationService simulationService = new SimulationService(stateStore);
    InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
    LiveEventService service = new LiveEventService(
            simulationService,
            stateStore,
            eventStateStore,
            new ServerIdentity("api-test"),
            null,
            eventId,
            "부산 콘서트 티켓팅",
            120,
            Duration.ofSeconds(60),
            Duration.ofMinutes(5),
            Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC),
            null
    );

    service.activeEvent();
    JoinEventResponse joined = service.join(eventId, new JoinEventRequest("권"));
    SeatHoldResponse readyHold = service.holdSeat(eventId, joined.participantId(), 1L);

    assertThat(readyHold.status()).isEqualTo("NOT_OPEN");
    assertThat(readyHold.message()).isEqualTo("티켓팅 시작 전입니다.");
}
```

Add a second test for `ENDED` with `Clock.offset(...)` or a mutable test clock. If using `Clock.fixed`, construct the service with `Clock.fixed(Instant.parse("2026-05-28T12:07:00Z"), ZoneOffset.UTC)` after calling `eventStateStore.startCountdown(...)` at `12:00:00Z`.

Expected ended assertion:

```java
assertThat(endedHold.status()).isEqualTo("EVENT_ENDED");
assertThat(endedHold.message()).isEqualTo("이벤트가 종료되었습니다.");
```

- [ ] **Step 2: Add simulation test for duplicate seat hold**

Add to `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`:

```java
@Test
void participantCannotHoldMultipleSeats() {
    SimulationStateStore stateStore = new SimulationStateStore();
    SeatReservationService seatReservationService = mock(SeatReservationService.class);
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID participantId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    stateStore.create(simulationId, 0);
    stateStore.registerParticipant(simulationId, participantId, "권", ParticipantType.HUMAN, "api-test");
    stateStore.registerQueueEntry(simulationId, participantId, "api-test");
    when(seatReservationService.holdSeat(eq(simulationId), eq(participantId), eq(1L), any()))
            .thenReturn(new SeatReservationResult(SeatReservationOutcome.HELD, 100L, 1L, participantId, "hold-1"));

    SimulationService service = new SimulationService(
            stateStore,
            new ServerIdentity("api-test"),
            (id, request) -> {
            },
            null,
            null,
            seatReservationService,
            null,
            new Random(1)
    );

    VirtualUserCommandResponse first = service.holdExplicitSeat(simulationId, participantId, 1L);
    VirtualUserCommandResponse second = service.holdExplicitSeat(simulationId, participantId, 2L);

    assertThat(first.status()).isEqualTo("PAYMENT_PENDING");
    assertThat(second.status()).isEqualTo("ALREADY_HOLDING");
    assertThat(second.selectedSeatLabel()).isEqualTo("A-1");
    verify(seatReservationService, times(1)).holdSeat(eq(simulationId), eq(participantId), anyLong(), any());
}
```

Add imports if absent:

```java
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.seat.SeatReservationOutcome;
import com.timedeal.seatreservation.seat.SeatReservationResult;
import com.timedeal.seatreservation.seat.SeatReservationService;

import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventServiceTest" --tests "*SimulationServiceTest"
```

Expected:

```text
Failures mention expected NOT_OPEN or ALREADY_HOLDING but actual behavior differs
```

- [ ] **Step 4: Add participant lookup helper to state gateway**

Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`:

```java
default VirtualUserView participant(UUID simulationId, UUID participantId) {
    return snapshot(simulationId).users().stream()
            .filter(user -> user.id().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));
}
```

- [ ] **Step 5: Gate live event commands by lifecycle**

In `LiveEventService`, add:

```java
private LiveEventStatus currentStatus(UUID eventId) {
    return eventStateStore.getOrCreate(eventId, now()).statusAt(now());
}

private boolean isOpen(UUID eventId) {
    return currentStatus(eventId) == LiveEventStatus.OPEN;
}
```

Update `enterQueue`:

```java
public VirtualUserCommandResponse enterQueue(UUID eventId, UUID participantId) {
    LiveEventStatus status = currentStatus(eventId);
    if (status == LiveEventStatus.READY) {
        return new VirtualUserCommandResponse(eventId, participantId, "NOT_STARTED", serverIdentity.id(), "이벤트 시작 전입니다.", null);
    }
    if (status == LiveEventStatus.ENDED) {
        return new VirtualUserCommandResponse(eventId, participantId, "EVENT_ENDED", serverIdentity.id(), "이벤트가 종료되었습니다.", null);
    }
    return simulationService.enterParticipantQueue(eventId, participantId);
}
```

Update `holdSeat` before calling `simulationService`:

```java
LiveEventStatus eventStatus = currentStatus(eventId);
if (eventStatus == LiveEventStatus.READY || eventStatus == LiveEventStatus.COUNTDOWN) {
    return new SeatHoldResponse(eventId, participantId, seatId, "NOT_OPEN", "티켓팅 시작 전입니다.", null, serverIdentity.id());
}
if (eventStatus == LiveEventStatus.ENDED) {
    return new SeatHoldResponse(eventId, participantId, seatId, "EVENT_ENDED", "이벤트가 종료되었습니다.", null, serverIdentity.id());
}
```

Update `confirmPayment` similarly:

```java
LiveEventStatus eventStatus = currentStatus(eventId);
if (eventStatus != LiveEventStatus.OPEN) {
    String message = eventStatus == LiveEventStatus.ENDED ? "이벤트가 종료되었습니다." : "티켓팅 시작 전입니다.";
    String status = eventStatus == LiveEventStatus.ENDED ? "EVENT_ENDED" : "NOT_OPEN";
    return new PaymentConfirmResponse(eventId, participantId, status, message, serverIdentity.id());
}
```

- [ ] **Step 6: Prevent duplicate seat holds in SimulationService**

In `SimulationService.holdExplicitSeat(...)`, after participant lookup and before admission/seat lookup, add:

```java
VirtualUserView participant = stateStore.participant(simulationId, participantId);
if (participant.status() == VirtualUserStatus.SEAT_HELD
        || participant.status() == VirtualUserStatus.PAYMENT_IN_PROGRESS
        || participant.status() == VirtualUserStatus.RESERVED) {
    return new VirtualUserCommandResponse(
            simulationId,
            participantId,
            "ALREADY_HOLDING",
            serverIdentity.id(),
            "이미 선점한 좌석이 있습니다.",
            participant.selectedSeatLabel()
    );
}
```

In `SimulationService.confirmPayment(...)`, replace the `orElseThrow` for missing selected seat with a domain response:

```java
if (participant.selectedSeatLabel() == null) {
    return new VirtualUserCommandResponse(
            simulationId,
            participantId,
            "NO_HELD_SEAT",
            serverIdentity.id(),
            "결제할 선점 좌석이 없습니다.",
            null
    );
}
```

- [ ] **Step 7: Run focused tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventServiceTest" --tests "*SimulationServiceTest" --tests "*RedisSimulationStateStoreTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java
git commit -m "fix: gate live event commands by lifecycle"
```

---

## Task 4: Add AI Batch Staggering And One-Time Auto Start

**Files:**

- Create: `backend/src/main/java/com/timedeal/seatreservation/event/AiBatch.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/AiBatchSchedule.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/event/AiBatchScheduleTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`

- [ ] **Step 1: Add schedule test**

Create `backend/src/test/java/com/timedeal/seatreservation/event/AiBatchScheduleTest.java`:

```java
package com.timedeal.seatreservation.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiBatchScheduleTest {
    @Test
    void splitsParticipantsIntoStaggeredBatches() {
        AiBatchSchedule schedule = AiBatchSchedule.defaultSchedule(150, 50);

        assertThat(schedule.batches()).containsExactly(
                new AiBatch(10, 10, Duration.ZERO),
                new AiBatch(20, 20, Duration.ofMillis(100)),
                new AiBatch(30, 30, Duration.ofMillis(300)),
                new AiBatch(40, 40, Duration.ofMillis(700)),
                new AiBatch(50, 50, Duration.ofMillis(1200))
        );
    }
}
```

- [ ] **Step 2: Add AI starter test**

Create `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`:

```java
package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LiveEventAiStarterTest {
    @Test
    void schedulesEachBatchWithDelay() {
        SimulationService simulationService = mock(SimulationService.class);
        List<Duration> delays = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        LiveEventAiStarter starter = new LiveEventAiStarter(
                simulationService,
                150,
                50,
                (delay, task) -> {
                    delays.add(delay);
                    tasks.add(task);
                }
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        starter.start(eventId);
        tasks.forEach(Runnable::run);

        assertThat(delays).containsExactly(Duration.ZERO, Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(700), Duration.ofMillis(1200));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(10, 10));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(20, 20));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(30, 30));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(40, 40));
        verify(simulationService).runSimulation(eventId, new RunSimulationRequest(50, 50));
    }
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*AiBatchScheduleTest" --tests "*LiveEventAiStarterTest"
```

Expected:

```text
Compilation failed
cannot find symbol class AiBatch
```

- [ ] **Step 4: Add AiBatch record**

Create `backend/src/main/java/com/timedeal/seatreservation/event/AiBatch.java`:

```java
package com.timedeal.seatreservation.event;

import java.time.Duration;

public record AiBatch(
        int participantCount,
        int concurrency,
        Duration delay
) {
}
```

- [ ] **Step 5: Add AiBatchSchedule**

Create `backend/src/main/java/com/timedeal/seatreservation/event/AiBatchSchedule.java`:

```java
package com.timedeal.seatreservation.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record AiBatchSchedule(List<AiBatch> batches) {
    public static AiBatchSchedule defaultSchedule(int participantCount, int maxConcurrency) {
        int remaining = Math.max(0, participantCount);
        int[] weights = {10, 20, 30, 40, 50};
        long[] delays = {0, 100, 300, 700, 1200};
        List<AiBatch> batches = new ArrayList<>();
        for (int index = 0; index < weights.length && remaining > 0; index++) {
            int count = Math.min(weights[index], remaining);
            int concurrency = Math.max(1, Math.min(maxConcurrency, count));
            batches.add(new AiBatch(count, concurrency, Duration.ofMillis(delays[index])));
            remaining -= count;
        }
        if (remaining > 0) {
            int concurrency = Math.max(1, Math.min(maxConcurrency, remaining));
            batches.add(new AiBatch(remaining, concurrency, Duration.ofMillis(1800)));
        }
        return new AiBatchSchedule(List.copyOf(batches));
    }
}
```

- [ ] **Step 6: Add LiveEventAiStarter**

Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`:

```java
package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class LiveEventAiStarter {
    private final SimulationService simulationService;
    private final int participantCount;
    private final int concurrency;
    private final BatchScheduler scheduler;

    @Autowired
    public LiveEventAiStarter(
            SimulationService simulationService,
            @Value("${live-event.ai.participant-count:150}") int participantCount,
            @Value("${live-event.ai.concurrency:50}") int concurrency
    ) {
        this(
                simulationService,
                participantCount,
                concurrency,
                new ExecutorBatchScheduler(Executors.newSingleThreadScheduledExecutor())
        );
    }

    LiveEventAiStarter(SimulationService simulationService, int participantCount, int concurrency, BatchScheduler scheduler) {
        this.simulationService = simulationService;
        this.participantCount = participantCount;
        this.concurrency = concurrency;
        this.scheduler = scheduler;
    }

    public void start(UUID eventId) {
        AiBatchSchedule schedule = AiBatchSchedule.defaultSchedule(participantCount, concurrency);
        for (AiBatch batch : schedule.batches()) {
            scheduler.schedule(batch.delay(), () -> simulationService.runSimulation(
                    eventId,
                    new RunSimulationRequest(batch.participantCount(), batch.concurrency())
            ));
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

    interface BatchScheduler {
        void schedule(Duration delay, Runnable task);

        default void shutdown() {
        }
    }

    static final class ExecutorBatchScheduler implements BatchScheduler {
        private final ScheduledExecutorService executor;

        ExecutorBatchScheduler(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void schedule(Duration delay, Runnable task) {
            executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}
```

- [ ] **Step 7: Verify one-time AI trigger in service test**

Add to `LiveEventServiceTest`:

```java
@Test
void startsAiOnceWhenEventIsOpen() {
    UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000777");
    SimulationStateStore stateStore = new SimulationStateStore();
    SimulationService simulationService = new SimulationService(stateStore);
    InMemoryLiveEventStateStore eventStateStore = new InMemoryLiveEventStateStore();
    LiveEventAiStarter aiStarter = mock(LiveEventAiStarter.class);
    Instant start = Instant.parse("2026-05-28T12:00:00Z");
    eventStateStore.startCountdown(eventId, start, Duration.ofSeconds(60), Duration.ofMinutes(5));
    LiveEventService service = new LiveEventService(
            simulationService,
            stateStore,
            eventStateStore,
            new ServerIdentity("api-test"),
            null,
            eventId,
            "부산 콘서트 티켓팅",
            120,
            Duration.ofSeconds(60),
            Duration.ofMinutes(5),
            Clock.fixed(start.plusSeconds(61), ZoneOffset.UTC),
            aiStarter
    );

    service.snapshot(eventId, null);
    service.snapshot(eventId, null);

    verify(aiStarter, times(1)).start(eventId);
}
```

- [ ] **Step 8: Run focused tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*AiBatchScheduleTest" --tests "*LiveEventAiStarterTest" --tests "*LiveEventServiceTest" --tests "*TrafficGeneratorServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event/AiBatch.java backend/src/main/java/com/timedeal/seatreservation/event/AiBatchSchedule.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java backend/src/test/java/com/timedeal/seatreservation/event/AiBatchScheduleTest.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java
git commit -m "feat: stagger ai participants after event opens"
```

---

## Task 5: Add Frontend Lifecycle API And Selectors

**Files:**

- Modify: `frontend/src/api/liveEventApi.ts`
- Modify: `frontend/src/api/liveEventApi.test.ts`
- Modify: `frontend/src/domain/liveEventSelectors.ts`
- Modify: `frontend/src/domain/liveEventSelectors.test.ts`
- Modify: `frontend/src/hooks/useLiveEventRoom.ts`
- Modify: `frontend/src/hooks/useLiveEventRoom.test.tsx`

- [ ] **Step 1: Update API client test**

Modify `frontend/src/api/liveEventApi.test.ts`:

```typescript
import { confirmPayment, fetchActiveEvent, holdSeat, joinEvent, queueParticipant, resetEvent, startAiParticipants, startEvent } from './liveEventApi';

it('calls live event lifecycle endpoints', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ status: 'COUNTDOWN' }),
  }));

  await fetchActiveEvent('');
  await startEvent('', 'event-1');
  await resetEvent('', 'event-1');
  await joinEvent('', 'event-1', '권');
  await queueParticipant('', 'event-1', 'participant-1');
  await holdSeat('', 'event-1', 'participant-1', 1);
  await confirmPayment('', 'event-1', 'participant-1');
  await startAiParticipants('', 'event-1', 150, 50);

  expect(fetch).toHaveBeenCalledWith('/api/events/active');
  expect(fetch).toHaveBeenCalledWith('/api/events/event-1/start', { method: 'POST' });
  expect(fetch).toHaveBeenCalledWith('/api/events/event-1/reset', { method: 'POST' });
});
```

- [ ] **Step 2: Update selector test**

Modify `frontend/src/domain/liveEventSelectors.test.ts`:

```typescript
import { canConfirmPayment, canReserve, canSelectSeat, formatEventStatus, getMyParticipant, getQueuePosition, getTimeLabel } from './liveEventSelectors';

expect(formatEventStatus('READY')).toBe('시작 전');
expect(formatEventStatus('COUNTDOWN')).toBe('오픈 대기');
expect(formatEventStatus('OPEN')).toBe('예매 진행 중');
expect(formatEventStatus('ENDED')).toBe('종료');
expect(getTimeLabel('COUNTDOWN', '2026-05-28T12:01:00Z', '2026-05-28T12:06:00Z', new Date('2026-05-28T12:00:30Z'))).toBe('오픈까지 30초');
expect(getTimeLabel('OPEN', '2026-05-28T12:01:00Z', '2026-05-28T12:06:00Z', new Date('2026-05-28T12:05:00Z'))).toBe('종료까지 60초');
expect(getQueuePosition(snapshot, 'me')).toBe(1);
expect(canSelectSeat('COUNTDOWN', me)).toEqual({ allowed: false, message: '티켓팅 시작 전입니다.' });
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- --run src/api/liveEventApi.test.ts src/domain/liveEventSelectors.test.ts
```

Expected:

```text
FAIL missing startEvent/resetEvent/canSelectSeat/getTimeLabel
```

- [ ] **Step 4: Update API types and functions**

Modify `frontend/src/api/liveEventApi.ts`:

```typescript
export type LiveEventStatus = 'READY' | 'COUNTDOWN' | 'OPEN' | 'ENDED';

export interface LiveEventResponse {
  eventId: string;
  title: string;
  status: LiveEventStatus;
  generation: number;
  opensAt: string | null;
  endsAt: string | null;
  seatCount: number;
}

export interface LiveEventSnapshot {
  eventId: string;
  title: string;
  status: LiveEventStatus;
  generation: number;
  opensAt: string | null;
  endsAt: string | null;
  seats: SeatView[];
  participants: EventParticipantView[];
  metrics: SimulationMetrics;
  serverStats: ServerStatsView[];
  running: boolean;
  myParticipantId: string | null;
}

export async function startEvent(apiBaseUrl: string, eventId: string): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/start`, { method: 'POST' }));
}

export async function resetEvent(apiBaseUrl: string, eventId: string): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/reset`, { method: 'POST' }));
}
```

- [ ] **Step 5: Add lifecycle selectors**

Modify `frontend/src/domain/liveEventSelectors.ts`:

```typescript
import type { EventParticipantView, LiveEventSnapshot, LiveEventStatus } from '../api/liveEventApi';

export function formatEventStatus(status: string): string {
  if (status === 'READY') return '시작 전';
  if (status === 'COUNTDOWN') return '오픈 대기';
  if (status === 'OPEN') return '예매 진행 중';
  if (status === 'ENDED') return '종료';
  return '준비 중';
}

export function getTimeLabel(status: LiveEventStatus, opensAt: string | null, endsAt: string | null, now = new Date()): string {
  if (status === 'COUNTDOWN' && opensAt) {
    return `오픈까지 ${secondsUntil(opensAt, now)}초`;
  }
  if (status === 'OPEN' && endsAt) {
    return `종료까지 ${secondsUntil(endsAt, now)}초`;
  }
  if (status === 'ENDED') {
    return '이벤트 종료';
  }
  return '시작 대기 중';
}

export function getQueuePosition(snapshot: LiveEventSnapshot | null, participantId: string | null): number | null {
  if (!snapshot || !participantId) return null;
  const queued = snapshot.participants.filter((participant) => participant.status === 'QUEUED');
  const index = queued.findIndex((participant) => participant.id === participantId);
  return index >= 0 ? index + 1 : null;
}

export function canSelectSeat(status: LiveEventStatus, participant: EventParticipantView | null): { allowed: boolean; message: string | null } {
  if (status === 'READY' || status === 'COUNTDOWN') return { allowed: false, message: '티켓팅 시작 전입니다.' };
  if (status === 'ENDED') return { allowed: false, message: '이벤트가 종료되었습니다.' };
  if (!participant) return { allowed: false, message: '이벤트에 입장해 주세요.' };
  if (participant.status === 'SEAT_HELD' || participant.status === 'PAYMENT_IN_PROGRESS' || participant.status === 'RESERVED') {
    return { allowed: false, message: '이미 선점한 좌석이 있습니다.' };
  }
  return { allowed: true, message: null };
}

function secondsUntil(target: string, now: Date): number {
  return Math.max(0, Math.ceil((new Date(target).getTime() - now.getTime()) / 1000));
}
```

- [ ] **Step 6: Update hook with lifecycle commands and command messages**

Modify imports in `frontend/src/hooks/useLiveEventRoom.ts`:

```typescript
import {
  confirmPayment,
  fetchActiveEvent,
  fetchEventSnapshot,
  holdSeat,
  joinEvent,
  queueParticipant,
  resetEvent,
  startEvent,
  type CommandResponse,
  type LiveEventSnapshot,
} from '../api/liveEventApi';
```

Add state:

```typescript
const [message, setMessage] = useState<string | null>(null);
```

Add helper:

```typescript
function applyCommandMessage(response: CommandResponse) {
  setMessage(response.message);
}
```

Add callbacks:

```typescript
const start = useCallback(async () => {
  if (!eventId) return;
  await startEvent(apiBaseUrl, eventId);
  setMessage('이벤트 카운트다운이 시작되었습니다.');
  await refresh();
}, [apiBaseUrl, eventId, refresh]);

const reset = useCallback(async () => {
  if (!eventId) return;
  const resetResponse = await resetEvent(apiBaseUrl, eventId);
  window.localStorage.removeItem(participantStorageKey);
  setParticipantId(null);
  setMessage('새 이벤트가 준비되었습니다.');
  setSnapshot(await fetchEventSnapshot(apiBaseUrl, resetResponse.eventId, null));
}, [apiBaseUrl, eventId]);
```

Update `reserve`, `selectSeat`, `pay` to capture response:

```typescript
const response = await queueParticipant(apiBaseUrl, eventId, participantId);
applyCommandMessage(response);
await refresh();
```

Return:

```typescript
return { eventId, participantId, snapshot, myParticipant, loading, error, message, join, reserve, selectSeat, pay, start, reset, refresh };
```

- [ ] **Step 7: Run frontend data tests**

Run:

```powershell
cd frontend
npm.cmd test -- --run src/api/liveEventApi.test.ts src/domain/liveEventSelectors.test.ts src/hooks/useLiveEventRoom.test.tsx
```

Expected:

```text
Test Files  3 passed
```

- [ ] **Step 8: Commit**

Run:

```powershell
git add frontend/src/api/liveEventApi.ts frontend/src/api/liveEventApi.test.ts frontend/src/domain/liveEventSelectors.ts frontend/src/domain/liveEventSelectors.test.ts frontend/src/hooks/useLiveEventRoom.ts frontend/src/hooks/useLiveEventRoom.test.tsx
git commit -m "feat: add live event lifecycle frontend state"
```

---

## Task 6: Replace UI Controls With User-Started Event Flow

**Files:**

- Modify: `frontend/src/components/EventHeader.tsx`
- Modify: `frontend/src/components/MyTicketPanel.tsx`
- Modify: `frontend/src/components/EventActivityPanel.tsx`
- Create: `frontend/src/components/QueuePanel.tsx`
- Create: `frontend/src/components/QueuePanel.test.tsx`
- Modify: `frontend/src/components/SeatMap.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Add QueuePanel test**

Create `frontend/src/components/QueuePanel.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QueuePanel } from './QueuePanel';
import type { LiveEventSnapshot } from '../api/liveEventApi';

describe('QueuePanel', () => {
  it('shows queue size and my approximate position', () => {
    const snapshot = {
      metrics: { queueSize: 2, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      participants: [
        { id: 'me', displayName: '나', type: 'HUMAN', status: 'QUEUED', selectedSeatLabel: null, timeline: [], seatAttemptCount: 0, conflictCount: 0, paymentAttemptCount: 0, reservationId: null },
        { id: 'ai-1', displayName: 'AI-1', type: 'AI', status: 'QUEUED', selectedSeatLabel: null, timeline: [], seatAttemptCount: 0, conflictCount: 0, paymentAttemptCount: 0, reservationId: null },
      ],
    } as LiveEventSnapshot;

    render(<QueuePanel snapshot={snapshot} participantId="me" />);

    expect(screen.getByText('대기열')).toBeInTheDocument();
    expect(screen.getByText('2명')).toBeInTheDocument();
    expect(screen.getByText('1번째')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Update App test for lifecycle controls**

Modify `frontend/src/App.test.tsx` mock snapshot:

```typescript
snapshot: {
  eventId: 'event-1',
  title: '부산 콘서트 티켓팅',
  status: 'READY',
  generation: 1,
  opensAt: null,
  endsAt: null,
  seats: [{ id: 1, label: 'A-1', status: 'AVAILABLE' }],
  participants: [],
  metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
  serverStats: [{ serverId: 'api-a', requestCount: 1, conflictCount: 0, successCount: 0 }],
  running: false,
  myParticipantId: null,
},
message: null,
start: vi.fn(),
reset: vi.fn(),
```

Update assertions:

```typescript
expect(screen.getByText('시작 전')).toBeInTheDocument();
expect(screen.getByText('이벤트 시작하기')).toBeInTheDocument();
expect(screen.queryByText('AI 참가자 시작')).not.toBeInTheDocument();
expect(screen.getByText('대기열')).toBeInTheDocument();
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- --run src/App.test.tsx src/components/QueuePanel.test.tsx
```

Expected:

```text
FAIL cannot find module QueuePanel
```

- [ ] **Step 4: Add QueuePanel**

Create `frontend/src/components/QueuePanel.tsx`:

```typescript
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { getQueuePosition } from '../domain/liveEventSelectors';

interface QueuePanelProps {
  snapshot: LiveEventSnapshot;
  participantId: string | null;
}

export function QueuePanel({ snapshot, participantId }: QueuePanelProps) {
  const position = getQueuePosition(snapshot, participantId);

  return (
    <section className="panel queue-panel">
      <h2>대기열</h2>
      <div className="status-line">
        <span>현재 대기</span>
        <strong>{snapshot.metrics.queueSize}명</strong>
      </div>
      <div className="status-line">
        <span>내 순서</span>
        <strong>{position ? `${position}번째` : '-'}</strong>
      </div>
      <div className="queue-list">
        {snapshot.participants
          .filter((participant) => participant.status === 'QUEUED')
          .slice(0, 6)
          .map((participant, index) => (
            <div key={participant.id} className={participant.id === participantId ? 'queue-row queue-row-me' : 'queue-row'}>
              <span>{index + 1}</span>
              <strong>{participant.displayName}</strong>
              <em>{participant.type}</em>
            </div>
          ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 5: Update EventHeader**

Replace `frontend/src/components/EventHeader.tsx`:

```typescript
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus, getTimeLabel } from '../domain/liveEventSelectors';

interface EventHeaderProps {
  snapshot: LiveEventSnapshot;
}

export function EventHeader({ snapshot }: EventHeaderProps) {
  return (
    <header className="top-bar">
      <div>
        <h1>{snapshot.title}</h1>
        <p>nginx · api-a/api-b · Redis 대기열 · PostgreSQL 좌석 · Kafka 결제 · worker</p>
      </div>
      <div className="event-status">
        <strong>{formatEventStatus(snapshot.status)}</strong>
        <span>{getTimeLabel(snapshot.status, snapshot.opensAt, snapshot.endsAt)}</span>
        <span>예약 완료 {snapshot.metrics.reservedCount}석</span>
      </div>
    </header>
  );
}
```

- [ ] **Step 6: Update MyTicketPanel**

Update `frontend/src/components/MyTicketPanel.tsx` props:

```typescript
import type { EventParticipantView, LiveEventStatus } from '../api/liveEventApi';

interface MyTicketPanelProps {
  status: LiveEventStatus;
  participant: EventParticipantView | null;
  loading: boolean;
  onJoin: () => void;
  onReserve: () => void;
  onPay: () => void;
}
```

Disable reserve unless joined and not ended:

```typescript
const reserveDisabled = !participant || status === 'READY' || status === 'ENDED' || !canReserve(participant);
```

Render reserve button text:

```tsx
<button className="primary-action icon-action" disabled={reserveDisabled} onClick={onReserve}>
  <Ticket size={18} /> {status === 'COUNTDOWN' ? '대기열 입장' : '예약하기'}
</button>
```

- [ ] **Step 7: Update EventActivityPanel**

Replace manual AI button with lifecycle buttons:

```typescript
interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  onStart: () => void;
  onReset: () => void;
}
```

Button block:

```tsx
{snapshot.status === 'READY' ? (
  <button className="secondary-action compact" onClick={onStart}>이벤트 시작하기</button>
) : null}
{snapshot.status === 'ENDED' ? (
  <button className="secondary-action compact" onClick={onReset}>새 이벤트 시작</button>
) : null}
```

- [ ] **Step 8: Update SeatMap**

Update props:

```typescript
import type { EventParticipantView, LiveEventStatus } from '../api/liveEventApi';
import type { SeatView } from '../api/simulationApi';
import { canSelectSeat } from '../domain/liveEventSelectors';

interface SeatMapProps {
  status: LiveEventStatus;
  seats: SeatView[];
  participant: EventParticipantView | null;
  selectedSeatLabel: string | null;
  onSelectSeat?: (seatId: number) => void;
}
```

Use disabled reason:

```typescript
const selection = canSelectSeat(status, participant);
const disabled = seat.status !== 'AVAILABLE' || !selection.allowed;
```

Show reason:

```tsx
{selection.message ? <p className="seat-map-message">{selection.message}</p> : null}
```

- [ ] **Step 9: Wire App**

Modify `frontend/src/App.tsx` imports:

```typescript
import { QueuePanel } from './components/QueuePanel';
```

Add message banner:

```tsx
{room.message ? <div className="info-banner">{room.message}</div> : null}
```

Update component props:

```tsx
<MyTicketPanel
  status={room.snapshot.status}
  participant={room.myParticipant}
  loading={room.loading}
  onJoin={() => void room.join('나')}
  onReserve={() => void room.reserve()}
  onPay={() => void room.pay()}
/>
<QueuePanel snapshot={room.snapshot} participantId={room.participantId} />
<SeatMap
  status={room.snapshot.status}
  seats={room.snapshot.seats}
  participant={room.myParticipant}
  selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
  onSelectSeat={(seatId) => void room.selectSeat(seatId)}
/>
<EventActivityPanel snapshot={room.snapshot} onStart={() => void room.start()} onReset={() => void room.reset()} />
```

- [ ] **Step 10: Add CSS**

Append to `frontend/src/styles.css`:

```css
.info-banner {
  border: 1px solid #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 8px;
  padding: 10px 12px;
  font-weight: 700;
}

.queue-panel {
  min-height: 220px;
}

.queue-list {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}

.queue-row {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.queue-row-me {
  border-color: #2563eb;
  background: #eff6ff;
}

.queue-row em {
  font-style: normal;
  color: #64748b;
  font-size: 12px;
}

.seat-map-message {
  margin: 8px 0 0;
  color: #b45309;
  font-weight: 700;
}
```

- [ ] **Step 11: Run frontend UI tests and build**

Run:

```powershell
cd frontend
npm.cmd test -- --run src/App.test.tsx src/components/QueuePanel.test.tsx src/domain/liveEventSelectors.test.ts
npm.cmd run build
```

Expected:

```text
Test Files  3 passed
✓ built
```

- [ ] **Step 12: Commit**

Run:

```powershell
git add frontend/src
git commit -m "feat: add user-started event room UI"
```

---

## Task 7: Update Docker Flow And Fast Local Verification Settings

**Files:**

- Modify only if needed: `infra/docker-compose.yml`
- Modify only if needed: `backend/src/main/resources/application-local.yml`

- [ ] **Step 1: Add optional fast local env overrides if manual verification is too slow**

If waiting 60 seconds + 5 minutes is too slow for repeated Docker checks, add commented documentation to `infra/docker-compose.yml` near api-a/api-b environment blocks:

```yaml
      # For faster local lifecycle testing, temporarily override:
      # LIVE_EVENT_COUNTDOWN_SECONDS: 5
      # LIVE_EVENT_OPEN_WINDOW_SECONDS: 30
```

Do not change defaults in committed local/prod config. Defaults must remain 60 seconds and 300 seconds.

- [ ] **Step 2: Run backend and frontend checks**

Run:

```powershell
cd backend
.\gradlew.bat test
cd ..\frontend
npm.cmd test
npm.cmd run build
```

Expected:

```text
BUILD SUCCESSFUL
Test Files all passed
✓ built
```

- [ ] **Step 3: Commit only if files changed**

If `infra/docker-compose.yml` changed:

```powershell
git add infra/docker-compose.yml
git commit -m "docs: note fast live event docker overrides"
```

If no files changed, skip commit.

---

## Task 8: End-To-End Docker Verification

**Files:**

- Modify only if verification exposes defects.

- [ ] **Step 1: Build and start Docker stack**

Run:

```powershell
cd infra
docker compose up -d --build
docker compose restart nginx
docker compose ps
```

Expected:

```text
infra-api-a-1 Up ... healthy
infra-api-b-1 Up ... healthy
infra-postgres-1 Up ... healthy
infra-redis-1 Up ... healthy
infra-nginx-1 Up ... 0.0.0.0:8080->8080/tcp
```

- [ ] **Step 2: Verify active event is shared through nginx**

Run:

```powershell
$responses = 1..4 | ForEach-Object { curl.exe -s http://localhost:8080/api/events/active | ConvertFrom-Json }
$responses | Select-Object eventId,title,status,generation,seatCount | ConvertTo-Json -Depth 3
```

Expected:

```json
[
  {
    "eventId": "00000000-0000-0000-0000-000000000001",
    "title": "부산 콘서트 티켓팅",
    "status": "READY",
    "generation": 1,
    "seatCount": 120
  }
]
```

Every entry must have the same `eventId`, `status`, and `generation`.

- [ ] **Step 3: Verify start countdown**

Run:

```powershell
$active = curl.exe -s http://localhost:8080/api/events/active | ConvertFrom-Json
$started = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/start"
$started | ConvertTo-Json -Depth 4
```

Expected:

```text
status = COUNTDOWN
opensAt is about 60 seconds in the future
endsAt is about 5 minutes after opensAt
```

- [ ] **Step 4: Verify before-open seat hold is blocked without 500**

Run:

```powershell
$joined = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants" -ContentType application/json -Body '{"displayName":"권"}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/queue"
$holdBeforeOpen = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/seats/1/hold"
$holdBeforeOpen | ConvertTo-Json -Depth 4
```

Expected:

```text
status = NOT_OPEN
message = 티켓팅 시작 전입니다.
```

- [ ] **Step 5: Verify open flow**

Wait until `opensAt` has passed, then run:

```powershell
$hold = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/seats/1/hold"
$duplicateHold = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/seats/2/hold"
$payment = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/payment-confirm"
Start-Sleep -Seconds 15
$snapshot = Invoke-RestMethod -Uri "http://localhost:8080/api/events/$($active.eventId)/snapshot?participantId=$($joined.participantId)"
[pscustomobject]@{
  holdStatus = $hold.status
  duplicateHoldStatus = $duplicateHold.status
  duplicateHoldMessage = $duplicateHold.message
  paymentStatus = $payment.status
  participants = $snapshot.participants.Count
  seats = ($snapshot.seats | Group-Object status | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', '
  users = ($snapshot.participants | Group-Object status | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', '
  serverStats = ($snapshot.serverStats | ForEach-Object { "$($_.serverId):$($_.requestCount)" }) -join ', '
} | ConvertTo-Json -Depth 5
```

Expected:

```text
holdStatus = PAYMENT_PENDING
duplicateHoldStatus = ALREADY_HOLDING
duplicateHoldMessage = 이미 선점한 좌석이 있습니다.
paymentStatus = PAYMENT_REQUESTED
participants includes AI participants after staggered start
serverStats includes api-a, api-b, worker
```

- [ ] **Step 6: Verify frontend dev server**

Run:

```powershell
cd ..\frontend
npm.cmd run dev -- --host 127.0.0.1
```

Expected:

```text
Local: http://127.0.0.1:5173/
```

Open `http://127.0.0.1:5173/` and verify:

- `READY` shows `이벤트 시작하기`.
- `COUNTDOWN` shows countdown and hides manual AI button.
- Before `OPEN`, clicking a seat is disabled with a visible message.
- `OPEN` allows one seat hold and payment confirmation.
- Holding a second seat is blocked.
- Queue panel shows queue size and approximate position.
- `ENDED` shows `새 이벤트 시작`.

- [ ] **Step 7: Commit verification fixes if any**

If no files changed, do not commit.

If verification required fixes:

```powershell
git add backend frontend infra
git commit -m "fix: stabilize user-started live event flow"
```

---

## Self-Review

Spec coverage:

- Visitor-started event: Tasks 1 and 2 add `READY -> COUNTDOWN -> OPEN -> ENDED` lifecycle and start/reset APIs.
- 60 second countdown and 5 minute open window: Tasks 1 and 2 add configurable durations and lifecycle response fields.
- Multi-server consistency: Task 1 stores lifecycle outside API memory through `LiveEventStateStore` and Redis implementation.
- AI stagger: Task 4 adds deterministic batches and one-time auto trigger after open.
- Visible queue: Tasks 5 and 6 add queue selectors and `QueuePanel`.
- Multiple seat hold bug: Task 3 blocks duplicate holds in backend and Task 6 disables additional seat clicks.
- Realistic seat click UX: Tasks 3, 5, and 6 return/display domain messages for not open, ended, conflicts, and already holding.
- Docker/local verification: Task 8 validates nginx, api-a/api-b, Redis, PostgreSQL, Kafka, worker, traffic-generator, and frontend.

Completeness scan:

- No incomplete marker text, unfinished sections, or vague edge-case instructions remain.
- Every task has concrete file paths, test commands, and expected outputs.

Type consistency:

- Backend lifecycle status uses `LiveEventStatus` values `READY`, `COUNTDOWN`, `OPEN`, `ENDED`.
- Frontend `LiveEventStatus` matches backend status strings.
- `LiveEventResponse` and `LiveEventSnapshot` both include `generation`, `opensAt`, and `endsAt`.
- `startEvent` and `resetEvent` names are used consistently in backend service/controller and frontend API/hook.
