# Mobile Dashboard Reordering & Sticky Event Controls Plan

**Goal:** Extract `EventControlPanel` and use CSS flex order to rearrange mobile dashboard widgets (Controls first, My Ticket second, Seat Map third).

---

### Task 1: Create EventControlPanel Component
**Files:**
- Create: `frontend/src/components/EventControlPanel.tsx`

- [ ] **Step 1: Write EventControlPanel with countdown timer and admin forms**
  Extract helper functions `getTimerLabelText`, `getTimerValueText`, and states (`now`, `aiCount`, `aiConcurrency`, `aiSpeed`) into this component.

### Task 2: Refactor Sidebar Component
**Files:**
- Modify: `frontend/src/components/Sidebar.tsx`

- [ ] **Step 1: Replace raw timer/controls markup with EventControlPanel**
  Clean up states (`now`, `aiCount`, `aiConcurrency`, `aiSpeed`), helper functions, and replace the controls block with `<EventControlPanel snapshot={snapshot} onStart={onStart} onReset={onReset} />`.

### Task 3: Update Dashboard Component
**Files:**
- Modify: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Import EventControlPanel and add containers**
  - Add wrapper `<div className="mobile-only-controls">` containing `<EventControlPanel snapshot={room.snapshot} onStart={...} onReset={...} className="panel" />` inside `.dashboard-hero-grid`.
  - Add class name `seat-map-panel` to the Seat Map panel div.

### Task 4: Add CSS Layout Ordering
**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Add desktop default display none**
  ```css
  .mobile-only-controls {
    display: none;
  }
  ```
- [ ] **Step 2: Add mobile flex overrides in media query**
  Inside `@media (max-width: 1023px)`:
  ```css
  .dashboard-hero-grid {
    display: flex;
    flex-direction: column;
  }
  .mobile-only-controls {
    display: block;
    order: 1;
  }
  .my-ticket-panel {
    order: 2;
  }
  .seat-map-panel {
    order: 3;
  }
  ```

### Task 5: Verify and Build
- [ ] **Step 1: Run unit tests**
  - Run `npm run test` inside `frontend`.
- [ ] **Step 2: Run production build**
  - Run `npm run build` inside `frontend`.
