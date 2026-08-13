import { useMemo } from "react";
import {
  CARRIER_SAMPLE_COUNT,
  samplesToSpaceIdRows,
  type CongestionSample,
} from "../carrierSamples";
import { C } from "../theme";
import type { CarrierCountConnectionStatus } from "../types/inference";

const W = 720;
const H = 300;
const PAD = { l: 58, r: 18, t: 22, b: 32 };

interface RealtimeCongestionChartProps {
  samples: CongestionSample[];
  connectionStatus: CarrierCountConnectionStatus;
}

function timeLabel(timestamp: number) {
  return new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(timestamp);
}

function connectionLabel(status: CarrierCountConnectionStatus) {
  const labels: Record<CarrierCountConnectionStatus, string> = {
    connecting: "연결 중",
    open: "수신 중",
    reconnecting: "재연결 중",
    unavailable: "API 미설정",
  };
  return labels[status];
}

export function RealtimeCongestionChart({
  samples,
  connectionStatus,
}: RealtimeCongestionChartProps) {
  const current = samples.at(-1);
  const spaceIdRows = samplesToSpaceIdRows(samples);
  const graph = useMemo(() => {
    const innerWidth = W - PAD.l - PAD.r;
    const innerHeight = H - PAD.t - PAD.b;
    const x = (index: number) => PAD.l + (innerWidth * index) / (CARRIER_SAMPLE_COUNT - 1);
    const y = (value: 0 | 1) => PAD.t + (1 - value) * innerHeight;
    const points = samples.map((sample, index) => `${x(index)},${y(sample.value)}`).join(" ");

    return {
      x,
      y,
      points,
      area:
        points.length > 0
          ? `${PAD.l},${PAD.t + innerHeight} ${points} ${W - PAD.r},${PAD.t + innerHeight}`
          : "",
    };
  }, [samples]);

  const congested = current?.value === 1;
  const accent = congested ? C.red : C.green;

  return (
    <div style={{ height: "100%", minHeight: 0, display: "flex", flexDirection: "column" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, paddingBottom: 8 }}>
        <div>
          <div style={{ color: C.txt, fontWeight: 800, fontSize: 15 }}>실시간 혼잡도 측정</div>
          <div style={{ color: C.dim, fontSize: 11, marginTop: 2 }}>최근 30개 수신값 · 0 = 원활 / 1 = 혼잡</div>
          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              gap: "3px 4px",
              maxHeight: 32,
              overflow: "hidden",
              marginTop: 4,
            }}
            aria-label="최근 수신 space id"
          >
            {spaceIdRows.length === 0 ? (
              <span style={{ color: C.dim, fontSize: 10 }}>space_id 수신 대기</span>
            ) : (
              spaceIdRows.map((spaceId, index) => (
                <span
                  key={`${spaceId}-${samples[index].timestamp}`}
                  style={{
                    color: "#cfe0ee",
                    border: `1px solid ${C.lineSoft}`,
                    background: C.cell,
                    borderRadius: 3,
                    padding: "1px 4px",
                    fontSize: 9,
                    lineHeight: 1.3,
                  }}
                >
                  {spaceId}
                </span>
              ))
            )}
          </div>
        </div>
        <div style={{ textAlign: "right" }}>
          <b style={{ display: "inline-block", color: accent, border: `1px solid ${accent}`, borderRadius: 999, padding: "4px 9px", fontSize: 12 }}>
            {current ? `${congested ? "혼잡" : "원활"} (${current.value})` : connectionLabel(connectionStatus)}
          </b>
          <div className="num" style={{ color: C.dim, fontSize: 10, marginTop: 4 }}>
            {current ? `갱신 ${timeLabel(current.timestamp)}` : "SSE 대기"}
          </div>
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
        {graph.area.length > 0 && <polygon points={graph.area} fill={`${accent}18`} />}
        {graph.points.length > 0 && <polyline points={graph.points} fill="none" stroke={accent} strokeWidth={3} strokeLinejoin="round" strokeLinecap="round" />}
        {samples.map((sample, index) => (
          <circle key={`${sample.spaceId}-${sample.timestamp}`} cx={graph.x(index)} cy={graph.y(sample.value)} r={index === samples.length - 1 ? 5 : 2.5} fill={sample.value === 1 ? C.red : C.green} stroke={C.panel} strokeWidth={1.5} />
        ))}
        <text x={PAD.l} y={H - 9} fontSize={11} fill={C.dim}>30회 전</text>
        <text x={W - PAD.r} y={H - 9} textAnchor="end" fontSize={11} fill={C.dim}>현재</text>
      </svg>
    </div>
  );
}
