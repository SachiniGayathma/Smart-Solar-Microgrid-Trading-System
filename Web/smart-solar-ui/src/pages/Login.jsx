import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";

export default function Login() {
  const { session, signIn } = useAuth();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [online, setOnline] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    document.body.classList.add("login-body");
    document.title = "Sign in · Smart Solar";
    return () => document.body.classList.remove("login-body");
  }, []);

  useEffect(() => {
    if (session) navigate("/dashboard", { replace: true });
  }, [session, navigate]);

  useEffect(() => {
    api("/api/health", { auth: false })
      .then((health) => setOnline(health?.mongodb === "connected"))
      .catch(() => setOnline(false));
  }, []);

  async function onSubmit(event) {
    event.preventDefault();
    if (!identifier.trim() || !password) {
      setError("Enter your email or NIC and your password.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const data = await api("/api/auth/login", {
        method: "POST",
        auth: false,
        body: { identifier: identifier.trim(), password },
      });
      if (data.role === "Prosumer") {
        setError("This portal is for Backoffice and Grid Operator staff. Prosumers use the mobile app.");
        setPassword("");
        return;
      }
      if (data.role !== "Backoffice" && data.role !== "GridOperator") {
        setError("This account is not allowed to use the web portal.");
        setPassword("");
        return;
      }
      signIn(data);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err.message);
      setPassword("");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-shell">
      <section className="login-brand">
        <div className="brand light">
          <span className="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="32" height="32">
              <circle cx="16" cy="13" r="4.2" fill="#e0a322" />
              <path d="M16 4.5v2.4M16 19.2v2.4M7.2 13H4.8M27.2 13h-2.4M9.2 6.2l1.7 1.7M21.1 18.1l1.7 1.7M9.2 19.8l1.7-1.7M21.1 7.9l1.7-1.7" stroke="#e0a322" strokeWidth="1.5" strokeLinecap="round" />
              <path d="M7 26.5h18" stroke="#9fb3c8" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </span>
          <div>
            <strong>Smart Solar</strong>
            <span>Microgrid Trading</span>
          </div>
        </div>
        <h1>Staff control for the solar microgrid.</h1>
        <p>Backoffice runs the system. Grid Operators run the day&apos;s bookings, slots, and QR handovers.</p>
        <ul className="login-points">
          <li><i className="bi bi-shield-check" /> Two staff roles, with administration kept to Backoffice.</li>
          <li><i className="bi bi-geo-alt" /> Hubs, battery slots, and trading windows.</li>
          <li><i className="bi bi-lightning-charge" /> Bookings inside 7 days, with 12 hours&apos; notice to change them.</li>
        </ul>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <p className="eyebrow">Staff sign in</p>
          <h2>Welcome back</h2>
          <p className="lede">Use your work email or NIC. Prosumer accounts stay on the mobile app.</p>
          <p className={`api-status ${online ? "is-online" : online === false ? "is-offline" : ""}`}>
            <i className="bi bi-circle-fill" />
            {online === null ? "Checking the trading API…" : online ? "Trading API connected" : "Trading API is not reachable"}
          </p>
          {error && <div className="banner is-danger" role="alert">{error}</div>}
          <form className="stack" onSubmit={onSubmit}>
            <label>Email or NIC
              <input value={identifier} onChange={(event) => setIdentifier(event.target.value)} autoComplete="username" placeholder="name@smartsolar.local" />
            </label>
            <label>Password
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
            </label>
            <button className="btn btn-primary btn-lg" type="submit" disabled={busy}>Sign in</button>
          </form>
          <details className="demo-note">
            <summary>Demonstration account</summary>
            <p>Backoffice · <span className="mono">backoffice@smartsolar.local</span> · <span className="mono">Admin@123</span></p>
            <p>Create Grid Operator accounts after you sign in.</p>
          </details>
        </div>
      </section>
    </div>
  );
}
