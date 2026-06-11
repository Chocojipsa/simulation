# Design Spec: Scrollbar Layout Fix for Main Content

## Problem
On screens wider than 1640px (240px sidebar + 1400px main content), the main layout area centers itself horizontally. Because the scrollable container (`.main-content`) is also the container that limits the width (`max-width: 1400px; margin: 0 auto;`), the vertical scrollbar is rendered at the right edge of this centered container instead of the right edge of the browser window. This looks floating and unaligned on wide monitors.

## Objective
Align the main content vertical scrollbar to the exact right edge of the screen, while keeping the dashboard's internal content nicely centered with a maximum width of 1400px.

## Design Solution
We will separate the scroll container responsibility from the width-limiting container responsibility:
1. **`.main-content-wrapper`**: A new outer full-width wrapper taking up the remaining horizontal space next to the sidebar. It will have `flex: 1`, `height: 100%`, and `overflow-y: auto`. The scrollbar will attach to this wrapper, placing it at the right edge of the browser window.
2. **`.main-content`**: The inner container that limits the maximum width to 1400px and centers itself horizontally. We will remove the scrolling (`overflow-y: auto`) from this class.

## Affected Files
1. `frontend/src/styles.css`
2. `frontend/src/Dashboard.tsx`
3. `frontend/src/components/MonitoringConsole.tsx`
