# 좌석 디자인 및 사이드바 버그 수정 구현 계획서 (Seat & Sidebar Bugfix Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 예매 화면의 좌석 CSS를 클래스 기반으로 재구현하고 텍스트 노출을 방지하며, 모니터링 페이지 내에서 사이드바가 가려지는 레이아웃 결함을 수정합니다.

**Architecture:** 
1. 인라인 스타일을 클래스명(`.seat`, `.seat.available`, 등)과 `styles.css`에 정의된 스타일 시트로 일원화하여 브라우저 기본 테두리와 여백 문제를 방지합니다.
2. `.main-content`의 `margin: 0 auto;`를 `margin-left: 0; margin-right: auto;`로 수정하여 사이드바와 본문이 유실 없이 밀착 정렬되도록 레이아웃을 고정합니다.
3. 콘솔 내부의 레이아웃도 인라인 스타일 대신 `.dashboard-grid` 클래스를 활용해 반응형 레이아웃을 안전하게 지원합니다.

**Tech Stack:** React 18, TypeScript, CSS3, Vitest

---

### Task 1: 스타일시트 수정 (styles.css)

**Files:**
- Modify: `frontend/src/styles.css:120-126` (main-content), `frontend/src/styles.css:308-314` (seat-map-container)

- [ ] **Step 1: styles.css 수정**
  `frontend/src/styles.css` 파일의 `.main-content` 마진 설정을 변경하고, `.seat` 관련 스타일 클래스 및 `.dashboard-grid` 모니터링 그리드를 파일 하단에 추가합니다.

  *수정 내용:*
  ```css
  /* Line 120-126: .main-content의 margin 수정 */
  .main-content {
    flex: 1;
    padding: 32px;
    overflow-y: auto;
    max-width: 1400px;
    margin-left: 0;
    margin-right: auto;
  }
  ```

  *추가 내용 (파일 끝에 삽입):*
  ```css
  /* 좌석 그리드 스타일 클래스 추가 */
  .seat {
    aspect-ratio: 1/1;
    width: 100%;
    max-width: 36px;
    min-width: 24px;
    border-radius: var(--radius-sm);
    border: 1px solid transparent;
    cursor: pointer;
    transition: all 0.15s ease;
    outline: none;
    font-size: 0;
    color: transparent;
    text-indent: -9999px;
    padding: 0;
    display: block;
    box-sizing: border-box;
  }

  .seat.available {
    background-color: var(--seat-available);
  }

  .seat.available:hover:not(:disabled) {
    background-color: #CBD5E1;
    transform: scale(1.05);
  }

  .seat.booked {
    background-color: var(--seat-booked);
    cursor: default;
  }

  .seat.held {
    background-color: var(--seat-selected);
  }

  .seat.payment {
    background-color: var(--warning-amber);
  }

  .seat.mine {
    background-color: var(--seat-selected);
    border: 2px solid var(--primary-indigo) !important;
    box-shadow: 0 0 6px var(--primary-indigo);
  }

  .seat:disabled {
    cursor: not-allowed;
    opacity: 0.8;
  }

  /* 모니터링 콘솔 그리드 및 반응형 추가 */
  .dashboard-grid {
    display: grid;
    grid-template-columns: 1fr 2fr;
    gap: 24px;
    margin-top: 24px;
  }

  @media (max-width: 1024px) {
    .dashboard-grid {
      grid-template-columns: 1fr;
    }
  }
  ```

- [ ] **Step 2: 로컬 테스트 실행**
  프론트엔드 테스트를 구동하여 스타일시트 빌드오류나 타입 문제가 없는지 확인합니다.
  Run: `npm run test` (in `frontend` directory)
  Expected: 모든 24개 테스트 케이스 PASS

- [ ] **Step 3: 중간 커밋**
  Run:
  ```bash
  git add frontend/src/styles.css
  git commit -m "style: update main-content margin and define seat/grid CSS classes"
  ```

---

### Task 2: 좌석 컴포넌트 수정 (SeatMap.tsx)

**Files:**
- Modify: `frontend/src/components/SeatMap.tsx:15-146`

- [ ] **Step 1: SeatMap.tsx 내 좌석 렌더링 스타일 변경, 안내 문구(selection.message) 제거, 범례(Legend) 하단 이동**
  `frontend/src/components/SeatMap.tsx`에서 다음을 적용합니다:
  1. 각 좌석을 렌더링할 때 클래스명 부여 방식으로 전환합니다.
  2. 안내 문구 배너(`selection.message ? (...) : null`)를 완전히 지워 빈 공간을 없앱니다.
  3. 좌석 범례(`Seat Legend` div) 요소를 원래 있던 윗부분에서 그리드 하단(그리드 렌더링 div 아래)으로 이동시킵니다.

  *수정 후 코드 구조:*
  ```tsx
  export function SeatMap({ status, seats, participant, selectedSeatLabel, onSelectSeat, readOnly = false }: SeatMapProps) {
    const selection = canSelectSeat(status, participant);

    // Extract unique rows and columns dynamically from seats labels (e.g. 'A-1', 'B-2')
    const rows = Array.from(new Set(seats.map(s => s.label.split('-')[0]))).sort();
    const cols = Array.from(new Set(seats.map(s => {
      const parts = s.label.split('-');
      return parts.length > 1 ? parseInt(parts[1], 10) : 0;
    }))).filter(c => c > 0).sort((a, b) => a - b);

    return (
      <section className="seat-map-container" style={{ padding: '24px', background: '#F8FAFC', borderRadius: 'var(--radius-lg)' }}>
        {/* Screen Area (seatdesign.PNG Reference) */}
        <div style={{ textAlign: 'center', margin: '0 auto 24px', maxWidth: '360px' }}>
          <div style={{ fontSize: '11px', fontWeight: '800', color: 'var(--teal-accent)', letterSpacing: '4px', marginBottom: '8px' }}>SCREEN</div>
          <div style={{ height: '4px', background: 'var(--teal-accent)', borderRadius: '9999px' }}></div>
        </div>

        {/* Dynamic Grid Map (seatdesign.PNG Reference) */}
        {seats.length > 0 && cols.length > 0 ? (
          <div 
            style={{ 
              display: 'grid', 
              gridTemplateColumns: `30px repeat(${cols.length}, minmax(24px, 1fr))`, 
              gap: '8px', 
              alignItems: 'center',
              maxWidth: '100%',
              overflowX: 'auto',
              padding: '4px',
              marginBottom: '24px' // Legend와의 간격을 위해 마진 추가
            }}
            aria-label="좌석표"
          >
            {/* Header Column Labels */}
            <div></div>
            {cols.map(c => (
              <div key={`header-col-${c}`} style={{ textAlign: 'center', fontSize: '11px', fontWeight: '800', color: 'var(--teal-accent)' }}>
                {c}
              </div>
            ))}

            {/* Rows */}
            {rows.map(r => (
              <Fragment key={`row-${r}`}>
                {/* Row Label (left side) */}
                <div style={{ fontWeight: '800', color: 'var(--teal-accent)', fontSize: '11px', textAlign: 'center' }}>
                  {r}
                </div>

                {/* Seats in Row */}
                {cols.map(c => {
                  const seatLabel = `${r}-${c}`;
                  const seat = seats.find(s => s.label === seatLabel);

                  if (!seat) {
                    return <div key={`empty-${seatLabel}`} style={{ aspectRatio: '1/1' }}></div>;
                  }

                  const mine = seat.label === selectedSeatLabel;
                  const disabled = !readOnly && (seat.status !== 'AVAILABLE' || !selection.allowed);

                  let statusClass = 'available';
                  if (seat.status === 'RESERVED') {
                    statusClass = 'booked';
                  } else if (seat.status === 'PAYMENT_IN_PROGRESS') {
                    statusClass = 'payment';
                  } else if (seat.status === 'HELD' || mine) {
                    statusClass = 'held';
                  }
                  if (mine) {
                    statusClass += ' mine';
                  }

                  return (
                    <button
                      key={seat.id}
                      type="button"
                      className={`seat ${statusClass}`}
                      disabled={disabled}
                      tabIndex={readOnly ? -1 : undefined}
                      title={`${seat.label} - ${seat.status}${mine ? ' (내 좌석)' : ''}`}
                      onClick={readOnly ? undefined : () => onSelectSeat?.(seat.id)}
                    />
                  );
                })}
              </Fragment>
            ))}
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)' }}>
            등록된 좌석이 없습니다.
          </div>
        )}

        {/* Seat Legend (하단으로 이동함) */}
        <div style={{ display: 'flex', gap: '16px', justifyContent: 'center', flexWrap: 'wrap', marginTop: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
            <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--seat-available)', borderRadius: 'var(--radius-sm)' }}></div>
            <span>예매 가능</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
            <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--seat-selected)', borderRadius: 'var(--radius-sm)' }}></div>
            <span>선점 (내 선택)</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
            <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--warning-amber)', borderRadius: 'var(--radius-sm)' }}></div>
            <span>결제 중</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
            <div style={{ width: '12px', height: '12px', backgroundColor: 'var(--seat-booked)', borderRadius: 'var(--radius-sm)' }}></div>
            <span>매진</span>
          </div>
        </div>
      </section>
    );
  }
  ```

- [ ] **Step 2: 테스트를 실행하여 렌더링에 지장이 없는지 확인**
  Run: `npm run test` (in `frontend` directory)
  Expected: 모든 24개 테스트 케이스 PASS

- [ ] **Step 3: 중간 커밋**
  Run:
  ```bash
  git add frontend/src/components/SeatMap.tsx
  git commit -m "refactor: clean up seat map status banners, move legend to the bottom, and apply classes"
  ```

---

### Task 3: 모니터링 콘솔 레이아웃 수정 (MonitoringConsole.tsx)

**Files:**
- Modify: `frontend/src/components/MonitoringConsole.tsx:54`

- [ ] **Step 1: MonitoringConsole.tsx 의 인라인 스타일을 클래스 스타일로 대체**
  `frontend/src/components/MonitoringConsole.tsx` 파일 내 54행의 `style={{ display: 'grid', ... }}`을 제거하고, `styles.css`에 정의한 `dashboard-grid` 클래스를 사용합니다.

  *수정 대상 코드:*
  ```tsx
        <div className="dashboard-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px', marginTop: '24px' }}>
  ```

  *수정 후 코드:*
  ```tsx
        <div className="dashboard-grid">
  ```

- [ ] **Step 2: 테스트 수행**
  Run: `npm run test`
  Expected: 모든 24개 테스트 케이스 PASS

- [ ] **Step 3: 최종 커밋**
  Run:
  ```bash
  git add frontend/src/components/MonitoringConsole.tsx
  git commit -m "style: remove inline grid styles from MonitoringConsole in favor of dashboard-grid class"
  ```

---

### Task 4: 최종 검증

- [ ] **Step 1: 전체 테스트 재동작 확인**
  Run: `npm run test`
  Expected: 모든 24개 테스트 케이스가 성공적으로 통과

- [ ] **Step 2: 코드 린트 및 빌드 검증**
  Run: `npm run build`
  Expected: 빌드가 에러 없이 정상 완료됨
