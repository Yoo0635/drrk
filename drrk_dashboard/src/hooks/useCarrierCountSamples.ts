import { useEffect, useState } from "react";
import { createCarrierCountStream } from "../api/carrierCountStream";
import {
  carrierSnapshotToSample,
  pushCarrierSample,
  type CongestionSample,
} from "../carrierSamples";
import type { CarrierCountConnectionStatus } from "../types/inference";

interface UseCarrierCountSamplesOptions {
  baseUrl?: string;
  EventSourceCtor?: typeof EventSource;
  now?: () => Date;
}

interface UseCarrierCountSamplesResult {
  samples: CongestionSample[];
  connectionStatus: CarrierCountConnectionStatus;
}

export function useCarrierCountSamples({
  baseUrl = import.meta.env.VITE_API_BASE_URL ?? "",
  EventSourceCtor,
  now,
}: UseCarrierCountSamplesOptions = {}): UseCarrierCountSamplesResult {
  const [samples, setSamples] = useState<CongestionSample[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<CarrierCountConnectionStatus>("connecting");
  const apiBaseUrlConfigured = baseUrl.trim().length > 0;

  useEffect(() => {
    const stream = createCarrierCountStream({
      baseUrl,
      EventSourceCtor,
      now,
      onOpen: () => setConnectionStatus("open"),
      onError: () => setConnectionStatus("reconnecting"),
      onSnapshot: (snapshot) => {
        setSamples((current) => pushCarrierSample(current, carrierSnapshotToSample(snapshot)));
      },
    });

    if (stream === null) {
      return undefined;
    }

    return () => stream.close();
  }, [baseUrl, EventSourceCtor, now]);

  return {
    samples,
    connectionStatus: apiBaseUrlConfigured ? connectionStatus : "unavailable",
  };
}
