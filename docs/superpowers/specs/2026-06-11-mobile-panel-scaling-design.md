# Responsive Mobile Panel Scaling Design Specification

**Goal:** Ensure the real-time seat map grid and ticketing panel scale down elegantly on screens narrower than 1024px, preventing overflow.

## Design Details

### 1. CSS Variable Based Sizing
To dynamically resize the seat map grid without moving React rendering logic to CSS, we will introduce CSS variables in `frontend/src/components/SeatMap.tsx` and control them via media queries in `frontend/src/styles.css`.

#### Component Changes (`SeatMap.tsx`):
- Replace inline padding `padding: '24px'` with `padding: 'var(--seat-map-padding, 24px)'` on the seat map container.
- Update the grid columns template: `gridTemplateColumns: '30px repeat(' + cols.length + ', minmax(var(--seat-size, 24px), 1fr))'`
- Update the grid gap: `gap: 'var(--seat-gap, 8px)'`

#### Stylesheet Changes (`styles.css`):
- Update the `.seat` class to use `--seat-min-width` with a default fallback:
  ```css
  .seat {
    min-width: var(--seat-min-width, 24px);
  }
  ```
- Add a media query hook to adjust these variables on screens < 1024px:
  ```css
  @media (max-width: 1023px) {
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
  }
  ```

### 2. Layout Width Controls
- Ensure all grid wrappers and panels have `box-sizing: border-box` and `max-width: 100%` so they never stretch past the screen.
- Verify `overflow-x: auto` works smoothly on the grid.
