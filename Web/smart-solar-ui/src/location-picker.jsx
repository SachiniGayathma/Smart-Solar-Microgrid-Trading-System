import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

const COLOMBO = [6.9271, 79.8612];
const pin = L.divIcon({
  className: "hub-pin",
  iconSize: [18, 18],
  iconAnchor: [9, 9],
});

function round(value) {
  return Number(value.toFixed(6));
}

export function LocationPicker({ latitude, longitude, onPick }) {
  const host = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const onPickRef = useRef(onPick);
  const [query, setQuery] = useState("");
  const [searchError, setSearchError] = useState("");
  const [searching, setSearching] = useState(false);
  onPickRef.current = onPick;

  function place(map, lat, lng) {
    const point = L.latLng(lat, lng);
    if (!markerRef.current) {
      markerRef.current = L.marker(point, { icon: pin, draggable: true }).addTo(map);
      markerRef.current.on("dragend", () => {
        const next = markerRef.current.getLatLng();
        onPickRef.current(round(next.lat), round(next.lng));
      });
    } else {
      markerRef.current.setLatLng(point);
    }
  }

  useEffect(() => {
    const map = L.map(host.current, { scrollWheelZoom: true }).setView(COLOMBO, 12);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap",
    }).addTo(map);
    map.on("click", (event) => {
      place(map, event.latlng.lat, event.latlng.lng);
      onPickRef.current(round(event.latlng.lat), round(event.latlng.lng));
    });
    mapRef.current = map;
    requestAnimationFrame(() => map.invalidateSize());
    return () => {
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || latitude === "" || longitude === "") return;
    const lat = Number(latitude);
    const lng = Number(longitude);
    if (Number.isNaN(lat) || Number.isNaN(lng)) return;
    place(map, lat, lng);
    if (!map.getBounds().contains([lat, lng]) || map.getZoom() < 13) {
      map.setView([lat, lng], Math.max(map.getZoom(), 14));
    }
  }, [latitude, longitude]);

  async function findPlace() {
    const term = query.trim();
    if (!term) return;
    setSearching(true);
    setSearchError("");
    try {
      const response = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(term)}&limit=1&lat=7&lon=80`);
      if (!response.ok) throw new Error("search failed");
      const data = await response.json();
      const feature = data.features?.[0];
      if (!feature) {
        setSearchError("No place matched that search. Click the map instead.");
        return;
      }
      const [lng, lat] = feature.geometry.coordinates;
      onPickRef.current(round(lat), round(lng));
    } catch {
      setSearchError("Place search is unavailable. Click the map to set the hub.");
    } finally {
      setSearching(false);
    }
  }

  const chosen = latitude !== "" && longitude !== "";

  return (
    <div className="location-picker">
      <div className="place-search">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              findPlace();
            }
          }}
          placeholder="Search a place, for example Nugegoda"
          aria-label="Search a place"
        />
        <button type="button" className="btn btn-ghost" onClick={findPlace} disabled={searching}>Find</button>
      </div>
      {searchError && <span className="field-error">{searchError}</span>}
      <div ref={host} className="hub-map" role="application" aria-label="Map. Click to set the hub location." />
      <p className="hint">
        {chosen
          ? `Selected ${latitude}, ${longitude}. Drag the marker if you need to adjust it.`
          : "Click the map or search for a place. The pin is the hub location."}
      </p>
    </div>
  );
}
