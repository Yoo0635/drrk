/** [실적, 계획] */
export type Pair = [actual: number, plan: number];

export interface StatBlock {
  cols: string[];
  total: Pair[];
  arr: Pair[];
  dep: Pair[];
}

export interface ChartCfg {
  /** y축 최대값 */
  max: number;
  color: string;
  /** 실적 — 막대 */
  act: number[];
  /** 계획 — 꺾은선 (24개) */
  plan: number[];
}

export interface DialData {
  label: string;
  pct: number;
  act: number;
  plan: number;
}

export interface ParkingRow {
  label: string;
  act: number;
  cap: number;
}

export interface ParkingGroup {
  title: string;
  rows: ParkingRow[];
}

export type RowKind = "total" | "arr" | "dep" | "trs";

export interface MiniRow<T extends string | number> {
  kind: RowKind;
  v: T[];
}

export type StandStatus =
  | "arrive"
  | "ready"
  | "boarding"
  | "boarded"
  | "wait"
  | "deicing";

export interface StandGroup {
  status: StandStatus;
  points: Array<[x: number, y: number]>;
}

export interface StatusLegend {
  status: StandStatus;
  label: string;
  n: number;
}
