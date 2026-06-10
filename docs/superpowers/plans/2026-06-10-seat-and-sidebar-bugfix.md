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
- Modify: `frontend/src/components/SeatMap.tsx:98-133`

- [ ] **Step 1: SeatMap.tsx 내 좌석 렌더링 스타일을 인라인에서 클래스로 변경**
  `frontend/src/components/SeatMap.tsx`에서 각 좌석을 렌더링할 때 사용하던 복잡한 인라인 배경색(`bg`), 테두리, 그림자 스타일링을 클래스명 부여 방식으로 전환합니다.

  *수정 대상 코드 (기존 98~133행 부근):*
  ```tsx
                const mine = seat.label === selectedSeatLabel;
                const disabled = !readOnly && (seat.status !== 'AVAILABLE' || !selection.allowed);

                // Select background color based on status and ownership
                let bg = 'var(--seat-available)';
                if (seat.status === 'RESERVED') {
                  bg = 'var(--seat-booked)';
                } else if (seat.status === 'PAYMENT_IN_PROGRESS') {
                  bg = 'var(--warning-amber)';
                } else if (seat.status === 'HELD' || mine) {
                  bg = 'var(--seat-selected)';
                }

                return (
                  <button
                    key={seat.id}
                    type="button"
                    disabled={disabled}
                    tabIndex={readOnly ? -1 : undefined}
                    title={`${seat.label} - ${seat.status}${mine ? ' (내 좌석)' : ''}`}
                    onClick={readOnly ? undefined : () => onSelectSeat?.(seat.id)}
                    style={{
                      aspectRatio: '1/1',
                      width: '100%',
                      maxWidth: '36px',
                      minWidth: '24px',
                      backgroundColor: bg,
                      border: mine ? '2px solid var(--primary-indigo)' : '1px solid transparent',
                      borderRadius: 'var(--radius-sm)',
                      cursor: readOnly || disabled ? 'default' : 'pointer',
                      transition: 'all 0.15s ease',
                      outline: 'none',
                      boxShadow: mine ? '0 0 6px var(--primary-indigo)' : 'none'
                    }}
                  />
                );
  ```

  *수정 후 코드:*
  ```tsx
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
  ```

- [ ] **Step 2: 테스트를 실행하여 렌더링에 지장이 없는지 확인**
  Run: `npm run test` (in `frontend` directory)
  Expected: 모든 24개 테스트 케이스 PASS

- [ ] **Step 3: 중간 커밋**
  Run:
  ```bash
  git add frontend/src/components/SeatMap.tsx
  git commit -m "refactor: apply class-based seat styling to SeatMap component"
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
