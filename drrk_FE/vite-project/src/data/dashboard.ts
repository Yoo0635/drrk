import type {
  ArrivalForecast,
  CongestionItem,
  RailInfo,
  RouteTimeItem,
} from "../types/dashboard";

/** 실시간 혼잡도 */
export const CONGESTION: CongestionItem[] = [
  { id: "a", label: "A", status: "free" },
  { id: "b", label: "B", status: "free" },
  { id: "c", label: "C", status: "free" },
  { id: "common", label: "공통", status: "free" },
];

/** 경로별 예상 이동시간 (초) */
export const ROUTE_TIMES: RouteTimeItem[] = [
  { id: "a", label: "A", seconds: 422 }, // 7:02
  { id: "b", label: "B", seconds: 329 }, // 5:29
  { id: "c", label: "C", seconds: 347 }, // 5:47
];

/** 공항철도 */
export const RAIL: RailInfo = {
  nextDeparture: "14:32",
  boardable: true,
};

/** 입국 혼잡 예보 */
export const FORECAST: ArrivalForecast = {
  flightNo: "OZ542",
  eta: "14:42",
  impact: "low",
};
