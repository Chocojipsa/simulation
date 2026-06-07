# Ticketing System Refactoring Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 "실시간 좌석 시뮬레이션" 구조를 실제 티켓팅 서비스와 유사한 구조로 리팩터링

**Architecture:** Dashboard(운영 대시보드) + Ticketing Window(사용자 예매창) 분리 구조. Redis(Queue, Seat Lock, TTL, Snapshot), Kafka(Payment Events), PostgreSQL(최종 결과), SSE(실시간 통신)

**Tech Stack:** Spring Boot 3.3 / Java 17 / React 18 / TypeScript / Redis / Kafka / PostgreSQL / Nginx / Docker Compose

---

## 현재 상태 총괄

### ✅ 완료된 항목 (건너뛰기)

| # | 항목 | 현재 상태 | 관련 파일 |
|---|------|----------|----------|
| 1 | **라우팅 구조** (`/`, `/ticketing/:eventId`) | ✅ 완료 | `frontend/src/App.tsx` |
| 2 | **Dashboard 페이지** (운영 대시보드) | ✅ 완료 | `frontend/src/Dashboard.tsx` |
| 3 | **Ticketing Window** (5단계 위저드) | ✅ 완료 | `frontend/src/components/TicketingWindow.tsx` |
| 4 | **공연 정보 표시** (공연명, 시간, 총 좌석 수) | ✅ 완료 | `EventHeader.tsx`, Dashboard metric strip |
| 5 | **운영 현황** (대기열, 예약 진행, 결제 완료) | ✅ 완료 | Dashboard metric strip, `QueuePanel.tsx` |
| 6 | **좌석 현황 — 모니터링 전용** (readOnly 모드) | ✅ 완료 | `SeatMap.tsx` (readOnly prop) |
| 7 | **최근 이벤트 로그** | ✅ 완료 | `EventActivityPanel.tsx` (SSE 기반) |
| 8 | **예약하기 버튼 → window.open()** | ✅ 완료 | `Dashboard.tsx` → `openTicketingWindow()` |
| 9 | **중복 창 방지** (기존 창 focus) | ✅ 완료 | `Dashboard.tsx` windowRef 관리 |
| 10 | **Step 1: 대기열 진입** (이름 입력 → participant 생성) | ✅ 완료 | `TicketingWindow.tsx` Step 1 |
| 11 | **Step 2: 대기열 대기** (순번, 예상 대기 표시) | ✅ 완료 | `TicketingWindow.tsx` Step 2 (polling 1.5s) |
| 12 | **Step 3: 좌석 선택** (HTTP 조회, 수동/자동 새로고침) | ✅ 완료 | `TicketingWindow.tsx` Step 3 (4s auto) |
| 13 | **좌석 충돌 처리** ("이미 선택된 좌석입니다" 안내) | ✅ 완료 | `holdSeat` 에러 처리 |
| 14 | **Step 4: 결제** (카운트다운 3분, 결제/이전 버튼) | ✅ 완료 | `TicketingWindow.tsx` Step 4 |
| 15 | **이전 단계 → Release API → 좌석 반환 → 좌석 선택** | ✅ 완료 | `releaseSeat` API 호출 후 Step 3 복귀 |
| 16 | **Step 5: 예매 완료** (공연명, 좌석, 예약번호, 시간) | ✅ 완료 | `TicketingWindow.tsx` Step 5 (receipt) |
| 17 | **Session Recovery** (localStorage participantId → Snapshot API → 상태 복구) | ✅ 완료 | `TicketingWindow.tsx` mount 시 복구 |
| 18 | **Seat Lock** (Redis TTL 기반) | ✅ 완료 | `RedisSimulationStateStore`, `SeatReservationService` |
| 19 | **Release API** (멱등성 보장) | ✅ 완료 | `POST .../seats/release` |
| 20 | **beforeunload → sendBeacon** (Best Effort + TTL 회수) | ✅ 완료 | `TicketingWindow.tsx` beforeunload 핸들러 |
| 21 | **Redis Queue** (Sorted Set FIFO 대기열) | ✅ 완료 | `WaitingQueueService`, `RedisAdmissionQueue` |
| 22 | **Redis Snapshot** (JSON + 분산 Lock) | ✅ 완료 | `RedisSimulationStateStore` |
| 23 | **Kafka Payment Flow** (request → worker → result) | ✅ 완료 | `payment.events` → `PaymentSimulationWorker` → `payment-results.events` |
| 24 | **Database** (PostgreSQL + Flyway 마이그레이션) | ✅ 완료 | `V1__init.sql` ~ `V3__simulation_scoped_inventory.sql` |
| 25 | **Dashboard SSE** (Redis Pub/Sub → SSE 브로드캐스트) | ✅ 완료 | `SnapshotPublisher/Subscriber`, `SimulationEventHub` |
| 26 | **Ticketing HTTP** (좌석 조회/선택/결제) | ✅ 완료 | `liveEventApi.ts` |
| 27 | **Exception Handling** (Broken Pipe, Client Abort, SSE Disconnect) | ✅ 완료 | `GlobalExceptionHandler.java` |
| 28 | **멀티 서버** (nginx + api-a/api-b) | ✅ 완료 | `docker-compose.yml`, `nginx.conf` |
| 29 | **AI 참가자 자동 시작** | ✅ 완료 | `LiveEventAiStarter`, `AiBatchSchedule` |

---

### ⚠️ 부분 완료 / 개선 필요 항목

| # | 항목 | 현재 상태 | 문제점 |
|---|------|----------|--------|
| P1 | **Dashboard 시스템 상태 메트릭** | ⚠️ 가짜 데이터 | `InsightPanel.tsx`가 Kafka Lag, Redis Lock 수, TPS 등을 랜덤 수학으로 계산. 백엔드에 실제 메트릭 API 없음 |
| P2 | **대기열 SSE** | ⚠️ HTTP Polling | 계획: "Queue는 SSE 사용". 현재: TicketingWindow에서 1.5초 HTTP polling. Dashboard SSE에는 `myQueuePosition` 포함 |
| P3 | **Dashboard "현재 접속자"** | ⚠️ 미확인 | 계획에 "현재 접속자" 표시 요구. SSE 연결 수 추적이 필요할 수 있음 |
| P4 | **Dashboard "입장 가능 인원"** | ⚠️ 미확인 | 계획에 명시된 메트릭. 현재 대시보드에 표시 여부 확인 필요 |

---

## 구현 태스크 (갭 해소)

### Task 1: Dashboard 실제 시스템 메트릭 API (Backend + Frontend)

> 현재 `InsightPanel.tsx`의 Kafka Lag, Redis Lock 수, TPS, API 응답 시간이 랜덤 값입니다. 실제 백엔드 메트릭을 제공하는 API가 필요합니다.

**Files:**
- Create: `backend/src/main/java/com/timedeal/seatreservation/metrics/SystemMetricsController.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/metrics/SystemMetricsService.java`
- Create: `backend/src/main/java/com/timedeal/seatreservation/metrics/SystemMetrics.java`
- Modify: `frontend/src/components/InsightPanel.tsx`
- Modify: `frontend/src/api/liveEventApi.ts`

**구현 범위:**

```
GET /api/system/metrics
```

응답:
```json
{
  "kafkaLag": 12,
  "redisLockCount": 3,
  "tps": 45.2,
  "avgResponseTimeMs": 23,
  "serverStats": [
    {"serverId": "api-a", "requestCount": 150, "conflictCount": 5, "successCount": 145},
    {"serverId": "api-b", "requestCount": 148, "conflictCount": 3, "successCount": 145}
  ]
}
```

**세부 단계:**

- [ ] **Step 1:** `SystemMetrics.java` record 생성 (kafkaLag, redisLockCount, tps, avgResponseTimeMs, serverStats)
- [ ] **Step 2:** `SystemMetricsService.java` 구현
  - Kafka AdminClient로 consumer group lag 조회
  - Redis `SCAN`으로 lock key 수 카운트 (패턴: `simulation:*:lock`)
  - 서버 내부 AtomicLong 카운터로 TPS 계산 (1초 윈도우)
  - API 응답 시간: HandlerInterceptor or Filter로 수집
- [ ] **Step 3:** `SystemMetricsController.java` — `GET /api/system/metrics`
- [ ] **Step 4:** `liveEventApi.ts`에 `fetchSystemMetrics()` 추가
- [ ] **Step 5:** `InsightPanel.tsx` 수정 — 가짜 데이터 → 실제 API 호출 (5초 polling)
- [ ] **Step 6:** 테스트 및 커밋

---

### Task 2: 대기열 SSE 전환 (Ticketing Window)

> 현재 TicketingWindow의 대기열 단계(Step 2)에서 1.5초마다 HTTP polling으로 순번을 확인합니다. 계획에 따라 SSE로 전환합니다.

**Files:**
- Modify: `frontend/src/components/TicketingWindow.tsx` (Step 2 polling → SSE)
- Modify: `backend/src/main/java/com/timedeal/seatreservation/event/LiveEventService.java` (queue 이벤트 발행)
- Possibly modify: `backend/src/main/java/com/timedeal/seatreservation/events/UserActivityPublisher.java`

**접근 방식:**

현재 백엔드에 이미 두 가지 SSE 스트림이 존재합니다:
1. `/api/events/{eventId}/stream` — 전체 snapshot (myQueuePosition 포함)
2. `/api/events/{eventId}/participants/{participantId}/stream` — 개별 활동

**추천: Option A** — 기존 participant SSE 스트림을 활용하여 queue position 업데이트를 받음

**세부 단계:**

- [ ] **Step 1:** 백엔드 — 대기열 admission 시 participant activity stream에 queue 이벤트 발행 확인
  - `UserActivityPublisher`가 queue position 변경 시 activity event 발행하도록 수정
  - event type: `queue_position_update` (position, estimatedWait)
  - event type: `queue_admitted` (admission 확인)
- [ ] **Step 2:** 프론트엔드 — TicketingWindow Step 2에서 polling 제거
  - `queueParticipant` POST는 최초 queue 진입 시에만 1회 호출
  - 이후 participant SSE stream에 EventSource 연결
  - `queue_position_update` event → 순번/예상 대기 시간 업데이트
  - `queue_admitted` event → Step 3 (좌석 선택)으로 전환
- [ ] **Step 3:** fallback — SSE 연결 실패 시 기존 polling으로 자동 전환
- [ ] **Step 4:** 테스트 및 커밋

---

### Task 3: Dashboard "현재 접속자" 및 "입장 가능 인원" 메트릭

> 계획에 명시된 운영 현황 메트릭 중 "현재 접속자"와 "입장 가능 인원"이 현재 대시보드에 표시되는지 확인하고, 없으면 추가합니다.

**Files:**
- Modify: `frontend/src/Dashboard.tsx` (metric strip 확인/수정)
- Possibly modify: `backend/.../LiveEventSnapshot.java` (metrics 필드 추가)
- Possibly modify: `backend/.../SimulationService.java` (접속자 수 계산)

**세부 단계:**

- [ ] **Step 1:** 현재 Dashboard metric strip에 표시되는 항목 확인
  - 현재: SEATS, QUEUE, HELD, KAFKA LAG, REDIS LOCKS, NODES
  - 계획: 현재 접속자, 대기열 인원, 예약 진행 중, 결제 완료, 입장 가능 인원
- [ ] **Step 2:** 누락 메트릭 식별 및 추가
  - "현재 접속자": `SimulationEventHub`의 SSE 연결 수를 snapshot에 포함
  - "입장 가능 인원": `maxActiveAdmissions - currentActiveCount` 계산값 추가
- [ ] **Step 3:** Dashboard metric strip UI 수정
- [ ] **Step 4:** 테스트 및 커밋

---

### Task 4: Dashboard 좌석 맵 hover 제거 확인

> 계획: "hover 효과도 제거한다." 현재 SeatMap readOnly 모드에서 hover CSS가 적용되는지 확인하고, 적용되면 제거합니다.

**Files:**
- Modify: `frontend/src/styles.css` or `frontend/src/components/SeatMap.tsx`

**세부 단계:**

- [ ] **Step 1:** `SeatMap.tsx` readOnly 모드에서 hover 클래스/이벤트 확인
- [ ] **Step 2:** CSS에서 readOnly일 때 hover 효과 제거 (cursor: default, no background change)
- [ ] **Step 3:** 테스트 및 커밋

---

### Task 5: Ticketing Window 안내 문구 확인

> 계획에 명시된 2가지 안내 문구가 좌석 선택 화면에 표시되는지 확인하고, 없으면 추가합니다.

**안내 문구:**
```
1. "좌석 현황은 조회 시점 기준입니다. 좌석 선택 시 서버에서 최종 예약 가능 여부를 확인합니다."
2. "좌석 선택 후 3분 동안 결제가 가능합니다."
```

**Files:**
- Modify: `frontend/src/components/TicketingWindow.tsx` (Step 3 영역)

**세부 단계:**

- [ ] **Step 1:** TicketingWindow Step 3에서 안내 문구 존재 여부 확인
- [ ] **Step 2:** 누락된 문구 추가 (info box 스타일)
- [ ] **Step 3:** 테스트 및 커밋

---

## 우선순위 정리

| 우선순위 | Task | 영향도 | 난이도 | 비고 |
|---------|------|--------|--------|------|
| 🔴 높음 | **Task 1: 실제 시스템 메트릭** | 포트폴리오 핵심 | 중 | 가짜 데이터 → 실제 데이터. 분산 시스템 역량 증명에 필수 |
| 🟡 중간 | **Task 2: 대기열 SSE** | UX + 아키텍처 | 중 | 계획 명세 충족. SSE 활용 범위 확대 |
| 🟡 중간 | **Task 3: 운영 메트릭 보완** | 대시보드 완성도 | 하 | 누락 메트릭 추가 |
| 🟢 낮음 | **Task 4: hover 제거** | UX 디테일 | 하 | CSS만 수정 |
| 🟢 낮음 | **Task 5: 안내 문구** | UX 디테일 | 하 | 텍스트 추가 |

---

## 결론

> **전체 계획의 약 85~90%가 이미 구현 완료**되어 있습니다.
> 핵심 아키텍처(Redis Lock, Kafka Payment, SSE Dashboard, Session Recovery, 멀티 서버)는 모두 동작합니다.

남은 작업은 크게 두 가지 카테고리입니다:

1. **포트폴리오 품질 향상** (Task 1, 3): 가짜 메트릭을 실제 데이터로 교체하여 분산 시스템 역량을 실증
2. **계획 명세 충족** (Task 2, 4, 5): 계획 문서에 명시된 세부 요구사항 반영

Task 4, 5는 코드 확인 후 이미 구현되어 있을 수 있으므로, 실제 구현 전 확인이 필요합니다.
