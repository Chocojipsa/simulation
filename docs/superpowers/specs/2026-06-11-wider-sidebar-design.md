# 타임딜 시뮬레이터 - 사이드바 확장 및 메인 레이아웃 개선 디자인 규격서

- **작성일**: 2026-06-11
- **상태**: 승인됨 (Approved)

---

## 1. 개요 및 배경

기존 프론트엔드 대시보드 및 모니터링 콘솔은 메인 콘텐츠 영역의 `max-width: 1400px`를 제거함으로써 와이드 모니터에서 화면이 좌우로 과도하게 넓어지고 요소들의 가로세로 비율(aspect ratio)이 흐트러지는 문제가 발생했습니다. 

이를 해결하기 위해, 화면 공간을 균형 있게 활용하면서 시각적인 만족도를 더 높일 수 있도록 다음과 같이 개선을 진행합니다.
1. 좌측 탭 네비게이션(사이드바)의 너비를 기존 `64px`에서 **`240px`**로 확장하고 브랜드 로고 및 텍스트 라벨을 노출하여 SaaS 스타일의 전문성을 강화합니다.
2. 메인 콘텐츠 영역의 가로세로 비율 안정을 위해 **`max-width: 1400px` 및 중앙 정렬(`margin: 0 auto`)** 구조를 다시 도입합니다.

---

## 2. 상세 디자인 규격

### 2.1. 사이드바 (Aside Sidebar)
- **크기**: 가로 너비 `240px` 고정, 세로 높이 `100vh`.
- **배경색**: `var(--bg-card)` (`#FFFFFF`), 우측 경계선 `1px solid var(--border-line)`.
- **브랜드 영역 (Sidebar Brand)**:
  - 상단에 로고와 브랜드명을 노출: `⏱️ TIMEDEAL`
  - 브랜드 텍스트 컬러: `var(--primary-indigo)` (`#4F46E5`), Bold (font-weight: 800)
- **네비게이션 링크 (Sidebar Link)**:
  - 아이콘(문자 형태 `D`, `M`)과 텍스트 라벨(`대기열`, `모니터링 콘솔`)을 나란히 배치.
  - 마우스 호버 시 배경 회색 변경 (`#F1F5F9`).
  - 활성화(Active) 시 배경색 `rgba(79, 70, 229, 0.08)`, 텍스트 및 아이콘 하이라이트 `var(--primary-indigo)`.

### 2.2. 메인 콘텐츠 레이아웃 (Main Content Layout)
- **정렬**: `.main-content` 컨테이너에 `max-width: 1400px; margin: 0 auto;`를 부여하여 화면 중앙에 오도록 배치.
- **반응형 제어**: 화면 너비가 줄어들 경우 가로 스크롤 없이 자연스럽게 축소되도록 유동적 패딩 적용.

---

## 3. 작업 영향 범위 및 대상 파일

1. **CSS 스타일 정의**:
   - [styles.css](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/styles.css)
2. **사이드바 컴포넌트 마크업**:
   - [Dashboard.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/Dashboard.tsx)
   - [components/MonitoringConsole.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/components/MonitoringConsole.tsx)
