import { useMemo } from "react";
import { C } from "../theme";
import type { ChartCfg } from "../types";

interface ChartProps {
  cfg: ChartCfg;
  height?: number;
}

const W = 300;
const PAD = { l: 18, r: 4, t: 6, b: 14 };
const HOURS = 24;

export function Chart({ cfg, height = 74 }: ChartProps) {
  const geo = useMemo(() => {
    const w = W - PAD.l - PAD.r;
    const h = height - PAD.t - PAD.b;
    const step = w / HOURS;
    const y = (v: number) => PAD.t + h - (v / cfg.max) * h;

    return {
      h,
      step,
      y,
      ticks: [0, 1, 2, 3, 4].map((i) => (cfg.max * i) / 4),
      line: cfg.plan
        .map((v, i) => `${PAD.l + step * i + step / 2},${y(v)}`)
        .join(" "),
    };
  }, [cfg, height]);

  return (
    <svg
      viewBox={`0 0 ${W} ${height}`}
      style={{ width: "100%", display: "block" }}
      role="img"
    >
      {geo.ticks.map((v) => (
        <g key={v}>
          <line
            x1={PAD.l}
            y1={geo.y(v)}
            x2={W - PAD.r}
            y2={geo.y(v)}
            stroke="#26343f"
            strokeWidth={0.6}
          />
          <text
            x={PAD.l - 3}
            y={geo.y(v) + 3}
            textAnchor="end"
            fontSize={5.5}
            fill="#7f95a7"
          >
            {cfg.max >= 1000 ? v.toLocaleString() : v}
          </text>
        </g>
      ))}

      {[0, 3, 6, 9, 12, 15, 18, 21].map((i) => (
        <text
          key={i}
          x={PAD.l + geo.step * i + geo.step / 2}
          y={height - 3}
          textAnchor="middle"
          fontSize={5.5}
          fill="#7f95a7"
        >
          {i}
        </text>
      ))}

      {cfg.act.map((v, i) => {
        const bh = Math.max(0, (v / cfg.max) * geo.h);
        return (
          <rect
            key={i}
            x={PAD.l + geo.step * i + geo.step * 0.16}
            y={PAD.t + geo.h - bh}
            width={geo.step * 0.68}
            height={bh}
            fill={cfg.color}
            rx={0.6}
          />
        );
      })}

      <polyline
        points={geo.line}
        fill="none"
        stroke={C.planLine}
        strokeWidth={1}
        strokeLinejoin="round"
      />
    </svg>
  );
}
