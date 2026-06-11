# Responsive Layout Mobile Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a fully responsive layout with a mobile header and a slide-in sidebar overlay on screens narrower than 1024px, preserving the desktop layout perfectly.

**Architecture:** Use a media query to stack the layout container. Render a new mobile header outside the sidebar logic and toggle the sidebar's visibility using React state `isOpen`. Apply fixed positioning and a slide-in animation to the sidebar on mobile, with a blurred backdrop.

**Tech Stack:** React, CSS, Lucide React

---

### Task 1: Update Sidebar Component

**Files:**
- Modify: `frontend/src/components/Sidebar.tsx`

- [ ] **Step 1: Import Menu icon and add state**

Modify `frontend/src/components/Sidebar.tsx` to import `Menu` from `lucide-react` and add the `isOpen` state to the `Sidebar` component.
Modify lines 1 to 5 to include the import:

```tsx
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Menu } from 'lucide-react';
import type { LiveEventSnapshot } from '../api/liveEventApi';
import { formatEventStatus } from '../domain/liveEventSelectors';
```

Modify the component declaration to add state (around lines 41-47):

```tsx
export function Sidebar({ activeTab, snapshot, onStart, onReset }: SidebarProps) {
  const [now, setNow] = useState(() => new Date());
  const [aiCount, setAiCount] = useState(cachedAiCount);
  const [aiConcurrency, setAiConcurrency] = useState(cachedAiConcurrency);
  const [aiSpeed, setAiSpeed] = useState(cachedAiSpeed);
  const [isOpen, setIsOpen] = useState(false);

  // Sync state changes with module cache variables
```

- [ ] **Step 2: Update Sidebar return markup to include mobile header and backdrop**

Modify `frontend/src/components/Sidebar.tsx` around lines 59 to 78 to render the mobile header, the backdrop, and add the `open` class to `<aside>`:

```tsx
  return (
    <>
      <div className="mobile-header">
        <button type="button" className="menu-toggle-btn" onClick={() => setIsOpen(!isOpen)} aria-label="Toggle menu">
          <Menu size={24} />
        </button>
        <div className="sidebar-brand">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>
        <div style={{ width: 40 }}></div>
      </div>

      {isOpen && <div className="sidebar-backdrop" onClick={() => setIsOpen(false)} />}

      <aside className={`sidebar ${isOpen ? 'open' : ''}`}>
        {/* Brand logo */}
        <div className="sidebar-brand desktop-only">
          <span className="brand-logo">⏱️</span>
          <span className="brand-text">TIMEDEAL</span>
        </div>

        {/* Navigation */}
        <nav className="sidebar-nav">
          <Link to="/" className={`sidebar-link ${activeTab === 'dashboard' ? 'active' : ''}`} onClick={() => setIsOpen(false)}>
            <span className="link-icon">D</span>
            <span className="link-text">대시보드</span>
          </Link>
          <Link to="/monitoring" className={`sidebar-link ${activeTab === 'monitoring' ? 'active' : ''}`} onClick={() => setIsOpen(false)}>
            <span className="link-icon">M</span>
            <span className="link-text">모니터링 콘솔</span>
          </Link>
        </nav>
```
*Note: Make sure to wrap the entire return statement in a fragment `<>` and properly close it at the end of the file `</>`.*

Modify the end of the file `frontend/src/components/Sidebar.tsx` to close the fragment:

```tsx
          {snapshot.status === 'ENDED' && (
            <button
              type="button"
              className="btn btn-primary control-btn"
              onClick={onReset}
            >
              새 이벤트 시작
            </button>
          )}
        </div>
      )}
      </aside>
    </>
  );
}
```

- [ ] **Step 3: Commit changes**

```bash
git add frontend/src/components/Sidebar.tsx
git commit -m "feat: add mobile header and responsive overlay drawer state to Sidebar"
```

---

### Task 2: Update Stylesheet Rules

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Add new classes and media queries to styles.css**

Append the following responsive CSS to the bottom of `frontend/src/styles.css`:

```css
/* Responsive Mobile Drawer Layout */
.mobile-header {
  display: none;
}

.desktop-only {
  display: flex;
}

.menu-toggle-btn {
  background: none;
  border: none;
  padding: 8px;
  cursor: pointer;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1023px) {
  .dashboard-container {
    flex-direction: column;
  }
  
  .mobile-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
    height: 56px;
    background: var(--bg-card);
    border-bottom: 1px solid var(--border-line);
    position: sticky;
    top: 0;
    z-index: 80;
    flex-shrink: 0;
  }

  .mobile-header .sidebar-brand {
    margin-bottom: 0;
    padding: 0;
    width: auto;
  }

  .desktop-only {
    display: none !important;
  }

  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 4px 0 24px rgba(0, 0, 0, 0.1);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .sidebar-backdrop {
    position: fixed;
    inset: 0;
    z-index: 90;
    background: rgba(15, 23, 42, 0.4);
    backdrop-filter: blur(2px);
    animation: fadeIn 0.3s ease-out;
  }

  .main-content {
    padding: 16px;
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

- [ ] **Step 2: Commit changes**

```bash
git add frontend/src/styles.css
git commit -m "style: implement responsive mobile drawer and header CSS layout"
```

---

### Task 3: Verify and Build

- [ ] **Step 1: Run tests**

Run: `npm run test` inside `frontend` directory.
Expected: All tests pass successfully.

- [ ] **Step 2: Run production build**

Run: `npm run build` inside `frontend` directory.
Expected: Production build compiles successfully with code 0.
