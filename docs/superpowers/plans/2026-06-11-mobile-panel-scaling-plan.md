# Responsive Mobile Panel Scaling Implementation Plan

**Goal:** Implement responsive CSS variables and media query overrides to scale down the seat map grid and card panels on screen widths less than 1024px.

---

### Task 1: Update SeatMap Component
**Files:**
- Modify: `frontend/src/components/SeatMap.tsx`

- [ ] **Step 1: Replace hardcoded inline padding, gap, and grid column sizing**
  - Locate `padding: '24px'` in the first container `<section>` and change to `padding: 'var(--seat-map-padding, 24px)'`.
  - Locate `gridTemplateColumns: ...` in the grid map container `div` and change `minmax(24px, 1fr)` to `minmax(var(--seat-size, 24px), 1fr)`.
  - Locate `gap: '8px'` in the grid map container `div` and change to `gap: 'var(--seat-gap, 8px)'`.

### Task 2: Update Stylesheet Rules
**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Set seat min-width with CSS variable**
  - Locate `.seat` styling (around line 669). Add:
    ```css
    min-width: var(--seat-min-width, 24px);
    ```
- [ ] **Step 2: Append CSS variables inside media query**
  - Locate `@media (max-width: 1023px)`.
  - Inside the media query block, add definitions for `.seat-map-container`, `.seat`, and overrides for `.panel` and `.card`:
    ```css
    .seat-map-container {
      --seat-size: 18px;
      --seat-gap: 4px;
      --seat-map-padding: 12px;
    }
    .seat {
      --seat-min-width: 18px;
    }
    .panel, .card {
      padding: 16px;
      margin-bottom: 16px;
    }
    ```

### Task 3: Verify and Build
- [ ] **Step 1: Run tests**
  - Run `npm run test` inside the `frontend` directory. All tests should pass.
- [ ] **Step 2: Run production build**
  - Run `npm run build` inside the `frontend` directory. It should compile successfully.
