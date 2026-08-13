import type { MapMarkerData } from "../types/marker";

export const MARKERS: MapMarkerData[] = [
  { id: "current", type: "current", x: 62, y: 8.5, label: "현재 위치" },

  // 무빙워크 — 혼잡도
  {
    id: "walk-1",
    type: "movingwalk",
    x: 25,
    y: 37,
    status: "free",
    detail: "캐리어 21대/10분",
  },
  {
    id: "walk-2",
    type: "movingwalk",
    x: 53,
    y: 37,
    status: "busy",
    detail: "캐리어 132대/10분",
  },
  {
    id: "walk-3",
    type: "movingwalk",
    x: 91,
    y: 26,
    status: "busy",
    detail: "캐리어 132대/10분",
  },
  {
    id: "walk-4",
    type: "movingwalk",
    x: 63,
    y: 15,
    status: "normal",
    detail: "캐리어 48대/10분",
  },
  {
    id: "walk-5",
    type: "movingwalk",
    x: 25,
    y: 7,
    status: "normal",
    detail: "캐리어 48대/10분",
  },

  // 공항철도
  {
    id: "rail-express",
    label: "공항철도 직행",
    type: "rail",
    x: 13,
    y: 95,
    detail: "다음 열차 19:20",
  },
  {
    id: "rail-local",
    label: "공항철도 일반",
    type: "rail",
    x: 37,
    y: 95,
    detail: "다음 열차 19:20",
  },
];
