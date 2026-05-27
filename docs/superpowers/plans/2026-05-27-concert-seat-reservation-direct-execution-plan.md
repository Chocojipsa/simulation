# Concert Seat Reservation Direct Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This project will not use subagent-driven execution unless the user explicitly asks for it again.

**Goal:** Turn the current backend skeleton into a working Korean local demo first, then replace the demo internals with Redis, PostgreSQL, Kafka, and multi-instance infrastructure.

**Architecture:** Keep one stable simulation API contract. First implement a fast `demo` profile with in-memory state, SSE, and a Spring-served Korean dashboard so the seat map visibly changes on localhost. Then connect the same flow to Redis queue admission, PostgreSQL reservation consistency, Kafka payment events, Docker Compose, and the Lightsail/RDS production topology.

**Tech Stack:** Java 17, Spring Boot, Gradle Groovy DSL, Spring MVC, SSE, Java concurrency for demo mode, Spring Data Redis, PostgreSQL, Kafka, Docker Compose, Korean static dashboard first, Next.js/Vercel after backend behavior is stable.

---

## Current State

Already implemented:

- `backend/` Spring Boot project with Java 17 and Gradle Groovy DSL.
- Domain enums and transition rules:
  - `SeatStatus`
  - `VirtualUserStatus`
  - `PaymentStatus`
  - `DomainTransitionPolicy`
- PostgreSQL migration:
  - `concerts`
  - `seats`
  - `simulation_sessions`
  - `virtual_users`
  - `reservations`
  - `payments`
  - `outbox_events`
  - partial unique index `active_reservation_per_seat`
- Redis waiting queue service:
  - sorted-set waiting queue
  - admission token with TTL
  - focused unit tests
- Simulation API contract:
  - `POST /simulations`
  - `GET /simulations/{simulationId}/events`
- Git branch:
  - `feature/concert-seat-reservation-mvp`

Known gaps:

- Some Korean strings are mojibake and must be repaired before UI/demo work.
- `POST /simulations` only returns a UUID. It does not create users, seats, progress, or visible simulation state.
- SSE currently opens an emitter but does not publish events.
- No local dashboard exists yet.
- PostgreSQL schema exists, but there are no repositories or transactional seat commands.
- Redis queue exists, but it is not connected to simulation creation or admission.
- Kafka payment worker is not implemented yet.
- Full backend tests cannot pass while Docker is unavailable because Testcontainers needs a running Docker daemon.

## Execution Rules

- Execute directly in this session with `superpowers:executing-plans`.
- Do not dispatch subagents.
- Prefer a visible vertical slice over isolated low-level components.
- Keep user-facing text Korean.
- Keep code identifiers, API paths, database names, and internal docs English.
- Commit after each coherent task when tests for that task pass.
- For now, verify non-Docker tests locally. Docker/Testcontainers verification is deferred until Docker daemon is available.

## File Structure

Files to modify or create during the next execution pass:

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`
- Create: `backend/src/main/resources/application-demo.yml`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SeatView.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserView.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/TimelineEntry.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationMetrics.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationSnapshot.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationRunner.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventHub.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationRunnerTest.java`
- Create: `backend/src/main/resources/static/index.html`
- Create: `backend/src/main/resources/static/app.js`
- Create: `backend/src/main/resources/static/styles.css`
- Later create: `backend/src/main/java/com/timedeal/seatreservation/payment/*`
- Later create: `infra/docker-compose.yml`
- Later create: `infra/nginx.conf`
- Later create: `README.md`

---

## Task 1: Repair Korean Text And Demo Profile

**Purpose:** Make the current API output Korean correctly and allow the app to run in demo mode without PostgreSQL, Redis, or Kafka.

**Files:**

- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java`
- Create: `backend/src/main/resources/application-demo.yml`

- [x] **Step 1: Fix the Korean response message**

Use this exact message in production code and tests:

```java
"시뮬레이션이 시작되었습니다."
```

- [x] **Step 2: Add demo profile auto-configuration exclusions**

Create `backend/src/main/resources/application-demo.yml`:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
      - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
      - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
```

- [x] **Step 3: Run the simulation controller test**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SimulationControllerTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationControllerTest.java backend/src/main/resources/application-demo.yml
git commit -m "fix: repair korean simulation response"
```

---

## Task 2: Working In-Memory Simulation State

**Purpose:** `POST /simulations` must create a real session with seats, virtual users, metrics, and a snapshot endpoint.

**Files:**

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SeatView.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserView.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/TimelineEntry.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationMetrics.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationSnapshot.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationController.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java`

- [x] **Step 1: Add snapshot model records**

Create records with these fields:

```java
public record SeatView(long id, String label, SeatStatus status) {}
public record TimelineEntry(String label, String message) {}
public record VirtualUserView(UUID id, String displayName, VirtualUserStatus status, String selectedSeatLabel, List<TimelineEntry> timeline) {}
public record SimulationMetrics(int queueSize, int admittedCount, int heldCount, int paymentInProgressCount, int reservedCount, int failedCount) {}
public record SimulationSnapshot(UUID simulationId, List<SeatView> seats, List<VirtualUserView> users, SimulationMetrics metrics, boolean running) {}
```

- [x] **Step 2: Write `SimulationServiceTest`**

The test must assert:

- creating a simulation returns the requested count
- a snapshot exists for the returned simulation id
- the snapshot has 120 seats
- the snapshot has the requested number of virtual users
- all initial seats are `AVAILABLE`
- all initial users are `QUEUED`

- [x] **Step 3: Implement `SimulationStateStore`**

Use:

```java
ConcurrentHashMap<UUID, MutableSimulationState>
```

Initial state:

- 120 seats
- labels `A-1` through `J-12`
- virtual users named `사용자 1`, `사용자 2`, ...
- user status `QUEUED`
- initial timeline message `대기열에 진입했습니다.`

- [x] **Step 4: Add snapshot endpoint**

Add:

```java
@GetMapping("/{simulationId}")
public SimulationSnapshot getSimulation(@PathVariable UUID simulationId)
```

Expected path:

```text
GET /simulations/{simulationId}
```

- [x] **Step 5: Run focused tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SimulationServiceTest" --tests "*SimulationControllerTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: create simulation snapshots"
```

---

## Task 3: Simulation Progression And SSE

**Purpose:** Seats must visibly move from available to held, payment in progress, reserved, or released/failed.

**Files:**

- Create: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationRunner.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventHub.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/events/SimulationEventStream.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationRunnerTest.java`

- [ ] **Step 1: Write runner tests**

The tests must assert:

- one tick admits at least one queued user
- admitted users eventually hold seats
- payment succeeds for deterministic users and marks seats `RESERVED`
- payment fails for deterministic users and releases seats back to `AVAILABLE`
- reserved seat count never exceeds 120

- [ ] **Step 2: Implement deterministic runner**

Progression rules:

- On each tick, admit up to 10 queued users.
- An admitted user selects the first available seat.
- A held seat moves to `PAYMENT_IN_PROGRESS` on a later tick.
- Payment succeeds when `abs(userId.hashCode()) % 5 != 0`.
- Payment failure marks the user `FAILED` and releases the seat to `AVAILABLE`.
- Payment success marks the user `RESERVED` and the seat `RESERVED`.

- [ ] **Step 3: Implement `SimulationEventHub`**

Responsibilities:

- register `SseEmitter` per simulation id
- remove emitter on completion, timeout, and error
- publish `SimulationSnapshot` as event name `snapshot`

- [ ] **Step 4: Start runner after simulation creation**

When `POST /simulations` succeeds, start background progression for that simulation.

- [ ] **Step 5: Run focused tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SimulationRunnerTest" --tests "*SimulationServiceTest" --tests "*SimulationControllerTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/main/java/com/timedeal/seatreservation/events backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: stream live simulation progress"
```

---

## Task 4: Korean Local Dashboard Without NPM

**Purpose:** The reviewer can open localhost and watch the seat map change without installing frontend dependencies.

**Files:**

- Create: `backend/src/main/resources/static/index.html`
- Create: `backend/src/main/resources/static/app.js`
- Create: `backend/src/main/resources/static/styles.css`

- [ ] **Step 1: Build the static dashboard**

The first screen must include:

- title: `콘서트 좌석 예약 시뮬레이션`
- input for virtual user count
- button: `시뮬레이션 시작`
- seat map with 120 seats
- legend:
  - `선택 가능`
  - `임시 선점`
  - `결제 진행 중`
  - `예약 완료`
- metrics:
  - `대기열 크기`
  - `입장 완료`
  - `임시 선점`
  - `결제 진행 중`
  - `예약 성공`
  - `결제 실패`
- selected user timeline panel

- [ ] **Step 2: Wire API calls**

`app.js` must:

- call `POST /simulations`
- call `GET /simulations/{simulationId}` once for initial state
- subscribe to `GET /simulations/{simulationId}/events`
- render each `snapshot` SSE event
- allow clicking one virtual user to show that user's timeline

- [ ] **Step 3: Run demo server**

Run:

```powershell
cd backend
.\gradlew.bat bootRun --args='--spring.profiles.active=demo'
```

Expected:

```text
Tomcat started on port 8080
```

Open:

```text
http://localhost:8080
```

- [ ] **Step 4: Commit**

```powershell
git add backend/src/main/resources/static
git commit -m "feat: add korean local dashboard"
```

---

## Task 5: Connect Redis Queue To The Same Flow

**Purpose:** Replace demo admission ordering with Redis sorted-set queue operations while preserving the same dashboard behavior.

**Files:**

- Modify: `backend/src/main/java/com/timedeal/seatreservation/queue/WaitingQueueService.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationRunner.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/simulation/RedisAdmissionSimulationTest.java`

- [ ] **Step 1: Add runner seam for admission**

Create an admission interface:

```java
public interface AdmissionQueue {
    void enter(String simulationId, String userId);
    List<String> pick(String simulationId, int limit);
    void grant(String simulationId, String userId);
}
```

- [ ] **Step 2: Implement in-memory and Redis adapters**

Implement:

- `InMemoryAdmissionQueue`
- `RedisAdmissionQueue`

- [ ] **Step 3: Update runner to use `AdmissionQueue`**

The runner should not know whether admission comes from memory or Redis.

- [ ] **Step 4: Run queue and runner tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*WaitingQueueServiceTest" --tests "*SimulationRunnerTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation backend/src/test/java/com/timedeal/seatreservation
git commit -m "feat: connect simulation admission to redis queue"
```

---

## Task 6: Add Kafka Payment Worker

**Purpose:** Move payment result generation behind Kafka events while keeping the demo behavior deterministic.

**Files:**

- Create: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentRequestedEvent.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentResultEvent.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java`

- [ ] **Step 1: Add event records**

Use:

```java
public record PaymentRequestedEvent(long reservationId, long seatId, String idempotencyKey) {}
public record PaymentResultEvent(long reservationId, long seatId, boolean success, String message) {}
```

- [ ] **Step 2: Add deterministic worker**

Use:

```java
boolean success = event.reservationId() % 5 != 0;
String message = success ? "결제 성공" : "결제 실패";
```

- [ ] **Step 3: Run payment tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*PaymentSimulationWorkerTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/payment backend/src/test/java/com/timedeal/seatreservation/payment
git commit -m "feat: add payment simulation worker"
```

---

## Task 7: PostgreSQL Transactional Seat Reservation

**Purpose:** Move seat correctness into PostgreSQL transactions and indexes.

**Files:**

- Create: `backend/src/main/java/com/timedeal/seatreservation/seat/SeatReservationService.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/seat/SeatReservationResult.java`
- Create: `backend/src/test/java/com/timedeal/seatreservation/seat/SeatReservationServiceTest.java`

- [ ] **Step 1: Add transactional command**

The service must expose:

```java
public SeatReservationResult holdSeat(UUID simulationId, UUID virtualUserId, long seatId, String idempotencyKey)
```

- [ ] **Step 2: Enforce one active reservation per seat**

Use the existing partial unique index:

```sql
active_reservation_per_seat
```

- [ ] **Step 3: Run database tests when Docker is available**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*SeatReservation*"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit**

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/seat backend/src/test/java/com/timedeal/seatreservation/seat
git commit -m "feat: enforce transactional seat holds"
```

---

## Task 8: Local Multi-Server Infrastructure

**Purpose:** Demonstrate the local version of production topology.

**Files:**

- Create: `backend/Dockerfile`
- Create: `infra/docker-compose.yml`
- Create: `infra/nginx.conf`

- [ ] **Step 1: Add Dockerfile**

Use Java 17 build and runtime images.

- [ ] **Step 2: Add Docker Compose**

Services:

- `postgres`
- `redis`
- `kafka`
- `api-a`
- `api-b`
- `worker`
- `nginx`

- [ ] **Step 3: Configure Nginx**

Proxy:

- `/simulations`
- `/simulations/*/events` with buffering disabled

- [ ] **Step 4: Run local stack when Docker is available**

Run:

```powershell
cd infra
docker compose up --build
```

Expected:

```text
nginx, api-a, api-b, worker, postgres, redis, kafka are running
```

- [ ] **Step 5: Commit**

```powershell
git add backend/Dockerfile infra
git commit -m "chore: add local multi-server stack"
```

---

## Task 9: README And Portfolio Explanation

**Purpose:** Make the project understandable to a Korean reviewer.

**Files:**

- Create: `README.md`

- [ ] **Step 1: Write Korean README**

Sections:

- `프로젝트 목표`
- `로컬 데모 실행`
- `아키텍처`
- `Redis를 쓰는 이유`
- `PostgreSQL을 쓰는 이유`
- `Kafka를 쓰는 이유`
- `멀티 서버 구조`
- `프로덕션 v1 배포 계획`
- `면접에서 설명할 포인트`

- [ ] **Step 2: Run available verification**

Run non-Docker tests:

```powershell
cd backend
.\gradlew.bat test --tests "*DomainTransitionPolicyTest" --tests "*WaitingQueueServiceTest" --tests "*Simulation*"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit**

```powershell
git add README.md
git commit -m "docs: add korean portfolio guide"
```

---

## Immediate Next Action

Start with Task 1, then Task 2, then Task 3. After Task 4, the project should have a working local Korean demo at:

```text
http://localhost:8080
```

At that point, the remaining work becomes infrastructure depth rather than proving the product concept.
