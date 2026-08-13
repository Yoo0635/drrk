import { useEffect, useMemo, useState } from "react";
import { C } from "../theme";

const SAMPLE_COUNT = 30;
const W = 720;
const H = 300;
const PAD = { l: 58, r: 18, t: 22, b: 32 };

export interface CongestionSample {
  value: 0 | 1;
  timestamp: number;
}

export type CongestionSubscribe = (
  onSample: (sample: CongestionSample) => void,
) => () => void;

interface RealtimeCongestionChartProps {
  subscribe?: CongestionSubscribe;
}

function initialSamples(): CongestionSample[] {
  const now = Date.now();
  let value: 0 | 1 = 0;

  return Array.from({ length: SAMPLE_COUNT }, (_, index) => {
    if (Math.random() > 0.72) value = value === 0 ? 1 : 0;
    return { value, timestamp: now - (SAMPLE_COUNT - index) * 1200 };
  });
}

function timeLabel(timestamp: number) {
  return new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(timestamp);
}

export function RealtimeCongestionChart({ subscribe }: RealtimeCongestionChartProps) {
  const [samples, setSamples] = useState<CongestionSample[]>(initialSamples);

  useEffect(() => {
    const receive = (sample: CongestionSample) => {
      if ((sample.value !== 0 && sample.value !== 1) || !Number.isFinite(sample.timestamp)) return;
      setSamples((current) => [...current, sample].slice(-SAMPLE_COUNT));
    };

    if (subscribe) return subscribe(receive);

    const timer = window.setInterval(() => {
      receive({ value: Math.random() > 0.64 ? 1 : 0, timestamp: Date.now() });
    }, 1200);
    return () => window.clearInterval(timer);
  }, [subscribe]);

  const current = samples.at(-1)!;
  const graph = useMemo(() => {
    const innerWidth = W - PAD.l - PAD.r;
    const innerHeight = H - PAD.t - PAD.b;
    const x = (index: number) => PAD.l + (innerWidth * index) / (SAMPLE_COUNT - 1);
    const y = (value: 0 | 1) => PAD.t + (1 - value) * innerHeight;
    const points = samples.map((sample, index) => `${x(index)},${y(sample.value)}`).join(" ");

    return {
      x,
      y,
      points,
      area: `${PAD.l},${PAD.t + innerHeight} ${points} ${W - PAD.r},${PAD.t + innerHeight}`,
    };
  }, [samples]);

  const congested = current.value === 1;
  const accent = congested ? C.red : C.green;

  return (
    <div style={{ height: "100%", minHeight: 0, display: "flex", flexDirection: "column" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, paddingBottom: 8 }}>
        <div>
          <div style={{ color: C.txt, fontWeight: 800, fontSize: 15 }}>실시간 혼잡도 측정</div>
          <div style={{ color: C.dim, fontSize: 11, marginTop: 2 }}>최근 30개 수신값 · 0 = 원활 / 1 = 혼잡</div>
        </div>
        <div style={{ textAlign: "right" }}>
          <b style={{ display: "inline-block", color: accent, border: `1px solid ${accent}`, borderRadius: 999, padding: "4px 9px", fontSize: 12 }}>
            {congested ? "혼잡" : "원활"} ({current.value})
          </b>
          <div className="num" style={{ color: C.dim, fontSize: 10, marginTop: 4 }}>갱신 {timeLabel(current.timestamp)}</div>
        </div>
      </div>

      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", height: "100%", minHeight: 0, flex: 1, display: "block" }} role="img" aria-label="실시간 혼잡도 측정 그래프">
        {[0, 1].map((value) => (
          <g key={value}>
            <line x1={PAD.l} x2={W - PAD.r} y1={graph.y(value as 0 | 1)} y2={graph.y(value as 0 | 1)} stroke={C.line} strokeWidth={1} />
            <text x={PAD.l - 10} y={graph.y(value as 0 | 1) + 4} textAnchor="end" fontSize={12} fill={value === 1 ? C.red : C.green} fontWeight={700}>
              {value === 1 ? "혼잡 (1)" : "원활 (0)"}
            </text>
          </g>
        ))}
        <polygon points={graph.area} fill={`${accent}18`} />
        <polyline points={graph.points} fill="none" stroke={accent} strokeWidth={3} strokeLinejoin="round" strokeLinecap="round" />
        {samples.map((sample, index) => (
          <circle key={sample.timestamp} cx={graph.x(index)} cy={graph.y(sample.value)} r={index === samples.length - 1 ? 5 : 2.5} fill={sample.value === 1 ? C.red : C.green} stroke={C.panel} strokeWidth={1.5} />
        ))}
        <text x={PAD.l} y={H - 9} fontSize={11} fill={C.dim}>30회 전</text>
        <text x={W - PAD.r} y={H - 9} textAnchor="end" fontSize={11} fill={C.dim}>현재</text>
      </svg>
    </div>
  );
}
