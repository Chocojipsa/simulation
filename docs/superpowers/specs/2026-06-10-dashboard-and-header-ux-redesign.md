# 대시보드 및 헤더 UX 개선 설계서 (Dashboard & Header UX Redesign Spec)

이 문서는 실시간 예매 데모 웹서비스의 이벤트 시작 액션 시의 헤더 레이아웃 출렁임 현상을 해결하고, 시스템 인프라 및 지표 패널의 위치를 대시보드에서 모니터링 콘솔로 이동하여 PC Web 최적화 SaaS UX를 구축하는 설계를 기술합니다.

---

## 1. 개요 및 분석 결과

### 1.1 이벤트 시작 시 헤더 레이아웃 출렁임 현상
* **현상**: 이벤트 상태가 `READY`일 때 헤더 영역에 복잡한 AI 설정 도구(AI 유저 수, 동시성, 속도 설정 등 약 500px 이상 공간 차지)가 가로로 렌더링되며, 이벤트를 시작하여 `OPEN` 상태가 되면 이 설정 도구가 사라지면서 헤더의 가로 너비가 대폭 좁아지고, "시작 전" -> "예매 중" 레이블 및 텍스트 정렬이 크게 요동쳐 화면 정렬이 깨지는 인상을 줌.
* **해결 방안**:
  * `EventHeader.tsx`에서 AI 설정 폼(`ai-config-toolbar`)을 완전히 제거합니다. 헤더는 글로벌 메타 정보(타이틀, 내비게이션 탭, 실시간 상태 배지 및 리셋 버튼)만 컴팩트하게 노출하도록 고정합니다.
  * AI 시뮬레이션 설정 및 시작 제어는 `Dashboard.tsx` 본문 내부에 독자적인 카드 패널(SaaS 시뮬레이션 제어 패널)로 배치하여 이벤트 시작 전(`READY` 상태)일 때만 본문에서 세팅 및 시작할 수 있게 분리합니다.

### 1.2 서버 분산 및 인프라 지표 패널 이동
* **현상**: 관제 지표 성격이 강한 "서버 분산(로드 밸런싱)" 및 "시스템 및 인프라 상태(Response Time, Kafka Lag, Redis Locks)" 정보가 서비스의 메인 메트릭을 요약하는 대시보드 화면 하단에 크게 노출되어 있어, 예매 데모 위주의 대시보드 화면이 너무 산만해 보임.
* **해결 방안**:
  * 해당 지표 패널(`InsightPanel`)을 `Dashboard.tsx`에서 완전히 삭제하고, 관리자 관제 전용 화면인 모니터링 콘솔(`/monitoring`)로 옮깁니다.
  * 모니터링 콘솔에서도 메트릭을 실시간으로 가져와 `InsightPanel`에 데이터를 공급할 수 있도록 `MonitoringConsole.tsx` 내부에도 5초 주기 메트릭 폴링 로직을 추가합니다.

---

## 2. 변경 파일 및 구성 상세

### 2.1 `frontend/src/components/EventHeader.tsx`
* **Props 인터페이스 복구**: `onStart` 핸들러 프롭을 다시 도입합니다.
  ```tsx
  interface EventHeaderProps {
    snapshot: LiveEventSnapshot;
    onStart: (request: { aiUserCount: number; aiConcurrency: number; aiSpeed: 'SLOW' | 'NORMAL' | 'FAST' }) => void;
    onReset: () => void;
  }
  ```
* **드롭다운 팝오버 상태 및 폼**:
  * 내부 상태 `isSettingsOpen` 토글 상태 추가.
  * 내부 상태 `aiCount`, `aiConcurrency`, `aiSpeed` 추가.
  * 이벤트 상태가 `READY`일 때 `이벤트 시작하기` 버튼을 렌더링하고, 클릭 시 설정 카드 팝오버가 토글되도록 구현합니다.
  * 팝오버 카드는 `position: absolute; right: 0; top: calc(100% + 8px); z-index: 50;` 속성과 깔끔한 Box Shadow, 백그라운드 카드를 가진 오버레이 형태로 배치되어 헤더 요소를 침범하지 않고 띄워집니다.

### 2.2 `frontend/src/Dashboard.tsx`
* 대시보드 본문 영역에 구현했던 임시 `simulation-starter-card` 영역을 완전히 삭제합니다.
* `EventHeader`를 호출할 때 `onStart` 프롭을 다시 온전히 넘겨줍니다:
  ```tsx
  <EventHeader
    snapshot={room.snapshot}
    onStart={(request) => void room.start(request)}
    onReset={() => void room.reset()}
  />
  ```
* 대시보드에 구현했던 로컬 상태 `aiCount`, `aiConcurrency`, `aiSpeed`를 제거합니다.

### 2.3 `frontend/src/components/MonitoringConsole.tsx`
* 대시보드 하단에 있었던 `InsightPanel`을 모니터링 페이지 하단에 배치합니다.
* 서버 메트릭 5초 주기 폴링 로직 및 상태 변수(`metrics`, `setMetrics`)를 `MonitoringConsole.tsx` 내에 추가 구현합니다.
* 모니터링 화면 렌더링 최하단에 아래 컴포넌트를 배치합니다:
  ```tsx
  <div className="insight-section" style={{ marginTop: '24px' }}>
    <InsightPanel snapshot={room.snapshot} metrics={metrics} />
  </div>
  ```
* `EventHeader` 호출 시 `onStart` 프롭이 복구되었으므로 이에 맞게 null 이나 mock onStart 동작을 넘겨주거나, 모니터링 탭에서도 시작 기능을 지원할 수 있도록 `onStart={(request) => void room.start(request)}`를 연동해 줍니다. (두 탭 간의 온전한 호환성 지원)

---

## 3. 검증 방법 (Testing)
1. **Vitest 실행**: `npm run test` 명령을 활용하여 컴포넌트 마운트 및 렌더링에 관한 기존 24개 테스트가 모두 정상 통과하는지 검증합니다.
2. **동작 및 비주얼 확인**:
   * 대시보드 접속 시 이벤트 시작 전(`READY`)일 때 본문 상단에 "AI 시뮬레이션 설정" 카드가 깔끔하게 나오는지 확인합니다.
   * "시뮬레이션 및 이벤트 시작하기" 버튼 클릭 후, 해당 카드 설정 영역이 본문에서 사라지며 헤더 배너 레이아웃이 튀는 현상 없이 우아하게 `예매 중` 상태로 전환되는지 확인합니다.
   * 대시보드에 더 이상 서버 분산 및 시스템 인프라 현황이 노출되지 않는지 확인하고, `/monitoring` 탭으로 이동하면 해당 인프라 메트릭 패널이 하단에 실시간으로 실시간 메트릭을 받으며 정상 렌더링되는지 점검합니다.
