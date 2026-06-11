# Ticketing Auto-Refresh & SSE Admission Bugfix Plan

**Goal:** Modify `TicketingWindow.tsx` to initialize `autoRefresh` to `true` and fetch the latest snapshot upon SSE queue admission before transitioning steps.

---

### Task 1: Update TicketingWindow Component
**Files:**
- Modify: `frontend/src/components/TicketingWindow.tsx`

- [ ] **Step 1: Set autoRefresh state default to true**
  Locate `const [autoRefresh, setAutoRefresh] = useState(false);` (around line 53) and change it to `const [autoRefresh, setAutoRefresh] = useState(true);`.

- [ ] **Step 2: Fetch snapshot inside queue_admitted event listener**
  Locate `else if (data.label === 'queue_admitted')` block (around lines 258-263) and update it to fetch the latest snapshot using `fetchEventSnapshot` and update snapshot state before calling `setStep(3)`.

### Task 2: Verify and Build
- [ ] **Step 1: Run tests**
  - Run `npm run test` inside the `frontend` directory. Verify that all 27 unit tests pass.
- [ ] **Step 2: Run production build**
  - Run `npm run build` inside the `frontend` directory. Verify that it compiles successfully.
