import { C } from "../theme";
import type { Pair, StatBlock } from "../types";

function PairCell({ value }: { value: Pair }) {
  return (
    <>
      {value[0]}
      <span style={{ color: C.cyan }}>/{value[1]}</span>
    </>
  );
}

export function StatTable({ cols, total, arr, dep }: StatBlock) {
  const cell = {
    padding: "5px 2px",
    border: `1px solid ${C.lineSoft}`,
    fontWeight: 700,
  } as const;

  return (
    <table
      className="num"
      style={{ width: "100%", borderCollapse: "collapse", textAlign: "center" }}
    >
      <thead>
        <tr>
          {cols.map((c) => (
            <th
              key={c}
              style={{
                background: C.cell,
                color: "#cfe0ee",
                fontSize: 11.5,
                fontWeight: 600,
                padding: "4px 2px",
                border: `1px solid ${C.lineSoft}`,
              }}
            >
              {c}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        <tr>
          {total.map((v, i) => (
            <td key={i} style={{ ...cell, fontSize: 21, color: C.txt }}>
              <PairCell value={v} />
            </td>
          ))}
        </tr>
        {[arr, dep].map((row, r) => (
          <tr key={r}>
            {row.map((v, i) => (
              <td key={i} style={{ ...cell, fontSize: 13.5, color: C.green }}>
                <PairCell value={v} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
