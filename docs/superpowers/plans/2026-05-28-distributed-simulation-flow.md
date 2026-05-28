# Distributed Simulation Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make generated virtual users enter a Redis-backed queue, attempt random seat holds through both API instances, retry on conflicts, and flow successful holds into Kafka payment processing.

**Architecture:** The traffic generator continues to drive virtual-user HTTP requests through nginx. The API orchestrates Redis snapshot updates, Redis waiting queue admission, PostgreSQL seat holds, and Kafka payment event publication. PostgreSQL remains the concurrency authority; Redis remains the live read model for the future Korean dashboard.

**Tech Stack:** Java 17, Spring Boot 3.3, Gradle Groovy DSL, Spring MVC, Spring Data Redis, JdbcTemplate/PostgreSQL, Spring Kafka, Docker Compose, nginx.

---

## Scope

This plan implements the backend simulation flow only. It does not create the React/Vite frontend and does not provision AWS resources.

The finished local behavior should be:

- `POST /api/simulations` creates Redis snapshot state and PostgreSQL simulation inventory.
- `POST /api/simulations/{simulationId}/run` starts the generator.
- each generated user calls `/queue`, then repeatedly calls `/seat-attempt`.
- `/queue` records visible queue state.
- `/seat-attempt` admits users from Redis queue, randomly chooses an available seat, tries a PostgreSQL hold, records conflicts, and publishes Kafka payment events after successful holds.
- payment result events update the Redis snapshot.
- `GET /api/simulations/{simulationId}` shows seats, users, attempts, conflicts, server stats, and payment outcomes.

## File Structure

Create or modify these files:

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserCommandResponse.java`
  - Add message and selected seat fields for UI-friendly command results.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
  - Add `/seat-attempt` endpoint.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
  - Orchestrate queue entry, seat attempts, PostgreSQL hold, and Kafka publish.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
  - Add focused mutation methods used by distributed command handling.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
  - Implement Redis-backed snapshot mutations and deterministic virtual-user IDs.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
  - Keep demo profile compatible with the expanded gateway.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationInventoryService.java`
  - Initialize PostgreSQL concert, base seats, simulation session, virtual users, and simulation seats.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/PaymentResultListener.java`
  - Consume payment result events and apply them to Redis snapshot state.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/VirtualUserHttpClient.java`
  - Keep the same interface but expand implementation behavior.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java`
  - Queue, then repeatedly call `/seat-attempt`.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java`
  - Keep parallel user execution.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java`
  - Fix Korean payment messages if still mojibake in source.

- Modify tests under:
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStoreTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationInventoryServiceTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/PaymentResultListenerTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/generator/TrafficGeneratorServiceTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`

---

### Task 1: Expand Virtual User Command API

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserCommandResponse.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`

- [ ] **Step 1: Write failing controller assertions for command response details**

Update `virtualUserEnterQueueReturnsHandlingServer` in `SimulationControllerTest` so the mocked response includes message and selected seat:

```java
when(simulationService.enterQueue(eq(simulationId), eq(userId))).thenReturn(new VirtualUserCommandResponse(
        simulationId,
        userId,
        "QUEUED",
        "api-test",
        "대기열에 진입했습니다.",
        null
));
```

Add assertions:

```java
.andExpect(jsonPath("$.message").value("대기열에 진입했습니다."))
.andExpect(jsonPath("$.selectedSeatLabel").doesNotExist());
```

Add a new test:

```java
@Test
void virtualUserSeatAttemptReturnsHandlingServerAndSelectedSeat() throws Exception {
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000006");
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000106");
    when(simulationService.attemptSeat(eq(simulationId), eq(userId))).thenReturn(new VirtualUserCommandResponse(
            simulationId,
            userId,
            "PAYMENT_REQUESTED",
            "api-test",
            "A-1 좌석을 선택했습니다. 결제를 요청했습니다.",
            "A-1"
    ));

    mvc.perform(post("/api/simulations/{simulationId}/users/{userId}/seat-attempt", simulationId, userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
            .andExpect(jsonPath("$.virtualUserId").value(userId.toString()))
            .andExpect(jsonPath("$.status").value("PAYMENT_REQUESTED"))
            .andExpect(jsonPath("$.handledBy").value("api-test"))
            .andExpect(jsonPath("$.message").value("A-1 좌석을 선택했습니다. 결제를 요청했습니다."))
            .andExpect(jsonPath("$.selectedSeatLabel").value("A-1"));
}
```

- [ ] **Step 2: Run the failing controller test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected: FAIL because `VirtualUserCommandResponse` lacks the new fields and `attemptSeat` endpoint does not exist.

- [ ] **Step 3: Expand `VirtualUserCommandResponse`**

Change `VirtualUserCommandResponse.java` to:

```java
package com.timedeal.seatreservation.simulation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VirtualUserCommandResponse(
        UUID simulationId,
        UUID virtualUserId,
        String status,
        String handledBy,
        String message,
        String selectedSeatLabel
) {
}
```

- [ ] **Step 4: Add controller endpoint**

Add to `SimulationController`:

```java
@PostMapping("/{simulationId}/users/{userId}/seat-attempt")
public VirtualUserCommandResponse attemptSeat(
        @PathVariable UUID simulationId,
        @PathVariable UUID userId
) {
    return simulationService.attemptSeat(simulationId, userId);
}
```

- [ ] **Step 5: Add temporary service method for compilation**

Add to `SimulationService`:

```java
public VirtualUserCommandResponse attemptSeat(UUID simulationId, UUID userId) {
    return new VirtualUserCommandResponse(
            simulationId,
            userId,
            "WAITING",
            serverIdentity.id(),
            "아직 대기 중입니다.",
            null
    );
}
```

Update `enterQueue` to use the expanded record:

```java
public VirtualUserCommandResponse enterQueue(UUID simulationId, UUID userId) {
    return new VirtualUserCommandResponse(
            simulationId,
            userId,
            "QUEUED",
            serverIdentity.id(),
            "대기열에 진입했습니다.",
            null
    );
}
```

- [ ] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java
git commit -m "feat: add virtual user seat attempt api"
```

---

### Task 2: Initialize PostgreSQL Inventory For Each Simulation

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationInventoryService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationInventoryServiceTest.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`

- [ ] **Step 1: Write inventory service test**

Create `SimulationInventoryServiceTest.java`:

```java
package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SimulationInventoryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SimulationInventoryService service = new SimulationInventoryService(jdbc);

    @Test
    void initializesConcertSeatsSessionVirtualUsersAndSimulationSeats() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000110");
        SimulationSnapshot snapshot = new SimulationSnapshot(
                simulationId,
                List.of(new SeatView(1L, "A-1", SeatStatus.AVAILABLE)),
                List.of(new VirtualUserView(
                        userId,
                        "사용자 1",
                        VirtualUserStatus.QUEUED,
                        null,
                        List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                        0,
                        0
                )),
                new SimulationMetrics(1, 0, 0, 0, 0, 0),
                List.of(),
                false
        );

        service.initialize(snapshot, 1);

        verify(jdbc).update("insert into concerts(id, title) values (1, ?) on conflict (id) do nothing", "분산 좌석 예매 콘서트");
        verify(jdbc).update("insert into seats(id, concert_id, seat_label, status) values (?, 1, ?, 'AVAILABLE') on conflict (id) do nothing", 1L, "A-1");
        verify(jdbc).update("insert into simulation_sessions(id, concert_id, requested_users, status) values (?, 1, ?, 'CREATED')", simulationId, 1);
        verify(jdbc).update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?) on conflict (id) do nothing", userId, simulationId, "사용자 1", "QUEUED");
        verify(jdbc).update("insert into simulation_seats(simulation_id, seat_id, seat_label, status) values (?, ?, ?, 'AVAILABLE') on conflict (simulation_id, seat_id) do nothing", simulationId, 1L, "A-1");
    }
}
```

- [ ] **Step 2: Run the failing inventory test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationInventoryServiceTest"
```

Expected: FAIL because `SimulationInventoryService` does not exist.

- [ ] **Step 3: Implement `SimulationInventoryService`**

Create `SimulationInventoryService.java`:

```java
package com.timedeal.seatreservation.simulation;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!demo")
public class SimulationInventoryService {
    private static final long CONCERT_ID = 1L;
    private static final String CONCERT_TITLE = "분산 좌석 예매 콘서트";

    private final JdbcTemplate jdbc;

    public SimulationInventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize(SimulationSnapshot snapshot, int requestedUsers) {
        jdbc.update("insert into concerts(id, title) values (1, ?) on conflict (id) do nothing", CONCERT_TITLE);

        for (SeatView seat : snapshot.seats()) {
            jdbc.update(
                    "insert into seats(id, concert_id, seat_label, status) values (?, 1, ?, 'AVAILABLE') on conflict (id) do nothing",
                    seat.id(),
                    seat.label()
            );
        }

        jdbc.update(
                "insert into simulation_sessions(id, concert_id, requested_users, status) values (?, 1, ?, 'CREATED')",
                snapshot.simulationId(),
                requestedUsers
        );

        for (VirtualUserView user : snapshot.users()) {
            jdbc.update(
                    "insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, ?, ?) on conflict (id) do nothing",
                    user.id(),
                    snapshot.simulationId(),
                    user.displayName(),
                    user.status().name()
            );
        }

        for (SeatView seat : snapshot.seats()) {
            jdbc.update(
                    "insert into simulation_seats(simulation_id, seat_id, seat_label, status) values (?, ?, ?, 'AVAILABLE') on conflict (simulation_id, seat_id) do nothing",
                    snapshot.simulationId(),
                    seat.id(),
                    seat.label()
            );
        }
    }
}
```

- [ ] **Step 4: Inject optional inventory initialization into service**

Add field and constructor parameter to `SimulationService`:

```java
private final SimulationInventoryService inventoryService;
```

Constructor:

```java
public SimulationService(
        SimulationStateGateway stateStore,
        ServerIdentity serverIdentity,
        ObjectProvider<TrafficGeneratorClient> trafficGeneratorClient,
        ObjectProvider<SimulationInventoryService> inventoryService
) {
    this.stateStore = stateStore;
    this.serverIdentity = serverIdentity;
    this.trafficGeneratorClient = trafficGeneratorClient.getIfAvailable(() -> (simulationId, request) -> {
    });
    this.inventoryService = inventoryService.getIfAvailable();
}
```

Test constructor:

```java
SimulationService(SimulationStateGateway stateStore) {
    this.stateStore = stateStore;
    this.serverIdentity = new ServerIdentity("api-test");
    this.trafficGeneratorClient = (simulationId, request) -> {
    };
    this.inventoryService = null;
}
```

Update `createSimulation`:

```java
SimulationSnapshot snapshot = stateStore.create(simulationId, request.virtualUserCount());
if (inventoryService != null) {
    inventoryService.initialize(snapshot, request.virtualUserCount());
}
```

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationInventoryServiceTest" --tests "*SimulationServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: initialize simulation inventory in postgres"
```

---

### Task 3: Add Redis Snapshot Mutations And Deterministic User IDs

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStoreTest.java`

- [ ] **Step 1: Write Redis state store test**

Create `RedisSimulationStateStoreTest.java`:

```java
package com.timedeal.seatreservation.simulation.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID userId = UUID.nameUUIDFromBytes((simulationId + ":1").getBytes(StandardCharsets.UTF_8));
        SimulationSnapshot initial = store.create(simulationId, 1);

        when(values.get("simulation:00000000-0000-0000-0000-000000000021:snapshot"))
                .thenReturn(objectMapper.writeValueAsString(initial));
        when(values.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        SimulationSnapshot updated = store.registerQueueEntry(simulationId, userId, "api-a");

        assertThat(updated.users().get(0).status()).isEqualTo(VirtualUserStatus.QUEUED);
        assertThat(updated.users().get(0).timeline()).anyMatch(entry -> entry.message().equals("대기열에 진입했습니다."));
        assertThat(updated.serverStats()).anyMatch(stats -> stats.serverId().equals("api-a") && stats.requestCount() == 1);
    }
}
```

- [ ] **Step 2: Run the failing Redis store test**

Run:

```powershell
.\gradlew.bat test --tests "*RedisSimulationStateStoreTest"
```

Expected: FAIL because new mutation methods do not exist and deterministic IDs are not used.

- [ ] **Step 3: Expand `SimulationStateGateway`**

Change `SimulationStateGateway.java`:

```java
package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;

import java.util.UUID;

public interface SimulationStateGateway {
    SimulationSnapshot create(UUID simulationId, int virtualUserCount);

    SimulationSnapshot snapshot(UUID simulationId);

    SimulationSnapshot markRunning(UUID simulationId);

    SimulationSnapshot registerQueueEntry(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordWaiting(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordSeatConflict(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy);

    SimulationSnapshot recordNoSeatAvailable(UUID simulationId, UUID virtualUserId, String handledBy);

    SimulationSnapshot recordPaymentRequested(UUID simulationId, UUID virtualUserId, SeatView seat, String handledBy);

    SimulationSnapshot applyPaymentResult(PaymentResultEvent event);
}
```

- [ ] **Step 4: Implement deterministic user IDs**

In `RedisSimulationStateStore.create`, call `createUsers(simulationId, virtualUserCount)`.

Use:

```java
UUID userId = UUID.nameUUIDFromBytes((simulationId + ":" + index).getBytes(StandardCharsets.UTF_8));
```

Create display names as:

```java
"사용자 " + index
```

Initial timeline:

```java
List.of(new TimelineEntry("생성", "가상 사용자가 생성되었습니다."))
```

- [ ] **Step 5: Implement snapshot mutation helpers in Redis store**

Add helper methods to `RedisSimulationStateStore`:

```java
private SimulationSnapshot mutate(UUID simulationId, UnaryOperator<SimulationSnapshot> mutator) {
    String lockKey = "simulation:%s:lock".formatted(simulationId);
    Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(2));
    if (!Boolean.TRUE.equals(locked)) {
        throw new IllegalStateException("Simulation snapshot is busy: " + simulationId);
    }
    try {
        SimulationSnapshot updated = mutator.apply(snapshot(simulationId));
        save(updated);
        return updated;
    } finally {
        redisTemplate.delete(lockKey);
    }
}
```

Add list update helpers:

```java
private List<VirtualUserView> updateUser(List<VirtualUserView> users, UUID userId, UnaryOperator<VirtualUserView> updater) {
    return users.stream()
            .map(user -> user.id().equals(userId) ? updater.apply(user) : user)
            .toList();
}

private List<SeatView> updateSeat(List<SeatView> seats, long seatId, SeatStatus status) {
    return seats.stream()
            .map(seat -> seat.id() == seatId ? new SeatView(seat.id(), seat.label(), status) : seat)
            .toList();
}
```

Add server stats helper:

```java
private List<ServerStatsView> incrementServerStats(List<ServerStatsView> stats, String serverId, boolean conflict, boolean success) {
    List<ServerStatsView> updated = new ArrayList<>();
    boolean found = false;
    for (ServerStatsView current : stats) {
        if (current.serverId().equals(serverId)) {
            found = true;
            updated.add(new ServerStatsView(
                    serverId,
                    current.requestCount() + 1,
                    current.conflictCount() + (conflict ? 1 : 0),
                    current.successCount() + (success ? 1 : 0)
            ));
        } else {
            updated.add(current);
        }
    }
    if (!found) {
        updated.add(new ServerStatsView(serverId, 1, conflict ? 1 : 0, success ? 1 : 0));
    }
    return updated;
}
```

- [ ] **Step 6: Implement Redis mutation methods**

Implement `registerQueueEntry` with status `QUEUED`, timeline `대기열`, and server stat increment.

Implement `recordWaiting` with timeline `대기 중`.

Implement `recordSeatConflict` with user status `SELECTING_SEAT`, timeline label `좌석 선택 실패`, message `이미 선택된 좌석입니다: <seatLabel>`, attempt count increment via the timeline, and conflict stat increment.

Implement `recordNoSeatAvailable` with timeline label `좌석 선택 실패`, message `선택 가능한 좌석이 없습니다.`, attempt count increment, and conflict stat increment.

Implement `recordPaymentRequested` with user status `PAYMENT_IN_PROGRESS`, selected seat label, seat status `PAYMENT_IN_PROGRESS`, timeline label `좌석 선택`, message `<seatLabel> 좌석을 선택했습니다. 결제를 요청했습니다.`, and success stat increment.

Implement `applyPaymentResult`:

```java
if (event.success()) {
    user status = RESERVED
    seat status = RESERVED
    timeline label = "결제 성공"
    message = "결제 성공"
} else {
    user status = FAILED
    seat status = AVAILABLE
    timeline label = "결제 실패"
    message = "결제 실패"
}
```

Recalculate metrics inside `save` input by adding a private `withMetrics` method before returning new snapshots.

- [ ] **Step 7: Add compatible demo implementations**

In `SimulationStateStore`, implement the expanded `SimulationStateGateway` methods using the same status and timeline semantics. This can reuse the mutable state already present; it only needs to keep demo tests compiling.

- [ ] **Step 8: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*RedisSimulationStateStoreTest" --tests "*SimulationServiceTest" --tests "*SimulationRunnerTest"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/main/java/com/timedeal/seatreservation/simulation/redis backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: add redis simulation state mutations"
```

---

### Task 4: Implement Queue Entry And Seat Attempt Orchestration

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`

- [ ] **Step 1: Write service tests for queue and waiting**

Add tests to `SimulationServiceTest` using Mockito fakes for `WaitingQueueService`, `SeatReservationService`, and `KafkaTemplate`.

Test queue:

```java
@Test
void enterQueueRegistersUserInRedisQueueAndSnapshot() {
    RecordingStateGateway state = new RecordingStateGateway();
    RecordingWaitingQueue waitingQueue = new RecordingWaitingQueue(false);
    SimulationService service = new SimulationService(
            state,
            new ServerIdentity("api-test"),
            (simulationId, request) -> {
            },
            null,
            waitingQueue,
            null,
            null,
            new Random(1)
    );
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000130");

    VirtualUserCommandResponse response = service.enterQueue(simulationId, userId);

    assertThat(waitingQueue.enteredSimulationId).isEqualTo(simulationId.toString());
    assertThat(waitingQueue.enteredUserId).isEqualTo(userId.toString());
    assertThat(state.registeredUserId).isEqualTo(userId);
    assertThat(response.status()).isEqualTo("QUEUED");
    assertThat(response.message()).isEqualTo("대기열에 진입했습니다.");
}
```

Test waiting:

```java
@Test
void seatAttemptReturnsWaitingWhenAdmissionTokenIsMissing() {
    RecordingStateGateway state = new RecordingStateGateway();
    RecordingWaitingQueue waitingQueue = new RecordingWaitingQueue(false);
    SimulationService service = new SimulationService(
            state,
            new ServerIdentity("api-test"),
            (simulationId, request) -> {
            },
            null,
            waitingQueue,
            null,
            null,
            new Random(1)
    );
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000031");
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000131");

    VirtualUserCommandResponse response = service.attemptSeat(simulationId, userId);

    assertThat(response.status()).isEqualTo("WAITING");
    assertThat(response.message()).isEqualTo("아직 대기 중입니다.");
}
```

- [ ] **Step 2: Write service tests for conflict and success**

Add a success test where `WaitingQueueService.hasAdmissionToken` is true, state snapshot has one available seat, `SeatReservationService.holdSeat` returns `HELD`, and Kafka template receives a `PaymentRequestedEvent`.

Add a conflict test where hold returns `ALREADY_HELD`, response status is `RETRY`, and message contains `이미 선택된 좌석입니다`.

- [ ] **Step 3: Run failing service tests**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationServiceTest"
```

Expected: FAIL because constructors and orchestration do not exist.

- [ ] **Step 4: Add dependencies to `SimulationService`**

Add optional fields:

```java
private final WaitingQueueService waitingQueueService;
private final SeatReservationService seatReservationService;
private final KafkaTemplate<String, PaymentRequestedEvent> paymentKafkaTemplate;
private final Random random;
```

Use `ObjectProvider` in the Spring constructor:

```java
ObjectProvider<WaitingQueueService> waitingQueueService,
ObjectProvider<SeatReservationService> seatReservationService,
ObjectProvider<KafkaTemplate<String, PaymentRequestedEvent>> paymentKafkaTemplate
```

Keep the existing package-private test constructor and add a second package-private constructor accepting explicit fakes.

- [ ] **Step 5: Implement `enterQueue`**

Use:

```java
if (waitingQueueService != null) {
    waitingQueueService.enterQueue(simulationId.toString(), userId.toString());
}
stateStore.registerQueueEntry(simulationId, userId, serverIdentity.id());
return new VirtualUserCommandResponse(
        simulationId,
        userId,
        "QUEUED",
        serverIdentity.id(),
        "대기열에 진입했습니다.",
        null
);
```

- [ ] **Step 6: Implement admission helper**

In `attemptSeat`, before selecting a seat:

```java
if (waitingQueueService != null && !waitingQueueService.hasAdmissionToken(simulationId.toString(), userId.toString())) {
    List<String> candidates = waitingQueueService.pickAdmissionCandidates(simulationId.toString(), 10);
    for (String candidate : candidates) {
        waitingQueueService.issueAdmissionToken(simulationId.toString(), candidate);
        waitingQueueService.removeAdmissionCandidate(simulationId.toString(), candidate);
    }
    if (!waitingQueueService.hasAdmissionToken(simulationId.toString(), userId.toString())) {
        stateStore.recordWaiting(simulationId, userId, serverIdentity.id());
        return new VirtualUserCommandResponse(simulationId, userId, "WAITING", serverIdentity.id(), "아직 대기 중입니다.", null);
    }
}
```

- [ ] **Step 7: Implement random seat attempt**

Inside `attemptSeat`:

```java
SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
List<SeatView> availableSeats = snapshot.seats().stream()
        .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
        .toList();
if (availableSeats.isEmpty()) {
    stateStore.recordNoSeatAvailable(simulationId, userId, serverIdentity.id());
    return new VirtualUserCommandResponse(simulationId, userId, "RETRY", serverIdentity.id(), "선택 가능한 좌석이 없습니다.", null);
}

SeatView seat = availableSeats.get(random.nextInt(availableSeats.size()));
String idempotencyKey = simulationId + ":" + userId + ":" + seat.id();
SeatReservationResult result = seatReservationService.holdSeat(simulationId, userId, seat.id(), idempotencyKey);
```

If result is `ALREADY_HELD`:

```java
stateStore.recordSeatConflict(simulationId, userId, seat, serverIdentity.id());
return new VirtualUserCommandResponse(simulationId, userId, "RETRY", serverIdentity.id(), "이미 선택된 좌석입니다: " + seat.label(), seat.label());
```

If result is `HELD` or `IDEMPOTENT_REPLAY`:

```java
stateStore.recordPaymentRequested(simulationId, userId, seat, serverIdentity.id());
paymentKafkaTemplate.send("payment.events", String.valueOf(result.reservationId()), new PaymentRequestedEvent(
        simulationId,
        userId,
        result.reservationId(),
        seat.id(),
        "payment-" + result.reservationId(),
        serverIdentity.id()
));
return new VirtualUserCommandResponse(simulationId, userId, "PAYMENT_REQUESTED", serverIdentity.id(), seat.label() + " 좌석을 선택했습니다. 결제를 요청했습니다.", seat.label());
```

- [ ] **Step 8: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationServiceTest" --tests "*SimulationControllerTest"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: orchestrate queue and seat attempts"
```

---

### Task 5: Make Traffic Generator Drive Queue And Seat Attempts

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/generator/TrafficGeneratorServiceTest.java`

- [ ] **Step 1: Write generator behavior test**

Update the fake `VirtualUserHttpClient` in `TrafficGeneratorServiceTest` to keep asserting that every virtual user is started through the configured target. This existing test remains valid.

Add a separate unit test for `HttpVirtualUserHttpClient` by introducing a package-private constructor that accepts `RestClient`.

Use a fake `RestClient` only if feasible. If `RestClient` is cumbersome to mock, extract a new interface:

```java
interface VirtualUserCommandClient {
    VirtualUserCommandResponse postQueue(String baseUrl, UUID simulationId, UUID userId);
    VirtualUserCommandResponse postSeatAttempt(String baseUrl, UUID simulationId, UUID userId);
}
```

Then test that `HttpVirtualUserHttpClient.runUser` calls queue once and seat-attempt until `PAYMENT_REQUESTED`, `COMPLETED`, or max attempts is reached.

- [ ] **Step 2: Run failing generator tests**

Run:

```powershell
.\gradlew.bat test --tests "*TrafficGeneratorServiceTest"
```

Expected: FAIL before implementation if the new command client is introduced.

- [ ] **Step 3: Implement bounded virtual-user loop**

In `HttpVirtualUserHttpClient.runUser`:

```java
UUID virtualUserId = UUID.nameUUIDFromBytes(
        (simulationId + ":" + virtualUserNumber).getBytes(StandardCharsets.UTF_8)
);

postQueue(baseUrl, simulationId, virtualUserId);
for (int attempt = 0; attempt < 30; attempt++) {
    VirtualUserCommandResponse response = postSeatAttempt(baseUrl, simulationId, virtualUserId);
    if ("PAYMENT_REQUESTED".equals(response.status())
            || "COMPLETED".equals(response.status())
            || "FAILED".equals(response.status())) {
        return;
    }
    sleepBriefly();
}
```

Use `Thread.sleep(100)` in `sleepBriefly`, preserving interrupt status.

- [ ] **Step 4: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*TrafficGeneratorServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/generator backend/src/test/java/com/timedeal/seatreservation/generator
git commit -m "feat: drive virtual users through seat attempts"
```

---

### Task 6: Apply Payment Results To Simulation Snapshot

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/PaymentResultListener.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/PaymentResultListenerTest.java`

- [ ] **Step 1: Write listener test**

Create `PaymentResultListenerTest.java`:

```java
package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentResultListenerTest {
    @Test
    void appliesPaymentResultToStateStore() {
        RecordingStateGateway state = new RecordingStateGateway();
        PaymentResultListener listener = new PaymentResultListener(state);
        PaymentResultEvent event = new PaymentResultEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000060"),
                UUID.fromString("00000000-0000-0000-0000-000000000160"),
                101L,
                7L,
                true,
                "결제 성공",
                "worker"
        );

        listener.handle(event);

        assertThat(state.appliedPaymentResult).isEqualTo(event);
    }
}
```

- [ ] **Step 2: Run failing listener test**

Run:

```powershell
.\gradlew.bat test --tests "*PaymentResultListenerTest"
```

Expected: FAIL because listener does not exist.

- [ ] **Step 3: Implement listener**

Create `PaymentResultListener.java`:

```java
package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.payment.PaymentResultEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class PaymentResultListener {
    private final SimulationStateGateway stateStore;

    public PaymentResultListener(SimulationStateGateway stateStore) {
        this.stateStore = stateStore;
    }

    @KafkaListener(topics = "payment-results.events", groupId = "payment-result-applier")
    public void handle(PaymentResultEvent event) {
        stateStore.applyPaymentResult(event);
    }
}
```

- [ ] **Step 4: Fix Korean payment messages**

In `PaymentSimulationWorker.simulate`:

```java
String message = success ? "결제 성공" : "결제 실패";
```

Update `PaymentSimulationWorkerTest` assertions to expect proper Korean strings.

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*PaymentResultListenerTest" --tests "*PaymentSimulationWorkerTest" --tests "*RedisSimulationStateStoreTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation/PaymentResultListener.java backend/src/main/java/com/timedeal/seatreservation/payment backend/src/test/java/com/timedeal/seatreservation
git commit -m "feat: apply payment results to simulation state"
```

---

### Task 7: End-To-End Local Verification

**Files:**
- Modify only if verification finds a defect in files changed by previous tasks.

- [ ] **Step 1: Run focused tests**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest" --tests "*SimulationServiceTest" --tests "*RedisSimulationStateStoreTest" --tests "*SimulationInventoryServiceTest" --tests "*PaymentResultListenerTest" --tests "*TrafficGeneratorServiceTest" --tests "*PaymentSimulationWorkerTest" --tests "*SeatReservationServiceTest"
```

Expected: PASS.

- [ ] **Step 2: Build backend jar**

Run:

```powershell
.\gradlew.bat bootJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Rebuild and start local compose**

Run:

```powershell
cd C:\Users\Kwon\Desktop\workspace\timedeal\infra
docker compose up -d --build
docker compose restart nginx
```

Expected: services start; `api-a` and `api-b` become healthy. Restarting nginx refreshes upstream container IPs after backend rebuilds.

- [ ] **Step 4: Create and run a simulation**

Run:

```powershell
$created = Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/simulations `
  -ContentType "application/json" `
  -Body '{"virtualUserCount":30}'

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/simulations/$($created.simulationId)/run" `
  -ContentType "application/json" `
  -Body '{"virtualUserCount":30,"concurrency":10}'
```

Expected: run response has `status = RUNNING`.

- [ ] **Step 5: Inspect snapshot**

Run:

```powershell
Start-Sleep -Seconds 5
$snapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/simulations/$($created.simulationId)"
$snapshot.metrics
$snapshot.serverStats
$snapshot.users | Select-Object -First 5
```

Expected:

- `serverStats` includes `api-a` and `api-b`.
- at least some users have `seatAttemptCount > 0`.
- at least some seats are `PAYMENT_IN_PROGRESS` or `RESERVED`.
- timelines include Korean messages for queue, seat selection, conflict, or payment.

- [ ] **Step 6: Inspect nginx and worker logs**

Run:

```powershell
docker compose logs --tail=200 nginx traffic-generator api-a api-b worker
```

Expected:

- nginx logs include `/queue` and `/seat-attempt`.
- requests come from `Java-http-client`.
- worker logs show Kafka consumer assignment and no repeated errors.

- [ ] **Step 7: Commit verification fixes if needed**

If verification required a code fix:

```powershell
git add <changed-files>
git commit -m "fix: stabilize distributed simulation flow"
```

If no fix was needed, do not create an empty commit.

---

## Self-Review

Spec coverage:

- Redis queue entry is covered by Tasks 3 and 4.
- Random seat selection and PostgreSQL hold attempts are covered by Task 4.
- Conflict retries are covered by Tasks 4 and 5.
- Kafka payment request and result application are covered by Tasks 4 and 6.
- Local runtime verification is covered by Task 7.

Placeholder scan:

- The plan has no `TBD` or unspecified implementation slots.
- The only conditional branch is the generator test extraction in Task 5; it includes the exact fallback interface and expected behavior.

Type consistency:

- `VirtualUserCommandResponse` is expanded before later tasks depend on `message` and `selectedSeatLabel`.
- `SimulationStateGateway` mutation methods are defined before `SimulationService` and `PaymentResultListener` call them.
- Kafka topic names match existing code: `payment.events` and `payment-results.events`.
