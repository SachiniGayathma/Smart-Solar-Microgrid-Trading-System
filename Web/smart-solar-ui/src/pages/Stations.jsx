import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";
import { matchesQuery } from "../format.js";
import { setFlash } from "../session.js";
import { Banner, Empty, Rule, Status, useConfirm, usePage } from "../shell.jsx";

export function Stations() {
  const { isBackoffice } = useAuth();
  usePage(
    "Microgrid nodes",
    isBackoffice
      ? "Hubs with GPS, kWh capacity, battery slots, and a schedule. Deactivation is blocked while active bookings exist."
      : "Active hubs. Update the battery storage count. Creating and retiring hubs is a Backoffice function.",
  );
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const [stations, setStations] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const query = params.get("q") || "";
  const status = params.get("status") || "";

  async function load() {
    try {
      const rows = await api("/api/stations");
      setStations(rows || []);
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const visible = stations
    .filter((station) => !status || station.status === status)
    .filter((station) => matchesQuery([station.name, station.schedule], query))
    .sort((a, b) => a.name.localeCompare(b.name));

  async function saveBattery(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const batteryStorageSlots = Number(data.get("batteryStorageSlots"));
    if (!Number.isInteger(batteryStorageSlots) || batteryStorageSlots < 0) {
      setNotice({ tone: "danger", text: "Battery storage slots cannot be negative." });
      return;
    }
    try {
      await api(`/api/stations/${data.get("id")}/availability`, { method: "PATCH", body: { batteryStorageSlots } });
      setNotice({ tone: "ok", text: "Battery storage count updated." });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err.message });
    }
  }

  async function setActive(station, activate) {
    const ok = await confirm({
      message: activate
        ? `Activate ${station.name}?`
        : `Deactivate ${station.name}? This is blocked if pending or approved energy reservations still exist.`,
      danger: !activate,
    });
    if (!ok) return;
    try {
      await api(`/api/stations/${station.id}/${activate ? "activate" : "deactivate"}`, { method: "POST" });
      setNotice({ tone: "ok", text: activate ? "Hub is active again." : "Hub deactivated." });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err.message });
    }
  }

  return (
    <>
      <Banner>{error}</Banner>
      {notice && <Banner tone={notice.tone}>{notice.text}</Banner>}
      <form key={`${query}|${status}`} className="toolbar" onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setParams({ q: String(data.get("q") || ""), status: String(data.get("status") || "") });
      }}>
        <input className="search" name="q" defaultValue={query} placeholder="Search hub name or schedule" />
        {isBackoffice && (
          <select className="filter" name="status" defaultValue={status} aria-label="Status">
            <option value="">All statuses</option>
            <option value="Active">Active</option>
            <option value="Deactivated">Deactivated</option>
          </select>
        )}
        <button className="btn btn-primary" type="submit">Filter</button>
        {isBackoffice && <Link className="btn btn-primary push" to="/stations/new"><i className="bi bi-plus-lg" /> New hub</Link>}
      </form>
      <p className="result-count">{visible.length} hub{visible.length === 1 ? "" : "s"}</p>
      <div className="table-panel">
        <table className="table">
          <thead>
            <tr>
              <th>Hub</th><th>GPS</th><th>Capacity</th><th>Battery slots</th><th>Schedule</th><th>Status</th><th></th>
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 && <tr><td colSpan={7}><Empty>No microgrid hubs match this filter.</Empty></td></tr>}
            {visible.map((station) => (
              <tr key={station.id}>
                <td><strong>{station.name}</strong></td>
                <td>
                  <span className="mono">{station.latitude}, {station.longitude}</span>
                  <a className="map-link" href={`https://www.google.com/maps?q=${station.latitude},${station.longitude}`} target="_blank" rel="noreferrer">Map</a>
                </td>
                <td>{station.capacityKwh} kWh</td>
                <td>
                  <form className="inline-form" onSubmit={saveBattery}>
                    <input type="hidden" name="id" value={station.id} />
                    <input className="capacity" type="number" name="batteryStorageSlots" min="0" defaultValue={station.batteryStorageSlots} aria-label={`Battery storage slots for ${station.name}`} />
                    <button className="btn btn-sm btn-ghost" type="submit">Save</button>
                  </form>
                </td>
                <td>{station.schedule || "—"}</td>
                <td><Status value={station.status} /></td>
                <td className="row-actions">
                  {isBackoffice && (
                    <>
                      <Link className="btn btn-sm btn-ghost" to={`/stations/${station.id}/edit`}>Update</Link>
                      {station.status === "Active" ? (
                        <button type="button" className="btn btn-sm btn-danger" onClick={() => setActive(station, false)}>Deactivate</button>
                      ) : (
                        <button type="button" className="btn btn-sm btn-primary" onClick={() => setActive(station, true)}>Activate</button>
                      )}
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function StationForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  usePage(editing ? "Update hub" : "New microgrid hub", "Record the GPS location, energy capacity, battery slots, and operating schedule.");
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: "", latitude: "", longitude: "", capacityKwh: "", batteryStorageSlots: "0", schedule: "" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return undefined;
    let cancel = false;
    api(`/api/stations/${id}`)
      .then((station) => {
        if (cancel || !station) return;
        setForm({
          name: station.name || "",
          latitude: String(station.latitude ?? ""),
          longitude: String(station.longitude ?? ""),
          capacityKwh: String(station.capacityKwh ?? ""),
          batteryStorageSlots: String(station.batteryStorageSlots ?? 0),
          schedule: station.schedule || "",
        });
      })
      .catch((err) => {
        if (!cancel) setError(err.message);
      });
    return () => {
      cancel = true;
    };
  }, [id]);

  function update(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    const latitude = Number(form.latitude);
    const longitude = Number(form.longitude);
    const capacityKwh = Number(form.capacityKwh);
    const batteryStorageSlots = Number(form.batteryStorageSlots);
    const next = {};
    if (!form.name.trim()) next.name = "Hub name is required.";
    if (form.latitude === "" || Number.isNaN(latitude) || latitude < -90 || latitude > 90) next.latitude = "Latitude must be between -90 and 90.";
    if (form.longitude === "" || Number.isNaN(longitude) || longitude < -180 || longitude > 180) next.longitude = "Longitude must be between -180 and 180.";
    if (form.capacityKwh === "" || Number.isNaN(capacityKwh) || capacityKwh <= 0) next.capacityKwh = "Capacity must be greater than zero.";
    if (!Number.isInteger(batteryStorageSlots) || batteryStorageSlots < 0) next.batteryStorageSlots = "Battery storage slots cannot be negative.";
    if (!form.schedule.trim()) next.schedule = "Enter the operating schedule.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    const body = {
      name: form.name.trim(),
      latitude,
      longitude,
      capacityKwh,
      batteryStorageSlots,
      schedule: form.schedule.trim(),
    };
    setBusy(true);
    setError("");
    try {
      if (editing) {
        await api(`/api/stations/${id}`, { method: "PUT", body });
        setFlash("Hub updated.");
      } else {
        await api("/api/stations", { method: "POST", body });
        setFlash(`Hub ${body.name} is active.`);
      }
      navigate("/stations");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel narrow">
      {editing && (
        <Rule title="Closing a hub is done from the list.">
          Deactivation is refused while pending or approved reservations exist for this hub.
        </Rule>
      )}
      <form className="form-grid" onSubmit={onSubmit}>
        <div className="span-2"><Banner>{error}</Banner></div>
        <label className="span-2">Hub name
          <input name="name" value={form.name} onChange={update} placeholder="Nugegoda Solar Hub" />
          <FieldError>{errors.name}</FieldError>
        </label>
        <label>Latitude
          <input name="latitude" value={form.latitude} onChange={update} placeholder="6.8649" />
          <FieldError>{errors.latitude}</FieldError>
        </label>
        <label>Longitude
          <input name="longitude" value={form.longitude} onChange={update} placeholder="79.8997" />
          <FieldError>{errors.longitude}</FieldError>
        </label>
        <label>Capacity (kWh)
          <input name="capacityKwh" value={form.capacityKwh} onChange={update} />
          <span className="hint">Energy capacity of the hub, in kWh.</span>
          <FieldError>{errors.capacityKwh}</FieldError>
        </label>
        <label>Battery storage slots
          <input name="batteryStorageSlots" value={form.batteryStorageSlots} onChange={update} />
          <FieldError>{errors.batteryStorageSlots}</FieldError>
        </label>
        <label className="span-2">Schedule
          <input name="schedule" value={form.schedule} onChange={update} placeholder="06:00-18:00 daily" />
          <FieldError>{errors.schedule}</FieldError>
        </label>
        <div className="span-2 form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy}>{editing ? "Save hub" : "Create hub"}</button>
          <Link className="btn btn-ghost" to="/stations">Cancel</Link>
        </div>
      </form>
    </section>
  );
}

function FieldError({ children }) {
  if (!children) return null;
  return <span className="field-error">{children}</span>;
}
