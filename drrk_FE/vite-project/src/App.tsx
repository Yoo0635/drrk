import { useState } from "react";
import AirportMap from "./components/AirportMap";
import DetailPanel from "./components/DetailPanel";
import { MARKERS } from "./data/markers";
import "./App.css";

function App() {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = MARKERS.find((m) => m.id === selectedId) ?? null;

  /** 같은 마커를 다시 누르면 선택 해제 */
  function handleSelect(id: string) {
    setSelectedId((prev) => (prev === id ? null : id));
  }

  return (
    <main className="page">
      <section className="top-area">
        <AirportMap
          markers={MARKERS}
          selectedId={selectedId}
          onSelect={handleSelect}
          coordPicker
        />
      </section>

      <section className="bottom-area">
        <DetailPanel marker={selected} />
      </section>
    </main>
  );
}

export default App;
