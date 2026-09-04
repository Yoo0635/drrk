export interface CarrierCountEvent {
  n_carriers: number;
  score: number | null;
  level: string | null;
  serverNow: string;
}

export interface CarrierCountSnapshot {
  carrierCount: number;
  congestionScore: number | null;
  congestionLevel: string | null;
  messageId: string;
  receivedAt: Date;
}

export type CongestionDeliveryStatus = "LIVE" | "RECOVERED_LATE";

export interface CongestionDeliveryEvent {
  messageId: string;
  calculatedAt: string;
  score: number;
  level: string;
  deliveryStatus: CongestionDeliveryStatus;
  retryCount: number;
  serverNow: string;
}

export interface CongestionDeliverySnapshot {
  messageId: string;
  calculatedAt: Date;
  score: number;
  level: string;
  deliveryStatus: CongestionDeliveryStatus;
  retryCount: number;
  serverNow: Date;
}

export interface CongestionHistorySampleEvent {
  messageId: string;
  calculatedAt: string;
  score: number;
  level: string;
  deliveryStatus: CongestionDeliveryStatus;
  retryCount: number;
}

export interface CongestionHistoryEvent {
  serverNow: string;
  windowStart: string;
  samples: CongestionHistorySampleEvent[];
}

export interface CongestionHistorySnapshot {
  serverNow: Date;
  windowStart: Date;
  samples: CongestionDeliverySnapshot[];
}

export type CarrierCountConnectionStatus =
  | "connecting"
  | "open"
  | "reconnecting"
  | "unavailable";
