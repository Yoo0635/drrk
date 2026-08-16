import { useState } from "react";
import Header from "./components/Header";
import AirportMap from "./components/AirportMap";
import Dashboard from "./components/Dashboard";
import { MARKERS } from "./data/markers";
import { useCarrierCountStream } from "./hooks/useCarrierCountStream";
import "./App.css";

export default function App() {
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);
  const { lastReceivedAt } = useCarrierCountStream();
  const updatedAt = lastReceivedAt === null ? "14:28" : formatHeaderTime(lastReceivedAt);

  return (
    <main className="page">
      <Header updatedAt={updatedAt} />

      <section className="top-area">
        <AirportMap
          markers={MARKERS}
          selectedId={selectedMarkerId}
          onSelect={setSelectedMarkerId}
          coordPicker={false}
        />
      </section>

      <section className="bottom-area">
        <Dashboard />
      </section>
    </main>
  );
}

function formatHeaderTime(date: Date) {
  return new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}
