# 디자인 사양서: 관리자 대시보드 레이아웃 개편 및 SSE 데이터 전송 최적화 (대안 C)

본 문서는 관리자 대시보드 화면이 복잡해지는 문제를 해결하기 위해 화면 역할을 다중 페이지로 격리하고, SSE(Server-Sent Events)를 통해 유통되는 대용량 시뮬레이션 데이터를 경량화하는 아키텍처 사양을 정의합니다.

---

## 1. 개선 배경 및 목표
* **현상**: 현재 대시보드(`Dashboard.tsx`) 한 페이지에 이벤트 통계, 실시간 좌석표, 대기열 참가자 리스트, 상세 활동 로그(각 참가자의 타임라인)가 전부 로드되어 한 화면의 정보 밀도가 과하게 높습니다.
* **성능 병목**: 실시간 유저 활동이 활발해질수록 수백 명의 타임라인 로그를 포함한 거대한 스냅샷 데이터(약 500KB~2MB)가 매번 SSE 브로드캐스트로 전송되어 브라우저 렌더링 병목 및 네트워크 대역폭 낭비가 발생합니다.
* **개선 목표**:
  1. 관리 화면을 일반 대시보드(이벤트 제어/좌석표)와 관제 콘솔(로그 모니터링)로 완전히 물리적 격리(대안 C).
  2. SSE 데이터에서 상세 참가자 활동 타임라인을 제외하고 지표 및 좌석 중심으로 경량화.
  3. 상세 로그는 온디맨드 API 호출 또는 전용 디테일 스트림으로 유도하여 성능 최적화.

---

## 2. 아키텍처 개요

### 2.1 화면 분할 및 URL 라우트
* **대시보드 페이지 (`/dashboard` 또는 기본 `/`)**:
  * **주요 구성**: 이벤트 헤더(제어 버튼), 핵심 지표 스트립, 실시간 좌석 배치도(Seat Map), 핵심 참가자 통계.
  * **데이터 소스**: 경량화된 실시간 SSE 스냅샷 연결.
* **관제 콘솔 페이지 (`/monitoring`)**:
  * **주요 구성**: 전체 참가자 진행 상황 흐름도(진행 중, 대기열, 완료), 전체 실시간 라이브 로그 스트림, 선택된 특정 참가자의 타임라인 추적.
  * **데이터 소스**: 온디맨드 HTTP API 호출 및 개별 대상 로그 스트림.

```mermaid
graph TD
    Client[React Client] -->|Route: /dashboard| Dash[Dashboard Page]
    Client -->|Route: /monitoring| Mon[Monitoring Console]
    
    Dash -->|Subscribes to| LightSSE[Lightweight Event Stream SSE]
    Mon -->|Fetches List| PartAPI[GET /api/events/{id}/participants]
    Mon -->|Selected User| DetAPI[GET /api/events/{id}/participants/{pId}/timeline]
```

### 2.2 SSE 데이터 경량화 스펙
* **SimulationSnapshot JSON 변경**:
  * 기존 `List<VirtualUserView> users`에 포함된 각 사용자의 `timeline` 리스트를 SSE 스냅샷 스트림 payload에서 제외하거나, 기본 SSE는 `users` 목록을 생략하고 `metrics` 내의 숫자 데이터만 포함하도록 최적화합니다.

---

## 3. 세부 설계 및 API 명세

### 3.1 백엔드 데이터 전송 최적화
* **경량 스냅샷 전송**:
  * `SimulationSnapshot` 객체에서 사용자 상세 리스트 및 타임라인을 뺀 경량 DTO `LightweightSimulationSnapshot` 또는 `users` 필드가 빈 리스트인 상태로 SSE 브로드캐스트 전송.
* **상세 타임라인 조회 API**:
  * **Endpoint**: `GET /api/events/{eventId}/participants/{participantId}/timeline`
  * **Response**:
    ```json
    [
      { "label": "INTENT", "message": "좌석을 탐색하기 시작합니다." },
      { "label": "THINKING", "message": "A-12 좌석이 비어있음을 인지했습니다." },
      { "label": "ACTION", "message": "A-12 좌석 선점을 시도합니다." }
    ]
    ```

### 3.2 프론트엔드 라우팅 및 연계
* **컴포넌트 분리**:
  * `Dashboard.tsx`에서 `QueuePanel`과 `EventActivityPanel`을 제거하고, 이를 `/monitoring` 전용 컴포넌트인 `MonitoringConsole.tsx`로 결합하여 독립적으로 관리.
  * 사용자가 모니터링 컴포넌트로 진입할 때만 해당 참가자들의 로그 API와 커넥션을 맺도록 설계.

---

## 4. 검증 및 테스트 계획
1. **네트워크 페이로드 크기 검증**:
   * 개발자 도구를 통해 기존 SSE 스냅샷 크기(수백 KB) 대비 신규 경량 SSE 크기(수 KB 단위)로 감소했는지 검증.
2. **화면 분할(멀티 윈도우) 동작 검증**:
   * 두 개의 브라우저 창을 띄워 각각 `/dashboard`와 `/monitoring`을 연 후, 한쪽 창에서 시뮬레이션을 시작했을 때 다른 쪽 창의 활동 로그가 실시간으로 잘 누적되는지 확인.
3. **온디맨드 호출 안정성**:
   * 로그 페이지의 참가자 클릭 시, 해당 유저의 타임라인 API만 정상적으로 지연 없이 로드되는지 확인.
