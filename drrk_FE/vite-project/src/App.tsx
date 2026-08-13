import { useState } from "react";
import Header from "./components/Header";
import AirportMap from "./components/AirportMap";
import Dashboard from "./components/Dashboard";
import { MARKERS } from "./data/markers";
import "./App.css";

export default function App() {
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);

  return (
    <main className="page">
      <Header updatedAt="14:28" />

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
