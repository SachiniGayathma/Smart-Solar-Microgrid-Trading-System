const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function colomboParts(date = new Date()) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Colombo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value ?? "";
  return {
    year: get("year"),
    month: get("month"),
    day: get("day"),
    hour: get("hour") === "24" ? "00" : get("hour"),
    minute: get("minute"),
  };
}

export function formatWhen(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  const { year, month, day, hour, minute } = colomboParts(date);
  return `${day} ${MONTHS[Number(month) - 1]} ${year}, ${hour}:${minute}`;
}

export function formatWindow(start, end) {
  if (!start) return "—";
  const a = colomboParts(new Date(start));
  const left = `${a.day} ${MONTHS[Number(a.month) - 1]} ${a.year}, ${a.hour}:${a.minute}`;
  if (!end) return left;
  const b = colomboParts(new Date(end));
  if (a.year === b.year && a.month === b.month && a.day === b.day) {
    return `${left} – ${b.hour}:${b.minute}`;
  }
  return `${left} – ${b.day} ${MONTHS[Number(b.month) - 1]} ${b.year}, ${b.hour}:${b.minute}`;
}

export function toColomboInput(iso) {
  const { year, month, day, hour, minute } = colomboParts(new Date(iso));
  return `${year}-${month}-${day}T${hour}:${minute}`;
}

export function fromColomboInput(value) {
  return new Date(`${value}:00+05:30`).toISOString();
}

export function defaultSlotWindow() {
  const today = colomboParts();
  const start = new Date(`${today.year}-${today.month}-${today.day}T09:00:00+05:30`);
  start.setUTCDate(start.getUTCDate() + 1);
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000);
  return { start: toColomboInput(start.toISOString()), end: toColomboInput(end.toISOString()) };
}

export function greeting() {
  const hour = Number(colomboParts().hour);
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

export function clockLabel() {
  const { day, month, hour, minute } = colomboParts();
  const weekday = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Colombo",
    weekday: "short",
  }).format(new Date());
  return `${weekday} ${Number(day)} ${MONTHS[Number(month) - 1]}, ${hour}:${minute}`;
}

export function roleLabel(role) {
  if (role === "GridOperator") return "Grid Operator";
  if (role === "Backoffice") return "Backoffice";
  if (role === "Prosumer") return "Prosumer";
  return role || "Staff";
}

export function initials(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "SS";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export function statusClass(status) {
  if (status === "Active" || status === "Approved" || status === "Available") return "is-ok";
  if (status === "Pending") return "is-wait";
  if (status === "Completed") return "is-done";
  return "is-stop";
}

export function canModifyBooking(reservation) {
  if (!reservation) return false;
  if (reservation.status !== "Pending" && reservation.status !== "Approved") return false;
  return new Date(reservation.scheduledAt).getTime() >= Date.now() + 12 * 60 * 60 * 1000;
}

export function matchesQuery(values, query) {
  const term = query.trim().toLowerCase();
  if (!term) return true;
  return values.some((value) => String(value || "").toLowerCase().includes(term));
}

export function byName(left, right) {
  return String(left.name || left.fullName || "").localeCompare(String(right.name || right.fullName || ""));
}

export function inScope(reservation, scope) {
  const status = reservation.status;
  if (scope === "pending") return status === "Pending";
  if (scope === "approved") {
    return status === "Approved" && new Date(reservation.scheduledAt).getTime() > Date.now();
  }
  if (scope === "history") return status === "Completed" || status === "Cancelled";
  return status === "Pending" || status === "Approved";
}

export function decorateBookings(reservations, stations, slots) {
  const stationName = new Map((stations || []).map((station) => [station.id, station.name]));
  const slotById = new Map((slots || []).map((slot) => [slot.id, slot]));
  return (reservations || []).map((reservation) => {
    const slot = slotById.get(reservation.slotId);
    return {
      reservation,
      stationName: stationName.get(reservation.stationId) || "Unknown hub",
      window: slot ? formatWindow(slot.startTime, slot.endTime) : formatWhen(reservation.scheduledAt),
    };
  });
}
