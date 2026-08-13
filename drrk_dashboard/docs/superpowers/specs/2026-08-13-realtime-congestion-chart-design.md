# Realtime congestion chart design

## Goal

Replace the airport map with a live-updating congestion chart that displays a stream of binary values: `0` for clear and `1` for congested.

## Component and data contract

- Add `RealtimeCongestionChart` as a self-contained component.
- Export `CongestionSample` with `value: 0 | 1` and `timestamp: number`.
- Export `CongestionSubscribe`, a function that receives a sample callback and returns an unsubscribe function.
- The component accepts an optional `subscribe` prop. A future WebSocket, SSE, or polling adapter supplies this prop and forwards normalized API values to the callback.

## Runtime behavior

- Without a subscription, generate a new binary sample every 1.2 seconds so the dashboard visibly behaves as a real-time screen during development.
- Retain the most recent 30 samples.
- Render a binary line chart with clear/congested axis labels, a current-status badge, last-update time, and a compact legend.
- Normalize timestamps at the adapter boundary; malformed values are not passed to the component.

## Layout

- Keep the chart in the existing flex-filling center panel so it continues to align with both sidebars.
- Remove airport-map-specific legend and status imports.

## Verification

- Run the production build and ESLint.
