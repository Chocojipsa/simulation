# 타임딜 시뮬레이터 - 사이드바 확장 및 메인 레이아웃 개선 디자인 규격서

- **작성일**: 2026-06-11
- **상태**: 승인됨 (Approved)

---

## 1. 개요 및 배경

기존 프론트엔드 대시보드 및 모니터링 콘솔은 메인 콘텐츠 영역의 `max-width: 1400px`를 제거함으로써 와이드 모니터에서 화면이 좌우로 과도하게 넓어지고 요소들의 가로세로 비율(aspect ratio)이 흐트러지는 문제가 발생했습니다. 

이를 해결하기 위해 다음과 같이 화면 구조를 전면 개편합니다.
1. 좌측 탭 네비게이션(사이드바)의 너비를 기존 `64px`에서 **`240px`**로 확장하고 브랜드 로고 및 텍스트 라벨을 노출하여 SaaS 스타일의 전문성을 강화합니다.
2. 기존에 상단에 자리하던 `top-bar` (`EventHeader.tsx`)를 전면 삭제하고, 시뮬레이션 제어/상태 관제 패널을 **사이드바 하단 영역**에 세로 정렬 형태로 통합 배치합니다.
3. 메인 콘텐츠 영역의 가로세로 비율 안정을 위해 **`max-width: 1400px` 및 중앙 정렬(`margin: 0 auto`)** 구조를 다시 도입합니다.

---

## 2. 상세 디자인 규격

### 2.1. 사이드바 (Aside Sidebar)
- **컴포넌트화**: `components/Sidebar.tsx` 공통 컴포넌트 생성.
- **크기**: 가로 너비 `240px` 고정, 세로 높이 `100vh`.
- **배경색**: `var(--bg-card)` (`#FFFFFF`), 우측 경계선 `1px solid var(--border-line)`.
- **브랜드 영역 (Sidebar Brand)**:
  - 상단에 로고와 브랜드명을 노출: `⏱️ TIMEDEAL`
  - 브랜드 텍스트 컬러: `var(--primary-indigo)` (`#4F46E5`), Bold (font-weight: 800)
- **네비게이션 링크 (Sidebar Link)**:
  - 아이콘(문자 형태 `D`, `M`)과 텍스트 라벨(`대기열`, `모니터링 콘솔`)을 나란히 배치.
  - 활성화(Active) 시 배경색 `rgba(79, 70, 229, 0.08)`, 텍스트 및 아이콘 하이라이트 `var(--primary-indigo)`.

### 2.2. 하단 시뮬레이션 제어 패널 (Sidebar Control Panel)
- **위치**: 사이드바 하단부 (`margin-top: auto`).
- **정렬 및 레이아웃**: 모든 제어 패널 요소를 가로 **중앙 정렬 (Center aligned)** 합니다.
- **상태 캐싱 (State Caching)**:
  - 탭 전환 시 입력한 `AI 유저 수`, `동시 인입 수`, `행동 속도`가 유실되지 않도록 모듈 수준 변수를 사용해 캐싱 및 보존합니다.
- **이벤트 상태 배지 (Event Status Badge)**:
  - 상태에 따른 명확한 테마 색상(Color-coded) 지정:
    - **READY**: 회색조 (`시작 대기`)
    - **COUNTDOWN**: 주황색조 (`오픈 카운트다운`)
    - **OPEN**: 초록색조와 함께 깜빡이는 라이브 점 (`● 예매 진행 중`)
    - **ENDED**: 빨간색조 (`종료`)
- **디지털 타이머 카드 (Digital Timer Card)**:
  - 중앙 정렬된 회색 음영 카드 배경 위에 노출.
  - 라벨 (예: `남은 시간`, `오픈까지`)을 작은 텍스트로 노출.
  - 타이머 값을 **`24px` 크기의 굵은 폰트(Monospace)**로 크게 표기하여 직관적인 시간 변화를 관제합니다.
  - 실시간 예매 현황(`예약 완료: 97 / 120`)을 하단에 보조 텍스트로 노출합니다.
- **상태별 컴포넌트 변화**:
  - **준비 상태 (`READY`)**:
    - AI 유저 수 입력란 (`number`, 0~1000)
    - 동시 인입 수 입력란 (`number`, 1~120)
    - 행동 속도 선택란 (`select`, SLOW/NORMAL/FAST)
    - [이벤트 시작하기] 버튼 (`btn-primary`)
  - **진행 중 (`COUNTDOWN` / `OPEN`)**:
    - 실시간 디지털 타이머 카드 노출 (예매 진행/카운트다운 시간 표기)
  - **종료 상태 (`ENDED`)**:
    - 실시간 디지털 타이머 카드 노출 (`종료됨` 표기 및 최종 예약 수 노출)
    - [새 이벤트 시작] (초기화) 버튼 (`btn-primary`)

### 2.3. 메인 콘텐츠 레이아웃 (Main Content Layout)
- **상단 헤더 제거**: 기존 `EventHeader` (`top-bar`)를 제거하고 콘텐츠 그리드가 바로 나타나도록 설계.
- **정렬**: `.main-content` 컨테이너에 `max-width: 1400px; margin: 0 auto;`를 부여하여 화면 중앙에 오도록 배치.

---

## 3. 작업 영향 범위 및 대상 파일

1. **공통 컴포넌트 추가**:
   - `frontend/src/components/Sidebar.tsx` (신규 생성)
2. **사이드바 컴포넌트 적용 및 상단 헤더 제거**:
   - [Dashboard.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/Dashboard.tsx)
   - [components/MonitoringConsole.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/components/MonitoringConsole.tsx)
3. **CSS 스타일 정의**:
   - [styles.css](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/styles.css)
