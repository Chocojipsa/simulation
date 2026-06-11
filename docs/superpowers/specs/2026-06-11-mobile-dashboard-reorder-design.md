# Mobile Dashboard Reordering & Sticky Event Controls Design Specification

**Goal:** Allow mobile users to view the live event status/countdown timer and the ticketing "Reserve" button on the same viewport without being pushed down by the large Seat Map grid.

## Design Details

### 1. Refactor EventControlPanel Component
We will extract the event status, timer countdown logic, and admin start/reset buttons from `Sidebar.tsx` into a reusable component:
- New file: `frontend/src/components/EventControlPanel.tsx`
- It will manage the countdown `useEffect` timer, local input states (`aiCount`, `aiConcurrency`, `aiSpeed`), and render the status timer card and control form.
- Accepts `snapshot`, `onStart`, `onReset`, and an optional `className`.

### 2. Update Sidebar Component
- [Sidebar.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/components/Sidebar.tsx) will import `EventControlPanel` and delegate the event control rendering to it.

### 3. Update Dashboard Layout
- Modify [Dashboard.tsx](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/Dashboard.tsx) to import `EventControlPanel` and render it inside `.dashboard-hero-grid` wrapped in a `.mobile-only-controls` container.
- Add `.seat-map-panel` class to the Seat Map container wrapper div in `Dashboard.tsx`.

### 4. Stylesheet Ordering & Visibility
- In [styles.css](file:///mnt/c/users/kwon/desktop/workspace/timedeal/frontend/src/styles.css):
  - Set `.mobile-only-controls` to `display: none` by default.
  - In the `@media (max-width: 1023px)` media query:
    - Change `.dashboard-hero-grid` to `display: flex; flex-direction: column;`.
    - Show `.mobile-only-controls` with `display: block;`.
    - Apply flex orders:
      - `.mobile-only-controls` -> `order: 1`
      - `.my-ticket-panel` -> `order: 2`
      - `.seat-map-panel` -> `order: 3`
