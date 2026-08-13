import { C } from "../theme";
import type { DialData } from "../types";

interface DialProps extends DialData {
  color?: string;
  size?: number;
}

export function Dial({
  pct,
  label,
  act,
  plan,
  color = C.cyan,
  size = 62,
}: DialProps) {
  return (
    <div style={{ textAlign: "center", width: "100%" }}>
      <div
        style={{
          width: size,
          height: size,
          borderRadius: "50%",
          margin: "0 auto 4px",
          background: `conic-gradient(${color} ${pct}%, #2b3947 0)`,
          display: "grid",
          placeItems: "center",
        }}
      >
        <div
          style={{
            width: size - 18,
            height: size - 18,
            borderRadius: "50%",
            background: C.panel,
            display: "grid",
            placeItems: "center",
            fontSize: 13,
            fontWeight: 700,
            color: C.txt,
          }}
        >
          {pct}%
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: C.dim, marginBottom: 3 }}>{label}</div>
      <div className="num" style={{ fontSize: 14, fontWeight: 700, color: C.txt }}>
        {act}
        <span style={{ color: C.cyan }}>/{plan}</span>
      </div>
    </div>
  );
}
