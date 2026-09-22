import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api.js";
import { formatWhen, matchesQuery } from "../format.js";
import { setFlash } from "../session.js";
import { Banner, Empty, Rule, Status, useConfirm, usePage } from "../shell.jsx";

export function Prosumers() {
  usePage("Prosumers", "NIC is the primary key. Deactivated profiles can only be reactivated by a Backoffice officer.");
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const query = params.get("q") || "";
  const status = params.get("status") || "";

  async function load() {
    try {
      const rows = await api("/api/users");
      setUsers((rows || []).filter((user) => user.role === "Prosumer"));
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const visible = users.filter((user) => {
    if (status && user.status !== status) return false;
    return matchesQuery([user.nic, user.fullName, user.email, user.phone], query);
  });

  async function changeStatus(user, activate) {
    const message = activate
      ? `${user.status === "Deactivated" ? "Reactivate" : "Activate"} ${user.fullName} (${user.nic})?`
      : `Deactivate ${user.fullName}? Only a Backoffice officer can reactivate this NIC.`;
    const ok = await confirm({ message, danger: !activate });
    if (!ok) return;
    try {
      await api(`/api/users/${user.id}/${activate ? "activate" : "deactivate"}`, { method: "POST" });
      setNotice({
        tone: "ok",
        text: activate ? "Prosumer account is active." : "Prosumer deactivated. Only a Backoffice officer can reactivate this account.",
      });
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
        <input className="search" name="q" defaultValue={query} placeholder="Search NIC, name, email, or phone" />
        <select className="filter" name="status" defaultValue={status} aria-label="Status">
          <option value="">All statuses</option>
          <option value="Pending">Pending</option>
          <option value="Active">Active</option>
          <option value="Deactivated">Deactivated</option>
        </select>
        <button className="btn btn-primary" type="submit">Filter</button>
        <Link className="btn btn-primary push" to="/prosumers/new"><i className="bi bi-plus-lg" /> Register prosumer</Link>
      </form>
      <p className="result-count">{visible.length} prosumer{visible.length === 1 ? "" : "s"}. NIC is the primary key.</p>
      <div className="table-panel">
        <table className="table">
          <thead>
            <tr>
              <th>NIC</th><th>Name</th><th>Email</th><th>Phone</th><th>Status</th><th>Registered</th><th></th>
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 && (
              <tr><td colSpan={7}><Empty>No prosumer profiles match this filter.</Empty></td></tr>
            )}
            {visible.map((user) => (
              <tr key={user.id}>
                <td className="mono">{user.nic}</td>
                <td><strong>{user.fullName}</strong></td>
                <td>{user.email}</td>
                <td>{user.phone || "—"}</td>
                <td><Status value={user.status} /></td>
                <td>{formatWhen(user.createdAt)}</td>
                <td className="row-actions">
                  {user.status !== "Active" && (
                    <button type="button" className="btn btn-sm btn-primary" onClick={() => changeStatus(user, true)}>
                      {user.status === "Deactivated" ? "Reactivate" : "Activate"}
                    </button>
                  )}
                  {user.status !== "Deactivated" && (
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => changeStatus(user, false)}>Deactivate</button>
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

export function ProsumerCreate() {
  usePage("Register prosumer", "New profiles stay Pending until a Backoffice officer activates them.");
  const navigate = useNavigate();
  const [form, setForm] = useState({ nic: "", fullName: "", email: "", phone: "", password: "" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function update(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    const next = {};
    if (!form.nic.trim()) next.nic = "NIC is required. It is the primary key for this profile.";
    if (!form.fullName.trim()) next.fullName = "Full name is required.";
    if (!form.email.trim() || !form.email.includes("@")) next.email = "Enter a valid email address.";
    if (!form.password) next.password = "Password is required.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    setBusy(true);
    setError("");
    try {
      await api("/api/auth/register", {
        method: "POST",
        auth: false,
        body: {
          nic: form.nic.trim(),
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
          password: form.password,
        },
      });
      setFlash(`Prosumer ${form.nic.trim().toUpperCase()} is registered and waiting for activation.`);
      navigate("/prosumers?status=Pending");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel narrow">
      <Rule title="Pending until you activate.">
        The profile is stored under the NIC. A deactivated account can only be turned back on by Backoffice.
      </Rule>
      <form className="form-grid" onSubmit={onSubmit}>
        <div className="span-2"><Banner>{error}</Banner></div>
        <label>NIC
          <input name="nic" value={form.nic} onChange={update} placeholder="200012345678" />
          <span className="hint">Primary key. Stored in uppercase.</span>
          <FieldError>{errors.nic}</FieldError>
        </label>
        <label>Full name
          <input name="fullName" value={form.fullName} onChange={update} />
          <FieldError>{errors.fullName}</FieldError>
        </label>
        <label>Email
          <input name="email" type="email" value={form.email} onChange={update} />
          <FieldError>{errors.email}</FieldError>
        </label>
        <label>Phone
          <input name="phone" value={form.phone} onChange={update} placeholder="07XXXXXXXX" />
        </label>
        <label className="span-2">Password
          <input name="password" type="password" autoComplete="new-password" value={form.password} onChange={update} />
          <FieldError>{errors.password}</FieldError>
        </label>
        <div className="span-2 form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy}>Register prosumer</button>
          <Link className="btn btn-ghost" to="/prosumers">Cancel</Link>
        </div>
      </form>
    </section>
  );
}

function FieldError({ children }) {
  if (!children) return null;
  return <span className="field-error">{children}</span>;
}
