# Distributed Simulation Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-instance in-memory simulation runner with a foundation where generated virtual-user HTTP traffic goes through nginx, reaches both API instances, and shares state through Redis/PostgreSQL/Kafka-scoped identifiers.

**Architecture:** This plan builds the backend and local infrastructure foundation before the React dashboard work. It introduces `/api/**` routing, removes sticky nginx routing, adds server identity to responses, creates simulation-scoped database structures, moves local/prod simulation state toward Redis, and adds a traffic-generator profile that sends real HTTP requests through nginx.

**Tech Stack:** Java 17, Spring Boot 3.3, Gradle Groovy DSL, Spring MVC, Spring Data Redis, JdbcTemplate/PostgreSQL, Flyway, Spring Kafka, Docker Compose, nginx.

---

## Scope

This is Plan 1 of the reworked MVP. It intentionally does not create the React/Vite frontend, full UI observability panels, or AWS deployment files. Those should be separate plans after this foundation is implemented.

This plan must leave the system in a working local state where:

- API routes live under `/api`.
- nginx does not use `ip_hash`.
- `POST /api/simulations` creates a simulation but does not run all virtual users inside one API instance.
- `POST /api/simulations/{simulationId}/run` starts the traffic-generator.
- traffic-generator sends virtual-user HTTP requests through nginx.
- API responses and snapshots expose which server handled work.
- state keys and database rows are scoped by `simulationId`.

## File Structure

Create or modify these files:

- Modify: `infra/nginx.conf`
  - Route `/api/**` to `api-a/api-b`.
  - Remove `ip_hash`.
  - Keep SSE buffering disabled.

- Modify: `infra/docker-compose.yml`
  - Add `traffic-generator` service.
  - Pass `APP_INSTANCE_ID` to `api-a`, `api-b`, and `worker`.
  - Pass generator target URL as `http://nginx:8080`.

- Modify: `backend/src/main/resources/application-local.yml`
  - Add local generator target URL and app instance id defaults.

- Create: `backend/src/main/resources/application-prod.yml`
  - Add env-driven production settings for DB, Redis, Kafka, and instance id.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
  - Move routes to `/api/simulations`.
  - Add run endpoint.
  - Add virtual-user command endpoints.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
  - Split create from run.
  - Create simulation-scoped database and Redis state.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/RunSimulationRequest.java`
  - Request body for traffic-generator run settings.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/RunSimulationResponse.java`
  - Response body for generator start.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserCommandResponse.java`
  - Response body for generated virtual-user API calls.

- Create: `backend/src/main/java/com/timedeal/seatreservation/identity/ServerIdentity.java`
  - Expose `api-a`, `api-b`, `worker`, or local instance id.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
  - Store snapshots, users, timelines, server stats, and recent flow events in Redis for non-demo profiles.

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/SimulationRedisKeys.java`
  - Centralize Redis key names.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
  - Restrict current in-memory store to `demo` profile.

- Create: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorController.java`
  - Internal endpoint used by API to start a run.

- Create: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java`
  - Sends virtual-user HTTP requests to nginx.

- Create: `backend/src/main/java/com/timedeal/seatreservation/generator/VirtualUserHttpClient.java`
  - Thin HTTP client around `RestClient`.

- Create: `backend/src/main/resources/db/migration/V3__simulation_scoped_inventory.sql`
  - Add simulation-scoped seats and constraints.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentRequestedEvent.java`
  - Include `simulationId`, `virtualUserId`, and `handledBy`.

- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentResultEvent.java`
  - Include `simulationId`, `virtualUserId`, and `handledBy`.

- Modify tests under:
  - `backend/src/test/java/com/timedeal/seatreservation/infra/LocalInfrastructureFilesTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/generator/TrafficGeneratorServiceTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/simulation/redis/SimulationRedisKeysTest.java`
  - `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`

---

### Task 1: Move Public API Behind `/api` And Remove Sticky nginx

**Files:**
- Modify: `infra/nginx.conf`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/infra/LocalInfrastructureFilesTest.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`

- [ ] **Step 1: Write failing nginx routing assertions**

Modify `LocalInfrastructureFilesTest.nginxProxiesApiServersAndDisablesBufferingForSse`:

```java
@Test
void nginxProxiesApiServersWithoutStickyRouting() throws Exception {
    String nginx = Files.readString(Path.of("../infra/nginx.conf"));

    assertThat(nginx).doesNotContain("ip_hash;");
    assertThat(nginx).contains("server api-a:8080;");
    assertThat(nginx).contains("server api-b:8080;");
    assertThat(nginx).contains("location /api/");
    assertThat(nginx).contains("proxy_buffering off;");
}
```

- [ ] **Step 2: Run the failing infra test**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest"
```

Expected: FAIL because `ip_hash;` is still present and `/api/` routing is missing.

- [ ] **Step 3: Update nginx routing**

Change `infra/nginx.conf` to:

```nginx
events {}

http {
  upstream api_servers {
    server api-a:8080;
    server api-b:8080;
  }

  server {
    listen 8080;

    location /api/ {
      proxy_pass http://api_servers;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location ~ ^/api/simulations/.*/events$ {
      proxy_pass http://api_servers;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header Connection "";
      proxy_buffering off;
      proxy_cache off;
    }

    location /health {
      proxy_pass http://api_servers;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location / {
      proxy_pass http://api_servers;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
  }
}
```

- [ ] **Step 4: Update controller test paths**

Change test requests in `SimulationControllerTest`:

```java
mvc.perform(post("/api/simulations")
        .contentType(APPLICATION_JSON)
        .content("{\"virtualUserCount\":100}"))
```

and:

```java
mvc.perform(get("/api/simulations/{simulationId}", simulationId))
```

- [ ] **Step 5: Move controller mapping**

Change `SimulationController`:

```java
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {
    // existing methods stay in this class
}
```

- [ ] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest" --tests "*SimulationControllerTest"
docker compose -f infra\docker-compose.yml config
```

Expected: Gradle tests PASS. Compose config renders with `/api/` routing and no `ip_hash;`.

- [ ] **Step 7: Commit**

```powershell
git add infra/nginx.conf backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java backend/src/test/java/com/timedeal/seatreservation/infra/LocalInfrastructureFilesTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java
git commit -m "fix: route simulation api without sticky nginx"
```

---

### Task 2: Add Server Identity To Responses And Snapshots

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/identity/ServerIdentity.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationResponse.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationSnapshot.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/ServerStatsView.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`

- [ ] **Step 1: Write failing controller assertions**

Add assertions to `startSimulationReturnsKoreanMessageAndSimulationId`:

```java
.andExpect(jsonPath("$.handledBy").value("api-test"))
```

Construct the mocked response with the new field:

```java
when(simulationService.createSimulation(any())).thenReturn(new SimulationResponse(
        simulationId,
        "시뮬레이션이 생성되었습니다.",
        100,
        "api-test"
));
```

Add server stats to the snapshot construction:

```java
new SimulationSnapshot(
        simulationId,
        List.of(new SeatView(1L, "A-1", SeatStatus.AVAILABLE)),
        List.of(new VirtualUserView(
                userId,
                "사용자 1",
                VirtualUserStatus.QUEUED,
                null,
                List.of(new TimelineEntry("대기열", "대기열에 진입했습니다.")),
                3,
                2
        )),
        new SimulationMetrics(1, 0, 0, 0, 0, 0),
        List.of(new ServerStatsView("api-test", 1, 0, 0)),
        true
)
```

Add:

```java
.andExpect(jsonPath("$.serverStats[0].serverId").value("api-test"))
```

- [ ] **Step 2: Run the failing controller test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected: compilation fails because `handledBy` and `ServerStatsView` do not exist.

- [ ] **Step 3: Add server identity component**

Create `ServerIdentity.java`:

```java
package com.timedeal.seatreservation.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServerIdentity {
    private final String id;

    public ServerIdentity(@Value("${app.instance-id:${HOSTNAME:local}}") String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
```

- [ ] **Step 4: Add response and snapshot fields**

Change `SimulationResponse.java`:

```java
package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record SimulationResponse(
        UUID simulationId,
        String message,
        int virtualUserCount,
        String handledBy
) {
}
```

Create `ServerStatsView.java`:

```java
package com.timedeal.seatreservation.simulation;

public record ServerStatsView(
        String serverId,
        long requestCount,
        long conflictCount,
        long successCount
) {
}
```

Change `SimulationSnapshot.java`:

```java
package com.timedeal.seatreservation.simulation;

import java.util.List;
import java.util.UUID;

public record SimulationSnapshot(
        UUID simulationId,
        List<SeatView> seats,
        List<VirtualUserView> users,
        SimulationMetrics metrics,
        List<ServerStatsView> serverStats,
        boolean running
) {
}
```

- [ ] **Step 5: Wire identity into service**

Change `SimulationService` constructors and response creation:

```java
private final ServerIdentity serverIdentity;

@Autowired
public SimulationService(
        SimulationStateStore stateStore,
        SimulationRunner simulationRunner,
        ServerIdentity serverIdentity
) {
    this.stateStore = stateStore;
    this.simulationRunner = simulationRunner;
    this.serverIdentity = serverIdentity;
}

SimulationService(SimulationStateStore stateStore) {
    this.stateStore = stateStore;
    this.simulationRunner = null;
    this.serverIdentity = new ServerIdentity("api-test");
}

public SimulationResponse createSimulation(CreateSimulationRequest request) {
    UUID simulationId = UUID.randomUUID();
    stateStore.create(simulationId, request.virtualUserCount());
    return new SimulationResponse(
            simulationId,
            "시뮬레이션이 생성되었습니다.",
            request.virtualUserCount(),
            serverIdentity.id()
    );
}
```

Remove automatic `simulationRunner.start(simulationId)` from `createSimulation`; running moves to Task 6.

- [ ] **Step 6: Add local instance env defaults**

In `application-local.yml`, add:

```yaml
app:
  instance-id: ${APP_INSTANCE_ID:local-api}
```

- [ ] **Step 7: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest" --tests "*SimulationServiceTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/identity/ServerIdentity.java backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/main/resources/application-local.yml backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: expose api server identity in simulation responses"
```

---

### Task 3: Add Simulation-Scoped Database Inventory

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__simulation_scoped_inventory.sql`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/seat/SeatReservationConstraintTest.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/seat/SeatReservationService.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/seat/SeatReservationServiceTest.java`

- [ ] **Step 1: Write failing constraint test**

Add a test to `SeatReservationConstraintTest`:

```java
@Test
void sameSeatLabelCanBeReservedIndependentlyPerSimulation() throws Exception {
    UUID simulationA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    UUID simulationB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    UUID userA = UUID.fromString("00000000-0000-0000-0000-000000000101");
    UUID userB = UUID.fromString("00000000-0000-0000-0000-000000000102");

    jdbc.update("insert into concerts(id, title) values (1, '콘서트')");
    jdbc.update("insert into simulation_sessions(id, concert_id, requested_users, status) values (?, 1, 1, 'CREATED')", simulationA);
    jdbc.update("insert into simulation_sessions(id, concert_id, requested_users, status) values (?, 1, 1, 'CREATED')", simulationB);
    jdbc.update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, '사용자 1', 'QUEUED')", userA, simulationA);
    jdbc.update("insert into virtual_users(id, simulation_id, display_name, status) values (?, ?, '사용자 2', 'QUEUED')", userB, simulationB);
    jdbc.update("insert into simulation_seats(simulation_id, seat_id, seat_label, status) values (?, 1, 'A-1', 'AVAILABLE')", simulationA);
    jdbc.update("insert into simulation_seats(simulation_id, seat_id, seat_label, status) values (?, 1, 'A-1', 'AVAILABLE')", simulationB);

    jdbc.update("insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key) values (1, ?, 1, ?, 'RESERVED', 'a')", simulationA, userA);
    jdbc.update("insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key) values (2, ?, 1, ?, 'RESERVED', 'b')", simulationB, userB);

    Integer count = jdbc.queryForObject("select count(*) from reservations", Integer.class);
    assertThat(count).isEqualTo(2);
}
```

- [ ] **Step 2: Run failing constraint test**

Run:

```powershell
.\gradlew.bat test --tests "*SeatReservationConstraintTest"
```

Expected: FAIL because `simulation_seats` and `reservations.simulation_id` do not exist.

- [ ] **Step 3: Add migration**

Create `V3__simulation_scoped_inventory.sql`:

```sql
create table simulation_seats (
    simulation_id uuid not null references simulation_sessions(id),
    seat_id bigint not null,
    seat_label varchar(20) not null,
    status varchar(40) not null default 'AVAILABLE',
    held_by_user_id uuid references virtual_users(id),
    updated_at timestamptz not null default now(),
    primary key (simulation_id, seat_id),
    unique (simulation_id, seat_label)
);

alter table reservations
    add column simulation_id uuid references simulation_sessions(id);

alter table payments
    add column simulation_id uuid references simulation_sessions(id);

create unique index active_reservation_per_simulation_seat
    on reservations(simulation_id, seat_id)
    where simulation_id is not null
      and status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED');

create unique index one_final_reservation_per_simulation_user
    on reservations(simulation_id, virtual_user_id)
    where simulation_id is not null
      and status in ('PAYMENT_IN_PROGRESS', 'RESERVED');
```

- [ ] **Step 4: Update seat reservation SQL to use simulation scope**

Change `SeatReservationService.ACTIVE_RESERVATION_COUNT_SQL`:

```java
static final String ACTIVE_RESERVATION_COUNT_SQL = """
        select count(*)
        from reservations
        where simulation_id = ?
          and seat_id = ?
          and status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED')
        """;
```

Change `HOLD_SEAT_SQL`:

```java
static final String HOLD_SEAT_SQL = """
        update simulation_seats
        set status = 'HELD', held_by_user_id = ?, updated_at = now()
        where simulation_id = ?
          and seat_id = ?
          and status = 'AVAILABLE'
        """;
```

Change `INSERT_RESERVATION_SQL`:

```java
static final String INSERT_RESERVATION_SQL = """
        insert into reservations(id, simulation_id, seat_id, virtual_user_id, status, idempotency_key)
        values (?, ?, ?, ?, ?, ?)
        """;
```

Change method calls:

```java
return transactions.execute(status -> holdSeatInTransaction(simulationId, virtualUserId, seatId, idempotencyKey));
```

and implementation:

```java
private SeatReservationResult holdSeatInTransaction(UUID simulationId, UUID virtualUserId, long seatId, String idempotencyKey) {
    SeatReservationResult existing = findExisting(idempotencyKey);
    if (existing != null) {
        return existing;
    }

    Integer activeReservationCount = jdbc.queryForObject(ACTIVE_RESERVATION_COUNT_SQL, Integer.class, simulationId, seatId);
    if (activeReservationCount != null && activeReservationCount > 0) {
        return new SeatReservationResult(SeatReservationOutcome.ALREADY_HELD, null, seatId, virtualUserId, idempotencyKey);
    }

    int updatedSeats = jdbc.update(HOLD_SEAT_SQL, virtualUserId, simulationId, seatId);
    if (updatedSeats == 0) {
        return new SeatReservationResult(SeatReservationOutcome.ALREADY_HELD, null, seatId, virtualUserId, idempotencyKey);
    }

    Long reservationId = jdbc.queryForObject(NEXT_RESERVATION_ID_SQL, Long.class);
    jdbc.update(INSERT_RESERVATION_SQL, reservationId, simulationId, seatId, virtualUserId, "HELD", idempotencyKey);

    return new SeatReservationResult(SeatReservationOutcome.HELD, reservationId, seatId, virtualUserId, idempotencyKey);
}
```

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SeatReservationConstraintTest" --tests "*SeatReservationServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/resources/db/migration/V3__simulation_scoped_inventory.sql backend/src/main/java/com/timedeal/seatreservation/seat/SeatReservationService.java backend/src/test/java/com/timedeal/seatreservation/seat
git commit -m "feat: scope seat reservations by simulation"
```

---

### Task 4: Centralize Redis Simulation Keys

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/SimulationRedisKeys.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/redis/SimulationRedisKeysTest.java`

- [ ] **Step 1: Write failing key test**

Create `SimulationRedisKeysTest.java`:

```java
package com.timedeal.seatreservation.simulation.redis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRedisKeysTest {
    @Test
    void keysAreScopedBySimulationId() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SimulationRedisKeys keys = new SimulationRedisKeys(simulationId);

        assertThat(keys.queue()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:queue");
        assertThat(keys.users()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:users");
        assertThat(keys.snapshot()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:snapshot");
        assertThat(keys.serverStats()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:server-stats");
        assertThat(keys.events()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:events");
        assertThat(keys.kafkaFlow()).isEqualTo("simulation:00000000-0000-0000-0000-000000000001:kafka-flow");
    }
}
```

- [ ] **Step 2: Run failing key test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationRedisKeysTest"
```

Expected: compilation fails because `SimulationRedisKeys` does not exist.

- [ ] **Step 3: Implement key helper**

Create `SimulationRedisKeys.java`:

```java
package com.timedeal.seatreservation.simulation.redis;

import java.util.UUID;

public final class SimulationRedisKeys {
    private final String prefix;

    public SimulationRedisKeys(UUID simulationId) {
        this.prefix = "simulation:" + simulationId;
    }

    public String queue() {
        return prefix + ":queue";
    }

    public String users() {
        return prefix + ":users";
    }

    public String snapshot() {
        return prefix + ":snapshot";
    }

    public String serverStats() {
        return prefix + ":server-stats";
    }

    public String events() {
        return prefix + ":events";
    }

    public String kafkaFlow() {
        return prefix + ":kafka-flow";
    }
}
```

- [ ] **Step 4: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationRedisKeysTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation/redis/SimulationRedisKeys.java backend/src/test/java/com/timedeal/seatreservation/simulation/redis/SimulationRedisKeysTest.java
git commit -m "feat: add simulation scoped redis keys"
```

---

### Task 5: Add Redis-Backed Snapshot Store For Local/Profiled Runs

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`

- [ ] **Step 1: Write gateway-oriented service test**

Change `SimulationServiceTest` to use the interface:

```java
class SimulationServiceTest {
    private final SimulationStateGateway stateStore = new SimulationStateStore();
    private final SimulationService simulationService = new SimulationService(stateStore);

    @Test
    void createSimulationCreatesInitialSnapshotWithoutStartingRun() {
        SimulationResponse response = simulationService.createSimulation(new CreateSimulationRequest(25));

        SimulationSnapshot snapshot = simulationService.getSimulation(response.simulationId());

        assertThat(response.virtualUserCount()).isEqualTo(25);
        assertThat(response.message()).isEqualTo("시뮬레이션이 생성되었습니다.");
        assertThat(response.handledBy()).isEqualTo("api-test");
        assertThat(snapshot.simulationId()).isEqualTo(response.simulationId());
        assertThat(snapshot.seats()).hasSize(120);
        assertThat(snapshot.users()).hasSize(25);
        assertThat(snapshot.metrics().queueSize()).isEqualTo(25);
        assertThat(snapshot.running()).isFalse();
    }
}
```

- [ ] **Step 2: Run failing service test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationServiceTest"
```

Expected: compilation fails because `SimulationStateGateway` does not exist or behavior still marks the simulation running.

- [ ] **Step 3: Create gateway interface**

Create `SimulationStateGateway.java`:

```java
package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public interface SimulationStateGateway {
    SimulationSnapshot create(UUID simulationId, int virtualUserCount);

    SimulationSnapshot snapshot(UUID simulationId);

    SimulationSnapshot markRunning(UUID simulationId);
}
```

- [ ] **Step 4: Make in-memory store demo-only**

Update `SimulationStateStore`:

```java
@Component
@Profile("demo")
public class SimulationStateStore implements SimulationStateGateway {
```

Set initial running to false:

```java
boolean running = false;
```

Implement:

```java
@Override
public SimulationSnapshot markRunning(UUID simulationId) {
    MutableSimulationState state = state(simulationId);
    synchronized (state) {
        state.running = true;
    }
    return snapshot(simulationId);
}
```

- [ ] **Step 5: Create Redis store skeleton**

Create `RedisSimulationStateStore.java`:

```java
package com.timedeal.seatreservation.simulation.redis;

import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.simulation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
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

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisSimulationStateStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
        Object value = redisTemplate.opsForValue().get(keys.snapshot());
        if (value instanceof SimulationSnapshot snapshot) {
            return snapshot;
        }
        throw new NoSuchElementException("Simulation not found: " + simulationId);
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
        redisTemplate.opsForValue().set(keys.snapshot(), snapshot, TTL);
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
```

- [ ] **Step 6: Update service dependency**

Change `SimulationService`:

```java
private final SimulationStateGateway stateStore;
```

Use `stateStore.create(...)` and `stateStore.snapshot(...)`.

- [ ] **Step 7: Verify focused simulation tests**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationServiceTest" --tests "*SimulationControllerTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/main/java/com/timedeal/seatreservation/simulation/redis backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: introduce redis backed simulation state gateway"
```

---

### Task 6: Add Run Endpoint And Traffic Generator Contract

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/RunSimulationRequest.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/RunSimulationResponse.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorClient.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`

- [ ] **Step 1: Write failing run endpoint test**

Add to `SimulationControllerTest`:

```java
@Test
void runSimulationStartsTrafficGenerator() throws Exception {
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    when(simulationService.runSimulation(eq(simulationId), any())).thenReturn(new RunSimulationResponse(
            simulationId,
            150,
            "RUNNING",
            "api-test"
    ));

    mvc.perform(post("/api/simulations/{simulationId}/run", simulationId)
                    .contentType(APPLICATION_JSON)
                    .content("{\"virtualUserCount\":150,\"concurrency\":30}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
            .andExpect(jsonPath("$.virtualUserCount").value(150))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.handledBy").value("api-test"));
}
```

Add static imports:

```java
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 2: Run failing controller test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected: compilation fails because request/response and service method do not exist.

- [ ] **Step 3: Add request/response records**

Create `RunSimulationRequest.java`:

```java
package com.timedeal.seatreservation.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RunSimulationRequest(
        @Min(1) @Max(1000) int virtualUserCount,
        @Min(1) @Max(100) int concurrency
) {
}
```

Create `RunSimulationResponse.java`:

```java
package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record RunSimulationResponse(
        UUID simulationId,
        int virtualUserCount,
        String status,
        String handledBy
) {
}
```

- [ ] **Step 4: Add generator client interface**

Create `TrafficGeneratorClient.java`:

```java
package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;

import java.util.UUID;

public interface TrafficGeneratorClient {
    void start(UUID simulationId, RunSimulationRequest request);
}
```

- [ ] **Step 5: Add controller endpoint**

Add to `SimulationController`:

```java
@PostMapping("/{simulationId}/run")
public RunSimulationResponse runSimulation(
        @PathVariable UUID simulationId,
        @Valid @RequestBody RunSimulationRequest request
) {
    return simulationService.runSimulation(simulationId, request);
}
```

- [ ] **Step 6: Add service method**

Inject `TrafficGeneratorClient` into `SimulationService`. In the package-private test constructor, use a no-op lambda.

```java
private final TrafficGeneratorClient trafficGeneratorClient;

SimulationService(SimulationStateGateway stateStore) {
    this.stateStore = stateStore;
    this.simulationRunner = null;
    this.serverIdentity = new ServerIdentity("api-test");
    this.trafficGeneratorClient = (simulationId, request) -> { };
}

public RunSimulationResponse runSimulation(UUID simulationId, RunSimulationRequest request) {
    stateStore.markRunning(simulationId);
    trafficGeneratorClient.start(simulationId, request);
    return new RunSimulationResponse(simulationId, request.virtualUserCount(), "RUNNING", serverIdentity.id());
}
```

- [ ] **Step 7: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest" --tests "*SimulationServiceTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/main/java/com/timedeal/seatreservation/generator backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: add simulation run command contract"
```

---

### Task 7: Implement Traffic Generator Service

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/generator/VirtualUserHttpClient.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/generator/TrafficGeneratorServiceTest.java`
- Modify: `backend/src/main/resources/application-local.yml`

- [ ] **Step 1: Write failing generator test**

Create `TrafficGeneratorServiceTest.java`:

```java
package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficGeneratorServiceTest {
    @Test
    void sendsEveryVirtualUserThroughConfiguredTarget() {
        List<String> calls = new ArrayList<>();
        VirtualUserHttpClient client = (baseUrl, simulationId, virtualUserNumber) ->
                calls.add(baseUrl + "|" + simulationId + "|" + virtualUserNumber);
        TrafficGeneratorService service = new TrafficGeneratorService(client, "http://nginx:8080", 1);
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        service.start(simulationId, new RunSimulationRequest(3, 1));

        assertThat(calls).containsExactly(
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|1",
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|2",
                "http://nginx:8080|00000000-0000-0000-0000-000000000004|3"
        );
    }
}
```

- [ ] **Step 2: Run failing generator test**

Run:

```powershell
.\gradlew.bat test --tests "*TrafficGeneratorServiceTest"
```

Expected: compilation fails because the generator classes do not exist.

- [ ] **Step 3: Add virtual user HTTP client interface**

Create `VirtualUserHttpClient.java`:

```java
package com.timedeal.seatreservation.generator;

import java.util.UUID;

@FunctionalInterface
public interface VirtualUserHttpClient {
    void runUser(String baseUrl, UUID simulationId, int virtualUserNumber);
}
```

- [ ] **Step 4: Add generator service**

Create `TrafficGeneratorService.java`:

```java
package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.simulation.RunSimulationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile("generator")
public class TrafficGeneratorService implements TrafficGeneratorClient {
    private final VirtualUserHttpClient client;
    private final String targetBaseUrl;
    private final int fallbackConcurrency;

    public TrafficGeneratorService(
            VirtualUserHttpClient client,
            @Value("${traffic-generator.target-base-url:http://localhost:8080}") String targetBaseUrl,
            @Value("${traffic-generator.default-concurrency:20}") int fallbackConcurrency
    ) {
        this.client = client;
        this.targetBaseUrl = targetBaseUrl;
        this.fallbackConcurrency = fallbackConcurrency;
    }

    @Override
    public void start(UUID simulationId, RunSimulationRequest request) {
        int concurrency = Math.max(1, request.concurrency() > 0 ? request.concurrency() : fallbackConcurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setThreadNamePrefix("virtual-user-");
        executor.initialize();
        for (int number = 1; number <= request.virtualUserCount(); number++) {
            int virtualUserNumber = number;
            executor.execute(() -> client.runUser(targetBaseUrl, simulationId, virtualUserNumber));
        }
        executor.shutdown();
    }
}
```

- [ ] **Step 5: Add local generator config**

In `application-local.yml`, add:

```yaml
traffic-generator:
  target-base-url: ${TRAFFIC_GENERATOR_TARGET_BASE_URL:http://localhost:8080}
  default-concurrency: ${TRAFFIC_GENERATOR_DEFAULT_CONCURRENCY:20}
```

- [ ] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*TrafficGeneratorServiceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/generator backend/src/test/java/com/timedeal/seatreservation/generator backend/src/main/resources/application-local.yml
git commit -m "feat: add traffic generator service"
```

---

### Task 8: Add Virtual User API Commands

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserCommandResponse.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`

- [ ] **Step 1: Write failing command endpoint test**

Add to `SimulationControllerTest`:

```java
@Test
void virtualUserEnterQueueReturnsHandlingServer() throws Exception {
    UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000105");
    when(simulationService.enterQueue(eq(simulationId), eq(userId))).thenReturn(new VirtualUserCommandResponse(
            simulationId,
            userId,
            "QUEUED",
            "api-test"
    ));

    mvc.perform(post("/api/simulations/{simulationId}/users/{userId}/queue", simulationId, userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
            .andExpect(jsonPath("$.virtualUserId").value(userId.toString()))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.handledBy").value("api-test"));
}
```

- [ ] **Step 2: Run failing command test**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected: compilation fails because command response and endpoint do not exist.

- [ ] **Step 3: Add command response**

Create `VirtualUserCommandResponse.java`:

```java
package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record VirtualUserCommandResponse(
        UUID simulationId,
        UUID virtualUserId,
        String status,
        String handledBy
) {
}
```

- [ ] **Step 4: Add endpoint**

Add to `SimulationController`:

```java
@PostMapping("/{simulationId}/users/{userId}/queue")
public VirtualUserCommandResponse enterQueue(
        @PathVariable UUID simulationId,
        @PathVariable UUID userId
) {
    return simulationService.enterQueue(simulationId, userId);
}
```

- [ ] **Step 5: Add service method**

Add to `SimulationService`:

```java
public VirtualUserCommandResponse enterQueue(UUID simulationId, UUID userId) {
    return new VirtualUserCommandResponse(simulationId, userId, "QUEUED", serverIdentity.id());
}
```

This first command only establishes the distributed HTTP command surface. Later tasks should move queue state mutation into Redis-backed user state.

- [ ] **Step 6: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: add virtual user queue command api"
```

---

### Task 9: Update Kafka Event Payloads With Simulation Context

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentRequestedEvent.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentResultEvent.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`

- [ ] **Step 1: Write failing payment event test**

Update `PaymentSimulationWorkerTest` to construct:

```java
PaymentRequestedEvent event = new PaymentRequestedEvent(
        UUID.fromString("00000000-0000-0000-0000-000000000006"),
        UUID.fromString("00000000-0000-0000-0000-000000000106"),
        11L,
        7L,
        "payment-key",
        "api-a"
);
```

Assert:

```java
PaymentResultEvent result = worker.simulate(event);

assertThat(result.simulationId()).isEqualTo(event.simulationId());
assertThat(result.virtualUserId()).isEqualTo(event.virtualUserId());
assertThat(result.handledBy()).isEqualTo("worker-test");
```

- [ ] **Step 2: Run failing payment test**

Run:

```powershell
.\gradlew.bat test --tests "*PaymentSimulationWorkerTest"
```

Expected: compilation fails because event records lack these fields.

- [ ] **Step 3: Update event records**

Change `PaymentRequestedEvent.java`:

```java
package com.timedeal.seatreservation.payment;

import java.util.UUID;

public record PaymentRequestedEvent(
        UUID simulationId,
        UUID virtualUserId,
        long reservationId,
        long seatId,
        String idempotencyKey,
        String handledBy
) {
}
```

Change `PaymentResultEvent.java`:

```java
package com.timedeal.seatreservation.payment;

import java.util.UUID;

public record PaymentResultEvent(
        UUID simulationId,
        UUID virtualUserId,
        long reservationId,
        long seatId,
        boolean success,
        String message,
        String handledBy
) {
}
```

- [ ] **Step 4: Inject worker identity**

Change `PaymentSimulationWorker`:

```java
private final ServerIdentity serverIdentity;

public PaymentSimulationWorker(
        KafkaTemplate<String, PaymentResultEvent> kafkaTemplate,
        ServerIdentity serverIdentity
) {
    this.kafkaTemplate = kafkaTemplate;
    this.serverIdentity = serverIdentity;
}
```

Change `simulate`:

```java
public PaymentResultEvent simulate(PaymentRequestedEvent event) {
    boolean success = event.reservationId() % 5 != 0;
    String message = success ? "결제 성공" : "결제 실패";
    return new PaymentResultEvent(
            event.simulationId(),
            event.virtualUserId(),
            event.reservationId(),
            event.seatId(),
            success,
            message,
            serverIdentity.id()
    );
}
```

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*PaymentSimulationWorkerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/payment backend/src/test/java/com/timedeal/seatreservation/payment
git commit -m "feat: include simulation context in payment events"
```

---

### Task 10: Add Local traffic-generator Service To Docker Compose

**Files:**
- Modify: `infra/docker-compose.yml`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/infra/LocalInfrastructureFilesTest.java`

- [ ] **Step 1: Write failing compose assertions**

Add to `LocalInfrastructureFilesTest.dockerComposeDefinesLocalInfrastructureServices`:

```java
assertThat(compose).contains("traffic-generator:");
assertThat(compose).contains("SPRING_PROFILES_ACTIVE: local,generator");
assertThat(compose).contains("TRAFFIC_GENERATOR_TARGET_BASE_URL: http://nginx:8080");
assertThat(compose).contains("APP_INSTANCE_ID: api-a");
assertThat(compose).contains("APP_INSTANCE_ID: api-b");
```

- [ ] **Step 2: Run failing infra test**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest"
```

Expected: FAIL because compose does not include `traffic-generator`.

- [ ] **Step 3: Update compose API environment**

Add to `api-a.environment`:

```yaml
      APP_INSTANCE_ID: api-a
```

Add to `api-b.environment`:

```yaml
      APP_INSTANCE_ID: api-b
```

Add to `worker.environment`:

```yaml
      APP_INSTANCE_ID: worker
```

- [ ] **Step 4: Add traffic-generator service**

Add to `infra/docker-compose.yml`:

```yaml
  traffic-generator:
    build:
      context: ../backend
    environment:
      SPRING_PROFILES_ACTIVE: local,generator
      APP_INSTANCE_ID: traffic-generator
      TRAFFIC_GENERATOR_TARGET_BASE_URL: http://nginx:8080
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/seat_reservation
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      nginx:
        condition: service_started
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_started
```

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest"
docker compose -f infra\docker-compose.yml config
```

Expected: PASS and compose renders `traffic-generator`.

- [ ] **Step 6: Commit**

```powershell
git add infra/docker-compose.yml backend/src/test/java/com/timedeal/seatreservation/infra/LocalInfrastructureFilesTest.java
git commit -m "chore: add local traffic generator service"
```

---

### Task 11: Add Production Profile Boundaries

**Files:**
- Create: `backend/src/main/resources/application-prod.yml`
- Create: `docs/deployment/production-v1.md`
- Create: `docs/deployment/environment-variables.md`
- Create: `docs/deployment/vercel-env.example`

- [ ] **Step 1: Write failing file existence test**

Add to `LocalInfrastructureFilesTest`:

```java
@Test
void productionDeploymentDocsAndProfileExist() {
    assertThat(Files.exists(Path.of("src/main/resources/application-prod.yml"))).isTrue();
    assertThat(Files.exists(Path.of("../docs/deployment/production-v1.md"))).isTrue();
    assertThat(Files.exists(Path.of("../docs/deployment/environment-variables.md"))).isTrue();
    assertThat(Files.exists(Path.of("../docs/deployment/vercel-env.example"))).isTrue();
}
```

- [ ] **Step 2: Run failing infra test**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest"
```

Expected: FAIL because the files do not exist.

- [ ] **Step 3: Add prod profile**

Create `application-prod.yml`:

```yaml
app:
  instance-id: ${APP_INSTANCE_ID}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST}
      port: ${SPRING_DATA_REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS}

traffic-generator:
  target-base-url: ${TRAFFIC_GENERATOR_TARGET_BASE_URL}
  default-concurrency: ${TRAFFIC_GENERATOR_DEFAULT_CONCURRENCY:20}
```

- [ ] **Step 4: Add deployment docs**

Create `docs/deployment/environment-variables.md`:

```markdown
# Environment Variables

## Backend

- `APP_INSTANCE_ID`: visible server id such as `api-a`, `api-b`, `worker`, or `traffic-generator`
- `SPRING_PROFILES_ACTIVE`: `prod`, `prod,worker`, or `prod,generator`
- `SPRING_DATASOURCE_URL`: RDS PostgreSQL JDBC URL
- `SPRING_DATASOURCE_USERNAME`: RDS username
- `SPRING_DATASOURCE_PASSWORD`: RDS password
- `SPRING_DATA_REDIS_HOST`: Redis host
- `SPRING_DATA_REDIS_PORT`: Redis port, default `6379`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka bootstrap servers
- `TRAFFIC_GENERATOR_TARGET_BASE_URL`: public or private nginx URL used by the generator
- `TRAFFIC_GENERATOR_DEFAULT_CONCURRENCY`: default generator concurrency

## Frontend

- `VITE_API_BASE_URL`: public API base URL
```

Create `docs/deployment/vercel-env.example`:

```text
VITE_API_BASE_URL=https://api.example.com
```

Create `docs/deployment/production-v1.md`:

```markdown
# Production v1 Deployment

Production v1 is prepared but not provisioned in the first MVP.

## Target Topology

Vercel serves the React frontend. The API domain points to nginx. Nginx distributes `/api/**` traffic to `api-a` and `api-b`. Redis and Kafka may run on a low-cost instance for the first portfolio deployment. PostgreSQL should use RDS for durable state.

## Suggested Low-Cost Layout

- Vercel: frontend
- Lightsail A: nginx and `api-a`
- Lightsail B: `api-b`, worker, and traffic-generator
- Lightsail C: Redis and Kafka
- RDS PostgreSQL: durable reservations

## Known Limitations

This layout is cost-oriented. Self-hosted nginx and self-hosted Redis/Kafka are not high availability. Production v2 can replace them with managed load balancing, ElastiCache, and managed Kafka or Redpanda Cloud.
```

- [ ] **Step 5: Verify**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/resources/application-prod.yml docs/deployment backend/src/test/java/com/timedeal/seatreservation/infra/LocalInfrastructureFilesTest.java
git commit -m "docs: add production profile deployment boundaries"
```

---

### Task 12: End-To-End Local Foundation Verification

**Files:**
- Modify only if verification finds a defect in files changed by previous tasks.

- [ ] **Step 1: Run focused non-Docker tests**

Run:

```powershell
.\gradlew.bat test --tests "*LocalInfrastructureFilesTest" --tests "*SimulationControllerTest" --tests "*SimulationServiceTest" --tests "*SimulationRedisKeysTest" --tests "*TrafficGeneratorServiceTest" --tests "*PaymentSimulationWorkerTest"
```

Expected: PASS.

- [ ] **Step 2: Build backend jar**

Run:

```powershell
.\gradlew.bat bootJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Validate compose config**

Run from repo root:

```powershell
docker compose -f infra\docker-compose.yml config
```

Expected: config renders successfully and includes `traffic-generator`, `api-a`, `api-b`, `worker`, `postgres`, `redis`, `kafka`, and `nginx`.

- [ ] **Step 4: Rebuild local containers**

Run:

```powershell
docker compose -f infra\docker-compose.yml up -d --build
```

Expected: services start. `api-a` and `api-b` become healthy.

- [ ] **Step 5: Verify public API create/run flow**

Run:

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/simulations -ContentType "application/json" -Body '{"virtualUserCount":20}'
$created
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/simulations/$($created.simulationId)/run" -ContentType "application/json" -Body '{"virtualUserCount":20,"concurrency":5}'
```

Expected:

- create response includes `simulationId` and `handledBy`
- run response has `status` equal to `RUNNING`
- no nginx 301 redirect

- [ ] **Step 6: Verify distribution logs**

Run:

```powershell
docker compose -f infra\docker-compose.yml logs --tail=120 api-a api-b nginx traffic-generator
```

Expected:

- nginx logs show `/api/**` requests
- both `api-a` and `api-b` receive generated virtual-user command requests
- traffic-generator targets `http://nginx:8080`

- [ ] **Step 7: Commit fixes only if needed**

If verification required a fix:

```powershell
git add <changed-files>
git commit -m "fix: stabilize distributed simulation foundation"
```

If no fix was needed, do not create an empty commit.

---

## Self-Review

Spec coverage:

- React/Vite frontend separation is intentionally deferred to the next plan.
- Redis/Kafka/PostgreSQL visibility panels are intentionally deferred to the next plan.
- This plan covers the prerequisite backend distribution foundation: `/api`, no sticky nginx, server identity, simulation-scoped DB, Redis key scoping, traffic-generator, Kafka payload context, and prod boundary files.

Placeholder scan:

- The plan has no unresolved sections.
- Deferred items are explicitly declared as out of scope for this plan and assigned to follow-up plans.

Type consistency:

- `SimulationResponse.handledBy`, `SimulationSnapshot.serverStats`, `RunSimulationRequest`, `RunSimulationResponse`, `VirtualUserCommandResponse`, and `TrafficGeneratorClient` are introduced before later tasks reference them.
- Redis key names match the approved design: `queue`, `users`, `snapshot`, `server-stats`, `events`, `kafka-flow`.

## Follow-Up Plans

After this foundation plan passes verification, write separate implementation plans for:

1. React/Vite frontend separation and Korean dashboard UI.
2. Redis/Kafka/PostgreSQL observability panels.
3. Payment worker state application and Kafka result consumers.
4. Production deployment preparation review and README/demo documentation.
