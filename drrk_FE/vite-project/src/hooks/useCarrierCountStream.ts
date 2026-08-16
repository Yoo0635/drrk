import { useEffect, useState } from "react";
import { createCarrierCountStream } from "../api/carrierCountStream";
import type {
  CarrierCountConnectionStatus,
  CarrierCountSnapshot,
  CarrierCountSnapshotsBySpace,
} from "../types/inference";

interface UseCarrierCountStreamOptions {
  baseUrl?: string;
  EventSourceCtor?: typeof EventSource;
  now?: () => Date;
}

interface UseCarrierCountStreamResult {
  snapshotsBySpace: CarrierCountSnapshotsBySpace;
  connectionStatus: CarrierCountConnectionStatus;
  lastReceivedAt: Date | null;
}

export function useCarrierCountStream({
  baseUrl = import.meta.env.VITE_API_BASE_URL ?? "",
  EventSourceCtor,
  now,
}: UseCarrierCountStreamOptions = {}): UseCarrierCountStreamResult {
  const [snapshotsBySpace, setSnapshotsBySpace] = useState<CarrierCountSnapshotsBySpace>({});
  const [connectionStatus, setConnectionStatus] =
    useState<CarrierCountConnectionStatus>("connecting");
  const [lastReceivedAt, setLastReceivedAt] = useState<Date | null>(null);
  const apiBaseUrlConfigured = baseUrl.trim().length > 0;

  useEffect(() => {
    const stream = createCarrierCountStream({
      baseUrl,
      EventSourceCtor,
      now,
      onOpen: () => setConnectionStatus("open"),
      onError: () => setConnectionStatus("reconnecting"),
      onSnapshot: (snapshot: CarrierCountSnapshot) => {
        setSnapshotsBySpace((current) => ({
          ...current,
          [snapshot.spaceId]: snapshot,
        }));
        setLastReceivedAt(snapshot.receivedAt);
      },
    });

    if (stream === null) {
      return undefined;
    }

    return () => stream.close();
  }, [baseUrl, EventSourceCtor, now]);

  return {
    snapshotsBySpace,
    connectionStatus: apiBaseUrlConfigured ? connectionStatus : "unavailable",
    lastReceivedAt,
  };
}
