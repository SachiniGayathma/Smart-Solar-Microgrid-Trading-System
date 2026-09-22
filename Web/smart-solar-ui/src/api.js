import { ApiError } from "./api-error.js";
import { clearSession, loadSession } from "./session.js";

const API_BASE = import.meta.env.VITE_API_URL || "http://localhost:5192";

export { ApiError };

function readError(data, status) {
  if (data && typeof data.message === "string" && data.message.trim()) {
    return data.message;
  }
  if (data && data.errors && typeof data.errors === "object") {
    const parts = [];
    for (const value of Object.values(data.errors)) {
      if (Array.isArray(value)) {
        parts.push(...value.filter((item) => typeof item === "string"));
      }
    }
    if (parts.length > 0) return parts.join(" ");
  }
  if (data && typeof data.title === "string" && data.title.trim()) {
    return data.title;
  }
  return `The trading API returned HTTP ${status}.`;
}

export async function api(path, { method = "GET", body, auth = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth) {
    const token = loadSession()?.token;
    if (!token) {
      throw new ApiError("Your session has expired. Sign in again.", 401);
    }
    headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError("Cannot reach the trading API. Start the backend, then try again.", 0);
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (response.status === 401 && auth) {
    clearSession();
    window.dispatchEvent(new Event("smartsolar-unauthorized"));
  }

  if (!response.ok) {
    throw new ApiError(readError(data, response.status), response.status);
  }

  return data;
}
