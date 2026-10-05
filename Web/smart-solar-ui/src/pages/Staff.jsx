import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";
import { formatWhen, matchesQuery, roleLabel } from "../format.js";
import { setFlash } from "../session.js";
import { Banner, Empty, Status, useConfirm, usePage } from "../shell.jsx";

export function Staff() {
  usePage("Staff accounts", "Backoffice and Grid Operator accounts. Operators do not see this administration area.");
  const { session } = useAuth();
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const query = params.get("q") || "";
  const status = params.get("status") || "";
  const role = params.get("role") || "";

  async function load() {
    try {
      const rows = await api("/api/users");
      setUsers((rows || []).filter((user) => user.role === "Backoffice" || user.role === "GridOperator"));
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const visible = users.filter((user) => {
    if (role && user.role !== role) return false;
    if (status && user.status !== status) return false;
    return matchesQuery([user.fullName, user.nic, user.email, user.phone], query);
  });

  async function changeStatus(user, activate) {
    const message = activate
      ? `Reactivate ${user.fullName}? They will be able to sign in again.`
      : `Deactivate ${user.fullName}? Only a Backoffice officer can reactivate this account.`;
    const ok = await confirm({ message, danger: !activate });
    if (!ok) return;
    try {
      await api(`/api/users/${user.id}/${activate ? "activate" : "deactivate"}`, { method: "POST" });
      setNotice({
        tone: "ok",
        text: activate
          ? "Account reactivated. The user can sign in again."
          : "Account deactivated. Only a Backoffice officer can reactivate it.",
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
      <form key={`${query}|${status}|${role}`} className="toolbar" onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setParams({ q: String(data.get("q") || ""), status: String(data.get("status") || ""), role: String(data.get("role") || "") });
      }}>
        <input className="search" name="q" defaultValue={query} placeholder="Search name, NIC, email, or phone" />
        <select className="filter" name="role" defaultValue={role} aria-label="Role">
          <option value="">All roles</option>
          <option value="Backoffice">Backoffice</option>
          <option value="GridOperator">Grid Operator</option>
        </select>
        <select className="filter" name="status" defaultValue={status} aria-label="Status">
          <option value="">All statuses</option>
          <option value="Active">Active</option>
          <option value="Deactivated">Deactivated</option>
        </select>
        <button className="btn btn-primary" type="submit">Filter</button>
        <Link className="btn btn-primary push" to="/staff/new"><i className="bi bi-plus-lg" /> New staff account</Link>
      </form>
      <p className="result-count">{visible.length} staff account{visible.length === 1 ? "" : "s"}</p>
      <div className="table-panel">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th><th>NIC</th><th>Email</th><th>Phone</th><th>Role</th><th>Status</th><th>Created</th><th></th>
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 && (
              <tr><td colSpan={8}><Empty>No staff accounts match this filter.</Empty></td></tr>
            )}
            {visible.map((user) => (
              <tr key={user.id}>
                <td><strong>{user.fullName}</strong>{user.id === session?.id && <span className="hint"> You</span>}</td>
                <td className="mono">{user.nic}</td>
                <td>{user.email}</td>
                <td>{user.phone || "—"}</td>
                <td>{roleLabel(user.role)}</td>
                <td><Status value={user.status} /></td>
                <td>{formatWhen(user.createdAt)}</td>
                <td className="row-actions">
                  {user.status === "Deactivated" ? (
                    <button type="button" className="btn btn-sm btn-primary" onClick={() => changeStatus(user, true)}>Reactivate</button>
                  ) : (
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

export function StaffCreate() {
  usePage("New staff account", "Create a Backoffice administrator or a Grid Operator. Both can sign in to this portal.");
  const navigate = useNavigate();
  const [form, setForm] = useState({ nic: "", fullName: "", email: "", phone: "", password: "", role: "GridOperator" });
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
    if (!form.nic.trim()) next.nic = "NIC is required.";
    if (!form.fullName.trim()) next.fullName = "Full name is required.";
    if (!form.email.trim() || !form.email.includes("@")) next.email = "Enter a valid email address.";
    if (!form.password) next.password = "Password is required.";
    if (form.role !== "Backoffice" && form.role !== "GridOperator") next.role = "Choose Backoffice or Grid Operator.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    setBusy(true);
    setError("");
    try {
      await api("/api/users", {
        method: "POST",
        body: {
          nic: form.nic.trim(),
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
          password: form.password,
          role: form.role,
        },
      });
      setFlash(`${roleLabel(form.role)} account created for ${form.fullName.trim()}.`);
      navigate("/staff");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel narrow">
      <form className="form-grid" onSubmit={onSubmit}>
        <Banner>{error}</Banner>
        <label className="span-2">NIC
          <input name="nic" value={form.nic} onChange={update} placeholder="199812345V" />
          <FieldError>{errors.nic}</FieldError>
        </label>
        <label>Full name
          <input name="fullName" value={form.fullName} onChange={update} />
          <FieldError>{errors.fullName}</FieldError>
        </label>
        <label>Email
          <input name="email" type="email" value={form.email} onChange={update} placeholder="name@smartsolar.local" />
          <FieldError>{errors.email}</FieldError>
        </label>
        <label>Phone
          <input name="phone" value={form.phone} onChange={update} placeholder="07XXXXXXXX" />
        </label>
        <label>Password
          <input name="password" type="password" autoComplete="new-password" value={form.password} onChange={update} />
          <FieldError>{errors.password}</FieldError>
        </label>
        <fieldset className="span-2">
          <legend>Role</legend>
          <div className="choices">
            <label className="choice">
              <input type="radio" name="role" value="Backoffice" checked={form.role === "Backoffice"} onChange={update} />
              <span><strong>Backoffice</strong><small>System administration: staff, prosumers, hubs, and slots.</small></span>
            </label>
            <label className="choice">
              <input type="radio" name="role" value="GridOperator" checked={form.role === "GridOperator"} onChange={update} />
              <span><strong>Grid Operator</strong><small>Operational tools: bookings, availability, and QR completion.</small></span>
            </label>
          </div>
        </fieldset>
        <div className="span-2 form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy}>Create account</button>
          <Link className="btn btn-ghost" to="/staff">Cancel</Link>
        </div>
      </form>
    </section>
  );
}

function FieldError({ children }) {
  if (!children) return null;
  return <span className="field-error">{children}</span>;
}
