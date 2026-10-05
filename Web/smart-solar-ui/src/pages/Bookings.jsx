import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";
import { canModifyBooking, decorateBookings, formatWhen, formatWindow, inScope } from "../format.js";
import { setFlash } from "../session.js";
import { Banner, Empty, Rule, Status, useConfirm, usePage } from "../shell.jsx";

const TITLES = {
  current: ["Current bookings", "Pending and approved bookings that are still open."],
  pending: ["Pending bookings", "Bookings waiting for approval. Approving one issues a QR token."],
  approved: ["Approved upcoming", "Approved bookings whose start time is still in the future."],
  history: ["Booking history", "Completed and cancelled energy transfers."],
};

export function Bookings() {
  const [params, setParams] = useSearchParams();
  const scope = params.get("scope") || "current";
  const search = params.get("search") || "";
  const [title, subtitle] = TITLES[scope] || TITLES.current;
  usePage(title, subtitle);
  const confirm = useConfirm();
  const [counts, setCounts] = useState({ pendingCount: 0, approvedFutureCount: 0, currentCount: 0, historyCount: 0 });
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const [token, setToken] = useState("");
  const [copied, setCopied] = useState(false);

  async function load() {
    const query = new URLSearchParams();
    if (scope === "pending") query.set("status", "Pending");
    if (scope === "approved") query.set("status", "Approved");
    if (search.trim()) query.set("search", search.trim());
    const path = query.toString() ? `/api/reservations?${query}` : "/api/reservations";
    try {
      const [dash, reservations, stations, slots] = await Promise.all([
        api("/api/reservations/dashboard"),
        api(path),
        api("/api/stations"),
        api("/api/slots"),
      ]);
      const decorated = decorateBookings(reservations, stations, slots)
        .filter((row) => inScope(row.reservation, scope))
        .sort((a, b) => new Date(a.reservation.scheduledAt) - new Date(b.reservation.scheduledAt));
      if (scope === "history") decorated.reverse();
      setCounts(dash || counts);
      setRows(decorated);
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, [scope, search]);

  async function act(id, action) {
    const approving = action === "approve";
    const ok = await confirm({
      message: approving
        ? "Approve this booking and issue a QR token?"
        : "Cancel this booking? Updates and cancellations need at least 12 hours before the scheduled start.",
      danger: !approving,
    });
    if (!ok) return;
    try {
      const result = await api(`/api/reservations/${id}/${action}`, { method: "POST" });
      setNotice({ tone: "ok", text: result?.summary || (approving ? "Booking approved." : "Booking cancelled.") });
      await load();
    } catch (err) {
      setNotice({ tone: "danger", text: err.message });
    }
  }

  async function copyToken() {
    try {
      await navigator.clipboard.writeText(token);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <>
      <Banner>{error}</Banner>
      {notice && <Banner tone={notice.tone}>{notice.text}</Banner>}
      <div className="segment" role="tablist" aria-label="Booking views">
        <ScopeLink scope="current" current={scope} search={search} count={counts.currentCount}>Current</ScopeLink>
        <ScopeLink scope="pending" current={scope} search={search} count={counts.pendingCount}>Pending</ScopeLink>
        <ScopeLink scope="approved" current={scope} search={search} count={counts.approvedFutureCount}>Upcoming approved</ScopeLink>
        <ScopeLink scope="history" current={scope} search={search} count={counts.historyCount}>History</ScopeLink>
      </div>
      <form key={`${scope}|${search}`} className="toolbar" onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const next = { scope };
        const term = String(data.get("search") || "");
        if (term) next.search = term;
        setParams(next);
      }}>
        <input className="search" name="search" defaultValue={search} placeholder="Search NIC, station id, or slot id" />
        <button className="btn btn-primary" type="submit">Search</button>
        {search && <Link className="btn btn-ghost" to={`/bookings?scope=${scope}`}>Clear</Link>}
        <Link className="btn btn-primary push" to="/bookings/new"><i className="bi bi-plus-lg" /> New booking</Link>
      </form>
      <p className="result-count">{rows.length} booking{rows.length === 1 ? "" : "s"}</p>
      <div className="table-panel">
        <table className="table">
          <thead>
            <tr><th>NIC</th><th>Hub</th><th>Window</th><th>Status</th><th>Booked</th><th></th></tr>
          </thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan={6}><Empty>No bookings match this view.</Empty></td></tr>}
            {rows.map((row) => {
              const item = row.reservation;
              const editable = canModifyBooking(item);
              return (
                <tr key={item.id}>
                  <td className="mono">{item.prosumerNic}</td>
                  <td>{row.stationName}</td>
                  <td>{row.window}</td>
                  <td><Status value={item.status} /></td>
                  <td>{formatWhen(item.createdAt)}</td>
                  <td className="row-actions">
                    {item.status === "Pending" && (
                      <button type="button" className="btn btn-sm btn-primary" onClick={() => act(item.id, "approve")}>Approve</button>
                    )}
                    {editable && (
                      <>
                        <Link className="btn btn-sm btn-ghost" to={`/bookings/${item.id}/edit`}>Update</Link>
                        <button type="button" className="btn btn-sm btn-danger" onClick={() => act(item.id, "cancel")}>Cancel</button>
                      </>
                    )}
                    {!editable && (item.status === "Pending" || item.status === "Approved") && <span className="hint">12-hour lock</span>}
                    {item.qrToken && (
                      <button type="button" className="btn btn-sm btn-ghost" onClick={() => { setToken(item.qrToken); setCopied(false); }}>QR</button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {token && (
        <div className="modal-back" onClick={() => setToken("")}>
          <div className="modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <h2>Booking QR token</h2>
            <p className="hint">The mobile app draws this token as a QR code. Operators can paste it into Verify QR.</p>
            <p className="token">{token}</p>
            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setToken("")}>Close</button>
              <button type="button" className="btn btn-primary" onClick={copyToken}>{copied ? "Copied" : "Copy token"}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function ScopeLink({ scope, current, search, count, children }) {
  const query = search ? `?scope=${scope}&search=${encodeURIComponent(search)}` : `?scope=${scope}`;
  return (
    <Link className={current === scope ? "is-active" : ""} to={`/bookings${query}`}>
      {children} <span>{count}</span>
    </Link>
  );
}

export function BookingForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  usePage(
    editing ? "Update booking" : "New booking",
    editing
      ? "Move this booking to another slot. It returns to Pending and needs another approval."
      : "Schedule a transfer within the next 7 days. The booking starts as Pending.",
  );
  const { isBackoffice } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ prosumerNic: "", slotId: "" });
  const [choices, setChoices] = useState([]);
  const [prosumers, setProsumers] = useState([]);
  const [summary, setSummary] = useState("");
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancel = false;
    async function load() {
      try {
        const [stations, slots] = await Promise.all([api("/api/stations"), api("/api/slots")]);
        let currentSlot = "";
        let nic = "";
        if (id) {
          const reservation = await api(`/api/reservations/${id}`);
          if (cancel) return;
          if (!canModifyBooking(reservation)) {
            setFlash(reservation.status === "Cancelled" || reservation.status === "Completed"
              ? "This reservation can no longer be changed."
              : "Updates require at least 12 hours' notice.", "danger");
            navigate("/bookings?scope=current", { replace: true });
            return;
          }
          currentSlot = reservation.slotId;
          nic = reservation.prosumerNic;
          setSummary(formatWhen(reservation.scheduledAt));
          setForm({ prosumerNic: nic, slotId: currentSlot });
        }
        const names = new Map((stations || []).map((station) => [station.id, station.name]));
        const now = Date.now();
        const week = now + 7 * 24 * 60 * 60 * 1000;
        const options = (slots || [])
          .filter((slot) => {
            if (slot.id === currentSlot) return true;
            const start = new Date(slot.startTime).getTime();
            return slot.status === "Available" && slot.availableCapacity >= 1 && start > now && start <= week;
          })
          .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
          .map((slot) => ({
            id: slot.id,
            label: `${names.get(slot.stationId) || "Hub"} · ${formatWindow(slot.startTime, slot.endTime)} · ${slot.availableCapacity} remaining${slot.id === currentSlot ? " · current" : ""}`,
          }));
        if (cancel) return;
        setChoices(options);
        if (isBackoffice && !editing) {
          const users = await api("/api/users");
          if (!cancel) {
            setProsumers((users || []).filter((user) => user.role === "Prosumer" && user.status === "Active"));
          }
        }
      } catch (err) {
        if (!cancel) setError(err.message);
      }
    }
    load();
    return () => {
      cancel = true;
    };
  }, [id, isBackoffice, editing, navigate]);

  function update(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    const next = {};
    if (!form.prosumerNic.trim()) next.prosumerNic = "Enter the prosumer NIC.";
    if (!form.slotId) next.slotId = "Select an energy slot.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    setBusy(true);
    setError("");
    try {
      const body = { slotId: form.slotId, prosumerNic: form.prosumerNic.trim() };
      const result = editing
        ? await api(`/api/reservations/${id}`, { method: "PUT", body })
        : await api("/api/reservations", { method: "POST", body });
      setFlash(result?.summary || (editing ? "Booking updated. It is pending approval again." : "Booking created and waiting for approval."));
      navigate("/bookings?scope=pending");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel narrow">
      <Rule title="7 days to book. 12 hours to change.">
        The slot must start in the future and within 7 days. Moving or cancelling a booking needs at least 12 hours before the scheduled start. An update returns the booking to Pending.
      </Rule>
      {editing && <p className="lede">Prosumer <span className="mono">{form.prosumerNic}</span> · currently {summary}</p>}
      {choices.length === 0 && <Empty>No bookable slots start within the next 7 days. Open a slot on an active hub first.</Empty>}
      <form className="form-grid" onSubmit={onSubmit}>
        <div className="span-2"><Banner>{error}</Banner></div>
        <label className="span-2">Prosumer NIC
          {editing ? (
            <input value={form.prosumerNic} readOnly />
          ) : (
            <>
              <input name="prosumerNic" list="prosumer-nics" value={form.prosumerNic} onChange={update} placeholder="199912345V" />
              <datalist id="prosumer-nics">
                {prosumers.map((person) => <option key={person.id} value={person.nic}>{person.fullName}</option>)}
              </datalist>
              <span className="hint">NIC of an active prosumer. Pending accounts cannot be booked.</span>
            </>
          )}
          <FieldError>{errors.prosumerNic}</FieldError>
        </label>
        <label className="span-2">Energy slot
          <select name="slotId" value={form.slotId} onChange={update}>
            <option value="">Select a slot inside the next 7 days</option>
            {choices.map((choice) => <option key={choice.id} value={choice.id}>{choice.label}</option>)}
          </select>
          <FieldError>{errors.slotId}</FieldError>
        </label>
        <div className="span-2 form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy || choices.length === 0}>{editing ? "Update booking" : "Create booking"}</button>
          <Link className="btn btn-ghost" to="/bookings?scope=current">Cancel</Link>
        </div>
      </form>
    </section>
  );
}

function FieldError({ children }) {
  if (!children) return null;
  return <span className="field-error">{children}</span>;
}
