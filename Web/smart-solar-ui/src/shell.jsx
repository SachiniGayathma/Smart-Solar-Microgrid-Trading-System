import { createContext, useContext, useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "./auth.jsx";
import { clockLabel, initials, roleLabel, statusClass } from "./format.js";
import { consumeFlash } from "./session.js";

const PageContext = createContext(null);
const ConfirmContext = createContext(async () => false);

export function usePage(title, subtitle) {
  const setPage = useContext(PageContext);
  useEffect(() => {
    setPage({ title, subtitle });
    document.title = `${title} · Smart Solar`;
  }, [setPage, title, subtitle]);
}

export function useConfirm() {
  return useContext(ConfirmContext);
}

export function Shell() {
  const [page, setPage] = useState({ title: "Smart Solar", subtitle: "" });
  const [dialog, setDialog] = useState(null);

  function confirm(options) {
    return new Promise((resolve) => {
      setDialog({ message: options.message, danger: options.danger, resolve });
    });
  }

  function closeDialog(value) {
    dialog?.resolve(value);
    setDialog(null);
  }

  useEffect(() => {
    if (!dialog) return undefined;
    function onKey(event) {
      if (event.key === "Escape") closeDialog(false);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [dialog]);

  return (
    <PageContext.Provider value={setPage}>
      <ConfirmContext.Provider value={confirm}>
        <Frame page={page} />
        {dialog && (
          <div className="modal-back" onClick={() => closeDialog(false)}>
            <div className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title" onClick={(event) => event.stopPropagation()}>
              <h2 id="confirm-title">Confirm action</h2>
              <p>{dialog.message}</p>
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => closeDialog(false)}>Go back</button>
                <button type="button" className={dialog.danger ? "btn btn-danger" : "btn btn-primary"} onClick={() => closeDialog(true)}>Continue</button>
              </div>
            </div>
          </div>
        )}
      </ConfirmContext.Provider>
    </PageContext.Provider>
  );
}

function Frame({ page }) {
  const { session, signOut, isBackoffice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [flash, setFlash] = useState(null);
  const scope = new URLSearchParams(location.search).get("scope") || "current";
  const onBookings = location.pathname === "/bookings";

  useEffect(() => {
    setFlash(consumeFlash());
    setOpen(false);
  }, [location.pathname, location.search]);

  function bookingClass(expected) {
    return onBookings && scope === expected ? "nav-link is-active" : "nav-link";
  }

  return (
    <>
      <a className="skip" href="#content">Skip to content</a>
      <div className={open ? "backdrop is-open" : "backdrop"} onClick={() => setOpen(false)} />
      <div className="shell">
        <aside className={open ? "sidebar is-open" : "sidebar"}>
          <div className="brand">
            <BrandMark />
            <div>
              <strong>Smart Solar</strong>
              <span>Microgrid control</span>
            </div>
          </div>
          <nav aria-label="Primary">
            <p className="nav-label">Operations</p>
            <NavLink to="/dashboard" className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
              <i className="bi bi-speedometer2" /> Overview
            </NavLink>
            <NavLink to="/bookings?scope=current" className={bookingClass("current")}>
              <i className="bi bi-lightning-charge" /> Current bookings
            </NavLink>
            <NavLink to="/bookings?scope=pending" className={bookingClass("pending")}>
              <i className="bi bi-hourglass-split" /> Pending approval
            </NavLink>
            <NavLink to="/bookings?scope=history" className={bookingClass("history")}>
              <i className="bi bi-clock-history" /> Booking history
            </NavLink>
            <NavLink to="/verify" className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
              <i className="bi bi-qr-code-scan" /> Verify QR
            </NavLink>
            <p className="nav-label">Network</p>
            <NavLink to="/slots" className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
              <i className="bi bi-calendar2-week" /> Energy slots
            </NavLink>
            <NavLink to="/stations" className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
              <i className="bi bi-geo-alt" /> Microgrid nodes
            </NavLink>
            {isBackoffice && (
              <>
                <p className="nav-label">Administration</p>
                <NavLink to="/staff" className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
                  <i className="bi bi-person-badge" /> Staff accounts
                </NavLink>
                <NavLink to="/prosumers" className={({ isActive }) => `nav-link${isActive ? " is-active" : ""}`}>
                  <i className="bi bi-people" /> Prosumers
                </NavLink>
              </>
            )}
          </nav>
          <div className="sidebar-foot">
            <div className="who">
              <span className="avatar">{initials(session?.fullName)}</span>
              <div>
                <strong>{session?.fullName || "Staff"}</strong>
                <span>{roleLabel(session?.role)}</span>
              </div>
            </div>
            <button
              type="button"
              className="signout"
              onClick={() => {
                signOut();
                navigate("/login", { replace: true });
              }}
            >
              <i className="bi bi-box-arrow-right" /> Sign out
            </button>
          </div>
        </aside>
        <div className="main">
          <header className="topbar">
            <button type="button" className="menu-btn" aria-label="Open menu" onClick={() => setOpen(true)}>
              <i className="bi bi-list" />
            </button>
            <div className="topbar-title">
              <h1>{page.title}</h1>
              {page.subtitle && <p>{page.subtitle}</p>}
            </div>
            <div className="topbar-meta">
              <span className="role-chip">{roleLabel(session?.role)}</span>
              <time title="Sri Lanka time">{clockLabel()}</time>
            </div>
          </header>
          <main className="page" id="content">
            {flash && <Banner tone={flash.tone}>{flash.message}</Banner>}
            <Outlet />
          </main>
        </div>
      </div>
    </>
  );
}

export function Banner({ tone = "danger", children }) {
  if (!children) return null;
  return <div className={`banner is-${tone}`} role={tone === "ok" ? "status" : "alert"}>{children}</div>;
}

export function Status({ value }) {
  return <span className={`status ${statusClass(value)}`}>{value}</span>;
}

export function Rule({ title, children }) {
  return (
    <div className="rule">
      <strong>{title}</strong> {children}
    </div>
  );
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

function BrandMark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" width="28" height="28">
        <circle cx="16" cy="13" r="4.2" fill="#e0a322" />
        <path d="M16 4.5v2.4M16 19.2v2.4M7.2 13H4.8M27.2 13h-2.4M9.2 6.2l1.7 1.7M21.1 18.1l1.7 1.7M9.2 19.8l1.7-1.7M21.1 7.9l1.7-1.7" stroke="#e0a322" strokeWidth="1.5" strokeLinecap="round" />
        <path d="M7 26.5h18" stroke="#9fb3c8" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
    </span>
  );
}
