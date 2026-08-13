import { useMemo } from "react";
import {
  CARRIER_SAMPLE_COUNT,
  type CongestionSample,
  type ScoreSample,
} from "../carrierSamples";
import { C } from "../theme";
import type { CarrierCountConnectionStatus } from "../types/inference";

const W = 720;
const H = 300;
const PAD = { l: 58, r: 18, t: 22, b: 32 };
const GAP = 26;

interface RealtimeCongestionChartProps {
  carrierSamples: CongestionSample[];
  scoreSamples: ScoreSample[];
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

function levelColor(level: string) {
  switch (level) {
    case "FULL":
      return C.red;
    case "HIGH":
      return C.orange;
    case "MEDIUM":
      return C.yellow;
    case "LOW":
      return C.green;
    default:
      return C.dim;
  }
}

function levelLabel(level: string) {
  switch (level) {
    case "FULL":
      return "포화";
    case "HIGH":
      return "혼잡";
    case "MEDIUM":
      return "보통";
    case "LOW":
      return "원활";
    default:
      return level;
  }
}

export function RealtimeCongestionChart({
  carrierSamples,
  scoreSamples,
  connectionStatus,
}: RealtimeCongestionChartProps) {
  const currentCarrier = carrierSamples.at(-1);
  const currentScore = scoreSamples.at(-1);
  const leftAccent = currentCarrier?.value === 1 ? C.red : C.green;
  const rightAccent = currentScore ? levelColor(currentScore.level) : C.blue;

  const graph = useMemo(() => {
    const innerWidth = W - PAD.l - PAD.r;
    const innerHeight = H - PAD.t - PAD.b;
    const halfWidth = (innerWidth - GAP) / 2;
    const leftStart = PAD.l;
    const rightStart = PAD.l + halfWidth + GAP;
    const leftEnd = leftStart + halfWidth;
    const rightEnd = rightStart + halfWidth;

    const xAt = (index: number, start: number) =>
      start + (halfWidth * index) / (CARRIER_SAMPLE_COUNT - 1);
    const carrierY = (value: 0 | 1) => PAD.t + (1 - value) * innerHeight;
    const scoreY = (score: number) => PAD.t + (1 - score) * innerHeight;

    const carrierPoints = carrierSamples
      .map((sample, index) => `${xAt(index, leftStart)},${carrierY(sample.value)}`)
      .join(" ");
    const scorePoints = scoreSamples
      .map((sample, index) => `${xAt(index, rightStart)},${scoreY(sample.score)}`)
      .join(" ");

    return {
      innerHeight,
      leftStart,
      leftEnd,
      rightStart,
      rightEnd,
      carrierY,
      scoreY,
      carrierPoints,
      scorePoints,
      carrierArea:
        carrierPoints.length > 0
          ? `${leftStart},${PAD.t + innerHeight} ${carrierPoints} ${leftEnd},${PAD.t + innerHeight}`
          : "",
      scoreArea:
        scorePoints.length > 0
          ? `${rightStart},${PAD.t + innerHeight} ${scorePoints} ${rightEnd},${PAD.t + innerHeight}`
          : "",
      xAt,
    };
  }, [carrierSamples, scoreSamples]);

  return (
    <div style={{ height: "100%", minHeight: 0, display: "flex", flexDirection: "column" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, paddingBottom: 8 }}>
        <div>
          <div style={{ color: C.txt, fontWeight: 800, fontSize: 15 }}>실시간 혼잡도 측정</div>
          <div style={{ color: C.dim, fontSize: 11, marginTop: 2 }}>
            최근 30개 수신값 · 좌측 0/1 감지, 우측 score/level 추이
          </div>
        </div>
        <div style={{ textAlign: "right" }}>
          <b
            style={{
              display: "inline-block",
              color: leftAccent,
              border: `1px solid ${leftAccent}`,
              borderRadius: 999,
              padding: "4px 9px",
              fontSize: 12,
            }}
          >
            {currentCarrier
              ? `${currentCarrier.value === 1 ? "혼잡" : "원활"} (${currentCarrier.value})`
              : connectionLabel(connectionStatus)}
          </b>
          <div className="num" style={{ color: C.dim, fontSize: 10, marginTop: 4 }}>
            {currentCarrier ? `갱신 ${timeLabel(currentCarrier.timestamp)}` : "SSE 대기"}
          </div>
        </div>
      </div>

      <svg
        viewBox={`0 0 ${W} ${H}`}
        style={{ width: "100%", height: "100%", minHeight: 0, flex: 1, display: "block" }}
        role="img"
        aria-label="실시간 혼잡도 측정 그래프"
      >
        <text x={graph.leftStart} y={13} fontSize={11} fill={C.dim}>
          감지 혼잡도
        </text>
        <text x={graph.rightStart} y={13} fontSize={11} fill={C.dim}>
          종합 혼잡도
        </text>
        <line
          x1={graph.leftEnd + GAP / 2}
          x2={graph.leftEnd + GAP / 2}
          y1={PAD.t - 6}
          y2={H - PAD.b + 8}
          stroke={C.lineSoft}
          strokeWidth={1}
        />

        {[0, 1].map((value) => (
          <g key={value}>
            <line
              x1={graph.leftStart}
              x2={graph.leftEnd}
              y1={graph.carrierY(value as 0 | 1)}
              y2={graph.carrierY(value as 0 | 1)}
              stroke={C.line}
              strokeWidth={1}
            />
            <text
              x={graph.leftStart - 10}
              y={graph.carrierY(value as 0 | 1) + 4}
              textAnchor="end"
              fontSize={12}
              fill={value === 1 ? C.red : C.green}
              fontWeight={700}
            >
              {value === 1 ? "혼잡 (1)" : "원활 (0)"}
            </text>
          </g>
        ))}

        {[0, 0.5, 1].map((value) => (
          <g key={value}>
            <line
              x1={graph.rightStart}
              x2={graph.rightEnd}
              y1={graph.scoreY(value)}
              y2={graph.scoreY(value)}
              stroke={C.line}
              strokeWidth={1}
              strokeDasharray={value === 0.5 ? "4 4" : undefined}
            />
          </g>
        ))}
        <text x={graph.rightStart - 10} y={graph.scoreY(1) + 4} textAnchor="end" fontSize={12} fill={C.red} fontWeight={700}>
          1.0
        </text>
        <text x={graph.rightStart - 10} y={graph.scoreY(0.5) + 4} textAnchor="end" fontSize={11} fill={C.dim}>
          0.5
        </text>
        <text x={graph.rightStart - 10} y={graph.scoreY(0) + 4} textAnchor="end" fontSize={12} fill={C.green} fontWeight={700}>
          0.0
        </text>

        {graph.carrierArea.length > 0 && (
          <polygon points={graph.carrierArea} fill={`${leftAccent}18`} />
        )}
        {graph.scoreArea.length > 0 && (
          <polygon points={graph.scoreArea} fill={`${rightAccent}16`} />
        )}

        {graph.carrierPoints.length > 0 && (
          <polyline
            points={graph.carrierPoints}
            fill="none"
            stroke={leftAccent}
            strokeWidth={3}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        )}
        {graph.scorePoints.length > 0 && (
          <polyline
            points={graph.scorePoints}
            fill="none"
            stroke={rightAccent}
            strokeWidth={3}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        )}

        {carrierSamples.map((sample, index) => (
          <circle
            key={`carrier-${sample.timestamp}-${index}`}
            cx={graph.xAt(index, graph.leftStart)}
            cy={graph.carrierY(sample.value)}
            r={index === carrierSamples.length - 1 ? 5 : 2.5}
            fill={sample.value === 1 ? C.red : C.green}
            stroke={C.panel}
            strokeWidth={1.5}
          />
        ))}

        {scoreSamples.map((sample, index) => {
          const x = graph.xAt(index, graph.rightStart);
          const y = graph.scoreY(sample.score);
          const color = levelColor(sample.level);

          return (
            <g key={`score-${sample.timestamp}-${index}`}>
              <circle
                cx={x}
                cy={y}
                r={index === scoreSamples.length - 1 ? 5 : 3}
                fill={color}
                stroke={C.panel}
                strokeWidth={1.5}
              />
              <text
                x={x}
                y={Math.min(H - 18, y + 15)}
                textAnchor="middle"
                fontSize={9}
                fill={color}
                fontWeight={700}
              >
                {levelLabel(sample.level)}
              </text>
            </g>
          );
        })}

        <text x={graph.leftStart} y={H - 9} fontSize={11} fill={C.dim}>
          30회 전
        </text>
        <text x={graph.leftEnd} y={H - 9} textAnchor="end" fontSize={11} fill={C.dim}>
          현재
        </text>
        <text x={graph.rightStart} y={H - 9} fontSize={11} fill={C.dim}>
          30회 전
        </text>
        <text x={graph.rightEnd} y={H - 9} textAnchor="end" fontSize={11} fill={C.dim}>
          현재
        </text>
      </svg>
    </div>
  );
}
