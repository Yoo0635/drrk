export interface CarrierCountEvent {
  n_carriers: number;
  score: number | null;
  level: string | null;
}

export interface CarrierCountSnapshot {
  carrierCount: number;
  congestionScore: number | null;
  congestionLevel: string | null;
  messageId: string;
  receivedAt: Date;
}

export type CarrierCountConnectionStatus =
  | "connecting"
  | "open"
  | "reconnecting"
  | "unavailable";
