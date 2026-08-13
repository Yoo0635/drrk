import { useLayoutEffect, useMemo, useRef, useState } from "react";
import type { RouteData } from "../types/route";
import "./RouteOverlay.css";

interface Props {
  route: RouteData | null;
}

interface Size {
  width: number;
  height: number;
}

interface PixelPoint {
  x: number;
  y: number;
}

interface ArrowPoint extends PixelPoint {
  angle: number;
}

function toPixelPoints(route: RouteData, size: Size): PixelPoint[] {
  return route.points.map((point) => ({
    x: (point.x / 100) * size.width,
    y: (point.y / 100) * size.height,
  }));
}

/**
 * 각 직선 구간 안쪽에 작은 방향 삼각형을 배치한다.
 * 코너에 너무 가까운 화살표는 피해서 네비게이션처럼 정돈되게 보이도록 한다.
 */
function createArrows(points: PixelPoint[], spacing = 78, cornerPadding = 28): ArrowPoint[] {
  const arrows: ArrowPoint[] = [];

  for (let i = 0; i < points.length - 1; i += 1) {
    const start = points[i];
    const end = points[i + 1];
    const dx = end.x - start.x;
    const dy = end.y - start.y;
    const length = Math.hypot(dx, dy);

    if (length <= cornerPadding * 2 + 8) continue;

    const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
    const usable = length - cornerPadding * 2;
    const count = Math.max(1, Math.floor(usable / spacing));
    const step = usable / (count + 1);

    for (let n = 1; n <= count; n += 1) {
      const distance = cornerPadding + step * n;
      const t = distance / length;
      arrows.push({
        x: start.x + dx * t,
        y: start.y + dy * t,
        angle,
      });
    }
  }

  return arrows;
}

export default function RouteOverlay({ route }: Props) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [size, setSize] = useState<Size>({ width: 0, height: 0 });

  useLayoutEffect(() => {
    const frame = svgRef.current?.parentElement;
    if (!frame) return;

    const update = () => {
      const rect = frame.getBoundingClientRect();
      setSize({ width: rect.width, height: rect.height });
    };

    update();

    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", update);
      return () => window.removeEventListener("resize", update);
    }

    const observer = new ResizeObserver(update);
    observer.observe(frame);
    return () => observer.disconnect();
  }, []);

  const pixelPoints = useMemo(() => {
    if (!route || size.width <= 0 || size.height <= 0) return [];
    return toPixelPoints(route, size);
  }, [route, size]);

  const arrows = useMemo(() => createArrows(pixelPoints), [pixelPoints]);

  if (!route || route.points.length < 2) return null;

  const lineWidth = route.width ?? 11;
  const pointString = pixelPoints.map((point) => `${point.x},${point.y}`).join(" ");

  return (
    <svg
      ref={svgRef}
      className="route-overlay"
      viewBox={`0 0 ${Math.max(size.width, 1)} ${Math.max(size.height, 1)}`}
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      {pixelPoints.length >= 2 && (
        <>
          <polyline
            className="route-overlay__halo"
            points={pointString}
            strokeWidth={lineWidth + 4}
          />

          <polyline
            className="route-overlay__line"
            points={pointString}
            stroke={route.color}
            strokeWidth={lineWidth}
            opacity={route.opacity ?? 1}
          />

          {arrows.map((arrow, index) => (
            <path
              key={`${route.id}-${index}`}
              className="route-overlay__arrow"
              d="M 5 0 L -4 -4 L -4 4 Z"
              transform={`translate(${arrow.x} ${arrow.y}) rotate(${arrow.angle})`}
            />
          ))}
        </>
      )}
    </svg>
  );
}
