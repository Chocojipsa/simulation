# Sidebar Viewport Height Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock `.dashboard-container` and `.sidebar` to the viewport height (`100vh`) so that the sidebar control panel is always visible at the bottom of the screen without scrolling the page.

**Architecture:** Update `styles.css` to change `.dashboard-container` to `height: 100vh` and `overflow: hidden`, and set `.sidebar` to `height: 100%` and `overflow-y: auto`.

**Tech Stack:** Vanilla CSS

---

### Task 1: Refactor Layout and Sidebar Heights in CSS
**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Update styles.css**
Modify the `.dashboard-container` and `.sidebar` rules in `frontend/src/styles.css`:

```css
.dashboard-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  height: 100%;
  background: var(--bg-card);
  border-right: 1px solid var(--border-line);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 24px 0;
  gap: 24px;
  flex-shrink: 0;
  overflow-y: auto;
}
```

- [ ] **Step 2: Verify build**
Run: `npm run build` in `frontend` directory.
Expected: Build succeeds.

- [ ] **Step 3: Commit**
```bash
git add frontend/src/styles.css
git commit -m "style: fix sidebar and dashboard container height to lock to viewport and scroll content internally"
```
