# Wider Sidebar and Centered Content Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the left sidebar to 240px with brand logos and text labels, and restore the 1400px max-width centered layout to the main content container.

**Architecture:** Update styles.css with layout rules for .sidebar (240px wide), .sidebar-brand, .sidebar-link, and restore .main-content max-width to 1400px centered. Update Dashboard.tsx and MonitoringConsole.tsx with the new sidebar HTML structure.

**Tech Stack:** React, TypeScript, Vitest, Vanilla CSS

---

### Task 1: Update Frontend Unit Tests for Sidebar Navigation Links
**Files:**
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write the failing test**
Modify `frontend/src/App.test.tsx` to assert that the links with role `link` and name "대시보드" and "모니터링 콘솔" are in the document.

Add these assertions inside the first test case in `frontend/src/App.test.tsx` (around line 52):
```tsx
    expect(screen.getByRole('link', { name: /대시보드/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /모니터링/ })).toBeInTheDocument();
```
Also inside the second test case (around line 70):
```tsx
    expect(screen.getByRole('link', { name: /대시보드/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /모니터링/ })).toBeInTheDocument();
```

- [ ] **Step 2: Run test to verify it fails**
Run: `npm test` inside `frontend` directory.
Expected: FAIL with "Unable to find an accessible element with the role "link" and name /대시보드/"

- [ ] **Step 3: Commit**
```bash
git add frontend/src/App.test.tsx
git commit -m "test: add assertions for new text-labeled sidebar links"
```

### Task 2: Refactor CSS Styles for the Wider Brand Sidebar & Centered Layout
**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write the style rules in styles.css**
Modify the sidebar and main content CSS styles in `frontend/src/styles.css`.
Replace `.sidebar` and `.sidebar-icon` rules, and add branding and new link styles:

```css
/* Layout Structures */
.dashboard-container {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 240px;
  background: var(--bg-card);
  border-right: 1px solid var(--border-line);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 24px 0;
  gap: 24px;
  flex-shrink: 0;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  margin-bottom: 8px;
  width: 100%;
}

.brand-logo {
  font-size: 20px;
}

.brand-text {
  font-size: 16px;
  font-weight: 800;
  color: var(--primary-indigo);
  letter-spacing: 0.05em;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 0 12px;
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
  width: 100%;
}

.sidebar-link:hover {
  background: #F1F5F9;
  color: var(--text-primary);
}

.sidebar-link.active {
  background: rgba(79, 70, 229, 0.08);
  color: var(--primary-indigo);
}

.sidebar-link .link-icon {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #EEF2F6;
  font-weight: 800;
  font-size: 12px;
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.sidebar-link.active .link-icon {
  background: var(--primary-indigo);
  color: white;
}

.main-content {
  flex: 1;
  padding: 32px;
  overflow-y: auto;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}
```

- [ ] **Step 2: Commit**
```bash
git add frontend/src/styles.css
git commit -m "style: define CSS styles for 240px wide brand sidebar and restore centered 1400px max-width content"
```

### Task 3: Implement Wider Brand Sidebar in Dashboard Component
**Files:**
- Modify: `frontend/src/Dashboard.tsx`

- [ ] **Step 1: Update the sidebar markup in Dashboard.tsx**
Replace the simple `<aside className="sidebar">` markup in `Dashboard.tsx` (for both empty state loader page and main dashboard page) with:

```tsx
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>
        <nav className="sidebar-nav">
          <Link to="/" className="sidebar-link active" title="Dashboard">
            <span className="link-icon">D</span>
            <span className="link-text">대시보드</span>
          </Link>
          <Link to="/monitoring" className="sidebar-link" title="Monitoring">
            <span className="link-icon">M</span>
            <span className="link-text">모니터링 콘솔</span>
          </Link>
        </nav>
      </aside>
```

- [ ] **Step 2: Verify compilation and tests**
Run: `npm test` inside `frontend` directory.
Note: The first test case in `App.test.tsx` should now pass (or partially pass), but the second test case (for `/monitoring`) will still fail because `MonitoringConsole.tsx` doesn't have the new sidebar yet.

- [ ] **Step 3: Commit**
```bash
git add frontend/src/Dashboard.tsx
git commit -m "feat: implement wider brand sidebar in Dashboard component"
```

### Task 4: Implement Wider Brand Sidebar in MonitoringConsole Component
**Files:**
- Modify: `frontend/src/components/MonitoringConsole.tsx`

- [ ] **Step 1: Update the sidebar markup in MonitoringConsole.tsx**
Replace the simple `<aside className="sidebar">` markup in `MonitoringConsole.tsx` (for both empty state page and main console page) with:

For loader empty state:
```tsx
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>
        <nav className="sidebar-nav">
          <Link to="/" className="sidebar-link" title="Dashboard">
            <span className="link-icon">D</span>
            <span className="link-text">대시보드</span>
          </Link>
          <Link to="/monitoring" className="sidebar-link active" title="Monitoring">
            <span className="link-icon">M</span>
            <span className="link-text">모니터링 콘솔</span>
          </Link>
        </nav>
      </aside>
```
For main page:
```tsx
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>
        <nav className="sidebar-nav">
          <Link to="/" className="sidebar-link" title="Dashboard">
            <span className="link-icon">D</span>
            <span className="link-text">대시보드</span>
          </Link>
          <Link to="/monitoring" className="sidebar-link active" title="Monitoring">
            <span className="link-icon">M</span>
            <span className="link-text">모니터링 콘솔</span>
          </Link>
        </nav>
      </aside>
```

- [ ] **Step 2: Run all tests to verify they pass**
Run: `npm test` inside `frontend` directory.
Expected: All tests PASS.

- [ ] **Step 3: Verify the production build succeeds**
Run: `npm run build` inside `frontend` directory.
Expected: Build succeeds with zero errors.

- [ ] **Step 4: Commit**
```bash
git add frontend/src/components/MonitoringConsole.tsx
git commit -m "feat: implement wider brand sidebar in MonitoringConsole component"
```
