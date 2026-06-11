# Design Spec: Responsive Layout with Mobile Drawer Overlay

## Problem
The current simulator layout has a fixed 240px wide left sidebar (`.sidebar`) and a flex main content area. On smaller devices (e.g. tablet and mobile screens under 1024px), the sidebar takes up a massive portion of the viewport, leaving the dashboard squished, overflowed, or completely unusable.

## Objective
Implement a fully responsive design while preserving the premium aesthetic on desktop.
- **Desktop (>= 1024px)**: Keeps the current layout (fixed left sidebar + main content).
- **Mobile/Tablet (< 1024px)**:
  - Sidebar is hidden off-screen by default.
  - A slim top mobile header is introduced, containing a brand logo and a hamburger menu button.
  - Toggling the hamburger button opens the sidebar as a slide-in drawer overlay with a blurry backdrop.
  - The main content padding is adjusted to maximize screen real-estate.

## Design Details

### 1. Component State & Markup (`Sidebar.tsx`)
- Equip `<Sidebar>` with a local React state `isOpen` (boolean).
- Add a mobile top header (`.mobile-header`) containing a menu button (using Lucide's `Menu` icon) and branding.
- Add a backdrop overlay (`.sidebar-backdrop`) that shows when the drawer is open. Clicking the backdrop closes the drawer.
- The sidebar wrapper `<aside>` gets an conditional class `.open` when `isOpen` is true.
- To improve usability, the drawer should close automatically when a nav link is clicked.

### 2. Stylesheet Layout Rules (`styles.css`)
- **Default (Desktop)**:
  - `.mobile-header` is `display: none`.
  - `.sidebar-backdrop` is `display: none`.
- **Media Query (`max-width: 1023px`)**:
  - `.dashboard-container` changes from standard flex row to `flex-direction: column` to stack the top mobile-header and scrollable content wrapper vertically.
  - `.sidebar` shifts to a fixed overlay position:
    - `position: fixed; top: 0; left: 0; bottom: 0; z-index: 100;`
    - Hidden by default via `transform: translateX(-100%)`.
    - Slide-in animation via `transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)`.
    - When `.sidebar.open` is active, apply `transform: translateX(0)`.
  - `.sidebar-backdrop`:
    - Full screen overlay: `position: fixed; inset: 0; z-index: 90; background: rgba(15, 23, 42, 0.4); backdrop-filter: blur(2px)`.
  - `.mobile-header`:
    - Rendered at the top: `display: flex; height: 56px; align-items: center; justify-content: space-between; padding: 0 16px; background: var(--bg-card); border-bottom: 1px solid var(--border-line); position: sticky; top: 0; z-index: 80;`.
  - `.main-content`:
    - Reduce outer padding from `32px` to `16px` to avoid wasting space on small viewports.

## Affected Files
1. `frontend/src/components/Sidebar.tsx`
2. `frontend/src/styles.css`
