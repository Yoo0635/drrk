# Dashboard equal-height columns design

## Goal

Align the bottom edges of the left sidebar, center content, and right sidebar at a 1920 by 1080 desktop viewport, eliminating unused space beneath shorter columns.

## Layout

- Make the dashboard a viewport-height flex container.
- Make the three-column grid fill the height remaining below the header.
- Give the left and right columns full grid height, then distribute that height across their five direct panel groups.
- Keep each two-panel group balanced so its pair fills the group height.

## Central content

- Preserve the compact top summary row at its content height.
- Let the map panel fill the remaining center-column height.
- Let the map wrapper flex within that panel instead of using a fixed height, while retaining the SVG aspect ratio.

## Responsive behavior

- Restrict viewport-height filling and flex growth to desktop layouts wider than 1180px.
- Retain the current stacked tablet and mobile layouts without forced column heights.

## Verification

- Run the production build and ESLint after the layout changes.
