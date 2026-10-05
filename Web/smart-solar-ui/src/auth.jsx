import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { clearSession, loadSession, saveSession } from "./session.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => loadSession());

  useEffect(() => {
    function onUnauthorized() {
      clearSession();
      setSession(null);
    }
    window.addEventListener("smartsolar-unauthorized", onUnauthorized);
    return () => window.removeEventListener("smartsolar-unauthorized", onUnauthorized);
  }, []);

  const value = useMemo(() => {
    function signIn(data) {
      const next = {
        token: data.token,
        id: data.id,
        nic: data.nic,
        fullName: data.fullName,
        email: data.email,
        role: data.role,
        status: data.status,
      };
      saveSession(next);
      setSession(next);
    }

    function signOut() {
      clearSession();
      setSession(null);
    }

    return {
      session,
      signIn,
      signOut,
      isBackoffice: session?.role === "Backoffice",
    };
  }, [session]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

export function RequireStaff() {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  return <Outlet />;
}

export function RequireBackoffice() {
  const { isBackoffice } = useAuth();
  if (!isBackoffice) return <Navigate to="/denied" replace />;
  return <Outlet />;
}
