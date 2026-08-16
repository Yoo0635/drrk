import type { CSSProperties, ReactNode } from "react";
import { C } from "../theme";

interface PanelProps {
  title?: ReactNode;
  sub?: ReactNode;
  children: ReactNode;
  style?: CSSProperties;
  bodyStyle?: CSSProperties;
}

export function Panel({ title, sub, children, style, bodyStyle }: PanelProps) {
  return (
    <section
      style={{
        background: C.panel,
        border: `1px solid ${C.line}`,
        borderRadius: 4,
        overflow: "hidden",
        ...style,
      }}
    >
      {title !== undefined && (
        <h2
          style={{
            background: C.head,
            fontSize: 12,
            fontWeight: 700,
            color: "#dcebf6",
            textAlign: "center",
            padding: "3px 7px",
            letterSpacing: "0.14em",
            borderBottom: `1px solid ${C.line}`,
          }}
        >
          {title}
          {sub !== undefined && (
            <span
              style={{
                letterSpacing: 0,
                fontWeight: 600,
                fontSize: 11.5,
                marginLeft: 10,
              }}
            >
              {sub}
            </span>
          )}
        </h2>
      )}
      <div style={{ padding: 6, ...bodyStyle }}>{children}</div>
    </section>
  );
}
