export type RouteId = "a" | "b" | "c";

export interface RoutePoint {
  /** 지도 이미지 왼쪽 기준 위치 (%) */
  x: number;
  /** 지도 이미지 위쪽 기준 위치 (%) */
  y: number;
}

export interface RouteData {
  id: RouteId;
  label: string;
  color: string;
  /** 경로선 두께(px) */
  width?: number;
  opacity?: number;
  points: RoutePoint[];
}
