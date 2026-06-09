# Configurable AI Simulation Parameters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the user to configure AI count, concurrency, and batch speed parameters on the dashboard before starting the event, and implement a robust backend mechanism to apply these configurations proportionally.

**Architecture:** Use Spring controller request body mapping to pass parameters to the event start action. Store settings temporarily in a thread-safeConcurrentHashMap inside the AI starter service, avoiding database mutations. Build the AI schedule using proportional percentages (10%, 15%, 20%, 25%, 30%) and customizable delay intervals. Update frontend API clients, hook parameters, and add a neo-brutalist styled inline toolbar directly in the Dashboard header.

**Tech Stack:** Java, Spring Boot, React, TypeScript, Vitest, JUnit

---

### Task 1: Backend Request Model & Overloaded Controller Endpoint

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/event/StartEventRequest.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java`
- Test: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java`

- [ ] **Step 1: Create StartEventRequest record**
  Create the record with fields for AI configuration:
  ```java
  package com.timedeal.seatreservation.event;
  
  public record StartEventRequest(
          Integer aiUserCount,
          Integer aiConcurrency,
          String aiSpeed
  ) {}
  ```

- [ ] **Step 2: Update LiveEventController.startEvent**
  Modify `LiveEventController.java` to accept an optional `@RequestBody` payload:
  ```java
      @PostMapping("/{eventId}/start")
      public LiveEventResponse startEvent(
              @PathVariable UUID eventId,
              @RequestBody(required = false) StartEventRequest request
      ) {
          return liveEventService.startEvent(eventId, request);
      }
  ```

- [ ] **Step 3: Update LiveEventService.java with overloaded stub**
  In `LiveEventService.java`, add overloaded `startEvent` signatures so that existing tests calling `startEvent(eventId)` do not break:
  ```java
      public LiveEventResponse startEvent(UUID eventId) {
          return startEvent(eventId, null);
      }
  
      public LiveEventResponse startEvent(UUID eventId, StartEventRequest request) {
          // Temporarily delegate to startEvent(eventId) to keep compiling
          return startEvent(eventId);
      }
  ```

- [ ] **Step 4: Write API Controller Test**
  Add a test to `LiveEventControllerTest.java` that POSTs to `/start` with a JSON payload:
  ```java
      @Test
      void startsLiveEventWithConfig() throws Exception {
          LiveEventService service = mock(LiveEventService.class);
          UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
          StartEventRequest request = new StartEventRequest(300, 80, "FAST");
          
          when(service.startEvent(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.any(StartEventRequest.class)))
                  .thenReturn(new LiveEventResponse(eventId, "Test Event", "COUNTDOWN", 1, Instant.now(), Instant.now().plusSeconds(600), 120));
                  
          MockMvc mvc = MockMvcBuilders.standaloneSetup(new LiveEventController(service, new SimulationEventHub(null, null))).build();
          
          mvc.perform(post("/api/events/{eventId}/start", eventId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.status", is("COUNTDOWN")));
      }
  ```

- [ ] **Step 5: Verify build & tests**
  Run: `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew compileJava compileTestJava`
  Run tests: `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew test --tests "com.timedeal.seatreservation.event.LiveEventControllerTest"`
  Expected: All tests pass.

- [ ] **Step 6: Commit**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/event/StartEventRequest.java \
          backend/src/main/java/com/timedeal/seatreservation/event/LiveEventController.java \
          backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java \
          backend/src/test/java/com/timedeal/seatreservation/event/LiveEventControllerTest.java
  git commit -m "backend: add StartEventRequest request body and overloaded start controller endpoint"
  ```

---

### Task 2: Service Layer AI Starter Configuration Registry

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java`

- [ ] **Step 1: Add configuration cache to LiveEventAiStarter**
  In `LiveEventAiStarter.java`, declare a concurrent map and `configure(...)` method:
  ```java
      private final java.util.concurrent.ConcurrentHashMap<UUID, AiConfig> customConfigs = new java.util.concurrent.ConcurrentHashMap<>();
  
      public record AiConfig(int participantCount, int concurrency, String speed) {}
  
      public void configure(UUID eventId, Integer participantCount, Integer concurrency, String speed) {
          if (participantCount == null && concurrency == null && speed == null) return;
          int count = participantCount != null ? Math.max(0, Math.min(1000, participantCount)) : this.participantCount;
          int maxConcurrency = concurrency != null ? Math.max(1, Math.min(120, concurrency)) : this.concurrency;
          String normalizedSpeed = speed != null ? speed.toUpperCase() : "NORMAL";
          customConfigs.put(eventId, new AiConfig(count, maxConcurrency, normalizedSpeed));
      }
      
      public AiConfig getCachedConfig(UUID eventId) {
          return customConfigs.get(eventId);
      }
  ```

- [ ] **Step 2: Update LiveEventService.startEvent to configure AI**
  Modify `LiveEventService.java` to call `configure` on the AI starter when request body configurations are provided:
  ```java
      public LiveEventResponse startEvent(UUID eventId, StartEventRequest request) {
          ensureExpectedEvent(eventId);
          LiveEventMetadata metadata = eventStateStore.getOrCreate(eventId, now());
          if (metadata.statusAt(now()) != LiveEventStatus.READY) {
              throw new IllegalStateException("Event already started or ended");
          }
          
          if (request != null && aiStarter != null) {
              aiStarter.configure(eventId, request.aiUserCount(), request.aiConcurrency(), request.aiSpeed());
          }
          
          LiveEventMetadata started = eventStateStore.startCountdown(eventId, now(), countdownDuration, openWindow);
          triggerAiIfOpen(started);
          return response(started);
      }
  ```

- [ ] **Step 3: Verify compilation & build**
  Run: `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew compileJava compileTestJava`

- [ ] **Step 4: Commit**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java \
          backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java
  git commit -m "backend: add configuration cache registry in LiveEventAiStarter and trigger from LiveEventService"
  ```

---

### Task 3: Proportional Batch Schedule and Custom Delays in AI Starter

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`
- Modify: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`

- [ ] **Step 1: Implement custom schedule builder and retrieve parameters**
  In `LiveEventAiStarter.java`, update the `start(UUID)` method to extract the cached configuration and generate the proportional batch schedule. Change `start(UUID)` to:
  ```java
      public void start(UUID eventId) {
          AiConfig config = customConfigs.remove(eventId);
          int count = config != null ? config.participantCount() : this.participantCount;
          int maxConcurrency = config != null ? config.concurrency() : this.concurrency;
          String speed = config != null ? config.speed() : "NORMAL";
          
          Duration interval;
          if ("FAST".equals(speed)) {
              interval = Duration.ofMillis(100);
          } else if ("SLOW".equals(speed)) {
              interval = Duration.ofMillis(1500);
          } else {
              interval = Duration.ofMillis(500); // NORMAL
          }
  
          AiBatchSchedule schedule = buildCustomSchedule(count, maxConcurrency, interval);
          for (AiBatch batch : schedule.batches()) {
              scheduler.schedule(batch.delay(), () -> simulationService.runSimulation(
                      eventId,
                      new RunSimulationRequest(batch.participantCount(), batch.concurrency())
              ));
          }
      }
  
      private AiBatchSchedule buildCustomSchedule(int participantCount, int maxConcurrency, Duration interval) {
          int remaining = Math.max(0, participantCount);
          int normalizedConcurrency = Math.max(1, maxConcurrency);
          double[] batchPercentages = {0.10, 0.15, 0.20, 0.25, 0.30};
          long delayMillis = interval.toMillis();
          java.util.ArrayList<AiBatch> batches = new java.util.ArrayList<>();
          
          for (double pct : batchPercentages) {
              if (remaining <= 0) break;
              int count = (int) Math.round(participantCount * pct);
              count = Math.min(count, remaining);
              if (count <= 0) continue;
              
              int concurrency = Math.min(normalizedConcurrency, count);
              batches.add(new AiBatch(count, concurrency, Duration.ofMillis(delayMillis)));
              remaining -= count;
              delayMillis += interval.toMillis();
          }
          if (remaining > 0) {
              int concurrency = Math.min(normalizedConcurrency, remaining);
              batches.add(new AiBatch(remaining, concurrency, Duration.ofMillis(delayMillis)));
          }
          return new AiBatchSchedule(List.copyOf(batches));
      }
  ```

- [ ] **Step 2: Add Unit Tests in LiveEventAiStarterTest.java**
  Add unit tests in `LiveEventAiStarterTest.java` verifying proportional distribution and interval mapping:
  ```java
      @Test
      void appliesConfiguredProportionalScheduleAndSpeeds() {
          SimulationService simService = mock(SimulationService.class);
          java.util.ArrayList<AiBatch> recordedBatches = new java.util.ArrayList<>();
          LiveEventAiStarter.BatchScheduler scheduler = (delay, task) -> {
              task.run();
          };
          
          LiveEventAiStarter starter = new LiveEventAiStarter(simService, 150, 50, scheduler);
          
          // Configure FAST speed and custom parameters
          UUID eventId = UUID.randomUUID();
          starter.configure(eventId, 1000, 100, "FAST");
          
          starter.start(eventId);
          
          // Verify simulationService was called with proportional numbers
          org.mockito.Mockito.verify(simService).runSimulation(
                  org.mockito.Mockito.eq(eventId),
                  org.mockito.Mockito.argThat(req -> req.participantCount() == 100 && req.concurrency() == 100) // 10%
          );
          org.mockito.Mockito.verify(simService).runSimulation(
                  org.mockito.Mockito.eq(eventId),
                  org.mockito.Mockito.argThat(req -> req.participantCount() == 150 && req.concurrency() == 100) // 15%
          );
          org.mockito.Mockito.verify(simService).runSimulation(
                  org.mockito.Mockito.eq(eventId),
                  org.mockito.Mockito.argThat(req -> req.participantCount() == 200 && req.concurrency() == 100) // 20%
          );
          org.mockito.Mockito.verify(simService).runSimulation(
                  org.mockito.Mockito.eq(eventId),
                  org.mockito.Mockito.argThat(req -> req.participantCount() == 250 && req.concurrency() == 100) // 25%
          );
          org.mockito.Mockito.verify(simService).runSimulation(
                  org.mockito.Mockito.eq(eventId),
                  org.mockito.Mockito.argThat(req -> req.participantCount() == 300 && req.concurrency() == 100) // 30%
          );
      }
  ```

- [ ] **Step 3: Run all backend tests**
  Run: `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew test`
  Expected: All tests pass.

- [ ] **Step 4: Commit**
  ```bash
  git add backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java \
          backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java
  git commit -m "backend: implement proportional percentage batching and custom delay intervals for AI simulation"
  ```

---

### Task 4: Frontend API and Hook Updates

**Files:**
- Modify: `frontend/src/api/liveEventApi.ts`
- Modify: `frontend/src/hooks/useLiveEventRoom.ts`

- [ ] **Step 1: Declare StartEventRequest interface in liveEventApi.ts**
  At the bottom of `frontend/src/api/liveEventApi.ts`, add the interface:
  ```typescript
  export interface StartEventRequest {
    aiUserCount?: number;
    aiConcurrency?: number;
    aiSpeed?: 'SLOW' | 'NORMAL' | 'FAST';
  }
  ```

- [ ] **Step 2: Update startEvent API client method**
  Modify `startEvent` to accept `request` parameters and send them in the POST request body:
  ```typescript
  export async function startEvent(
    apiBaseUrl: string,
    eventId: string,
    request?: StartEventRequest
  ): Promise<LiveEventResponse> {
    return readJson(await fetch(`${apiBaseUrl}/api/events/${eventId}/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request || {}),
    }));
  }
  ```

- [ ] **Step 3: Update start in useLiveEventRoom.ts**
  In `useLiveEventRoom.ts`, modify `start` signature to pass the request object:
  ```typescript
    const start = useCallback(async (request?: StartEventRequest) => {
      if (!eventId) return;
      await startEvent(apiBaseUrl, eventId, request);
      await refresh();
    }, [apiBaseUrl, eventId, refresh]);
  ```

- [ ] **Step 4: Verify type-check**
  Run: `npx tsc --noEmit` inside `frontend/` directory.

- [ ] **Step 5: Commit**
  ```bash
  git add frontend/src/api/liveEventApi.ts \
          frontend/src/hooks/useLiveEventRoom.ts
  git commit -m "frontend: update startEvent API client and useLiveEventRoom hook to support StartEventRequest"
  ```

---

### Task 5: Frontend Dashboard UI Settings Layout

**Files:**
- Modify: `frontend/src/components/EventHeader.tsx`
- Modify: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Add inputs and update EventHeaderProps**
  In `EventHeader.tsx`, update the `onStart` prop type to receive configurations, declare local settings states, and implement the inline controls layout inside the `EventHeader` component:
  ```typescript
  interface EventHeaderProps {
    snapshot: LiveEventSnapshot;
    onStart: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
    onReset: () => void;
  }
  ```
  And inside `EventHeader`:
  ```typescript
    const [aiCount, setAiCount] = useState<number>(150);
    const [aiConcurrency, setAiConcurrency] = useState<number>(50);
    const [aiSpeed, setAiSpeed] = useState<'SLOW' | 'NORMAL' | 'FAST'>('NORMAL');
  ```
  Add controls next to the "Start Event" action (around lines 43-45):
  ```html
          {snapshot.status === 'READY' ? (
            <div className="ai-config-toolbar" style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
              <div className="input-group" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <label htmlFor="ai-count-input" style={{ fontSize: '12px', fontWeight: '800' }}>AI 유저</label>
                <input
                  id="ai-count-input"
                  type="number"
                  min={0}
                  max={1000}
                  value={aiCount}
                  onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
                  style={{ width: '70px', padding: '6px', border: '2px solid var(--line)', fontFamily: 'monospace', fontWeight: '800' }}
                />
              </div>
              <div className="input-group" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <label htmlFor="ai-concurrency-input" style={{ fontSize: '12px', fontWeight: '800' }}>동시성</label>
                <input
                  id="ai-concurrency-input"
                  type="number"
                  min={1}
                  max={120}
                  value={aiConcurrency}
                  onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
                  style={{ width: '60px', padding: '6px', border: '2px solid var(--line)', fontFamily: 'monospace', fontWeight: '800' }}
                />
              </div>
              <div className="input-group" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <label htmlFor="ai-speed-select" style={{ fontSize: '12px', fontWeight: '800' }}>속도</label>
                <select
                  id="ai-speed-select"
                  value={aiSpeed}
                  onChange={(e) => setAiSpeed(e.target.value as any)}
                  style={{ padding: '6px', border: '2px solid var(--line)', fontWeight: '800', backgroundColor: 'var(--paper)' }}
                >
                  <option value="SLOW">느림 (1.5초)</option>
                  <option value="NORMAL">보통 (0.5초)</option>
                  <option value="FAST">빠름 (0.1초)</option>
                </select>
              </div>
              <button 
                type="button"
                className="header-action" 
                onClick={() => onStart({ aiUserCount: aiCount, aiConcurrency: aiConcurrency, aiSpeed: aiSpeed })}
              >
                이벤트 시작하기
              </button>
            </div>
          ) : null}
  ```

- [ ] **Step 2: Update Dashboard.tsx invocation**
  In `Dashboard.tsx`, update `onStart` call inside `<EventHeader ... />` (line 77):
  ```typescript
        <EventHeader snapshot={room.snapshot} onStart={(request) => void room.start(request)} onReset={() => void room.reset()} />
  ```

- [ ] **Step 3: Verify compilation & type checks**
  Run: `npx tsc --noEmit` inside `frontend/` directory.

- [ ] **Step 4: Commit**
  ```bash
  git add frontend/src/components/EventHeader.tsx \
          frontend/src/Dashboard.tsx
  git commit -m "frontend: implement inline AI simulation configuration toolbar in EventHeader"
  ```
