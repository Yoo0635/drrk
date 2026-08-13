# Dashboard compact layout design

## Goal

Fit the airport operations dashboard into a 1920 by 1080 desktop viewport without vertical scrolling, while retaining every current panel and data point.

## Layout

- Keep the existing three-column desktop grid and its responsive two-column and one-column breakpoints.
- Reduce page padding, header height, column gaps, panel heading padding, and panel body padding consistently.
- Use denser typography and shorter chart heights only where labels and values remain legible.

## Central map

- Limit the displayed airport map to a compact fixed height (about 330px) and preserve its aspect ratio with `object-fit: contain`-style sizing through its wrapper.
- Place the status legend in two compact columns below the map so it does not add unnecessary vertical height.

## Supporting panels

- Compress chart rows, tables, dials, parking bars, and transfer indicators by reducing internal padding and selected visual dimensions.
- Preserve all panels, their ordering, and their data; do not hide content behind tabs or accordions.

## Responsive behavior

- At existing tablet and mobile breakpoints, keep the current stacking behavior so the dashboard remains readable rather than clipped.
- The no-scroll target applies to a 1920 by 1080 desktop viewport; shorter or narrower screens may naturally scroll.

## Verification

- Run the production build and ESLint after the layout changes.
