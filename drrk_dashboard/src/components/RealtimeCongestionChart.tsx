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
/** 레벨 라벨끼리 최소로 떨어져야 하는 가로 간격(px). 이보다 가까우면 라벨을 생략한다. */
const LABEL_MIN_GAP = 38;

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

const LEVEL_SEVERITY: Record<string, number> = { FULL: 3, HIGH: 2, MEDIUM: 1, LOW: 0 };

function severityOf(level: string) {
  return LEVEL_SEVERITY[level] ?? 0;
}

/**
 * 라벨을 그릴 인덱스를 고른다.
 *
 * <p>30개를 모두 그리면 글씨가 겹치므로 레벨이 바뀌는 지점과 현재값만 후보로 두고,
 * 후보끼리 {@link LABEL_MIN_GAP}보다 가까우면 <b>더 심각한 레벨</b>을 남긴다.
 * 되돌아온 "원활"보다 잠깐 치솟은 "혼잡"이 운영자에게 중요한 정보이기 때문이다.
 * 현재값은 언제나 남긴다.</p>
 */
function pickLabeledIndices(
  samples: ScoreSample[],
  xAt: (index: number) => number,
): number[] {
  if (samples.length === 0) {
    return [];
  }

  const lastIndex = samples.length - 1;
  const candidates = samples.reduce<number[]>((acc, sample, index) => {
    const levelChanged = index > 0 && samples[index - 1].level !== sample.level;
    if (index === 0 || levelChanged || index === lastIndex) {
      acc.push(index);
    }
    return acc;
  }, []);

  const kept = [lastIndex];
  const ranked = candidates
    .filter((index) => index !== lastIndex)
    .sort((a, b) => {
      const bySeverity = severityOf(samples[b].level) - severityOf(samples[a].level);
      return bySeverity !== 0 ? bySeverity : b - a;
    });

  ranked.forEach((index) => {
    const fits = kept.every((other) => Math.abs(xAt(other) - xAt(index)) >= LABEL_MIN_GAP);
    if (fits) {
      kept.push(index);
    }
  });

  return kept.sort((a, b) => a - b);
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
      /**
       * 라벨을 그릴 인덱스. 30개를 모두 그리면 글씨가 겹치므로
       * 레벨이 바뀌는 지점과 현재값만 남기고, 그마저도 너무 붙으면 생략한다.
       */
      labeledIndices: pickLabeledIndices(scoreSamples, (index) => xAt(index, rightStart)),
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
          const isLast = index === scoreSamples.length - 1;
          const showLabel = graph.labeledIndices.includes(index);
          // 아래쪽 점은 라벨을 위로 올려 x축 눈금(30회 전 / 현재)과 겹치지 않게 한다.
          const nearBottom = y > PAD.t + graph.innerHeight - 30;
          const labelY = nearBottom ? y - 11 : y + 16;
          // 양끝 라벨이 차트 밖으로 잘리지 않도록 기준점을 안쪽으로 붙인다.
          const labelAnchor =
            x > graph.rightEnd - 18 ? "end" : x < graph.rightStart + 18 ? "start" : "middle";

          return (
            <g key={`score-${sample.timestamp}-${index}`}>
              <circle
                cx={x}
                cy={y}
                r={isLast ? 5 : 3}
                fill={color}
                stroke={C.panel}
                strokeWidth={1.5}
              />
              {showLabel && (
                <text
                  x={x}
                  y={labelY}
                  textAnchor={labelAnchor}
                  fontSize={isLast ? 11 : 10}
                  fill={color}
                  fontWeight={700}
                  // 꺾은선 위에 라벨이 올라가도 읽히도록 배경색 외곽선을 두른다.
                  stroke={C.panel}
                  strokeWidth={3}
                  paintOrder="stroke"
                  strokeLinejoin="round"
                >
                  {levelLabel(sample.level)}
                </text>
              )}
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
