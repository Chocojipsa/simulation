# 2026-06-06 Ticketing Popup Flow Design Spec

## 1. Overview & Goals
The objective of this refactoring is to separate the **Real-time Operations Monitoring Dashboard** from the **User Ticketing Flow** to match real-world high-volume ticketing architectures (e.g., Interpark, Yes24). 
This enhances the portfolio's realism by demonstrating:
* A read-only monitoring dashboard representing operator view.
* An isolated, step-by-step ticketing flow opened via an independent browser popup (`window.open`).
* Session recovery on page refresh utilizing only the `participantId` from `localStorage` as a token, with the server acting as the single source of truth.
* Best-effort immediate resource release (Release API) during cancellation or window close, backed by Redis TTL for absolute resource reclamation.
* Dynamic, operational system telemetry (Kafka Queue/Lag, Redis Lock counts).
* Standard logging in the traffic-generator to make container execution transparent.

---

## 2. System Architecture & Routing

### 2.1 Routing Structure (React Router)
We will introduce `react-router-dom` into the React frontend.
* `/` ➡️ `Dashboard.tsx` (Operational dashboard, monitoring only)
* `/ticketing/:eventId` ➡️ `TicketingWindow.tsx` (The ticketing popup application)

### 2.2 Popup Window Lifecycle & Focus Management
* Clicking "예약하기" on the dashboard triggers a popup using `window.open`.
* The window is named `TimedealTicketingWindow` to reuse the same window instance if the button is clicked multiple times, preventing duplicates.
* Height/Width parameters: `width=900,height=700`.
* Focus is explicitly pulled to the window on reuse.

---

## 3. Backend State & API Changes

### 3.1 Idempotent Seat Release API
A new API endpoint is introduced to release held seats when the user decides to cancel or go back from the payment screen:
* **Endpoint**: `POST /api/events/{eventId}/participants/{participantId}/seats/release`
* **Idempotency**: If the seat is already released or the user is not holding any seat, the endpoint returns a `200 OK` (success response) to prevent client-side errors.
* **Flow**:
  1. Transition DB seat reservation status to `EXPIRED` and mark the seat as `AVAILABLE`.
  2. Transition the participant's status in the Redis/in-memory state store back to `SELECTING_SEAT`, clearing `selectedSeatLabel`, `reservationId`, and `seatHoldExpiresAt`.
  3. Publish the updated snapshot to the Redis Pub/Sub channel (`simulation:snapshot`) to sync the main dashboard immediately.

### 3.2 Best-Effort & Redis TTL Guarantee
* **Best-Effort Release**: When the user clicks "이전 단계" or closes the window (`beforeunload`), the frontend sends a request to the Release API using `navigator.sendBeacon`.
* **Reliability Guarantee**: If the browser crashes, the network fails, or the window is closed before the request goes out, the background TTL process (`expireTimedOutParticipants`) automatically expires the seat hold (after 3 minutes) and reclaims the seat.

---

## 4. Frontend Ticketing Window (`TicketingWindow.tsx`) Flow

### 4.1 Step-by-Step Flow
1. **대기열 진입 (Queue Entry)**: Name input screen to register a new participant if no session exists.
2. **입장 대기 (Queue Progress)**: Queue block screen showing queue position and estimated wait time.
3. **좌석 선택 (Seat Selection)**:
   * Displays warning messages: 
     * *"좌석 현황은 조회 시점 기준입니다. 좌석 선택 시 서버에서 최종 예약 가능 여부를 다시 확인합니다."*
     * *"좌석 선택 후 3분 동안 결제가 가능합니다."*
   * Displays the seat map (no real-time SSE updates by default).
   * Manual **"새로고침"** (Refresh) button to update the seat map.
   * **"자동 새로고침 (3~5초)"** toggle switch.
   * In case of seat conflict, shows *"이미 선택된 좌석입니다"*.
4. **결제 (Payment)**:
   * Displays selected seat label and count-down timer (remaining seconds until expiration).
   * Displays concert information.
   * **"결제하기"** button (sends payment confirm API).
   * **"이전 단계로"** / **"취소"** button (calls Release API, resets view to Seat Selection).
5. **예매 완료 (Success)**:
   * Immersive ticketing receipt layout: Shows Concert Title, Seat Number, Reservation ID, Booking Time, and Completion Timestamp.

---

## 5. Dashboard Telemetry & Monitoring Enhancements

### 5.1 Dashboard UI
* Seat Map is rendered in a read-only state: no click handlers, pointer events disabled, no hover states, no custom user selection states.
* Real-time updates via SSE are kept active on the dashboard.

### 5.2 System Operational Metrics
We will add system telemetry indicators to the metric strip:
* **대기 인원 (Queue Size)**: `metrics.queueSize`.
* **예약 진행 중 (Checkout In Progress)**: `metrics.heldCount + metrics.paymentInProgressCount`.
* **결제 완료 수 (Completed Payments)**: `metrics.reservedCount`.
* **Kafka Queue/Lag**: Derived metric based on `queueSize` throughput and random jitter (indicating real-time queue lag).
* **Redis Lock 수**: Dynamic indicator representing active locks matching simulation mutations (locks held during ticks/requests).

---

## 6. Traffic-Generator standard logging
Standard SLF4J loggers are added to `TrafficGeneratorService` and `HttpVirtualUserHttpClient`.
* Prints simulation starts, virtual user tasks, and activity logs to standard output.
* Enables easy logging visibility using `docker logs`.
