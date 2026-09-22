import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";
import { defaultSlotWindow, formatWindow, fromColomboInput, toColomboInput } from "../format.js";
import { setFlash } from "../session.js";
import { Banner, Empty, Rule, Status, useConfirm, usePage } from "../shell.jsx";

export function Slots() {
  usePage("Energy slots", "Trading windows on a hub. Closing a slot is blocked while a pending or approved booking still uses it.");
  const { isBackoffice } = useAuth();
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const [stations, setStations] = useState([]);
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const stationId = params.get("stationId") || "";
  const status = params.get("status") || "";

  async function load() {
    try {
      const stationRows = await api("/api/stations");
      const path = stationId ? `/api/slots?stationId=${encodeURIComponent(stationId)}` : "/api/slots";
      const slotRows = await api(path);
      const names = new Map((stationRows || []).map((station) => [station.id, station.name]));
      setStations(stationRows || []);
      setRows((slotRows || [])
        .filter((slot) => !status || slot.status === status)
        .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
        .map((slot) => ({ slot, stationName: names.get(slot.stationId) || "Unknown hub" })));
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, [stationId, status]);

  async function saveCapacity(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const availableCapacity = Number(data.get("availableCapacity"));
    if (Number.isNaN(availableCapacity) || availableCapacity < 0) {
      setNotice({ tone: "danger", text: "Remaining places cannot be negative." });
      return;
    }
    try {
      await api(`/api/slots/${data.get("id")}/availability`, { method: "PATCH", body: { availableCapacity } });
      setNotice({ tone: "ok", text: "Slot capacity updated." });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err.message });
    }
  }

  async function deactivate(slot) {
    const ok = await confirm({
      message: "Deactivate this slot? This is blocked if a pending or approved booking still uses it.",
      danger: true,
    });
    if (!ok) return;
    try {
      await api(`/api/slots/${slot.id}/deactivate`, { method: "POST" });
      setNotice({ tone: "ok", text: "Slot deactivated." });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err.message });
    }
  }

  async function remove(slot) {
    const ok = await confirm({
      message: "Delete this slot permanently? This is blocked while active bookings exist.",
      danger: true,
    });
    if (!ok) return;
    try {
      await api(`/api/slots/${slot.id}`, { method: "DELETE" });
      setNotice({ tone: "ok", text: "Slot deleted." });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err.message });
    }
  }

  return (
    <>
      <Banner>{error}</Banner>
      {notice && <Banner tone={notice.tone}>{notice.text}</Banner>}
      <form key={`${stationId}|${status}`} className="toolbar" onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setParams({ stationId: String(data.get("stationId") || ""), status: String(data.get("status") || "") });
      }}>
        <select className="filter wide" name="stationId" defaultValue={stationId} aria-label="Hub">
          <option value="">All hubs</option>
          {stations.map((station) => <option key={station.id} value={station.id}>{station.name}</option>)}
        </select>
        <select className="filter" name="status" defaultValue={status} aria-label="Status">
          <option value="">All statuses</option>
          <option value="Available">Available</option>
          <option value="Deactivated">Deactivated</option>
        </select>
        <button className="btn btn-primary" type="submit">Filter</button>
        <Link className="btn btn-primary push" to="/slots/new"><i className="bi bi-plus-lg" /> New slot</Link>
      </form>
      <p className="result-count">{rows.length} slot{rows.length === 1 ? "" : "s"}. Times are Sri Lanka time.</p>
      <div className="table-panel">
        <table className="table">
          <thead>
            <tr><th>Hub</th><th>Window</th><th>Places left</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan={5}><Empty>No energy slots match this filter.</Empty></td></tr>}
            {rows.map(({ slot, stationName }) => (
              <tr key={slot.id}>
                <td><strong>{stationName}</strong></td>
                <td>{formatWindow(slot.startTime, slot.endTime)}</td>
                <td>
                  {slot.status === "Available" ? (
                    <form className="inline-form" onSubmit={saveCapacity}>
                      <input type="hidden" name="id" value={slot.id} />
                      <input className="capacity" type="number" name="availableCapacity" min="0" step="1" defaultValue={slot.availableCapacity} aria-label="Remaining places" />
                      <button className="btn btn-sm btn-ghost" type="submit">Save</button>
                    </form>
                  ) : slot.availableCapacity}
                </td>
                <td><Status value={slot.status} /></td>
                <td className="row-actions">
                  <Link className="btn btn-sm btn-ghost" to={`/slots/${slot.id}/edit`}>Update</Link>
                  {slot.status !== "Deactivated" && (
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => deactivate(slot)}>Deactivate</button>
                  )}
                  {isBackoffice && (
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => remove(slot)}>Delete</button>
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

export function SlotForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  usePage(
    editing ? "Update energy slot" : "New energy slot",
    "A slot is a time window on an active hub. Bookings can only use windows that start within 7 days.",
  );
  const navigate = useNavigate();
  const defaults = defaultSlotWindow();
  const [stations, setStations] = useState([]);
  const [form, setForm] = useState({ stationId: "", startTime: defaults.start, endTime: defaults.end, availableCapacity: "4" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancel = false;
    async function load() {
      try {
        const stationRows = await api("/api/stations");
        if (cancel) return;
        setStations((stationRows || []).filter((station) => station.status === "Active"));
        if (!id) return;
        const slot = await api(`/api/slots/${id}`);
        if (cancel || !slot) return;
        setStations((current) => {
          if (current.some((station) => station.id === slot.stationId)) return current;
          const match = (stationRows || []).find((station) => station.id === slot.stationId);
          return match ? [...current, match] : current;
        });
        setForm({
          stationId: slot.stationId,
          startTime: toColomboInput(slot.startTime),
          endTime: toColomboInput(slot.endTime),
          availableCapacity: String(slot.availableCapacity ?? 0),
        });
      } catch (err) {
        if (!cancel) setError(err.message);
      }
    }
    load();
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
    const availableCapacity = Number(form.availableCapacity);
    const next = {};
    if (!form.stationId) next.stationId = "Select a hub.";
    if (!form.startTime) next.startTime = "Start time is required.";
    if (!form.endTime) next.endTime = "End time is required.";
    if (form.startTime && form.endTime && fromColomboInput(form.endTime) <= fromColomboInput(form.startTime)) {
      next.endTime = "End time must be after the start time.";
    }
    if (Number.isNaN(availableCapacity) || availableCapacity < 0) next.availableCapacity = "Remaining places cannot be negative.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    const body = {
      stationId: form.stationId,
      startTime: fromColomboInput(form.startTime),
      endTime: fromColomboInput(form.endTime),
      availableCapacity,
    };
    setBusy(true);
    setError("");
    try {
      if (editing) await api(`/api/slots/${id}`, { method: "PUT", body });
      else await api("/api/slots", { method: "POST", body });
      setFlash(editing ? "Energy slot updated." : "Energy slot created.");
      navigate("/slots");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel narrow">
      <Rule title="Booking window.">
        Prosumers can reserve a slot only when it starts within the next 7 days. Each booking takes one place. Times are Sri Lanka time.
      </Rule>
      {stations.length === 0 && <Empty>No active hub is available. A Backoffice officer needs to create one before slots can be opened.</Empty>}
      <form className="form-grid" onSubmit={onSubmit}>
        <div className="span-2"><Banner>{error}</Banner></div>
        <label className="span-2">Microgrid hub
          <select name="stationId" value={form.stationId} onChange={update}>
            <option value="">Select a hub</option>
            {stations.map((station) => <option key={station.id} value={station.id}>{station.name}</option>)}
          </select>
          <FieldError>{errors.stationId}</FieldError>
        </label>
        <label>Starts
          <input type="datetime-local" name="startTime" value={form.startTime} onChange={update} />
          <FieldError>{errors.startTime}</FieldError>
        </label>
        <label>Ends
          <input type="datetime-local" name="endTime" value={form.endTime} onChange={update} />
          <FieldError>{errors.endTime}</FieldError>
        </label>
        <label className="span-2">Remaining places
          <input type="number" min="0" step="1" name="availableCapacity" value={form.availableCapacity} onChange={update} />
          <span className="hint">How many bookings this window can still accept.</span>
          <FieldError>{errors.availableCapacity}</FieldError>
        </label>
        <div className="span-2 form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy || stations.length === 0}>{editing ? "Save slot" : "Create slot"}</button>
          <Link className="btn btn-ghost" to="/slots">Cancel</Link>
        </div>
      </form>
    </section>
  );
}

function FieldError({ children }) {
  if (!children) return null;
  return <span className="field-error">{children}</span>;
}
