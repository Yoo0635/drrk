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

/**
 * 혼잡도 라벨
 * '원활'을 '보통'으로 바꿨다. 여유·원활은 의미가 겹쳐 3단계 구분이
 * 흐려지고, 색(초록–노랑–빨강)과도 대응이 어긋나기 때문이다.
 */
export const STATUS_LABEL: Record<MarkerStatus, string> = {
  free: "여유",
  normal: "보통",
  busy: "혼잡",
};

/** label이 있으면 그대로, 없으면 혼잡도를 라벨로 사용 */
export function getMarkerLabel(marker: MapMarkerData): string {
  return marker.label ?? STATUS_LABEL[marker.status ?? "normal"];
}
