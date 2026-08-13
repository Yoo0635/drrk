export interface CarrierCountEvent {
  space_id: string;
  n_carriers: number;
}

export interface CarrierCountSnapshot {
  spaceId: string;
  carrierCount: number;
  messageId: string;
  receivedAt: Date;
}

export type CarrierCountConnectionStatus =
  | "connecting"
  | "open"
  | "reconnecting"
  | "unavailable";
