# Dashboard Compact Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the complete airport operations dashboard at 1920 by 1080 without vertical scrolling while retaining all existing panels and data.

**Architecture:** Keep the existing React component tree and three-column grid. Apply reusable compact spacing rules in the shared CSS, then reduce inline dimensions in the dashboard composition where each panel's content needs a specific visual adjustment.

**Tech Stack:** React 19, TypeScript, Vite, CSS.

## Global Constraints

- Preserve every panel, its data, and its desktop ordering.
- Optimize the no-scroll layout specifically for a 1920 by 1080 desktop viewport.
- Retain the existing two-column and one-column responsive breakpoints.
- Do not introduce dependencies or tabs/accordions.

---

### Task 1: Add reusable compact dashboard spacing

**Files:**
- Modify: `src/App.tsx`
- Modify: `src/index.css`
- Modify: `src/components/Panel.tsx`

**Interfaces:**
- Consumes: Existing `.grid`, `.col`, `.two`, and `Panel` component styles.
- Produces: A `.dashboard` root class and compact panel spacing shared by every dashboard panel.

- [ ] **Step 1: Add a compact dashboard root class**

Add the following styles to `src/index.css` and apply `className="dashboard"` to the root `div` in `src/App.tsx`:

```css
.dashboard { padding: 8px; font-size: 12px; }
.grid { gap: 6px; }
.col, .two { gap: 6px; }
```

- [ ] **Step 2: Reduce shared panel chrome**

In `src/components/Panel.tsx`, reduce heading padding from `5px 8px` to `3px 7px`, heading font size from `12.5` to `12`, and body padding from `8` to `6`.

- [ ] **Step 3: Verify static type and style checks**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.

### Task 2: Compact the dashboard-specific content

**Files:**
- Modify: `src/App.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: The compact shared spacing from Task 1 and current data/component props.
- Produces: A 1920 by 1080-friendly dashboard with a compact map, charts, and tables.

- [ ] **Step 1: Reduce vertical dimensions in `App.tsx`**

Change the root padding to `8`, header bottom padding to `6px`, chart defaults from 74px to 58px through explicit `height={58}` calls for the two left charts, and `ChartRow` height from 58px to 46px.

- [ ] **Step 2: Resize visual-heavy widgets**

In `src/App.tsx`, set the map wrapper `maxHeight` to `330`, reduce the transfer ring from `78` to `62` pixels, and reduce the ring inset from `11` to `9` pixels. Change the map legend grid to two columns.

- [ ] **Step 3: Tighten table and gauge spacing**

In `src/App.tsx`, change the `gaugeRow` padding to `0`, reduce table cell padding values from `4px 1px` to `2px 1px`, and reduce the delayed/cancelled value font size from `22` to `19`.

- [ ] **Step 4: Verify the production bundle and lint**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.

### Task 3: Confirm responsive constraints

**Files:**
- Modify: `src/index.css`

**Interfaces:**
- Consumes: Existing `1180px` and `720px` media queries.
- Produces: Explicit compact desktop styling that does not change the breakpoint behavior.

- [ ] **Step 1: Preserve current responsive rules**

Keep these existing rules unchanged while retaining the new compact base gaps:

```css
@media (max-width: 1180px) {
  .grid { grid-template-columns: 1fr 1fr; }
  .col--map { grid-column: 1 / -1; order: 3; }
}
@media (max-width: 720px) {
  .grid { grid-template-columns: 1fr; }
  .col--map { order: 0; }
}
```

- [ ] **Step 2: Run final verification**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.
