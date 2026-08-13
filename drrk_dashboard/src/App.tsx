import type { ReactNode } from "react";
import { C } from "./theme";
import {
  BAGGAGE,
  CARGO,
  CHARTS,
  DATE,
  DELAY,
  DELAY_RATE,
  OPS,
  PARKING,
  PAX,
  RUNWAY,
  STANDS,
  STATUS_COLOR,
  STATUS_LEGEND,
  TRANSFER,
  WEATHER,
} from "./data/dashboard";
import { Chart } from "./components/Chart";
import { Dial } from "./components/Dial";
import { ArrIcon, DepIcon, PlaneIcon } from "./components/Icons";
import { MiniTable } from "./components/MiniTable";
import { Panel } from "./components/Panel";
import { ParkingBar } from "./components/ParkingBar";
import { RealtimeCongestionChart } from "./components/RealtimeCongestionChart";
import { useCarrierCountSamples } from "./hooks/useCarrierCountSamples";
import { StatTable } from "./components/StatTable";
import type { ChartCfg } from "./types";

const gaugeRow = {
  display: "flex",
  justifyContent: "space-around",
  alignItems: "flex-start",
  padding: 0,
} as const;

function Plan({ children }: { children: ReactNode }) {
  return <span style={{ color: C.cyan }}>{children}</span>;
}

function ChartRow({ icon, cfg }: { icon: "arr" | "plane"; cfg: ChartCfg }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 6,
        padding: 0,
      }}
    >
      <span
        style={{
          width: 34,
          flex: "0 0 34px",
          display: "flex",
          justifyContent: "center",
        }}
      >
        {icon === "arr" ? <ArrIcon /> : <PlaneIcon />}
      </span>
      <div style={{ flex: 1 }}>
        <Chart cfg={cfg} height={46} />
      </div>
    </div>
  );
}

export default function App() {
  const { samples: carrierSamples, connectionStatus } = useCarrierCountSamples();

  return (
    <div
      style={{
        background: C.bg,
        color: C.txt,
        fontSize: 13,
        padding: 8,
        minHeight: "100%",
      }}
      className="dashboard"
    >
      {/* ---------- top bar ---------- */}
      <header
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 20,
          padding: "0 2px 6px",
          flexWrap: "wrap",
        }}
      >
        <div
          style={{
            background: "linear-gradient(180deg,#eef5fa,#c9d9e6)",
            color: "#0d1620",
            fontWeight: 800,
            fontSize: 17,
            padding: "5px 14px",
            borderRadius: 3,
          }}
        >
          <span style={{ letterSpacing: "0.06em" }}>AOS</span> (공항운영
          대시보드)
        </div>
        <div
          style={{
            fontFamily: 'Georgia,"Times New Roman",serif',
            fontSize: 16,
            fontWeight: 700,
            textAlign: "right",
          }}
        >
          Beyond an Airport, Changing the World
          <span style={{ marginLeft: 16 }}>{DATE}</span>
        </div>
      </header>

      <div className="grid">
        {/* ================= LEFT ================= */}
        <div className="col">
          <Panel title="운 항" sub="(실적/계획)">
            <StatTable {...OPS} />
          </Panel>

          <Panel
            title="시간대별 운항"
            sub={
              <>
                <b style={{ color: C.txt }}>22편</b> / <Plan>12편</Plan>
              </>
            }
          >
            <Chart cfg={CHARTS.opsArr} height={58} />
            <Chart cfg={CHARTS.opsDep} height={58} />
          </Panel>

          <div className="two">
            <Panel title="총 운행">
              <div style={gaugeRow}>
                {DELAY_RATE.map((d) => (
                  <Dial key={d.label} {...d} />
                ))}
              </div>
            </Panel>

            <Panel title="지연 / 결항">
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "34px 1fr 1fr",
                  fontSize: 11.5,
                  color: "#cfe0ee",
                  textAlign: "center",
                  background: C.cell,
                  border: `1px solid ${C.lineSoft}`,
                  padding: "3px 0",
                }}
              >
                <span />
                <span>지연</span>
                <span>결항</span>
              </div>
              {DELAY.map((d, i) => (
                <div
                  key={i}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "34px 1fr 1fr",
                    alignItems: "center",
                    border: `1px solid ${C.lineSoft}`,
                    borderTop: 0,
                    padding: "3px 0",
                  }}
                >
                  <span style={{ display: "flex", justifyContent: "center" }}>
                    {i === 0 ? <ArrIcon size={20} /> : <DepIcon size={20} />}
                  </span>
                  <span
                    className="num"
                    style={{
                      textAlign: "center",
                      fontSize: 19,
                      fontWeight: 800,
                      color: C.yellow,
                    }}
                  >
                    {d.delay}
                  </span>
                  <span
                    className="num"
                    style={{
                      textAlign: "center",
                      fontSize: 19,
                      fontWeight: 800,
                      color: C.red,
                    }}
                  >
                    {d.cancel}
                  </span>
                </div>
              ))}
            </Panel>
          </div>

          <Panel
            title="8월 14일 운행편"
            sub={
              <>
                <b style={{ color: C.txt }}>{STANDS.used} 대</b> /{" "}
                <Plan>{STANDS.total} 대</Plan>
              </>
            }
          >
            <div style={gaugeRow}>
              {STANDS.items.map((s) => (
                <Dial key={s.label} {...s} color={C.blue} size={54} />
              ))}
            </div>
          </Panel>

          <Panel title="활주로" sub="( 1시간 )">
            <table
              className="num"
              style={{
                width: "100%",
                borderCollapse: "collapse",
                textAlign: "center",
              }}
            >
              <thead>
                <tr>
                  {RUNWAY.cols.map((c) => (
                    <th
                      key={c}
                      style={{
                        background: C.cell,
                        fontSize: 10.5,
                        fontWeight: 600,
                        color: "#cfe0ee",
                        padding: "2px 1px",
                        border: `1px solid ${C.lineSoft}`,
                      }}
                    >
                      {c}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  { row: RUNWAY.arr, color: C.green },
                  { row: RUNWAY.dep, color: C.cyan },
                ].map(({ row, color }, r) => (
                  <tr key={r}>
                    {row.map((v, i) => (
                      <td
                        key={i}
                        style={{
                          padding: "2px 1px",
                          border: `1px solid ${C.lineSoft}`,
                          fontSize: 19,
                          fontWeight: 800,
                          color:
                            v === 0
                              ? "#4c5f70"
                              : r === 0 && i === 4
                                ? C.orange
                                : color,
                        }}
                      >
                        {v}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        </div>

        {/* ================= CENTER ================= */}
        <div className="col col--map">
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 260px",
              gap: 10,
            }}
          >
            <Panel
              title="시간대별 환승객"
              sub={
                <>
                  T1 <Plan>170명</Plan> / T2 <Plan>1,594명</Plan>
                </>
              }
            >
              <ChartRow icon="arr" cfg={CHARTS.trsT1} />
              <ChartRow icon="plane" cfg={CHARTS.trsT2} />
            </Panel>

            <Panel title="기상현황">
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gridTemplateRows: "1fr auto",
                  alignItems: "center",
                  justifyItems: "center",
                  gap: "8px 14px",
                  minHeight: 112,
                  padding: "10px 8px",
                }}
              >
                <div style={{ fontSize: 34, lineHeight: 1 }}>☀️</div>
                <div
                  className="num"
                  style={{
                    fontSize: 25,
                    fontWeight: 800,
                    color: C.orange,
                    whiteSpace: "nowrap",
                    alignSelf: "center",
                    justifySelf: "center",
                  }}
                >
                  {WEATHER.range}
                </div>
                <div
                  className="num"
                  style={{
                    fontSize: 13,
                    lineHeight: 1.9,
                    color: "#cfe0ee",
                    flex: 1,
                    gridColumn: "1 / -1",
                    width: "100%",
                    display: "grid",
                    gridTemplateColumns: "1fr 1fr",
                    columnGap: 16,
                  }}
                >
                  {WEATHER.rows.map(([k, v]) => (
                    <div
                      key={k}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                      }}
                    >
                      <span style={{ whiteSpace: "pre" }}>{k}</span>
                      <b style={{ color: C.txt }}>{v}</b>
                    </div>
                  ))}
                </div>
              </div>
            </Panel>
          </div>

          <Panel
            title={undefined}
            style={{
              flex: 1,
              minHeight: 0,
              display: "flex",
              flexDirection: "column",
            }}
            bodyStyle={{
              flex: 1,
              minHeight: 0,
              display: "flex",
              flexDirection: "column",
            }}
          >
            <RealtimeCongestionChart
              samples={carrierSamples}
              connectionStatus={connectionStatus}
            />
            <div
              style={{
                display: "none",
                gridTemplateColumns: "repeat(2,1fr)",
                gap: "2px 8px",
                padding: "6px 2px 0",
                fontSize: 11,
                color: "#cfe0ee",
              }}
            >
              <div
                style={{
                  gridColumn: "1/-1",
                  color: C.txt,
                  fontWeight: 700,
                  marginBottom: 2,
                }}
              >
                주기 상태
              </div>
              {STATUS_LEGEND.map((s) => (
                <span
                  key={s.status}
                  style={{ display: "flex", alignItems: "center", gap: 5 }}
                >
                  <i
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: "50%",
                      background: STATUS_COLOR[s.status],
                      flex: "0 0 8px",
                    }}
                  />
                  {s.label} ({s.n})
                </span>
              ))}
            </div>
          </Panel>
        </div>

        {/* ================= RIGHT ================= */}
        <div className="col">
          <Panel title="여 객" sub="(실적/계획)">
            <StatTable {...PAX} />
          </Panel>

          <Panel
            title="시간대별 출발여객"
            sub={
              <>
                T1 <Plan>1,190명</Plan> / T2 <Plan>1,012명</Plan>
              </>
            }
          >
            <ChartRow icon="arr" cfg={CHARTS.depT1} />
            <ChartRow icon="plane" cfg={CHARTS.depT2} />
          </Panel>

          <div className="two">
            {PARKING.map((p) => (
              <Panel key={p.title} title={p.title}>
                {p.rows.map((r) => (
                  <ParkingBar key={r.label} {...r} />
                ))}
              </Panel>
            ))}
          </div>

          <div className="two">
            <Panel title="환승" sub="(일누적)">
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 12,
                  padding: "6px 4px",
                }}
              >
                <div
                  style={{
                    width: 54,
                    height: 54,
                    flex: "0 0 54px",
                    borderRadius: "50%",
                    background: `conic-gradient(from 180deg, ${C.orange} 100%, transparent 0)`,
                    display: "grid",
                    placeItems: "center",
                    position: "relative",
                  }}
                >
                  <div
                    style={{
                      position: "absolute",
                      inset: 8,
                      borderRadius: "50%",
                      background: C.panel,
                    }}
                  />
                  <span
                    className="num"
                    style={{
                      position: "relative",
                      fontSize: 13,
                      fontWeight: 800,
                    }}
                  >
                    100%
                  </span>
                </div>
                <div style={{ textAlign: "center", flex: 1, minWidth: 0 }}>
                  <div
                    style={{ fontSize: 10, color: C.dim, whiteSpace: "nowrap" }}
                  >
                    총승객 대비
                  </div>
                  <div style={{ fontSize: 11, color: C.dim }}>총승객대비</div>
                  <div
                    className="num"
                    style={{
                      fontSize: 18,
                      fontWeight: 800,
                      whiteSpace: "nowrap",
                    }}
                  >
                    {TRANSFER.count}
                  </div>
                  <div
                    className="num"
                    style={{
                      color: C.orange,
                      fontSize: 10,
                      fontWeight: 700,
                      whiteSpace: "nowrap",
                    }}
                  >
                    총승객 기준 100%
                  </div>
                </div>
              </div>
            </Panel>

            <Panel title="예정 항공편">
              <MiniTable
                cols={BAGGAGE.cols}
                rows={BAGGAGE.rows}
                format={(v) => v.toLocaleString()}
              />
            </Panel>
          </div>

          <Panel title="2026년 운행 실적">
            <MiniTable cols={CARGO.cols} rows={CARGO.rows} />
          </Panel>
        </div>
      </div>
    </div>
  );
}
