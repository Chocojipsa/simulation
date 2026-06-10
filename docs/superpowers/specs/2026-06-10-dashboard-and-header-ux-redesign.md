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
* AI 설정 관련 상태 및 폼 마크업을 제거하고 props 인터페이스를 컴팩트하게 변경합니다.
* **props 변경**:
  ```tsx
  interface EventHeaderProps {
    snapshot: LiveEventSnapshot;
    onReset: () => void;
  }
  ```
* **마크업 변경**: `READY` 상태일 때 나타나던 `ai-config-toolbar` 전체를 삭제하고, `event-actions` 영역에는 이벤트 완료 상태(`ENDED`)일 때의 `새 이벤트 시작` 버튼만 유지합니다.

### 2.2 `frontend/src/Dashboard.tsx`
* 헤더에서 제거된 AI 설정 도구를 본문 내의 "시뮬레이션 시작 패널" 카드로 추가 구현합니다.
* `snapshot.status === 'READY'`일 때 본문 상단에 아래 카드를 노출합니다:
  ```tsx
  {room.snapshot.status === 'READY' && (
    <section className="panel simulation-starter-card" style={{ padding: '24px', marginBottom: '24px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '16px' }}>AI 시뮬레이션 설정 및 시작</h2>
      <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <label style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-secondary)' }}>AI 유저 수</label>
          <input
            type="number"
            min={0}
            max={1000}
            value={aiCount}
            onChange={(e) => setAiCount(Math.max(0, Math.min(1000, parseInt(e.target.value) || 0)))}
            style={{ width: '120px', padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)' }}
          />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <label style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-secondary)' }}>동시 인입 수 (Concurrency)</label>
          <input
            type="number"
            min={1}
            max={120}
            value={aiConcurrency}
            onChange={(e) => setAiConcurrency(Math.max(1, Math.min(120, parseInt(e.target.value) || 1)))}
            style={{ width: '120px', padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)' }}
          />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <label style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-secondary)' }}>행동 속도 (Speed)</label>
          <select
            value={aiSpeed}
            onChange={(e) => setAiSpeed(e.target.value as any)}
            style={{ padding: '8px 12px', border: '1px solid var(--border-line)', borderRadius: 'var(--radius-md)', backgroundColor: '#FFFFFF' }}
          >
            <option value="SLOW">느림 (1.5초)</option>
            <option value="NORMAL">보통 (0.5초)</option>
            <option value="FAST">빠름 (0.1초)</option>
          </select>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => room.start({ aiUserCount: aiCount, aiConcurrency: aiConcurrency, aiSpeed: aiSpeed })}
          style={{ height: '38px', padding: '0 24px' }}
        >
          시뮬레이션 및 이벤트 시작하기
        </button>
      </div>
    </section>
  )}
  ```
* 대시보드 본문 하단의 `InsightPanel`을 삭제합니다.

### 2.3 `frontend/src/components/MonitoringConsole.tsx`
* 대시보드 하단에 있었던 `InsightPanel`을 모니터링 페이지 하단에 배치합니다.
* 서버 메트릭 5초 주기 폴링 로직 및 상태 변수(`metrics`, `setMetrics`)를 `MonitoringConsole.tsx` 내에 추가 구현합니다.
* 모니터링 화면 렌더링 최하단에 아래 컴포넌트를 배치합니다:
  ```tsx
  <div className="insight-section" style={{ marginTop: '24px' }}>
    <InsightPanel snapshot={room.snapshot} metrics={metrics} />
  </div>
  ```

---

## 3. 검증 방법 (Testing)
1. **Vitest 실행**: `npm run test` 명령을 활용하여 컴포넌트 마운트 및 렌더링에 관한 기존 24개 테스트가 모두 정상 통과하는지 검증합니다.
2. **동작 및 비주얼 확인**:
   * 대시보드 접속 시 이벤트 시작 전(`READY`)일 때 본문 상단에 "AI 시뮬레이션 설정" 카드가 깔끔하게 나오는지 확인합니다.
   * "시뮬레이션 및 이벤트 시작하기" 버튼 클릭 후, 해당 카드 설정 영역이 본문에서 사라지며 헤더 배너 레이아웃이 튀는 현상 없이 우아하게 `예매 중` 상태로 전환되는지 확인합니다.
   * 대시보드에 더 이상 서버 분산 및 시스템 인프라 현황이 노출되지 않는지 확인하고, `/monitoring` 탭으로 이동하면 해당 인프라 메트릭 패널이 하단에 실시간으로 실시간 메트릭을 받으며 정상 렌더링되는지 점검합니다.
