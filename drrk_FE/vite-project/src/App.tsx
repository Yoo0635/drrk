import { useState } from "react";
import Header from "./components/Header";
import AirportMap from "./components/AirportMap";
import Dashboard from "./components/Dashboard";
import RouteSelector from "./components/RouteSelector";
import { MARKERS } from "./data/markers";
import { ROUTES } from "./data/routes";
import type { RouteId } from "./types/route";
import "./App.css";

export default function App() {
  const [selectedRouteId, setSelectedRouteId] = useState<RouteId>("a");

  return (
    <main className="page">
      <Header updatedAt="14:28" />

      <section className="top-area">
        <RouteSelector
          routes={ROUTES}
          selectedRouteId={selectedRouteId}
          onChange={setSelectedRouteId}
        />

        <AirportMap
          markers={MARKERS}
          routes={ROUTES}
          selectedRouteId={selectedRouteId}
          selectedId={null}
          onSelect={() => {}}
          coordPicker={false}
        />
      </section>

      <section className="bottom-area">
        <Dashboard />
      </section>
    </main>
  );
}
