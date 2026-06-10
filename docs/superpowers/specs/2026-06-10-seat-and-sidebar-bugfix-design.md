# 좌석 디자인 및 사이드바 버그 수정 설계서 (Seat & Sidebar Bugfix Design Spec)

이 문서는 실시간 예매 데모 웹서비스의 좌석 CSS 버그(텍스트 노출 포함)와 모니터링 콘솔 이동 시 사이드바가 가려지는 레이아웃 버그에 대한 해결 설계를 기술합니다.

---

## 1. 개요 및 분석 결과

### 1.1 좌석 CSS 버그 및 글자 노출 버그
* **현상**: 좌석 렌더링 시 브라우저 기본 버튼 스타일로 인해 테두리가 깨지거나 정렬이 어긋날 수 있으며, 이전 버전의 흔적 등으로 글자가 노출되거나 렌더링이 오작동함.
* **원인**: `SeatMap.tsx` 내에서 스타일 지정을 거의 인라인(`style={{ ... }}`)으로 처리하여 브라우저의 기본 UI 스타일 오버라이드가 불완전하였고, `.seat` 클래스 체계가 완벽히 CSS로 구현되어 있지 않음.
* **해결 방안**:
  * `SeatMap.tsx`에 인라인 스타일을 적용하는 대신 상태에 맞는 클래스명을 부여함 (`seat`, `available`, `booked`, `held`, `payment`, `mine`).
  * `styles.css`에 해당 클래스들을 상세히 정의하고, 기본 버튼 스타일을 리셋(`padding: 0; outline: none; border: none;`)함.
  * 좌석 내부에 절대 텍스트가 표시되지 않도록 CSS 속성(`font-size: 0; color: transparent; text-indent: -9999px;`)을 명시하여 방어적으로 설계함.

### 1.2 모니터링 콘솔(`/monitoring`) 이동 시 사이드바 유실 버그
* **현상**: 모니터링 페이지로 이동 시 왼쪽 내비게이션 사이드바가 화면에서 완전히 사라짐.
* **원인**: `.main-content`에 선언된 `margin: 0 auto;`로 인해 flexbox 컨테이너인 `.dashboard-container` 안에서 자식 정렬 계산이 붕괴되어, 본문 너비가 넓어질 때 사이드바(`.sidebar`)가 화면 왼쪽 구석 밖으로 밀려남.
* **해결 방안**:
  * `.main-content`의 `margin: 0 auto;`를 `margin-left: 0; margin-right: auto;`로 바꾸어 사이드바와 본문이 가로 배치 상태로 밀착하며 왼쪽 정렬되도록 수정함.
  * 모니터링 콘솔 내부의 `.dashboard-grid`가 `styles.css`에 빠져 있어, 이를 CSS로 선언하고 반응형 미디어 쿼리를 추가하여 브라우저 폭이 좁을 때 아래로 쌓이도록 함.

---

## 2. 변경 파일 및 구성 상세

### 2.1 `frontend/src/styles.css`
* `.main-content` 스타일 수정:
  ```css
  .main-content {
    flex: 1;
    padding: 32px;
    overflow-y: auto;
    max-width: 1400px;
    margin-left: 0;
    margin-right: auto;
  }
  ```
* `.seat` 및 각 좌석 상태별 스타일 클래스 추가:
  ```css
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
  ```
* `.dashboard-grid` 모니터링용 그리드 및 반응형 추가:
  ```css
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

### 2.2 `frontend/src/components/SeatMap.tsx`
* 좌석 윗단에 노출되던 안내 배너(`selection.message`)를 완전히 제거합니다.
* 좌석 범례(`Seat Legend` div) 요소를 화면 상단에서 제거하고, 좌석 그리드 하단(`cols.length > 0` 렌더링 구문 아래)으로 이동시킵니다.
* 버튼 요소에서 인라인 스타일을 걷어내고 신규 CSS 클래스명을 부여하도록 변경:
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

### 2.3 `frontend/src/components/MonitoringConsole.tsx`
* 모니터링 콘솔 내의 인라인 스타일 그리드를 제거하고 `.dashboard-grid` 클래스로 일원화:
  ```tsx
  <div className="dashboard-grid">
  ```

---

## 3. 검증 방법 (Testing)
1. **정적 테스트**: `npm run test` 실행을 통해 기존 24개의 Vitest 케이스가 모두 정상적으로 성공하는지 검증.
2. **시각적 동작성 확인**:
   * 로컬에서 페이지 접속 시 좌석 내부에 글자(A-1 등)가 렌더링되지 않고 깔끔한 사각형으로 나오는지 확인.
   * `/monitoring` 탭으로 이동해도 왼쪽 사이드바가 가려지거나 사라지지 않고 항상 온전히 노출되는지 검증.
