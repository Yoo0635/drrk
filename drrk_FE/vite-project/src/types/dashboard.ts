import type { MarkerStatus } from "./marker";

/** 실시간 혼잡도 — 구간별 */
export interface CongestionItem {
  id: string;
  label: string;
  status: MarkerStatus;
}

/** 경로별 예상 이동시간 */
export interface RouteTimeItem {
  id: string;
  label: string;
  /** 초 단위로 저장하고 표시할 때 m:ss로 변환 */
  seconds: number;
}

/** 공항철도 운행 정보 */
export interface RailInfo {
  /** 다음 열차 출발시각 "HH:MM" */
  nextDeparture: string;
  /** 지금 출발해서 탑승 가능한지 */
  boardable: boolean;
}

/** 입국 혼잡 예보 */
export type ImpactLevel = "low" | "medium" | "high";

export interface ArrivalForecast {
  flightNo: string;
  /** 도착 예정시각 "HH:MM" */
  eta: string;
  impact: ImpactLevel;
}

export const IMPACT_LABEL: Record<ImpactLevel, string> = {
  low: "혼잡 영향 낮음",
  medium: "혼잡 영향 보통",
  high: "혼잡 영향 높음",
};

/** 초 → "7:02" */
export function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}
