# Dashboard Equal-Height Columns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the desktop dashboard's three column bottoms and fill their available viewport height.

**Architecture:** Use desktop-only CSS flex sizing for the dashboard root, grid, side columns, and nested two-panel rows. Pass flex layout styles to the existing map `Panel`, allowing its existing map wrapper to consume the remaining center-column height.

**Tech Stack:** React 19, TypeScript, CSS, Vite.

## Global Constraints

- Preserve all dashboard panels, data, and ordering.
- Apply equal-height behavior only above the existing 1180px tablet breakpoint.
- Do not add dependencies.

---

### Task 1: Fill the desktop dashboard viewport

**Files:**
- Modify: `src/index.css`

**Interfaces:**
- Consumes: `.dashboard`, `.grid`, `.col`, `.two`, and the 1180px media query.
- Produces: Desktop-only flex sizing that gives each column identical available height.

- [ ] **Step 1: Add desktop height allocation rules**

Add a `min-width: 1181px` media query that makes `.dashboard` a column flex container with `height: 100%`, makes `.grid` use `flex: 1`, `min-height: 0`, and `align-items: stretch`, and makes every `.col` use `min-height: 0`.

- [ ] **Step 2: Allocate side-column groups**

Within that media query, assign `flex: 1 1 0` and `min-height: 0` to `.col:not(.col--map) > section` and `.col:not(.col--map) > .two`; assign `height: 100%` to `.col:not(.col--map) > .two > section`.

- [ ] **Step 3: Verify production checks**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.

### Task 2: Make the central map panel fill remaining height

**Files:**
- Modify: `src/App.tsx`

**Interfaces:**
- Consumes: The `Panel` component's existing `style` and `bodyStyle` props.
- Produces: A map panel that expands after the fixed-height summary row.

- [ ] **Step 1: Give the map panel flex layout props**

Set the map `Panel`'s `style` prop to `{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }` and its `bodyStyle` prop to `{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }`.

- [ ] **Step 2: Let the map wrapper consume the panel space**

Replace the map wrapper's fixed `height: 330` with `flex: 1`, `minHeight: 0`, and `height: "auto"` so the existing SVG fills the panel without changing its viewBox.

- [ ] **Step 3: Verify production checks**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.
