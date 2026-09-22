import { useState } from "react";
import { api } from "../api.js";
import { formatWhen } from "../format.js";
import { Banner, Rule, Status, usePage } from "../shell.jsx";

export default function Verify() {
  usePage("Verify QR", "Paste the token from an approved booking. A match marks the energy transfer completed.");
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [stationName, setStationName] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(event) {
    event.preventDefault();
    if (!token.trim()) {
      setError("Paste the QR token.");
      return;
    }
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const reservation = await api("/api/reservations/verify-qr", {
        method: "POST",
        body: { qrToken: token.trim() },
      });
      let name = reservation.stationId;
      try {
        const station = await api(`/api/stations/${reservation.stationId}`);
        name = station?.name || name;
      } catch {
        name = reservation.stationId;
      }
      setStationName(name);
      setResult(reservation);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel narrow">
      <Rule title="Completion check.">
        The token must belong to an approved booking. A match marks the energy transfer completed.
      </Rule>
      <form className="stack" onSubmit={onSubmit}>
        <Banner>{error}</Banner>
        <label>QR token
          <textarea rows={3} value={token} onChange={(event) => setToken(event.target.value)} placeholder="Paste the token from the approved booking" />
        </label>
        <div className="form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy}>Verify and complete</button>
        </div>
      </form>
      {result && (
        <div className="result-card">
          <Status value={result.status} />
          <h2>{result.summary}</h2>
          <dl>
            <div><dt>NIC</dt><dd className="mono">{result.prosumerNic}</dd></div>
            <div><dt>Hub</dt><dd>{stationName}</dd></div>
            <div><dt>Scheduled</dt><dd>{formatWhen(result.scheduledAt)}</dd></div>
          </dl>
        </div>
      )}
    </section>
  );
}
