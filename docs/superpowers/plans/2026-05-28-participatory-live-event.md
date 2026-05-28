# Participatory Live Event Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a shared live ticketing event where browser users and AI participants compete through the same Redis queue, PostgreSQL seat hold, Kafka payment, and multi-server API path.

**Architecture:** Add event-facing APIs over the current simulation engine first. Keep PostgreSQL as the seat concurrency authority, Redis as the live event read model and queue state, Kafka as the async payment boundary, and traffic-generator as the AI participant runner. Update the React app from an isolated simulation dashboard into a Korean live event room.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Kafka, Spring Data Redis, PostgreSQL/Flyway, React 18, Vite, TypeScript, Vitest, Docker Compose, nginx.

---

## File Structure

Backend event facade:

- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`: event-facing REST API.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`: maps event commands to existing simulation services.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventSnapshot.java`: frontend contract for active event state.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventResponse.java`: active event metadata response.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/JoinEventRequest.java`: optional participant display name.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/JoinEventResponse.java`: participant id and event id.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/SeatHoldResponse.java`: explicit seat hold result.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/PaymentConfirmResponse.java`: explicit payment confirmation result.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/StartAiParticipantsRequest.java`: AI count and concurrency.
- Create `backend/src/main/java/com/timedeal/seatreservation/event/ParticipantType.java`: `HUMAN`, `AI`.

Backend simulation extensions:

- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`: add human participant registration, explicit seat hold state, event snapshot support.
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`: demo profile implementation of new state operations.
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`: Redis implementation of new state operations.
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`: expose methods that accept explicit participant id and seat id.
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserView.java`: add participant type and payment attempt count.
- Modify `backend/src/main/java/com/timedeal/seatreservation/domain/VirtualUserStatus.java`: add `WAITING_ROOM` and `PAYMENT_FAILED`.

Backend AI generator:

- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/VirtualUserCommandClient.java`: add event API methods.
- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/RestVirtualUserCommandClient.java`: call `/api/events/...` endpoints.
- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java`: run AI participants against event APIs.
- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/TrafficGeneratorService.java`: keep async participant spawning, rename concepts in method internals only when low-risk.

Frontend:

- Create `frontend/src/api/liveEventApi.ts`: event API client.
- Create `frontend/src/hooks/useLiveEventRoom.ts`: active event, participant identity, polling, commands.
- Create `frontend/src/domain/liveEventSelectors.ts`: derived UI state.
- Create `frontend/src/components/EventHeader.tsx`: title, countdown/open state, metrics.
- Create `frontend/src/components/MyTicketPanel.tsx`: queue rank, selected seat, reserve/payment buttons.
- Create `frontend/src/components/EventActivityPanel.tsx`: Korean activity feed and infra metrics.
- Modify `frontend/src/components/SeatMap.tsx`: support clickable seats and "my seat" styling.
- Modify `frontend/src/App.tsx`: render event room instead of isolated simulation starter.
- Modify `frontend/src/styles.css`: event room layout and readable Korean UI.

Tests:

- Create `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`.
- Create `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`.
- Modify existing simulation, Redis store, payment, and generator tests next to touched files.
- Create `frontend/src/api/liveEventApi.test.ts`.
- Create `frontend/src/hooks/useLiveEventRoom.test.tsx`.
- Create `frontend/src/domain/liveEventSelectors.test.ts`.
- Modify `frontend/src/App.test.tsx`, `frontend/src/components/SeatMap` tests if present.

---

## Task 1: Preserve And Verify Payment Reopen Foundation

**Files:**

- Existing modified backend/payment, seat, simulation, Redis, test, and `infra/docker-compose.yml` files currently in the working tree.

- [ ] **Step 1: Inspect pending diff**

Run:

```powershell
git status --short
git diff --stat
git diff -- backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java
git diff -- backend/src/main/java/com/timedeal/seatreservation/seat/SeatReservationService.java
git diff -- backend/src/main/java/com/timedeal/seatreservation/simulation/PaymentResultListener.java
```

Expected:

- Payment failure rate is configurable.
- Payment result listener applies DB state before Redis snapshot state.
- Failed payment releases the seat back to `AVAILABLE`.
- `PaymentKafkaConfig.java` and `PaymentKafkaConfigTest.java` are untracked.

- [ ] **Step 2: Run focused backend tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*PaymentSimulationWorkerTest" --tests "*PaymentKafkaConfigTest" --tests "*SeatReservationServiceTest" --tests "*PaymentResultListenerTest" --tests "*SimulationServiceTest" --tests "*RedisSimulationStateStoreTest" --tests "*HttpVirtualUserHttpClientTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Verify local Docker flow**

Run:

```powershell
cd ..\infra
docker compose up -d --build
docker compose restart nginx
```

Then run one API flow:

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/simulations -ContentType application/json -Body '{"virtualUserCount":150}'
$runUri = "http://localhost:8080/api/simulations/$($created.simulationId)/run"
Invoke-RestMethod -Method Post -Uri $runUri -ContentType application/json -Body '{"virtualUserCount":150,"concurrency":50}'
Start-Sleep -Seconds 30
Invoke-RestMethod -Uri "http://localhost:8080/api/simulations/$($created.simulationId)" | ConvertTo-Json -Depth 8
```

Expected:

- Snapshot has `reservedCount` increasing.
- Failed payment seats can become `AVAILABLE`.
- No users remain stuck forever only because a failed payment reopened a seat late.

- [ ] **Step 4: Commit payment foundation**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java backend/src/main/java/com/timedeal/seatreservation/payment/PaymentSimulationWorker.java backend/src/main/java/com/timedeal/seatreservation/payment/PaymentKafkaConfig.java backend/src/main/java/com/timedeal/seatreservation/seat/SeatReservationService.java backend/src/main/java/com/timedeal/seatreservation/simulation/PaymentResultListener.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java backend/src/main/resources/application-local.yml backend/src/main/resources/application-prod.yml backend/src/test/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClientTest.java backend/src/test/java/com/timedeal/seatreservation/payment/PaymentSimulationWorkerTest.java backend/src/test/java/com/timedeal/seatreservation/payment/PaymentKafkaConfigTest.java backend/src/test/java/com/timedeal/seatreservation/seat/SeatReservationServiceTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/PaymentResultListenerTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/SimulationServiceTest.java backend/src/test/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStoreTest.java infra/docker-compose.yml
git commit -m "feat: reopen seats after payment failure"
```

Expected:

- Only the payment foundation files are committed.
- Live event implementation work remains in separate commits.

---

## Task 2: Add Event API Contracts

**Files:**

- Create `backend/src/main/java/com/timedeal/seatreservation/event/ParticipantType.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventResponse.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventSnapshot.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/JoinEventRequest.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/JoinEventResponse.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/SeatHoldResponse.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/PaymentConfirmResponse.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/StartAiParticipantsRequest.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserView.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/domain/VirtualUserStatus.java`

- [ ] **Step 1: Write the contract serialization test**

Create `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventContractTest.java`:

```java
package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.simulation.SeatView;
import com.timedeal.seatreservation.simulation.ServerStatsView;
import com.timedeal.seatreservation.simulation.SimulationMetrics;
import com.timedeal.seatreservation.simulation.TimelineEntry;
import com.timedeal.seatreservation.simulation.VirtualUserView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEventContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesLiveEventSnapshotForFrontend() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        LiveEventSnapshot snapshot = new LiveEventSnapshot(
                eventId,
                "부산 콘서트 티켓팅",
                "OPEN",
                Instant.parse("2026-05-28T12:00:00Z"),
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

        assertThat(json).contains("\"eventId\":\"" + eventId + "\"");
        assertThat(json).contains("\"title\":\"부산 콘서트 티켓팅\"");
        assertThat(json).contains("\"type\":\"HUMAN\"");
        assertThat(json).contains("\"myParticipantId\":\"" + participantId + "\"");
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventContractTest"
```

Expected:

```text
Compilation failed
cannot find symbol class LiveEventSnapshot
```

- [ ] **Step 3: Add event contract records**

Create `ParticipantType.java`:

```java
package com.timedeal.seatreservation.event;

public enum ParticipantType {
    HUMAN,
    AI
}
```

Create `LiveEventResponse.java`:

```java
package com.timedeal.seatreservation.event;

import java.time.Instant;
import java.util.UUID;

public record LiveEventResponse(
        UUID eventId,
        String title,
        String status,
        Instant opensAt,
        int seatCount
) {
}
```

Create `LiveEventSnapshot.java`:

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
        Instant opensAt,
        List<SeatView> seats,
        List<VirtualUserView> participants,
        SimulationMetrics metrics,
        List<ServerStatsView> serverStats,
        boolean running,
        UUID myParticipantId
) {
}
```

Create `JoinEventRequest.java`:

```java
package com.timedeal.seatreservation.event;

import jakarta.validation.constraints.Size;

public record JoinEventRequest(
        @Size(max = 30) String displayName
) {
    public String normalizedDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return "나";
        }
        return displayName.trim();
    }
}
```

Create `JoinEventResponse.java`:

```java
package com.timedeal.seatreservation.event;

import java.util.UUID;

public record JoinEventResponse(
        UUID eventId,
        UUID participantId,
        String displayName,
        String status,
        String handledBy
) {
}
```

Create `SeatHoldResponse.java`:

```java
package com.timedeal.seatreservation.event;

import java.util.UUID;

public record SeatHoldResponse(
        UUID eventId,
        UUID participantId,
        long seatId,
        String status,
        String message,
        String selectedSeatLabel,
        String handledBy
) {
}
```

Create `PaymentConfirmResponse.java`:

```java
package com.timedeal.seatreservation.event;

import java.util.UUID;

public record PaymentConfirmResponse(
        UUID eventId,
        UUID participantId,
        String status,
        String message,
        String handledBy
) {
}
```

Create `StartAiParticipantsRequest.java`:

```java
package com.timedeal.seatreservation.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StartAiParticipantsRequest(
        @Min(1) @Max(1000) int participantCount,
        @Min(1) @Max(100) int concurrency
) {
}
```

- [ ] **Step 4: Extend existing view/status contracts**

Modify `VirtualUserStatus.java`:

```java
package com.timedeal.seatreservation.domain;

public enum VirtualUserStatus {
    CREATED,
    WAITING_ROOM,
    QUEUED,
    ADMITTED,
    SELECTING_SEAT,
    SEAT_HELD,
    PAYMENT_IN_PROGRESS,
    PAYMENT_FAILED,
    RESERVED,
    FAILED,
    EXPIRED
}
```

Modify `VirtualUserView.java`:

```java
package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.domain.VirtualUserStatus;
import com.timedeal.seatreservation.event.ParticipantType;

import java.util.List;
import java.util.UUID;

public record VirtualUserView(
        UUID id,
        String displayName,
        ParticipantType type,
        VirtualUserStatus status,
        String selectedSeatLabel,
        List<TimelineEntry> timeline,
        int seatAttemptCount,
        int conflictCount,
        int paymentAttemptCount,
        Long reservationId
) {
}
```

Update every `new VirtualUserView(...)` compile error by inserting:

```java
ParticipantType.AI
```

for existing generated users and:

```java
0, null
```

as the final `paymentAttemptCount` and `reservationId` arguments.

- [ ] **Step 5: Run contract and existing affected tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventContractTest" --tests "*SimulationServiceTest" --tests "*RedisSimulationStateStoreTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event backend/src/main/java/com/timedeal/seatreservation/domain/VirtualUserStatus.java backend/src/main/java/com/timedeal/seatreservation/simulation/VirtualUserView.java backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventContractTest.java backend/src/test/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/simulation/redis
git commit -m "feat: add live event response contracts"
```

---

## Task 3: Add Active Event Facade And Human Join

**Files:**

- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Create `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateStore.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/redis/RedisSimulationStateStore.java`
- Test `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`
- Test `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`

- [ ] **Step 1: Write service test**

Create `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`:

```java
package com.timedeal.seatreservation.simulation;

import com.timedeal.seatreservation.event.JoinEventRequest;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.LiveEventResponse;
import com.timedeal.seatreservation.event.LiveEventService;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.ParticipantType;
import com.timedeal.seatreservation.identity.ServerIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEventServiceTest {
    @Test
    void createsActiveEventOnceAndLetsHumanJoinWaitingRoom() {
        SimulationStateStore stateStore = new SimulationStateStore();
        SimulationService simulationService = new SimulationService(stateStore);
        LiveEventService service = new LiveEventService(
                simulationService,
                stateStore,
                new ServerIdentity("api-test"),
                "부산 콘서트 티켓팅",
                120
        );

        LiveEventResponse active = service.activeEvent();
        JoinEventResponse joined = service.join(active.eventId(), new JoinEventRequest("권"));
        LiveEventSnapshot snapshot = service.snapshot(active.eventId(), joined.participantId());

        assertThat(joined.displayName()).isEqualTo("권");
        assertThat(joined.status()).isEqualTo("WAITING_ROOM");
        assertThat(snapshot.participants())
                .anySatisfy(participant -> {
                    assertThat(participant.id()).isEqualTo(joined.participantId());
                    assertThat(participant.type()).isEqualTo(ParticipantType.HUMAN);
                    assertThat(participant.status().name()).isEqualTo("WAITING_ROOM");
                });
    }
}
```

- [ ] **Step 2: Write controller test**

Create `LiveEventControllerTest.java`:

```java
package com.timedeal.seatreservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveEventControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesActiveEventAndJoinEndpoint() throws Exception {
        SimulationStateStore stateStore = new SimulationStateStore();
        LiveEventService service = new LiveEventService(
                new SimulationService(stateStore),
                stateStore,
                new ServerIdentity("api-test"),
                "부산 콘서트 티켓팅",
                120
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service)).build();

        String activeJson = mvc.perform(get("/api/events/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("부산 콘서트 티켓팅")))
                .andExpect(jsonPath("$.eventId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventId = objectMapper.readTree(activeJson).get("eventId").asText();

        mvc.perform(post("/api/events/{eventId}/participants", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"권\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is(eventId)))
                .andExpect(jsonPath("$.displayName", is("권")))
                .andExpect(jsonPath("$.status", is("WAITING_ROOM")));
    }
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventServiceTest" --tests "*LiveEventControllerTest"
```

Expected:

```text
Compilation failed
cannot find symbol class LiveEventService
```

- [ ] **Step 4: Extend state gateway**

Add to `SimulationStateGateway.java`:

```java
SimulationSnapshot registerParticipant(UUID simulationId, UUID participantId, String displayName, com.timedeal.seatreservation.event.ParticipantType type, String handledBy);
```

Implement in both stores by appending a `VirtualUserView` with:

```java
new VirtualUserView(
        participantId,
        displayName,
        type,
        VirtualUserStatus.WAITING_ROOM,
        null,
        List.of(new TimelineEntry("입장", "이벤트에 입장했습니다.")),
        0,
        0,
        0,
        null
)
```

For Redis store, use the existing `mutate(...)` method and return a new `SimulationSnapshot` with `participants` appended. For demo store, append to `MutableSimulationState.users`.

- [ ] **Step 5: Add service**

Create `LiveEventService.java`:

```java
package com.timedeal.seatreservation.event;

import com.timedeal.seatreservation.identity.ServerIdentity;
import com.timedeal.seatreservation.simulation.CreateSimulationRequest;
import com.timedeal.seatreservation.simulation.SimulationService;
import com.timedeal.seatreservation.simulation.SimulationSnapshot;
import com.timedeal.seatreservation.simulation.SimulationStateGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LiveEventService {
    private final SimulationService simulationService;
    private final SimulationStateGateway stateGateway;
    private final ServerIdentity serverIdentity;
    private final String title;
    private final int seatCount;
    private final AtomicReference<UUID> activeEventId = new AtomicReference<>();

    public LiveEventService(
            SimulationService simulationService,
            SimulationStateGateway stateGateway,
            ServerIdentity serverIdentity,
            @Value("${live-event.title:Live Ticketing Event}") String title,
            @Value("${live-event.seat-count:120}") int seatCount
    ) {
        this.simulationService = simulationService;
        this.stateGateway = stateGateway;
        this.serverIdentity = serverIdentity;
        this.title = title;
        this.seatCount = seatCount;
    }

    public LiveEventResponse activeEvent() {
        UUID eventId = activeEventId.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            return simulationService.createSimulation(new CreateSimulationRequest(0)).simulationId();
        });
        return new LiveEventResponse(eventId, title, "OPEN", Instant.now(Clock.systemUTC()), seatCount);
    }

    public JoinEventResponse join(UUID eventId, JoinEventRequest request) {
        UUID participantId = UUID.randomUUID();
        String displayName = request.normalizedDisplayName();
        stateGateway.registerParticipant(eventId, participantId, displayName, ParticipantType.HUMAN, serverIdentity.id());
        return new JoinEventResponse(eventId, participantId, displayName, "WAITING_ROOM", serverIdentity.id());
    }

    public LiveEventSnapshot snapshot(UUID eventId, UUID myParticipantId) {
        SimulationSnapshot snapshot = simulationService.getSimulation(eventId);
        return new LiveEventSnapshot(
                eventId,
                title,
                status(snapshot),
                Instant.now(Clock.systemUTC()),
                snapshot.seats(),
                snapshot.users(),
                snapshot.metrics(),
                snapshot.serverStats(),
                snapshot.running(),
                myParticipantId
        );
    }

    private String status(SimulationSnapshot snapshot) {
        return snapshot.running() ? "OPEN" : "OPEN";
    }
}
```

This intentionally keeps the MVP always open; countdown becomes a separate task after the event room works.

- [ ] **Step 6: Add controller**

Create `LiveEventController.java`:

```java
package com.timedeal.seatreservation.event;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class LiveEventController {
    private final LiveEventService liveEventService;

    public LiveEventController(LiveEventService liveEventService) {
        this.liveEventService = liveEventService;
    }

    @GetMapping("/active")
    public LiveEventResponse activeEvent() {
        return liveEventService.activeEvent();
    }

    @GetMapping("/{eventId}/snapshot")
    public LiveEventSnapshot snapshot(
            @PathVariable UUID eventId,
            @RequestParam(required = false) UUID participantId
    ) {
        return liveEventService.snapshot(eventId, participantId);
    }

    @PostMapping("/{eventId}/participants")
    public JoinEventResponse join(
            @PathVariable UUID eventId,
            @Valid @RequestBody JoinEventRequest request
    ) {
        return liveEventService.join(eventId, request);
    }
}
```

- [ ] **Step 7: Run tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventServiceTest" --tests "*LiveEventControllerTest" --tests "*SimulationServiceTest" --tests "*RedisSimulationStateStoreTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/event backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: add live event join api"
```

---

## Task 4: Add Human Queue, Explicit Seat Hold, And Payment Confirm

**Files:**

- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationService.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/simulation/SimulationStateGateway.java`
- Modify both state stores.
- Modify `backend/src/main/java/com/timedeal/seatreservation/payment/PaymentRequestedEvent.java` only if it cannot represent the participant payment.

- [ ] **Step 1: Write service test for human flow**

Append to `backend/src/test/java/com/timedeal/seatreservation/simulation/LiveEventServiceTest.java`:

```java
@Test
void humanCanQueueHoldSeatAndConfirmPayment() {
    SimulationStateStore stateStore = new SimulationStateStore();
    SimulationService simulationService = new SimulationService(stateStore);
    LiveEventService service = new LiveEventService(
            simulationService,
            stateStore,
            new ServerIdentity("api-test"),
            "부산 콘서트 티켓팅",
            120
    );

    LiveEventResponse active = service.activeEvent();
    JoinEventResponse joined = service.join(active.eventId(), new JoinEventRequest("권"));

    var queue = service.enterQueue(active.eventId(), joined.participantId());
    var hold = service.holdSeat(active.eventId(), joined.participantId(), 1L);
    var confirm = service.confirmPayment(active.eventId(), joined.participantId());

    assertThat(queue.status()).isEqualTo("QUEUED");
    assertThat(hold.status()).isIn("PAYMENT_PENDING", "PAYMENT_REQUESTED");
    assertThat(hold.selectedSeatLabel()).isEqualTo("A-1");
    assertThat(confirm.status()).isEqualTo("PAYMENT_REQUESTED");
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventServiceTest"
```

Expected:

```text
Compilation failed
cannot find symbol method enterQueue
```

- [ ] **Step 3: Add explicit simulation service methods**

Add methods to `SimulationService.java`:

```java
public VirtualUserCommandResponse enterParticipantQueue(UUID simulationId, UUID participantId) {
    return enterQueue(simulationId, participantId);
}

public VirtualUserCommandResponse holdExplicitSeat(UUID simulationId, UUID participantId, long seatId) {
    if (!admitIfPossible(simulationId, participantId)) {
        stateStore.recordWaiting(simulationId, participantId, serverIdentity.id());
        return new VirtualUserCommandResponse(
                simulationId,
                participantId,
                "WAITING",
                serverIdentity.id(),
                "아직 대기 중입니다.",
                null
        );
    }

    SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
    SeatView seat = snapshot.seats().stream()
            .filter(candidate -> candidate.id() == seatId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

    if (seatReservationService == null) {
        return recordSeatConflict(simulationId, participantId, seat);
    }

    String idempotencyKey = simulationId + ":" + participantId + ":" + seat.id();
    SeatReservationResult result = seatReservationService.holdSeat(simulationId, participantId, seat.id(), idempotencyKey);
    if (result.outcome() == SeatReservationOutcome.ALREADY_HELD) {
        return recordSeatConflict(simulationId, participantId, seat);
    }

    stateStore.recordSeatHeldForPayment(simulationId, participantId, seat, result.reservationId(), serverIdentity.id());
    return new VirtualUserCommandResponse(
            simulationId,
            participantId,
            "PAYMENT_PENDING",
            serverIdentity.id(),
            seat.label() + " 좌석을 선점했습니다. 결제를 확인해 주세요.",
            seat.label()
    );
}

public VirtualUserCommandResponse confirmPayment(UUID simulationId, UUID participantId) {
    SimulationSnapshot snapshot = stateStore.snapshot(simulationId);
    VirtualUserView participant = snapshot.users().stream()
            .filter(user -> user.id().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));
    SeatView seat = snapshot.seats().stream()
            .filter(candidate -> candidate.label().equals(participant.selectedSeatLabel()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Selected seat not found for participant: " + participantId));

    Long reservationId = stateStore.markPaymentRequestedByParticipant(simulationId, participantId, serverIdentity.id());
    if (paymentKafkaTemplate != null && reservationId != null) {
        publishPaymentRequest(simulationId, participantId, seat, new SeatReservationResult(
                SeatReservationOutcome.HELD,
                reservationId,
                seat.id(),
                participantId,
                simulationId + ":" + participantId + ":" + seat.id()
        ));
    }
    return new VirtualUserCommandResponse(
            simulationId,
            participantId,
            "PAYMENT_REQUESTED",
            serverIdentity.id(),
            "결제 확인 요청을 보냈습니다.",
            seat.label()
    );
}
```

Add gateway methods:

```java
SimulationSnapshot recordSeatHeldForPayment(UUID simulationId, UUID participantId, SeatView seat, Long reservationId, String handledBy);

Long markPaymentRequestedByParticipant(UUID simulationId, UUID participantId, String handledBy);
```

Store the reservation id in Redis/demo state by writing it to `VirtualUserView.reservationId` inside `recordSeatHeldForPayment`. `markPaymentRequestedByParticipant` must read that value from the participant, increment `paymentAttemptCount`, set the participant status to `PAYMENT_IN_PROGRESS`, and return the reservation id.

- [ ] **Step 4: Add event service methods**

Add to `LiveEventService.java`:

```java
public VirtualUserCommandResponse enterQueue(UUID eventId, UUID participantId) {
    return simulationService.enterParticipantQueue(eventId, participantId);
}

public SeatHoldResponse holdSeat(UUID eventId, UUID participantId, long seatId) {
    var response = simulationService.holdExplicitSeat(eventId, participantId, seatId);
    return new SeatHoldResponse(
            eventId,
            participantId,
            seatId,
            response.status(),
            response.message(),
            response.selectedSeatLabel(),
            response.handledBy()
    );
}

public PaymentConfirmResponse confirmPayment(UUID eventId, UUID participantId) {
    var response = simulationService.confirmPayment(eventId, participantId);
    return new PaymentConfirmResponse(
            eventId,
            participantId,
            response.status(),
            response.message(),
            response.handledBy()
    );
}
```

- [ ] **Step 5: Add controller endpoints**

Add to `LiveEventController.java`:

```java
@PostMapping("/{eventId}/participants/{participantId}/queue")
public VirtualUserCommandResponse enterQueue(
        @PathVariable UUID eventId,
        @PathVariable UUID participantId
) {
    return liveEventService.enterQueue(eventId, participantId);
}

@PostMapping("/{eventId}/participants/{participantId}/seats/{seatId}/hold")
public SeatHoldResponse holdSeat(
        @PathVariable UUID eventId,
        @PathVariable UUID participantId,
        @PathVariable long seatId
) {
    return liveEventService.holdSeat(eventId, participantId, seatId);
}

@PostMapping("/{eventId}/participants/{participantId}/payment-confirm")
public PaymentConfirmResponse confirmPayment(
        @PathVariable UUID eventId,
        @PathVariable UUID participantId
) {
    return liveEventService.confirmPayment(eventId, participantId);
}
```

Import:

```java
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;
```

- [ ] **Step 6: Run tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*LiveEventServiceTest" --tests "*LiveEventControllerTest" --tests "*SimulationServiceTest" --tests "*RedisSimulationStateStoreTest" --tests "*SeatReservationServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event backend/src/main/java/com/timedeal/seatreservation/simulation backend/src/test/java/com/timedeal/seatreservation/event backend/src/test/java/com/timedeal/seatreservation/simulation
git commit -m "feat: add human live event reservation flow"
```

---

## Task 5: Route AI Participants Through Event APIs

**Files:**

- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/VirtualUserCommandClient.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/RestVirtualUserCommandClient.java`
- Modify `backend/src/main/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClient.java`
- Modify `backend/src/test/java/com/timedeal/seatreservation/generator/HttpVirtualUserHttpClientTest.java`

- [ ] **Step 1: Write generator test**

Append to `HttpVirtualUserHttpClientTest.java`:

```java
@Test
void aiParticipantUsesEventEndpoints() {
    RecordingEventCommandClient commandClient = new RecordingEventCommandClient();
    HttpVirtualUserHttpClient client = new HttpVirtualUserHttpClient(commandClient, 3, 0);
    UUID eventId = UUID.randomUUID();

    client.runUser("http://nginx:8080", eventId, 1);

    assertThat(commandClient.joinedEventId).isEqualTo(eventId);
    assertThat(commandClient.enteredQueue).isTrue();
    assertThat(commandClient.heldSeat).isTrue();
    assertThat(commandClient.confirmedPayment).isTrue();
}
```

Add a local fake client in the test:

```java
private static final class RecordingEventCommandClient implements VirtualUserCommandClient {
    UUID joinedEventId;
    boolean enteredQueue;
    boolean heldSeat;
    boolean confirmedPayment;
    UUID participantId = UUID.randomUUID();

    @Override
    public JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName) {
        joinedEventId = eventId;
        return new JoinEventResponse(eventId, participantId, displayName, "WAITING_ROOM", "api-test");
    }

    @Override
    public VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId) {
        enteredQueue = true;
        return new VirtualUserCommandResponse(eventId, participantId, "QUEUED", "api-test", "대기열에 진입했습니다.", null);
    }

    @Override
    public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
        heldSeat = true;
        return new SeatHoldResponse(eventId, participantId, 1L, "PAYMENT_PENDING", "A-1 좌석을 선점했습니다.", "A-1", "api-test");
    }

    @Override
    public PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId) {
        confirmedPayment = true;
        return new PaymentConfirmResponse(eventId, participantId, "PAYMENT_REQUESTED", "결제 확인 요청을 보냈습니다.", "api-test");
    }
}
```

- [ ] **Step 2: Extend `VirtualUserCommandClient`**

Replace the interface with methods for both compatibility and event flow:

```java
package com.timedeal.seatreservation.generator;

import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.VirtualUserCommandResponse;

import java.util.UUID;

public interface VirtualUserCommandClient {
    JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName);

    VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId);

    SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId);

    PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId);
}
```

- [ ] **Step 3: Update REST client**

In `RestVirtualUserCommandClient.java`, implement event endpoints:

```java
@Override
public JoinEventResponse joinEvent(String baseUrl, UUID eventId, String displayName) {
    return restClient.post()
            .uri(baseUrl + "/api/events/{eventId}/participants", eventId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new JoinEventRequest(displayName))
            .retrieve()
            .body(JoinEventResponse.class);
}

@Override
public VirtualUserCommandResponse postQueue(String baseUrl, UUID eventId, UUID participantId) {
    return restClient.post()
            .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/queue", eventId, participantId)
            .retrieve()
            .body(VirtualUserCommandResponse.class);
}

@Override
public SeatHoldResponse holdRandomSeat(String baseUrl, UUID eventId, UUID participantId) {
    LiveEventSnapshot snapshot = restClient.get()
            .uri(baseUrl + "/api/events/{eventId}/snapshot?participantId={participantId}", eventId, participantId)
            .retrieve()
            .body(LiveEventSnapshot.class);
    SeatView seat = snapshot.seats().stream()
            .filter(candidate -> candidate.status() == SeatStatus.AVAILABLE)
            .findAny()
            .orElseThrow(() -> new IllegalStateException("No available seat"));
    return restClient.post()
            .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/seats/{seatId}/hold", eventId, participantId, seat.id())
            .retrieve()
            .body(SeatHoldResponse.class);
}

@Override
public PaymentConfirmResponse confirmPayment(String baseUrl, UUID eventId, UUID participantId) {
    return restClient.post()
            .uri(baseUrl + "/api/events/{eventId}/participants/{participantId}/payment-confirm", eventId, participantId)
            .retrieve()
            .body(PaymentConfirmResponse.class);
}
```

Add imports:

```java
import com.timedeal.seatreservation.domain.SeatStatus;
import com.timedeal.seatreservation.event.JoinEventRequest;
import com.timedeal.seatreservation.event.JoinEventResponse;
import com.timedeal.seatreservation.event.LiveEventSnapshot;
import com.timedeal.seatreservation.event.PaymentConfirmResponse;
import com.timedeal.seatreservation.event.SeatHoldResponse;
import com.timedeal.seatreservation.simulation.SeatView;
import org.springframework.http.MediaType;
```

- [ ] **Step 4: Update AI client flow**

In `HttpVirtualUserHttpClient.runUser(...)`, replace deterministic virtual user id generation with event join:

```java
String displayName = "AI-" + virtualUserNumber;
JoinEventResponse joined = runWithTransientRetry(() -> commandClient.joinEvent(baseUrl, simulationId, displayName));
UUID participantId = joined.participantId();

runWithTransientRetry(() -> commandClient.postQueue(baseUrl, simulationId, participantId));
for (int attempt = 0; attempt < maxSeatAttempts; attempt++) {
    SeatHoldResponse hold = runWithTransientRetry(() -> commandClient.holdRandomSeat(baseUrl, simulationId, participantId));
    if ("PAYMENT_PENDING".equals(hold.status()) || "PAYMENT_REQUESTED".equals(hold.status())) {
        runWithTransientRetry(() -> commandClient.confirmPayment(baseUrl, simulationId, participantId));
        return;
    }
    if ("FAILED".equals(hold.status())) {
        return;
    }
    sleepBriefly();
}
```

Change `runWithTransientRetry` to a generic method:

```java
private <T> T runWithTransientRetry(Supplier<T> command) {
    RuntimeException lastException = null;
    for (int attempt = 0; attempt < COMMAND_RETRY_ATTEMPTS; attempt++) {
        try {
            return command.get();
        } catch (RuntimeException exception) {
            lastException = exception;
            sleepBriefly();
        }
    }
    throw lastException;
}
```

- [ ] **Step 5: Add AI start endpoint**

In `LiveEventService.java`:

```java
public RunSimulationResponse startAiParticipants(UUID eventId, StartAiParticipantsRequest request) {
    return simulationService.runSimulation(eventId, new RunSimulationRequest(request.participantCount(), request.concurrency()));
}
```

In `LiveEventController.java`:

```java
@PostMapping("/{eventId}/ai/start")
public RunSimulationResponse startAiParticipants(
        @PathVariable UUID eventId,
        @Valid @RequestBody StartAiParticipantsRequest request
) {
    return liveEventService.startAiParticipants(eventId, request);
}
```

Add imports:

```java
import com.timedeal.seatreservation.simulation.RunSimulationResponse;
```

- [ ] **Step 6: Run backend tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*HttpVirtualUserHttpClientTest" --tests "*LiveEventServiceTest" --tests "*LiveEventControllerTest" --tests "*TrafficGeneratorServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

Run:

```powershell
git add backend/src/main/java/com/timedeal/seatreservation/event backend/src/main/java/com/timedeal/seatreservation/generator backend/src/test/java/com/timedeal/seatreservation/event backend/src/test/java/com/timedeal/seatreservation/generator
git commit -m "feat: route ai participants through live event api"
```

---

## Task 6: Build Live Event Frontend API And Hook

**Files:**

- Create `frontend/src/api/liveEventApi.ts`
- Create `frontend/src/api/liveEventApi.test.ts`
- Create `frontend/src/hooks/useLiveEventRoom.ts`
- Create `frontend/src/hooks/useLiveEventRoom.test.tsx`
- Create `frontend/src/domain/liveEventSelectors.ts`
- Create `frontend/src/domain/liveEventSelectors.test.ts`

- [ ] **Step 1: Add frontend API test**

Create `frontend/src/api/liveEventApi.test.ts`:

```typescript
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchActiveEvent, joinEvent, queueParticipant, holdSeat, confirmPayment, startAiParticipants } from './liveEventApi';

describe('liveEventApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls event endpoints with participant identity', async () => {
    await fetchActiveEvent('');
    await joinEvent('', 'event-1', '권');
    await queueParticipant('', 'event-1', 'participant-1');
    await holdSeat('', 'event-1', 'participant-1', 7);
    await confirmPayment('', 'event-1', 'participant-1');
    await startAiParticipants('', 'event-1', 150, 50);

    const calls = vi.mocked(fetch).mock.calls.map(([url, init]) => [url, init?.method ?? 'GET']);
    expect(calls).toEqual([
      ['/api/events/active', 'GET'],
      ['/api/events/event-1/participants', 'POST'],
      ['/api/events/event-1/participants/participant-1/queue', 'POST'],
      ['/api/events/event-1/participants/participant-1/seats/7/hold', 'POST'],
      ['/api/events/event-1/participants/participant-1/payment-confirm', 'POST'],
      ['/api/events/event-1/ai/start', 'POST'],
    ]);
  });
});
```

- [ ] **Step 2: Add API client**

Create `frontend/src/api/liveEventApi.ts`:

```typescript
import type { SeatView, ServerStatsView, SimulationMetrics, TimelineEntry } from './simulationApi';

export type ParticipantType = 'HUMAN' | 'AI';
export type ParticipantStatus =
  | 'CREATED'
  | 'WAITING_ROOM'
  | 'QUEUED'
  | 'ADMITTED'
  | 'SELECTING_SEAT'
  | 'SEAT_HELD'
  | 'PAYMENT_IN_PROGRESS'
  | 'PAYMENT_FAILED'
  | 'RESERVED'
  | 'FAILED'
  | 'EXPIRED';

export interface EventParticipantView {
  id: string;
  displayName: string;
  type: ParticipantType;
  status: ParticipantStatus;
  selectedSeatLabel: string | null;
  timeline: TimelineEntry[];
  seatAttemptCount: number;
  conflictCount: number;
  paymentAttemptCount: number;
  reservationId: number | null;
}

export interface LiveEventResponse {
  eventId: string;
  title: string;
  status: string;
  opensAt: string;
  seatCount: number;
}

export interface LiveEventSnapshot {
  eventId: string;
  title: string;
  status: string;
  opensAt: string;
  seats: SeatView[];
  participants: EventParticipantView[];
  metrics: SimulationMetrics;
  serverStats: ServerStatsView[];
  running: boolean;
  myParticipantId: string | null;
}

export interface JoinEventResponse {
  eventId: string;
  participantId: string;
  displayName: string;
  status: string;
  handledBy: string;
}

export interface CommandResponse {
  eventId?: string;
  simulationId?: string;
  participantId?: string;
  virtualUserId?: string;
  status: string;
  message: string;
  selectedSeatLabel?: string | null;
  handledBy: string;
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function fetchActiveEvent(apiBaseUrl: string): Promise<LiveEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/active`));
}

export async function fetchEventSnapshot(apiBaseUrl: string, eventId: string, participantId: string | null): Promise<LiveEventSnapshot> {
  const query = participantId ? `?participantId=${participantId}` : '';
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/snapshot${query}`));
}

export async function joinEvent(apiBaseUrl: string, eventId: string, displayName: string): Promise<JoinEventResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  }));
}

export async function queueParticipant(apiBaseUrl: string, eventId: string, participantId: string): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/queue`, { method: 'POST' }));
}

export async function holdSeat(apiBaseUrl: string, eventId: string, participantId: string, seatId: number): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/seats/${seatId}/hold`, { method: 'POST' }));
}

export async function confirmPayment(apiBaseUrl: string, eventId: string, participantId: string): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/participants/${participantId}/payment-confirm`, { method: 'POST' }));
}

export async function startAiParticipants(apiBaseUrl: string, eventId: string, participantCount: number, concurrency: number): Promise<CommandResponse> {
  return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/ai/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ participantCount, concurrency }),
  }));
}
```

- [ ] **Step 3: Add selectors**

Create `frontend/src/domain/liveEventSelectors.ts`:

```typescript
import type { EventParticipantView, LiveEventSnapshot } from '../api/liveEventApi';

export function getMyParticipant(snapshot: LiveEventSnapshot | null, participantId: string | null): EventParticipantView | null {
  if (!snapshot || !participantId) {
    return null;
  }
  return snapshot.participants.find((participant) => participant.id === participantId) ?? null;
}

export function canReserve(participant: EventParticipantView | null): boolean {
  return !participant || participant.status === 'WAITING_ROOM' || participant.status === 'PAYMENT_FAILED';
}

export function canConfirmPayment(participant: EventParticipantView | null): boolean {
  return participant?.status === 'PAYMENT_IN_PROGRESS' || participant?.status === 'SEAT_HELD';
}

export function formatEventStatus(status: string): string {
  if (status === 'OPEN') return '예매 진행 중';
  if (status === 'COUNTDOWN') return '오픈 대기';
  if (status === 'ENDED') return '종료';
  return '준비 중';
}
```

Create `frontend/src/domain/liveEventSelectors.test.ts`:

```typescript
import { describe, expect, it } from 'vitest';
import { canConfirmPayment, canReserve, formatEventStatus, getMyParticipant } from './liveEventSelectors';
import type { LiveEventSnapshot } from '../api/liveEventApi';

describe('liveEventSelectors', () => {
  it('finds my participant and derives actions', () => {
    const snapshot = {
      participants: [
        { id: 'me', displayName: '나', type: 'HUMAN', status: 'WAITING_ROOM', selectedSeatLabel: null, timeline: [], seatAttemptCount: 0, conflictCount: 0, paymentAttemptCount: 0, reservationId: null },
      ],
    } as LiveEventSnapshot;

    const me = getMyParticipant(snapshot, 'me');

    expect(me?.displayName).toBe('나');
    expect(canReserve(me)).toBe(true);
    expect(canConfirmPayment(me)).toBe(false);
    expect(formatEventStatus('OPEN')).toBe('예매 진행 중');
  });
});
```

- [ ] **Step 4: Add hook**

Create `frontend/src/hooks/useLiveEventRoom.ts`:

```typescript
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  confirmPayment,
  fetchActiveEvent,
  fetchEventSnapshot,
  holdSeat,
  joinEvent,
  queueParticipant,
  startAiParticipants,
  type LiveEventSnapshot,
} from '../api/liveEventApi';
import { getMyParticipant } from '../domain/liveEventSelectors';

const participantStorageKey = 'timedeal.participantId';

export function useLiveEventRoom(apiBaseUrl: string) {
  const [eventId, setEventId] = useState<string | null>(null);
  const [participantId, setParticipantId] = useState<string | null>(() => window.localStorage.getItem(participantStorageKey));
  const [snapshot, setSnapshot] = useState<LiveEventSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!eventId) return;
    const next = await fetchEventSnapshot(apiBaseUrl, eventId, participantId);
    setSnapshot(next);
  }, [apiBaseUrl, eventId, participantId]);

  useEffect(() => {
    let cancelled = false;
    async function boot() {
      try {
        const active = await fetchActiveEvent(apiBaseUrl);
        if (cancelled) return;
        setEventId(active.eventId);
        const next = await fetchEventSnapshot(apiBaseUrl, active.eventId, participantId);
        if (!cancelled) setSnapshot(next);
      } catch {
        if (!cancelled) setError('이벤트 정보를 불러오지 못했습니다.');
      }
    }
    void boot();
    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, participantId]);

  useEffect(() => {
    if (!eventId) return undefined;
    const timer = window.setInterval(() => void refresh().catch(() => setError('이벤트 상태 갱신에 실패했습니다.')), 500);
    return () => window.clearInterval(timer);
  }, [eventId, refresh]);

  const join = useCallback(async (displayName: string) => {
    if (!eventId) return;
    setLoading(true);
    try {
      const joined = await joinEvent(apiBaseUrl, eventId, displayName);
      window.localStorage.setItem(participantStorageKey, joined.participantId);
      setParticipantId(joined.participantId);
      setError(null);
      await refresh();
    } catch {
      setError('이벤트 입장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl, eventId, refresh]);

  const reserve = useCallback(async () => {
    if (!eventId || !participantId) return;
    await queueParticipant(apiBaseUrl, eventId, participantId);
    await refresh();
  }, [apiBaseUrl, eventId, participantId, refresh]);

  const selectSeat = useCallback(async (seatId: number) => {
    if (!eventId || !participantId) return;
    await holdSeat(apiBaseUrl, eventId, participantId, seatId);
    await refresh();
  }, [apiBaseUrl, eventId, participantId, refresh]);

  const pay = useCallback(async () => {
    if (!eventId || !participantId) return;
    await confirmPayment(apiBaseUrl, eventId, participantId);
    await refresh();
  }, [apiBaseUrl, eventId, participantId, refresh]);

  const startAi = useCallback(async (count: number, concurrency: number) => {
    if (!eventId) return;
    await startAiParticipants(apiBaseUrl, eventId, count, concurrency);
    await refresh();
  }, [apiBaseUrl, eventId, refresh]);

  const myParticipant = useMemo(() => getMyParticipant(snapshot, participantId), [snapshot, participantId]);

  return { eventId, participantId, snapshot, myParticipant, loading, error, join, reserve, selectSeat, pay, startAi, refresh };
}
```

- [ ] **Step 5: Run frontend tests**

Run:

```powershell
cd frontend
npm.cmd test -- --run src/api/liveEventApi.test.ts src/domain/liveEventSelectors.test.ts
```

Expected:

```text
Test Files  2 passed
```

- [ ] **Step 6: Commit**

Run:

```powershell
git add frontend/src/api/liveEventApi.ts frontend/src/api/liveEventApi.test.ts frontend/src/domain/liveEventSelectors.ts frontend/src/domain/liveEventSelectors.test.ts frontend/src/hooks/useLiveEventRoom.ts
git commit -m "feat: add live event frontend data layer"
```

---

## Task 7: Replace Dashboard With Korean Live Event Room

**Files:**

- Modify `frontend/src/App.tsx`
- Modify `frontend/src/components/SeatMap.tsx`
- Create `frontend/src/components/EventHeader.tsx`
- Create `frontend/src/components/MyTicketPanel.tsx`
- Create `frontend/src/components/EventActivityPanel.tsx`
- Modify `frontend/src/styles.css`
- Modify `frontend/src/App.test.tsx`

- [ ] **Step 1: Write App test**

Replace `frontend/src/App.test.tsx` with:

```typescript
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./hooks/useLiveEventRoom', () => ({
  useLiveEventRoom: () => ({
    snapshot: {
      eventId: 'event-1',
      title: '부산 콘서트 티켓팅',
      status: 'OPEN',
      opensAt: '2026-05-28T12:00:00Z',
      seats: [{ id: 1, label: 'A-1', status: 'AVAILABLE' }],
      participants: [],
      metrics: { queueSize: 0, admittedCount: 0, heldCount: 0, paymentInProgressCount: 0, reservedCount: 0, failedCount: 0 },
      serverStats: [{ serverId: 'api-a', requestCount: 1, conflictCount: 0, successCount: 0 }],
      running: false,
      myParticipantId: null,
    },
    myParticipant: null,
    loading: false,
    error: null,
    join: vi.fn(),
    reserve: vi.fn(),
    selectSeat: vi.fn(),
    pay: vi.fn(),
    startAi: vi.fn(),
  }),
}));

describe('App', () => {
  it('renders Korean live event room', () => {
    render(<App />);

    expect(screen.getByText('부산 콘서트 티켓팅')).toBeInTheDocument();
    expect(screen.getByText('예매 진행 중')).toBeInTheDocument();
    expect(screen.getByText('예약하기')).toBeInTheDocument();
    expect(screen.getByText('AI 참가자 시작')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Update SeatMap contract**

Modify `SeatMap.tsx` to accept click handling:

```typescript
import type { SeatView } from '../api/simulationApi';

interface SeatMapProps {
  seats: SeatView[];
  selectedSeatLabel: string | null;
  onSelectSeat?: (seatId: number) => void;
}

export function SeatMap({ seats, selectedSeatLabel, onSelectSeat }: SeatMapProps) {
  return (
    <section className="panel seat-map-panel">
      <h2>좌석 현황</h2>
      <div className="seat-grid">
        {seats.map((seat) => {
          const mine = seat.label === selectedSeatLabel;
          const disabled = seat.status !== 'AVAILABLE';
          return (
            <button
              key={seat.id}
              type="button"
              className={`seat seat-${seat.status.toLowerCase()}${mine ? ' seat-mine' : ''}`}
              disabled={disabled}
              onClick={() => onSelectSeat?.(seat.id)}
              title={`${seat.label} ${seat.status}`}
            >
              {seat.label}
            </button>
          );
        })}
      </div>
    </section>
  );
}
```

- [ ] **Step 3: Add UI components**

Create `EventHeader.tsx`:

```typescript
import { formatEventStatus } from '../domain/liveEventSelectors';
import type { LiveEventSnapshot } from '../api/liveEventApi';

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
        <span>예약 완료 {snapshot.metrics.reservedCount}석</span>
      </div>
    </header>
  );
}
```

Create `MyTicketPanel.tsx`:

```typescript
import { CreditCard, LogIn, Ticket } from 'lucide-react';
import type { EventParticipantView } from '../api/liveEventApi';
import { canConfirmPayment, canReserve } from '../domain/liveEventSelectors';

interface MyTicketPanelProps {
  participant: EventParticipantView | null;
  loading: boolean;
  onJoin: () => void;
  onReserve: () => void;
  onPay: () => void;
}

export function MyTicketPanel({ participant, loading, onJoin, onReserve, onPay }: MyTicketPanelProps) {
  return (
    <section className="panel my-ticket-panel">
      <h2>내 예매 상태</h2>
      <div className="status-line">
        <span>참가자</span>
        <strong>{participant?.displayName ?? '입장 전'}</strong>
      </div>
      <div className="status-line">
        <span>상태</span>
        <strong>{participant?.status ?? 'WAITING_ROOM'}</strong>
      </div>
      <div className="status-line">
        <span>선택 좌석</span>
        <strong>{participant?.selectedSeatLabel ?? '-'}</strong>
      </div>
      {!participant ? (
        <button className="primary-action" disabled={loading} onClick={onJoin}>
          <LogIn size={18} /> 이벤트 입장
        </button>
      ) : (
        <button className="primary-action" disabled={!canReserve(participant)} onClick={onReserve}>
          <Ticket size={18} /> 예약하기
        </button>
      )}
      <button className="secondary-action" disabled={!canConfirmPayment(participant)} onClick={onPay}>
        <CreditCard size={18} /> 결제 확인
      </button>
    </section>
  );
}
```

Create `EventActivityPanel.tsx`:

```typescript
import type { LiveEventSnapshot } from '../api/liveEventApi';

interface EventActivityPanelProps {
  snapshot: LiveEventSnapshot;
  onStartAi: () => void;
}

export function EventActivityPanel({ snapshot, onStartAi }: EventActivityPanelProps) {
  const recent = snapshot.participants.flatMap((participant) =>
    participant.timeline.slice(-2).map((entry) => ({
      id: `${participant.id}-${entry.label}-${entry.message}`,
      participant: participant.displayName,
      label: entry.label,
      message: entry.message,
    })),
  ).slice(-8);

  return (
    <section className="panel activity-panel">
      <div className="panel-title-row">
        <h2>실시간 진행</h2>
        <button className="secondary-action compact" onClick={onStartAi}>AI 참가자 시작</button>
      </div>
      <div className="infra-grid">
        {snapshot.serverStats.map((stat) => (
          <div key={stat.serverId}>
            <strong>{stat.serverId}</strong>
            <span>{stat.requestCount}건 처리</span>
          </div>
        ))}
      </div>
      <ol className="activity-list">
        {recent.map((entry) => (
          <li key={entry.id}>
            <strong>{entry.participant}</strong>
            <span>{entry.label}</span>
            <p>{entry.message}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
```

- [ ] **Step 4: Replace App**

Replace `App.tsx`:

```typescript
import { EventActivityPanel } from './components/EventActivityPanel';
import { EventHeader } from './components/EventHeader';
import { MyTicketPanel } from './components/MyTicketPanel';
import { SeatMap } from './components/SeatMap';
import { useLiveEventRoom } from './hooks/useLiveEventRoom';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export default function App() {
  const room = useLiveEventRoom(apiBaseUrl);

  if (!room.snapshot) {
    return (
      <main className="dashboard">
        <section className="panel empty-state">
          <h1>티켓팅 이벤트를 불러오는 중입니다</h1>
          {room.error ? <p>{room.error}</p> : null}
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard">
      <EventHeader snapshot={room.snapshot} />
      {room.error ? <div className="error-banner">{room.error}</div> : null}
      <div className="dashboard-grid">
        <MyTicketPanel
          participant={room.myParticipant}
          loading={room.loading}
          onJoin={() => void room.join('나')}
          onReserve={() => void room.reserve()}
          onPay={() => void room.pay()}
        />
        <SeatMap
          seats={room.snapshot.seats}
          selectedSeatLabel={room.myParticipant?.selectedSeatLabel ?? null}
          onSelectSeat={(seatId) => void room.selectSeat(seatId)}
        />
        <EventActivityPanel snapshot={room.snapshot} onStartAi={() => void room.startAi(150, 50)} />
      </div>
    </main>
  );
}
```

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
cd frontend
npm.cmd test -- --run src/App.test.tsx src/api/liveEventApi.test.ts src/domain/liveEventSelectors.test.ts
npm.cmd run build
```

Expected:

```text
Test Files  3 passed
```

and:

```text
✓ built
```

- [ ] **Step 6: Commit**

Run:

```powershell
git add frontend/src
git commit -m "feat: build Korean live event room"
```

---

## Task 8: End-To-End Local Verification

**Files:**

- Modify only if verification exposes a defect.

- [ ] **Step 1: Run all backend tests**

Run:

```powershell
cd backend
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run all frontend tests and build**

Run:

```powershell
cd ..\frontend
npm.cmd test
npm.cmd run build
```

Expected:

```text
Test Files
```

with all files passing, then Vite build succeeds.

- [ ] **Step 3: Run Docker stack**

Run:

```powershell
cd ..\infra
docker compose up -d --build
docker compose restart nginx
```

Expected:

```text
Container timedeal-api-a-1 Healthy
Container timedeal-api-b-1 Healthy
Container timedeal-nginx-1 Started
```

- [ ] **Step 4: Verify event API manually**

Run:

```powershell
$active = Invoke-RestMethod -Uri http://localhost:8080/api/events/active
$joined = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants" -ContentType application/json -Body '{"displayName":"권"}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/queue"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/seats/1/hold"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/participants/$($joined.participantId)/payment-confirm"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/events/$($active.eventId)/ai/start" -ContentType application/json -Body '{"participantCount":30,"concurrency":10}'
Start-Sleep -Seconds 10
Invoke-RestMethod -Uri "http://localhost:8080/api/events/$($active.eventId)/snapshot?participantId=$($joined.participantId)" | ConvertTo-Json -Depth 8
```

Expected:

- Snapshot has the human participant and AI participants.
- `serverStats` includes both `api-a` and `api-b` after AI traffic runs.
- Seat statuses include `PAYMENT_IN_PROGRESS` or `RESERVED`.
- Kafka worker eventually moves some payments to final status.

- [ ] **Step 5: Run frontend dev server**

Run:

```powershell
cd ..\frontend
npm.cmd run dev -- --host 127.0.0.1
```

Expected:

```text
Local: http://127.0.0.1:5173/
```

Open the URL and verify:

- Korean UI renders without mojibake.
- `이벤트 입장` creates a participant.
- `예약하기` enters the queue.
- Clicking a green seat moves the participant toward payment.
- `결제 확인` sends a Kafka-backed payment request.
- `AI 참가자 시작` adds AI participants to the same event.

- [ ] **Step 6: Commit verification fixes**

If no code changed, do not create a commit.

If verification required fixes, run:

```powershell
git add backend frontend infra docs
git commit -m "fix: stabilize live event local flow"
```

---

## Self-Review

Spec coverage:

- Shared live event: Tasks 2 and 3 add event contracts and active event facade.
- Human users: Tasks 3, 4, 6, and 7 add join, queue, seat hold, payment confirm, and UI.
- AI participants: Task 5 routes generator traffic through event APIs.
- Redis queue and snapshot: Tasks 3 and 4 extend the state gateway and stores.
- PostgreSQL seat authority: Task 4 reuses `SeatReservationService.holdSeat`.
- Kafka payment flow: Task 1 preserves payment reopen foundation; Task 4 adds human payment confirm.
- Korean frontend: Tasks 6 and 7 replace user-facing frontend text.
- Local Docker verification: Task 8 validates nginx, API servers, Redis, PostgreSQL, Kafka, worker, and frontend.

Placeholder scan:

- The plan avoids placeholder markers and unspecified edge handling.
- The only intentionally deferred product feature is countdown, explicitly kept out of this MVP slice by using `OPEN` status.

Type consistency:

- Backend event id maps to current simulation id.
- Participant id maps to current virtual user id.
- `VirtualUserView` becomes the shared participant read model and includes `ParticipantType`.
- Frontend `EventParticipantView` matches the updated backend `VirtualUserView` JSON shape.
