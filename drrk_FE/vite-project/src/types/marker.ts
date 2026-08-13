export type MarkerType = "current" | "movingwalk" | "rail";

/** 혼잡도 3단계 */
export type MarkerStatus = "free" | "normal" | "busy";

export interface MapMarkerData {
  id: string;
  /** 이미지 왼쪽 끝 기준 가로 위치 (%) */
  x: number;
  /** 이미지 위쪽 끝 기준 세로 위치 (%) */
  y: number;
  type: MarkerType;
  /** 무빙워크는 생략 — 혼잡도가 라벨로 표시됨 */
  label?: string;
  status?: MarkerStatus;
  /** 하단 패널에 표시할 값 */
  detail?: string;
}

export const STATUS_LABEL: Record<MarkerStatus, string> = {
  free: "여유",
  normal: "원활",
  busy: "혼잡",
};

/** label이 있으면 그대로, 없으면 혼잡도를 라벨로 사용 */
export function getMarkerLabel(marker: MapMarkerData): string {
  return marker.label ?? STATUS_LABEL[marker.status ?? "normal"];
}
