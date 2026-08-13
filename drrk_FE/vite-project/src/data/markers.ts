import type { MapMarkerData } from "../types/marker";

/**
 * 지도 위 마커 좌표.
 * x/y는 airport-map.png 기준 퍼센트 좌표다.
 */
export const MARKERS: MapMarkerData[] = [
  {
    id: "current",
    x: 60.0,
    y: 10.8,
    type: "current",
    label: "현재 위치",
    detail: "인천공항 T1 · 도착층",
  },
  {
    id: "moving-a",
    x: 23.5,
    y: 10.0,
    type: "movingwalk",
    status: "normal",
    detail: "A 무빙워크",
  },
  {
    id: "moving-b",
    x: 59.8,
    y: 18.0,
    type: "movingwalk",
    status: "normal",
    detail: "B 무빙워크",
  },
  {
    id: "moving-c",
    x: 88.0,
    y: 27.0,
    type: "movingwalk",
    status: "busy",
    detail: "C 무빙워크",
  },
  {
    id: "moving-common-free",
    x: 23.5,
    y: 40.5,
    type: "movingwalk",
    status: "free",
    detail: "공통 무빙워크",
  },
  {
    id: "moving-common-busy",
    x: 50.5,
    y: 40.5,
    type: "movingwalk",
    status: "busy",
    detail: "공통 무빙워크",
  },
  {
    id: "rail-express",
    x: 10.5,
    y: 95.0,
    type: "rail",
    label: "공항철도 직행",
    detail: "공항철도 직행",
  },
  {
    id: "rail-all-stop",
    x: 34.0,
    y: 96.0,
    type: "rail",
    label: "공항철도 일반",
    detail: "공항철도 일반",
  },
];
