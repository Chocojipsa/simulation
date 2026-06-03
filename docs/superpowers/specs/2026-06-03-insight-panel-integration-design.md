# Insight Panel Integration Design

## Purpose
Integrate the existing `InsightPanel` into the live event dashboard (`App.tsx`) to provide visible observability of the distributed backend infrastructure for portfolio demonstration purposes.

## Architecture & Layout
- **Layout Choice**: We will use a 3-column layout (Option A). The `.dashboard-grid` will contain three main columns:
  1. **Left Column (New)**: `InsightPanel` (Server distribution, Redis queue, Kafka payment states, PostgreSQL seat status).
  2. **Middle Column**: `MyTicketPanel` and `SeatMap`.
  3. **Right Column (`.side-column`)**: `QueuePanel` and `EventActivityPanel`.
- **Data Flow**: `App.tsx` currently receives `LiveEventSnapshot` from `useLiveEventRoom`. However, `InsightPanel.tsx` is typed to expect `SimulationSnapshot`. We will need to adapt `InsightPanel.tsx` to accept `LiveEventSnapshot` so it can render the `snapshot.serverStats` and `snapshot.metrics` provided by the live SSE stream.

## Components to Modify
1. **`App.tsx`**: Add `InsightPanel` to the beginning of the `.dashboard-grid` children.
2. **`InsightPanel.tsx`**: Update the prop type from `SimulationSnapshot` to `LiveEventSnapshot`. Ensure `countSeatsByStatus` is compatible or update it to use `LiveEventSnapshot`'s seat format.

## Error Handling & Testing
- If the SSE stream disconnects, the InsightPanel will simply display the last known snapshot metrics.
- We will add/update unit tests for `App.tsx` and `InsightPanel.tsx` to ensure the new 3-column layout renders correctly without crashing.

## Scope
- In Scope: Rendering `InsightPanel` in `App.tsx`, adapting types.
- Out of Scope: Adding new backend metrics.
