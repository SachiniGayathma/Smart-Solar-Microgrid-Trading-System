import { Status } from "./shell.jsx";

function SolarMark({ live }) {
  return (
    <span className={live ? "solar-badge is-live" : "solar-badge"} aria-hidden="true">
      <svg className="solar-rays" viewBox="0 0 32 32" width="28" height="28">
        <circle cx="16" cy="16" r="4" fill="#fbbf24" />
        <path d="M16 4.5v3M16 24.5v3M4.5 16h3M24.5 16h3M7.4 7.4l2.1 2.1M22.5 22.5l2.1 2.1M7.4 24.6l2.1-2.1M22.5 9.5l2.1-2.1" stroke="#22c55e" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
    </span>
  );
}

export function NodeBoard({ stations }) {
  if (!stations.length) return null;
  const peak = Math.max(...stations.map((station) => Number(station.capacityKwh) || 0), 1);
  return (
    <section className="scene" aria-label="Microgrid hubs">
      <p className="scene-label">Hubs on the grid</p>
      <div className="node-board">
        {stations.map((station, index) => {
          const live = station.status === "Active";
          const fill = Math.max(8, Math.round((Number(station.capacityKwh) / peak) * 100));
          return (
            <article key={station.id} className={live ? "node-card is-live" : "node-card"} style={{ animationDelay: `${Math.min(index, 8) * 70}ms` }}>
              <div className="node-top">
                <SolarMark live={live} />
                <div>
                  <strong>{station.name}</strong>
                  <span>{station.schedule || "No schedule"}</span>
                </div>
                <Status value={station.status} />
              </div>
              <div className="meter" aria-hidden="true">
                <span style={{ "--fill": `${fill}%` }} />
              </div>
              <p>{station.capacityKwh} kWh · {station.batteryStorageSlots} battery slot{station.batteryStorageSlots === 1 ? "" : "s"}</p>
            </article>
          );
        })}
      </div>
    </section>
  );
}

export function SlotBoard({ rows }) {
  if (!rows.length) return null;
  return (
    <section className="scene" aria-label="Energy slots">
      <p className="scene-label">Trading windows</p>
      <div className="slot-board">
        {rows.map(({ slot, stationName, windowLabel, places }, index) => {
          const open = slot ? slot.status === "Available" : true;
          return (
            <article key={slot?.id || index} className={open ? "slot-card" : "slot-card is-off"} style={{ animationDelay: `${Math.min(index, 8) * 70}ms` }}>
              <div className="slot-top">
                <strong>{stationName || "Hub"}</strong>
                {slot && <Status value={slot.status} />}
              </div>
              <div className="slot-track" aria-hidden="true">
                <span className="energy-bead" />
              </div>
              <p>{windowLabel || "Choose a start and end"}</p>
              {places !== undefined && <span className="hint">{places} place{Number(places) === 1 ? "" : "s"} open</span>}
            </article>
          );
        })}
      </div>
    </section>
  );
}
