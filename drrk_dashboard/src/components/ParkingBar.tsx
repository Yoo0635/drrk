import { C } from "../theme";
import type { ParkingRow } from "../types";

export function ParkingBar({ label, act, cap }: ParkingRow) {
  const pct = Math.min(100, Math.round((act / cap) * 100));

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "34px 1fr",
        gap: 6,
        alignItems: "center",
        marginBottom: 6,
      }}
    >
      <span style={{ fontSize: 11.5, color: "#cfe0ee", textAlign: "center" }}>
        {label}
      </span>
      <div
        style={{
          position: "relative",
          height: 22,
          borderRadius: 2,
          background: "#1b2733",
          border: `1px solid ${C.lineSoft}`,
          overflow: "hidden",
        }}
        role="meter"
        aria-valuenow={act}
        aria-valuemax={cap}
        aria-label={`${label} 주차 ${act} / ${cap}`}
      >
        <div
          style={{
            position: "absolute",
            inset: "0 auto 0 0",
            width: `${pct}%`,
            background: "linear-gradient(180deg,#41e07a,#22b558)",
          }}
        />
        <b
          className="num"
          style={{
            position: "absolute",
            inset: 0,
            display: "grid",
            placeItems: "center",
            fontSize: 12,
            fontWeight: 800,
            color: "#06210f",
          }}
        >
          {act.toLocaleString()}/{cap.toLocaleString()}
        </b>
      </div>
    </div>
  );
}
