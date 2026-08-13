# Realtime Congestion Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the airport map with a live-updating binary congestion chart ready for a future server subscription.

**Architecture:** A new React component owns normalized sample history and SVG rendering. Its optional `subscribe` adapter accepts any future transport; when absent, a local interval emits samples for a realistic live preview.

**Tech Stack:** React 19, TypeScript, inline SVG, Vite.

## Global Constraints

- Interpret `0` as clear and `1` as congested.
- Keep the most recent 30 normalized samples.
- Do not add dependencies.
- Preserve the center panel's flex-filling behavior.

---

### Task 1: Create the reusable live chart

**Files:**
- Create: `src/components/RealtimeCongestionChart.tsx`

**Interfaces:**
- Produces: `CongestionSample`, `CongestionSubscribe`, and `RealtimeCongestionChart`.
- Consumes: `C` from `src/theme.ts`.

- [ ] **Step 1: Define the server adapter contract**

Export these types:

```ts
export interface CongestionSample { value: 0 | 1; timestamp: number; }
export type CongestionSubscribe = (
  onSample: (sample: CongestionSample) => void,
) => () => void;
```

- [ ] **Step 2: Implement bounded sample history**

Use React state initialized with 30 binary samples. Add incoming samples with a functional state update and retain the last 30 values using `slice(-30)`.

- [ ] **Step 3: Add fallback live data and subscription cleanup**

Use an effect that calls `subscribe(onSample)` when supplied. Otherwise, use `window.setInterval` at 1200ms to toggle or randomly emit a binary sample. Return the adapter unsubscribe function or `clearInterval` from the effect.

- [ ] **Step 4: Render the binary chart**

Draw a responsive SVG line over horizontal 0/1 grid lines. Include a current status badge, last update time, and labels `원활 (0)` and `혼잡 (1)`.

- [ ] **Step 5: Verify production checks**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.

### Task 2: Replace the map panel with the live chart

**Files:**
- Modify: `src/App.tsx`

**Interfaces:**
- Consumes: `RealtimeCongestionChart` from `src/components/RealtimeCongestionChart.tsx`.
- Produces: The `실시간 혼잡도 측정` center panel.

- [ ] **Step 1: Remove map-only imports and markup**

Remove `AirportMap`, `STATUS_COLOR`, and `STATUS_LEGEND` imports and replace the map wrapper plus status legend with the chart component.

- [ ] **Step 2: Retain flex-filling panel styles**

Keep the existing center panel `style` and `bodyStyle` flex values, and render `<RealtimeCongestionChart />` as its child.

- [ ] **Step 3: Verify production checks**

Run: `npm.cmd run build; npm.cmd run lint`

Expected: both commands exit with code 0.
