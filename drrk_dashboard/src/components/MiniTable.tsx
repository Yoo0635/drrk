import type { ComponentType } from "react";
import { C } from "../theme";
import type { MiniRow, RowKind } from "../types";
import { ArrIcon, DepIcon, PlaneIcon, TrsIcon } from "./Icons";

const KIND_ICON: Record<RowKind, ComponentType<{ size?: number }>> = {
  total: PlaneIcon,
  arr: ArrIcon,
  dep: DepIcon,
  trs: TrsIcon,
};

const KIND_COLOR: Record<RowKind, string> = {
  total: C.txt,
  arr: C.green,
  dep: C.cyan,
  trs: "#c9b6e8",
};

interface MiniTableProps<T extends string | number> {
  cols: string[];
  rows: MiniRow<T>[];
  format?: (v: T) => string;
}

export function MiniTable<T extends string | number>({
  cols,
  rows,
  format,
}: MiniTableProps<T>) {
  const td = {
    padding: "4px 6px",
    border: `1px solid ${C.lineSoft}`,
    fontSize: 13,
    fontWeight: 700,
  } as const;

  return (
    <table
      className="num"
      style={{ width: "100%", borderCollapse: "collapse", textAlign: "right" }}
    >
      <thead>
        <tr>
          <th
            style={{
              ...td,
              background: C.cell,
              fontSize: 11,
              fontWeight: 600,
              width: 30,
            }}
          />
          {cols.map((c) => (
            <th
              key={c}
              style={{
                ...td,
                background: C.cell,
                fontSize: 11,
                fontWeight: 600,
                textAlign: "center",
                color: "#cfe0ee",
              }}
            >
              {c}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => {
          const Icon = KIND_ICON[r.kind];
          return (
            <tr key={i}>
              <td style={{ ...td, textAlign: "center", padding: 2 }}>
                <Icon size={18} />
              </td>
              {r.v.map((v, j) => (
                <td key={j} style={{ ...td, color: KIND_COLOR[r.kind] }}>
                  {format ? format(v) : v}
                </td>
              ))}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
