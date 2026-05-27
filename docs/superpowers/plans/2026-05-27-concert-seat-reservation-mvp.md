# Concert Seat Reservation MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local MVP where a Korean dashboard starts a concert seat reservation simulation, backend-generated virtual users compete for seats, Redis manages queue/holds, PostgreSQL stores durable state, Kafka processes payment events, and SSE updates the seat map.

**Architecture:** Use one Spring Boot backend codebase with API and worker profiles. Local development uses Docker Compose with a reverse proxy, two API containers, one worker container, Redis, PostgreSQL, Kafka, and a thin Next.js frontend. Production v1 uses Vercel for frontend, Lightsail A for Nginx + api-a, Lightsail B for api-b + worker, Lightsail C for Redis + Kafka, and RDS PostgreSQL for durable state.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Gradle Groovy DSL, Spring Data JPA, Spring Data Redis, Spring Kafka, PostgreSQL, Redis, Kafka, Docker Compose, Lightsail, RDS, Vercel, Next.js, TypeScript, SSE, JUnit, Testcontainers.

---

## File Structure

Create this structure:

- `backend/build.gradle`: backend Gradle build configuration.
- `backend/settings.gradle`: Gradle project name.
- `backend/src/main/java/com/timedeal/seatreservation/SeatReservationApplication.java`: Spring Boot entrypoint.
- `backend/src/main/java/com/timedeal/seatreservation/domain/*`: enums and domain transition rules.
- `backend/src/main/java/com/timedeal/seatreservation/simulation/*`: simulation session orchestration.
- `backend/src/main/java/com/timedeal/seatreservation/queue/*`: Redis waiting queue and admission token logic.
- `backend/src/main/java/com/timedeal/seatreservation/seat/*`: seat hold and reservation command handling.
- `backend/src/main/java/com/timedeal/seatreservation/payment/*`: payment events and worker.
- `backend/src/main/java/com/timedeal/seatreservation/events/*`: outbox and SSE event support.
- `backend/src/main/resources/db/migration/V1__init.sql`: PostgreSQL schema.
- `backend/src/test/java/com/timedeal/seatreservation/*`: focused tests.
- `frontend/package.json`: frontend build configuration.
- `frontend/src/app/page.tsx`: dashboard page.
- `frontend/src/lib/api.ts`: API and SSE client helpers.
- `frontend/src/lib/labels.ts`: Korean presentation labels.
- `infra/docker-compose.yml`: local infrastructure.
- `infra/nginx.conf`: local reverse proxy config.
- `infra/prod/lightsail-a.md`: production setup notes for Nginx + api-a.
- `infra/prod/lightsail-b.md`: production setup notes for api-b + worker.
- `infra/prod/lightsail-c.md`: production setup notes for Redis + Kafka.
- `infra/prod/rds.md`: production setup notes for RDS PostgreSQL.
- `README.md`: Korean run guide.

## Task 1: Backend Project Skeleton

**Files:**
- Create: `backend/settings.gradle`
- Create: `backend/build.gradle`
- Create: `backend/src/main/java/com/timedeal/seatreservation/SeatReservationApplication.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/ContextLoadTest.java`

- [ ] **Step 1: Create Gradle settings**

Create `backend/settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'concert-seat-reservation'
```

- [ ] **Step 2: Create backend build file**

Create `backend/build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.timedeal'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:jdbc'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:kafka'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Create Spring Boot entrypoint**

Run: `cd backend && gradle wrapper --gradle-version 8.10.2`

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/*` are created. Java 17 is the project baseline.

- [ ] **Step 4: Create Spring Boot entrypoint**

Create `backend/src/main/java/com/timedeal/seatreservation/SeatReservationApplication.java`:

```java
package com.timedeal.seatreservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SeatReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatReservationApplication.class, args);
    }
}
```

- [ ] **Step 5: Write context load test**

Create `backend/src/test/java/com/timedeal/seatreservation/ContextLoadTest.java`:

```java
package com.timedeal.seatreservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ContextLoadTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run backend tests**

Run: `cd backend && ./gradlew test`

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "chore: scaffold spring backend"
```

## Task 2: Domain States And Transition Rules

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/domain/SeatStatus.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/domain/VirtualUserStatus.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/domain/PaymentStatus.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/domain/DomainTransitionPolicy.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/domain/DomainTransitionPolicyTest.java`

- [ ] **Step 1: Write failing transition policy test**

Create `backend/src/test/java/com/timedeal/seatreservation/domain/DomainTransitionPolicyTest.java`:

```java
package com.timedeal.seatreservation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainTransitionPolicyTest {
    private final DomainTransitionPolicy policy = new DomainTransitionPolicy();

    @Test
    void seatCanMoveFromAvailableToHeld() {
        assertThat(policy.canChangeSeatStatus(SeatStatus.AVAILABLE, SeatStatus.HELD)).isTrue();
    }

    @Test
    void reservedSeatCannotMoveBackToHeld() {
        assertThat(policy.canChangeSeatStatus(SeatStatus.RESERVED, SeatStatus.HELD)).isFalse();
    }

    @Test
    void virtualUserCanFinishReservationAfterPayment() {
        assertThat(policy.canChangeVirtualUserStatus(
                VirtualUserStatus.PAYMENT_IN_PROGRESS,
                VirtualUserStatus.RESERVED
        )).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*DomainTransitionPolicyTest"`

Expected: FAIL because `DomainTransitionPolicy`, `SeatStatus`, and `VirtualUserStatus` do not exist.

- [ ] **Step 3: Create state enums**

Create `backend/src/main/java/com/timedeal/seatreservation/domain/SeatStatus.java`:

```java
package com.timedeal.seatreservation.domain;

public enum SeatStatus {
    AVAILABLE,
    HELD,
    PAYMENT_IN_PROGRESS,
    RESERVED
}
```

Create `backend/src/main/java/com/timedeal/seatreservation/domain/VirtualUserStatus.java`:

```java
package com.timedeal.seatreservation.domain;

public enum VirtualUserStatus {
    CREATED,
    QUEUED,
    ADMITTED,
    SELECTING_SEAT,
    SEAT_HELD,
    PAYMENT_IN_PROGRESS,
    RESERVED,
    FAILED,
    EXPIRED
}
```

Create `backend/src/main/java/com/timedeal/seatreservation/domain/PaymentStatus.java`:

```java
package com.timedeal.seatreservation.domain;

public enum PaymentStatus {
    REQUESTED,
    SUCCEEDED,
    FAILED,
    TIMED_OUT
}
```

- [ ] **Step 4: Implement transition policy**

Create `backend/src/main/java/com/timedeal/seatreservation/domain/DomainTransitionPolicy.java`:

```java
package com.timedeal.seatreservation.domain;

import java.util.Map;
import java.util.Set;

public class DomainTransitionPolicy {
    private static final Map<SeatStatus, Set<SeatStatus>> SEAT_TRANSITIONS = Map.of(
            SeatStatus.AVAILABLE, Set.of(SeatStatus.HELD),
            SeatStatus.HELD, Set.of(SeatStatus.AVAILABLE, SeatStatus.PAYMENT_IN_PROGRESS),
            SeatStatus.PAYMENT_IN_PROGRESS, Set.of(SeatStatus.RESERVED, SeatStatus.AVAILABLE),
            SeatStatus.RESERVED, Set.of()
    );

    private static final Map<VirtualUserStatus, Set<VirtualUserStatus>> USER_TRANSITIONS = Map.of(
            VirtualUserStatus.CREATED, Set.of(VirtualUserStatus.QUEUED),
            VirtualUserStatus.QUEUED, Set.of(VirtualUserStatus.ADMITTED, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.ADMITTED, Set.of(VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.SELECTING_SEAT, Set.of(VirtualUserStatus.SEAT_HELD, VirtualUserStatus.FAILED),
            VirtualUserStatus.SEAT_HELD, Set.of(VirtualUserStatus.PAYMENT_IN_PROGRESS, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.PAYMENT_IN_PROGRESS, Set.of(VirtualUserStatus.RESERVED, VirtualUserStatus.FAILED),
            VirtualUserStatus.RESERVED, Set.of(),
            VirtualUserStatus.FAILED, Set.of(),
            VirtualUserStatus.EXPIRED, Set.of()
    );

    public boolean canChangeSeatStatus(SeatStatus from, SeatStatus to) {
        return SEAT_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean canChangeVirtualUserStatus(VirtualUserStatus from, VirtualUserStatus to) {
        return USER_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
```

- [ ] **Step 5: Run transition tests**

Run: `cd backend && ./gradlew test --tests "*DomainTransitionPolicyTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/timedeal/seatreservation/domain backend/src/test/java/com/timedeal/seatreservation/domain
git commit -m "feat: define reservation state transitions"
```

## Task 3: PostgreSQL Schema And Repository Tests

**Files:**
- Create: `backend/src/main/resources/application-test.yml`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/test/java/com/timedeal/seatreservation/seat/SeatReservationConstraintTest.java`

- [ ] **Step 1: Configure test profile**

Create `backend/src/main/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///seat_reservation
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
```

- [ ] **Step 2: Write failing database constraint test**

Create `backend/src/test/java/com/timedeal/seatreservation/seat/SeatReservationConstraintTest.java`:

```java
package com.timedeal.seatreservation.seat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeatReservationConstraintTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyOneActiveReservationCanExistForOneSeat() {
        jdbc.update("insert into concerts(id, title) values (?, ?)", 1L, "테스트 콘서트");
        jdbc.update("insert into seats(id, concert_id, seat_label) values (?, ?, ?)", 10L, 1L, "A-1");
        jdbc.update("insert into reservations(id, seat_id, status, idempotency_key) values (?, ?, ?, ?)",
                100L, 10L, "RESERVED", "reservation-100");

        assertThatThrownBy(() -> jdbc.update(
                "insert into reservations(id, seat_id, status, idempotency_key) values (?, ?, ?, ?)",
                101L, 10L, "RESERVED", "reservation-101"
        )).hasMessageContaining("active_reservation_per_seat");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*SeatReservationConstraintTest"`

Expected: FAIL because the database migration does not exist.

- [ ] **Step 4: Create initial schema**

Create `backend/src/main/resources/db/migration/V1__init.sql`:

```sql
create table concerts (
    id bigint primary key,
    title varchar(100) not null,
    created_at timestamptz not null default now()
);

create table seats (
    id bigint primary key,
    concert_id bigint not null references concerts(id),
    seat_label varchar(20) not null,
    status varchar(40) not null default 'AVAILABLE',
    updated_at timestamptz not null default now(),
    unique (concert_id, seat_label)
);

create table simulation_sessions (
    id uuid primary key,
    concert_id bigint not null references concerts(id),
    requested_users integer not null,
    status varchar(40) not null,
    created_at timestamptz not null default now()
);

create table virtual_users (
    id uuid primary key,
    simulation_id uuid not null references simulation_sessions(id),
    display_name varchar(40) not null,
    status varchar(40) not null,
    selected_seat_id bigint references seats(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table reservations (
    id bigint primary key,
    seat_id bigint not null references seats(id),
    virtual_user_id uuid references virtual_users(id),
    status varchar(40) not null,
    idempotency_key varchar(100) not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index active_reservation_per_seat
    on reservations(seat_id)
    where status in ('HELD', 'PAYMENT_IN_PROGRESS', 'RESERVED');

create table payments (
    id bigint primary key,
    reservation_id bigint not null references reservations(id),
    status varchar(40) not null,
    idempotency_key varchar(100) not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(120) not null,
    event_type varchar(120) not null,
    payload jsonb not null,
    published_at timestamptz,
    created_at timestamptz not null default now()
);

create index outbox_unpublished_idx on outbox_events(created_at) where published_at is null;
```

- [ ] **Step 5: Run repository test**

Run: `cd backend && ./gradlew test --tests "*SeatReservationConstraintTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources backend/src/test/java/com/timedeal/seatreservation/seat
git commit -m "feat: add reservation database schema"
```

## Task 4: Redis Waiting Queue

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/queue/WaitingQueueService.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/queue/WaitingQueueServiceTest.java`

- [ ] **Step 1: Write Redis queue test**

Create `backend/src/test/java/com/timedeal/seatreservation/queue/WaitingQueueServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*WaitingQueueServiceTest"`

Expected: FAIL because `WaitingQueueService` does not exist.

- [ ] **Step 3: Implement Redis queue service**

Create `backend/src/main/java/com/timedeal/seatreservation/queue/WaitingQueueService.java`:

```java
package com.timedeal.seatreservation.queue;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class WaitingQueueService {
    private final StringRedisTemplate redis;
    private final Clock clock;

    public WaitingQueueService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void enterQueue(String simulationId, String virtualUserId) {
        redis.opsForZSet().add(queueKey(simulationId), virtualUserId, clock.millis());
    }

    public List<String> pickAdmissionCandidates(List<String> queuedUserIds, int limit) {
        return queuedUserIds.stream().limit(limit).toList();
    }

    public void issueAdmissionToken(String simulationId, String virtualUserId) {
        redis.opsForValue().set(tokenKey(simulationId, virtualUserId), "granted", Duration.ofSeconds(60));
    }

    public boolean hasAdmissionToken(String simulationId, String virtualUserId) {
        return Boolean.TRUE.equals(redis.hasKey(tokenKey(simulationId, virtualUserId)));
    }

    private String queueKey(String simulationId) {
        return "simulation:%s:queue".formatted(simulationId);
    }

    private String tokenKey(String simulationId, String virtualUserId) {
        return "simulation:%s:admission:%s".formatted(simulationId, virtualUserId);
    }
}
```

- [ ] **Step 4: Register Clock bean**

Create `backend/src/main/java/com/timedeal/seatreservation/SystemClockConfig.java`:

```java
package com.timedeal.seatreservation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SystemClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 5: Run queue test**

Run: `cd backend && ./gradlew test --tests "*WaitingQueueServiceTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/timedeal/seatreservation backend/src/test/java/com/timedeal/seatreservation/queue
git commit -m "feat: add redis waiting queue service"
```

## Task 5: Simulation API And SSE Contract

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/CreateSimulationRequest.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationResponse.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventStream.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`

- [ ] **Step 1: Write API contract test**

Create `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`:

```java
package com.timedeal.seatreservation.simulation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    SimulationService simulationService;

    @Test
    void startSimulationReturnsKoreanMessageAndSimulationId() throws Exception {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(simulationService.createSimulation(any())).thenReturn(new SimulationResponse(
                simulationId,
                "시뮬레이션이 시작되었습니다.",
                100
        ));

        mvc.perform(post("/simulations")
                        .contentType(APPLICATION_JSON)
                        .content("{\"virtualUserCount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.message").value("시뮬레이션이 시작되었습니다."))
                .andExpect(jsonPath("$.virtualUserCount").value(100));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*SimulationControllerTest"`

Expected: FAIL because simulation API classes do not exist.

- [ ] **Step 3: Implement request and response records**

Create `backend/src/main/java/com/timedeal/seatreservation/simulation/CreateSimulationRequest.java`:

```java
package com.timedeal.seatreservation.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateSimulationRequest(
        @Min(1) @Max(1000) int virtualUserCount
) {
}
```

Create `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationResponse.java`:

```java
package com.timedeal.seatreservation.simulation;

import java.util.UUID;

public record SimulationResponse(
        UUID simulationId,
        String message,
        int virtualUserCount
) {
}
```

- [ ] **Step 4: Implement simulation service**

Create `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`:

```java
package com.timedeal.seatreservation.simulation;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SimulationService {
    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        return new SimulationResponse(simulationId, "시뮬레이션이 시작되었습니다.", request.virtualUserCount());
    }
}
```

- [ ] **Step 5: Implement controller**

Create `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`:

```java
package com.timedeal.seatreservation.simulation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/simulations")
public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public SimulationResponse createSimulation(@Valid @RequestBody CreateSimulationRequest request) {
        return simulationService.createSimulation(request);
    }

    @GetMapping(path = "/{simulationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String simulationId) {
        return new SseEmitter(60_000L);
    }
}
```

- [ ] **Step 6: Run API contract test**

Run: `cd backend && ./gradlew test --tests "*SimulationControllerTest"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: add simulation API contract"
```

## Task 6: Kafka Payment Event MVP

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentRequestedEvent.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentResultEvent.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`

- [ ] **Step 1: Write payment worker test**

Create `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`:

```java
package com.timedeal.seatreservation.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSimulationWorkerTest {
    @Test
    void deterministicPaymentRuleSucceedsForEvenReservationId() {
        PaymentSimulationWorker worker = new PaymentSimulationWorker(null);
        PaymentResultEvent result = worker.simulate(new PaymentRequestedEvent(200L, 10L, "payment-200"));

        assertThat(result.reservationId()).isEqualTo(200L);
        assertThat(result.success()).isTrue();
    }

    @Test
    void deterministicPaymentRuleFailsForOddReservationId() {
        PaymentSimulationWorker worker = new PaymentSimulationWorker(null);
        PaymentResultEvent result = worker.simulate(new PaymentRequestedEvent(201L, 10L, "payment-201"));

        assertThat(result.reservationId()).isEqualTo(201L);
        assertThat(result.success()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*PaymentSimulationWorkerTest"`

Expected: FAIL because payment event classes do not exist.

- [ ] **Step 3: Implement payment event records**

Create `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentRequestedEvent.java`:

```java
package com.timedeal.seatreservation.payment;

public record PaymentRequestedEvent(
        long reservationId,
        long seatId,
        String idempotencyKey
) {
}
```

Create `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentResultEvent.java`:

```java
package com.timedeal.seatreservation.payment;

public record PaymentResultEvent(
        long reservationId,
        long seatId,
        boolean success,
        String message
) {
}
```

- [ ] **Step 4: Implement deterministic payment worker logic**

Create `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java`:

```java
package com.timedeal.seatreservation.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class PaymentSimulationWorker {
    private final KafkaTemplate<String, PaymentResultEvent> kafkaTemplate;

    public PaymentSimulationWorker(KafkaTemplate<String, PaymentResultEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "payment.events", groupId = "payment-simulation-worker")
    public void handle(PaymentRequestedEvent event) {
        PaymentResultEvent result = simulate(event);
        if (kafkaTemplate != null) {
            kafkaTemplate.send("payment-results.events", String.valueOf(result.reservationId()), result);
        }
    }

    public PaymentResultEvent simulate(PaymentRequestedEvent event) {
        boolean success = event.reservationId() % 2 == 0;
        String message = success ? "결제 성공" : "결제 실패";
        return new PaymentResultEvent(event.reservationId(), event.seatId(), success, message);
    }
}
```

- [ ] **Step 5: Run payment worker test**

Run: `cd backend && ./gradlew test --tests "*PaymentSimulationWorkerTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/timedeal/seatreservation/payment backend/src/test/java/com/timedeal/seatreservation/payment
git commit -m "feat: add payment simulation worker"
```

## Task 7: Frontend Korean Dashboard MVP

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/src/lib/labels.ts`
- Create: `frontend/src/app/page.tsx`

- [ ] **Step 1: Create frontend package**

Create `frontend/package.json`:

```json
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "lint": "next lint"
  },
  "dependencies": {
    "@types/node": "latest",
    "@types/react": "latest",
    "@types/react-dom": "latest",
    "next": "latest",
    "react": "latest",
    "react-dom": "latest",
    "typescript": "latest"
  },
  "devDependencies": {}
}
```

- [ ] **Step 2: Create TypeScript config**

Create `frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["dom", "dom.iterable", "es2022"],
    "allowJs": false,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx"],
  "exclude": ["node_modules"]
}
```

- [ ] **Step 3: Create Korean label mapping**

Create `frontend/src/lib/labels.ts`:

```ts
export const seatStatusLabels = {
  AVAILABLE: "선택 가능",
  HELD: "임시 선점",
  PAYMENT_IN_PROGRESS: "결제 진행 중",
  RESERVED: "예약 완료",
} as const;

export const seatStatusColors = {
  AVAILABLE: "#22c55e",
  HELD: "#f59e0b",
  PAYMENT_IN_PROGRESS: "#ef4444",
  RESERVED: "#9ca3af",
} as const;
```

- [ ] **Step 4: Create dashboard page**

Create `frontend/src/app/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { seatStatusColors, seatStatusLabels } from "../lib/labels";

type SeatStatus = keyof typeof seatStatusLabels;

const initialSeats: Array<{ id: string; label: string; status: SeatStatus }> = Array.from({ length: 120 }, (_, index) => ({
  id: `seat-${index + 1}`,
  label: `${String.fromCharCode(65 + Math.floor(index / 12))}-${(index % 12) + 1}`,
  status: "AVAILABLE",
}));

export default function Page() {
  const [seats, setSeats] = useState(initialSeats);

  function startDemo() {
    setSeats((current) =>
      current.map((seat, index) => {
        if (index < 20) return { ...seat, status: "RESERVED" };
        if (index < 34) return { ...seat, status: "PAYMENT_IN_PROGRESS" };
        if (index < 50) return { ...seat, status: "HELD" };
        return seat;
      }),
    );
  }

  return (
    <main style={{ padding: 24, fontFamily: "system-ui, sans-serif" }}>
      <section style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center" }}>
        <div>
          <h1 style={{ margin: 0 }}>콘서트 좌석 예약 시뮬레이션</h1>
          <p style={{ color: "#475569" }}>Redis 대기열, PostgreSQL 정합성, Kafka 결제 이벤트를 눈으로 확인합니다.</p>
        </div>
        <button onClick={startDemo} style={{ padding: "10px 14px", border: 0, background: "#111827", color: "white" }}>
          시뮬레이션 시작
        </button>
      </section>

      <section style={{ marginTop: 24, display: "grid", gridTemplateColumns: "2fr 1fr", gap: 20 }}>
        <div>
          <div style={{ textAlign: "center", fontWeight: 700, marginBottom: 12 }}>STAGE</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(12, 1fr)", gap: 6 }}>
            {seats.map((seat) => (
              <div
                key={seat.id}
                title={`${seat.label} ${seatStatusLabels[seat.status]}`}
                style={{
                  height: 24,
                  borderRadius: 4,
                  background: seatStatusColors[seat.status],
                }}
              />
            ))}
          </div>
        </div>

        <aside style={{ display: "grid", gap: 12 }}>
          <section style={{ border: "1px solid #d1d5db", padding: 12 }}>
            <h2 style={{ marginTop: 0 }}>전체 지표</h2>
            <p>대기열 크기: 0</p>
            <p>예약 성공: {seats.filter((seat) => seat.status === "RESERVED").length}</p>
            <p>결제 실패: 0</p>
            <p>Kafka 지연: 0</p>
          </section>
          <section style={{ border: "1px solid #d1d5db", padding: 12 }}>
            <h2 style={{ marginTop: 0 }}>상태 범례</h2>
            {Object.entries(seatStatusLabels).map(([status, label]) => (
              <p key={status}>
                <span style={{ display: "inline-block", width: 12, height: 12, background: seatStatusColors[status as SeatStatus] }} /> {label}
              </p>
            ))}
          </section>
        </aside>
      </section>
    </main>
  );
}
```

- [ ] **Step 5: Run frontend build**

Run: `cd frontend && npm install && npm run build`

Expected: PASS and Next.js production build completes.

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "feat: add korean simulation dashboard"
```

## Task 8: Local Infrastructure And Production v1 Notes

**Files:**
- Create: `backend/Dockerfile`
- Create: `infra/docker-compose.yml`
- Create: `infra/nginx.conf`
- Create: `infra/prod/lightsail-a.md`
- Create: `infra/prod/lightsail-b.md`
- Create: `infra/prod/lightsail-c.md`
- Create: `infra/prod/rds.md`

- [ ] **Step 1: Create backend Dockerfile**

Create `backend/Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create Nginx load balancer config**

Create `infra/nginx.conf`:

```nginx
events {}

http {
  upstream api_servers {
    server api-a:8080;
    server api-b:8080;
  }

  server {
    listen 8080;

    location / {
      proxy_pass http://api_servers;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
  }
}
```

- [ ] **Step 3: Create Docker Compose stack**

Create `infra/docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: seat_reservation
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
    ports:
      - "5432:5432"

  redis:
    image: redis:7
    ports:
      - "6379:6379"

  kafka:
    image: bitnami/kafka:3.7
    environment:
      KAFKA_CFG_NODE_ID: 1
      KAFKA_CFG_PROCESS_ROLES: broker,controller
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
    ports:
      - "9092:9092"

  api-a:
    build:
      context: ../backend
    environment:
      SPRING_PROFILES_ACTIVE: api
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/seat_reservation
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - postgres
      - redis
      - kafka

  api-b:
    build:
      context: ../backend
    environment:
      SPRING_PROFILES_ACTIVE: api
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/seat_reservation
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - postgres
      - redis
      - kafka

  worker:
    build:
      context: ../backend
    environment:
      SPRING_PROFILES_ACTIVE: worker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/seat_reservation
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - postgres
      - redis
      - kafka

  nginx:
    image: nginx:1.27
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "8080:8080"
    depends_on:
      - api-a
      - api-b
```

- [ ] **Step 4: Start infrastructure**

Run: `cd infra && docker compose up --build`

Expected: PostgreSQL, Redis, Kafka, API A, API B, worker, and Nginx containers start.

- [ ] **Step 5: Verify load balancer**

Run: `curl -X POST http://localhost:8080/simulations -H "Content-Type: application/json" -d "{\"virtualUserCount\":100}"`

Expected: response includes `simulationId`, `시뮬레이션이 시작되었습니다.`, and `virtualUserCount`.

- [ ] **Step 6: Commit**

Create `infra/prod/lightsail-a.md`:

```markdown
# Lightsail A: Nginx + api-a

## Role

Lightsail A is the public backend entry point for production v1.

It runs:

- Nginx
- `api-a`

## Responsibilities

- Terminate HTTPS for `api.<domain>`.
- Proxy API traffic to `api-a` on Lightsail A and `api-b` on Lightsail B.
- Disable proxy buffering for SSE endpoints.

## Limitation

Nginx on Lightsail A is a cost-control choice, not a highly available managed load balancer. Production v2 can replace it with Lightsail Load Balancer or AWS ALB.
```

Create `infra/prod/lightsail-b.md`:

```markdown
# Lightsail B: api-b + worker

## Role

Lightsail B runs the second API instance and the background worker.

It runs:

- `api-b`
- worker

## Responsibilities

- Receive proxied API traffic from Nginx on Lightsail A.
- Consume Kafka events through the worker process.
- Keep API state externalized to Redis, Kafka, and RDS PostgreSQL.
```

Create `infra/prod/lightsail-c.md`:

```markdown
# Lightsail C: Redis + Kafka

## Role

Lightsail C is the self-hosted infrastructure node for production v1.

It runs:

- Redis
- Kafka

## Responsibilities

- Store waiting queue, admission token, and temporary seat-hold state in Redis.
- Carry payment, reservation, audit, and metric events through Kafka.

## Resource Note

Kafka and Redis are placed on a dedicated Lightsail instance to reduce memory pressure on the application servers. Kafka heap should be capped for the selected Lightsail bundle.
```

Create `infra/prod/rds.md`:

```markdown
# RDS PostgreSQL

## Role

RDS PostgreSQL is the production source of truth.

It stores:

- concerts
- seats
- simulation sessions
- virtual users
- reservations
- payments
- outbox events
- audit logs

## Notes

Production v1 uses RDS instead of running PostgreSQL inside an application server. This keeps durable state outside the Lightsail compute nodes.
```

- [ ] **Step 7: Commit**

```bash
git add backend/Dockerfile infra
git commit -m "chore: add local and prod infrastructure notes"
```

## Task 9: Korean README And MVP Verification

**Files:**
- Create: `README.md`

- [ ] **Step 1: Create Korean README**

Create `README.md`:

```markdown
# 콘서트 좌석 예약 시뮬레이션

Java 백엔드 포트폴리오용 좌석 예약 시뮬레이션입니다.

## 목표

- Redis 대기열과 입장 토큰
- PostgreSQL 예약 정합성
- Kafka 결제 이벤트 처리
- API 인스턴스 2개와 프록시 기반 요청 분산
- 실시간 좌석표 대시보드
- Vercel + Lightsail 3대 + RDS 기반 production v1 배포

## 로컬 실행

```bash
cd infra
docker compose up --build
```

## API 확인

```bash
curl -X POST http://localhost:8080/simulations \
  -H "Content-Type: application/json" \
  -d "{\"virtualUserCount\":100}"
```

## 배포 구조

- Vercel: 프론트엔드
- Lightsail A: Nginx + api-a
- Lightsail B: api-b + worker
- Lightsail C: Redis + Kafka
- RDS PostgreSQL: 영속 데이터 저장소

## 데모에서 볼 것

- 좌석이 초록색, 노란색, 빨간색, 회색으로 변하는지 확인합니다.
- 대기열 크기와 예약 성공 수가 바뀌는지 확인합니다.
- 같은 좌석이 중복 예약되지 않는지 확인합니다.
- 결제 실패 이벤트가 좌석 해제 또는 실패 상태로 이어지는지 확인합니다.
```
- [ ] **Step 2: Run backend verification**

Run: `cd backend && ./gradlew test`

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run frontend verification**

Run: `cd frontend && npm run build`

Expected: PASS and production build completes.

- [ ] **Step 4: Run local stack verification**

Run: `cd infra && docker compose up --build`

Expected: stack starts and `POST /simulations` returns a Korean success message.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: add korean demo guide"
```

## Self-Review

Spec coverage:

- Multi-server request handling: Task 8.
- Redis waiting queue and temporary state: Task 4.
- PostgreSQL reservation consistency: Task 3.
- Kafka payment processing: Task 6.
- Korean UI output: Task 7 and Task 9.
- SSE contract: Task 5.
- Failure handling foundation: Task 2, Task 3, Task 6.

Known follow-up plans after MVP:

- Implement full outbox publisher with `SKIP LOCKED`.
- Implement real Redis sorted set admission polling against Redis.
- Implement seat hold expiration worker.
- Implement payment retry and DLQ with Spring Kafka error handlers.
- Replace frontend demo seat changes with live SSE events.
- Add k6 or Gatling load tests for 100, 500, and 1,000 users.

