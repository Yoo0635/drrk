import type {
  CarrierCountEvent,
  CarrierCountSnapshot,
  CongestionDeliveryEvent,
  CongestionDeliverySnapshot,
} from "../types/inference";

const CARRIER_COUNT_STREAM_PATH = "/api/v1/inference/carriers/stream";

interface CarrierCountStreamOptions {
  baseUrl: string;
  EventSourceCtor?: typeof EventSource;
  now?: () => Date;
  onSnapshot: (snapshot: CarrierCountSnapshot) => void;
  onCongestionDelivery?: (snapshot: CongestionDeliverySnapshot) => void;
  onOpen?: () => void;
  onError?: () => void;
}

interface CarrierCountStream {
  close: () => void;
}

export function createCarrierCountStream({
  baseUrl,
  EventSourceCtor = EventSource,
  now = () => new Date(),
  onSnapshot,
  onCongestionDelivery,
  onOpen,
  onError,
}: CarrierCountStreamOptions): CarrierCountStream | null {
  const streamUrl = buildCarrierCountStreamUrl(baseUrl);
  if (streamUrl === null) {
    return null;
  }

  const eventSource = new EventSourceCtor(streamUrl);
  eventSource.onopen = () => onOpen?.();
  eventSource.onerror = () => onError?.();
  eventSource.addEventListener("carrier-count", (event) => {
    const payload = parseCarrierCountEvent(event.data);
    if (payload === null) {
      return;
    }

    onSnapshot({
      carrierCount: payload.n_carriers,
      congestionScore: payload.score,
      congestionLevel: payload.level,
      messageId: event.lastEventId,
      receivedAt: now(),
    });
  });
  eventSource.addEventListener("congestion-delivery", (event) => {
    const payload = parseCongestionDeliveryEvent(event.data);
    if (payload === null) {
      return;
    }

    onCongestionDelivery?.({
      messageId: payload.messageId,
      calculatedAt: new Date(payload.calculatedAt),
      score: payload.score,
      level: payload.level,
      deliveryStatus: payload.deliveryStatus,
      retryCount: payload.retryCount,
    });
  });

  return {
    close: () => eventSource.close(),
  };
}

function parseCongestionDeliveryEvent(data: string): CongestionDeliveryEvent | null {
  try {
    const value: unknown = JSON.parse(data);
    return isCongestionDeliveryEvent(value) ? value : null;
  } catch {
    return null;
  }
}

function buildCarrierCountStreamUrl(baseUrl: string): string | null {
  const trimmedBaseUrl = baseUrl.trim();
  if (trimmedBaseUrl.length === 0) {
    return null;
  }

  return `${trimmedBaseUrl.replace(/\/+$/, "")}${CARRIER_COUNT_STREAM_PATH}`;
}

function parseCarrierCountEvent(data: string): CarrierCountEvent | null {
  try {
    const value: unknown = JSON.parse(data);
    if (!isCarrierCountEvent(value)) {
      return null;
    }
    return value;
  } catch {
    return null;
  }
}

function isCarrierCountEvent(value: unknown): value is CarrierCountEvent {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  const carrierCount = candidate.n_carriers;
  const score = candidate.score;
  const level = candidate.level;
  return (
    typeof carrierCount === "number" &&
    Number.isInteger(carrierCount) &&
    carrierCount >= 0 &&
    ((score === null && level === null) ||
      (typeof score === "number" &&
        Number.isFinite(score) &&
        score >= 0 &&
        score <= 1 &&
        typeof level === "string" &&
        level.trim().length > 0))
  );
}

function isCongestionDeliveryEvent(value: unknown): value is CongestionDeliveryEvent {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  const status = candidate.deliveryStatus;
  return (
    typeof candidate.messageId === "string" &&
    candidate.messageId.trim().length > 0 &&
    typeof candidate.calculatedAt === "string" &&
    Number.isFinite(Date.parse(candidate.calculatedAt)) &&
    typeof candidate.score === "number" &&
    Number.isFinite(candidate.score) &&
    candidate.score >= 0 &&
    candidate.score <= 1 &&
    typeof candidate.level === "string" &&
    candidate.level.trim().length > 0 &&
    (status === "LIVE" || status === "RECOVERED_LATE") &&
    typeof candidate.retryCount === "number" &&
    Number.isInteger(candidate.retryCount) &&
    candidate.retryCount >= 0
  );
}
