# Configurable AI Simulation Redis Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable multi-instance sync for transient AI configuration using Redis cache with a local map fallback.

**Architecture:** Inject RedisTemplate and ObjectMapper into LiveEventAiStarter. Store configuration as JSON in Redis with 10-minute TTL. Fall back to local ConcurrentHashMap memory if RedisTemplate is not available or throws exceptions.

**Tech Stack:** Spring Boot, Spring Data Redis, Jackson ObjectMapper, JUnit 5.

---

### Task 1: Update LiveEventAiStarter class signature and implementation

**Files:**
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java`

- [ ] **Step 1: Implement imports, fields, and constructors**
  Modify imports and add fields:
  ```java
  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.springframework.data.redis.core.RedisTemplate;
  import org.springframework.beans.factory.ObjectProvider;
  ```
  And define the constructors:
  ```java
      private final ConcurrentHashMap<UUID, AiConfig> localConfigs = new ConcurrentHashMap<>();
      private final RedisTemplate<String, String> redisTemplate;
      private final ObjectMapper objectMapper;

      @Autowired
      public LiveEventAiStarter(
              SimulationService simulationService,
              @Value("${live-event.ai-user-count:150}") int participantCount,
              @Value("${live-event.ai.concurrency:50}") int concurrency,
              ObjectProvider<RedisTemplate<String, String>> redisTemplateProvider,
              ObjectMapper objectMapper
      ) {
          this(
                  simulationService,
                  participantCount,
                  concurrency,
                  new ExecutorBatchScheduler(Executors.newSingleThreadScheduledExecutor()),
                  redisTemplateProvider.getIfAvailable(),
                  objectMapper
          );
      }

      LiveEventAiStarter(
              SimulationService simulationService,
              int participantCount,
              int concurrency,
              BatchScheduler scheduler
      ) {
          this(simulationService, participantCount, concurrency, scheduler, null, new ObjectMapper());
      }

      LiveEventAiStarter(
              SimulationService simulationService,
              int participantCount,
              int concurrency,
              BatchScheduler scheduler,
              RedisTemplate<String, String> redisTemplate,
              ObjectMapper objectMapper
      ) {
          this.simulationService = simulationService;
          this.participantCount = participantCount;
          this.concurrency = concurrency;
          this.scheduler = scheduler;
          this.redisTemplate = redisTemplate;
          this.objectMapper = objectMapper;
      }
  ```

- [ ] **Step 2: Update `configure`, `getCachedConfig`, and `start`**
  Modify the methods to utilize Redis with local fallback:
  ```java
      public void configure(UUID eventId, Integer participantCount, Integer concurrency, String speed) {
          if (participantCount == null && concurrency == null && speed == null) return;
          int count = participantCount != null ? Math.max(0, Math.min(1000, participantCount)) : this.participantCount;
          int maxConcurrency = concurrency != null ? Math.max(1, Math.min(120, concurrency)) : this.concurrency;
          String normalizedSpeed = speed != null ? speed.toUpperCase() : "NORMAL";
          AiConfig config = new AiConfig(count, maxConcurrency, normalizedSpeed);

          if (redisTemplate != null) {
              try {
                  String json = objectMapper.writeValueAsString(config);
                  redisTemplate.opsForValue().set("live-event:" + eventId + ":ai-config", json, Duration.ofMinutes(10));
              } catch (Exception e) {
                  localConfigs.put(eventId, config);
              }
          } else {
              localConfigs.put(eventId, config);
          }
      }

      public AiConfig getCachedConfig(UUID eventId) {
          if (redisTemplate != null) {
              try {
                  String json = redisTemplate.opsForValue().get("live-event:" + eventId + ":ai-config");
                  if (json != null) {
                      return objectMapper.readValue(json, AiConfig.class);
                  }
              } catch (Exception ignored) {}
          }
          return localConfigs.get(eventId);
      }
  ```
  And `start`:
  ```java
      public void start(UUID eventId) {
          AiConfig config = null;
          if (redisTemplate != null) {
              try {
                  String json = redisTemplate.opsForValue().get("live-event:" + eventId + ":ai-config");
                  if (json != null) {
                      config = objectMapper.readValue(json, AiConfig.class);
                      redisTemplate.delete("live-event:" + eventId + ":ai-config");
                  }
              } catch (Exception ignored) {}
          }
          if (config == null) {
              config = localConfigs.remove(eventId);
          }

          int count = config != null ? config.participantCount() : this.participantCount;
          int maxConcurrency = config != null ? config.concurrency() : this.concurrency;
          String speed = config != null ? config.speed() : "NORMAL";

          Duration interval;
          if ("FAST".equals(speed)) {
              interval = Duration.ofMillis(100);
          } else if ("SLOW".equals(speed)) {
              interval = Duration.ofMillis(1500);
          } else {
              interval = Duration.ofMillis(500);
          }

          AiBatchSchedule schedule = buildCustomSchedule(count, maxConcurrency, interval);
          for (AiBatch batch : schedule.batches()) {
              scheduler.schedule(batch.delay(), () -> simulationService.runSimulation(
                      eventId,
                      new RunSimulationRequest(batch.participantCount(), batch.concurrency())
              ));
          }
      }
  ```

---

### Task 2: Implement Unit Tests in LiveEventAiStarterTest

**Files:**
- Modify: `backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`

- [ ] **Step 1: Write unit tests verifying Redis template serialization and fallback**
  Add mock/stub test cases to verify that `LiveEventAiStarter` interacts with `RedisTemplate` when it is provided, and uses local cache when it is absent or when Redis fails:
  ```java
      @Test
      void usesRedisTemplateWhenAvailable() throws Exception {
          SimulationService simService = mock(SimulationService.class);
          LiveEventAiStarter.BatchScheduler scheduler = (delay, task) -> {};
          
          org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate = mock(org.springframework.data.redis.core.RedisTemplate.class);
          org.springframework.data.redis.core.ValueOperations<String, String> valOps = mock(org.springframework.data.redis.core.ValueOperations.class);
          org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valOps);
          
          com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
          LiveEventAiStarter starter = new LiveEventAiStarter(simService, 150, 50, scheduler, redisTemplate, mapper);
          
          UUID eventId = UUID.randomUUID();
          starter.configure(eventId, 200, 30, "SLOW");
          
          // Verify that it serializes and saves to redis
          org.mockito.Mockito.verify(valOps).set(
                  org.mockito.Mockito.eq("live-event:" + eventId + ":ai-config"),
                  org.mockito.Mockito.contains("\"participantCount\":200"),
                  org.mockito.Mockito.any(java.time.Duration.class)
          );
          
          // Verify getCachedConfig reads from redis
          org.mockito.Mockito.when(valOps.get("live-event:" + eventId + ":ai-config"))
                  .thenReturn("{\"participantCount\":200,\"concurrency\":30,\"speed\":\"SLOW\"}");
                  
          LiveEventAiStarter.AiConfig cached = starter.getCachedConfig(eventId);
          assertThat(cached).isNotNull();
          assertThat(cached.participantCount()).isEqualTo(200);
          assertThat(cached.concurrency()).isEqualTo(30);
          assertThat(cached.speed()).isEqualTo("SLOW");
      }

      @Test
      void fallsBackToLocalConfigWhenRedisThrowsException() {
          SimulationService simService = mock(SimulationService.class);
          LiveEventAiStarter.BatchScheduler scheduler = (delay, task) -> {};
          
          org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate = mock(org.springframework.data.redis.core.RedisTemplate.class);
          org.springframework.data.redis.core.ValueOperations<String, String> valOps = mock(org.springframework.data.redis.core.ValueOperations.class);
          org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valOps);
          org.mockito.Mockito.doThrow(new RuntimeException("Redis error")).when(valOps).set(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any(java.time.Duration.class));
          
          com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
          LiveEventAiStarter starter = new LiveEventAiStarter(simService, 150, 50, scheduler, redisTemplate, mapper);
          
          UUID eventId = UUID.randomUUID();
          starter.configure(eventId, 100, 10, "NORMAL");
          
          // Should fall back to local config
          LiveEventAiStarter.AiConfig cached = starter.getCachedConfig(eventId);
          assertThat(cached).isNotNull();
          assertThat(cached.participantCount()).isEqualTo(100);
          assertThat(cached.concurrency()).isEqualTo(10);
      }
  ```

---

### Task 3: Build Verification & Test Running

- [ ] **Step 1: Run compilation**
  Run:
  `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew compileJava compileTestJava`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all backend tests**
  Run:
  `JAVA_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/jdk17 GRADLE_USER_HOME=/mnt/c/users/kwon/desktop/workspace/timedeal/backend/.gradle_home ./gradlew test --tests "com.timedeal.seatreservation.event.LiveEventAiStarterTest"`
  Expected: BUILD SUCCESSFUL

---

### Task 4: Commit Locally

- [ ] **Step 1: Commit local changes**
  Run:
  `git add backend/src/main/java/com/timedeal/seatreservation/event/LiveEventAiStarter.java backend/src/test/java/com/timedeal/seatreservation/event/LiveEventAiStarterTest.java`
  `git commit -m "backend: serialize custom AI config to Redis with local fallback for distributed safety"`
