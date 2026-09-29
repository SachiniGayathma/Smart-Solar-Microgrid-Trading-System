import { useEffect, useState } from "react";

const HOURS = [12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
const MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55];

function pad(value) {
  return String(value).padStart(2, "0");
}

function to24(hour24, minute) {
  return `${pad(hour24)}:${pad(minute)}`;
}

function faceHour(hour24) {
  return hour24 % 12 || 12;
}

function periodOf(hour24) {
  return hour24 >= 12 ? "PM" : "AM";
}

function withHour(hour24, face) {
  const base = hour24 >= 12 ? 12 : 0;
  return (face % 12) + base;
}

function withPeriod(hour24, period) {
  const afternoon = hour24 >= 12;
  if (period === "PM" && !afternoon) return hour24 + 12;
  if (period === "AM" && afternoon) return hour24 - 12;
  return hour24;
}

export function parseSchedule(value) {
  const match = String(value || "").match(/(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})/);
  if (!match) return null;
  const openHour = Number(match[1]);
  const openMinute = Number(match[2]);
  const closeHour = Number(match[3]);
  const closeMinute = Number(match[4]);
  if ([openHour, closeHour].some((hour) => hour > 23) || [openMinute, closeMinute].some((minute) => minute > 59)) return null;
  return {
    open: { hour: openHour, minute: openMinute },
    close: { hour: closeHour, minute: closeMinute },
  };
}

export function formatSchedule(open, close) {
  return `${to24(open.hour, open.minute)}-${to24(close.hour, close.minute)} daily`;
}

function spot(index, radius) {
  const radians = ((index * 30) - 90) * (Math.PI / 180);
  return {
    left: `${50 + radius * Math.cos(radians)}%`,
    top: `${50 + radius * Math.sin(radians)}%`,
  };
}

function Clock({ label, time, onChange }) {
  const [mode, setMode] = useState("hour");
  const numbers = mode === "hour" ? HOURS : MINUTES;
  const selectedIndex = mode === "hour" ? time.hour % 12 : Math.round(time.minute / 5) % 12;
  const hand = mode === "hour" ? (time.hour % 12) * 30 : time.minute * 6;

  function choose(index) {
    if (mode === "hour") {
      onChange({ ...time, hour: withHour(time.hour, numbers[index]) });
      setMode("minute");
      return;
    }
    onChange({ ...time, minute: numbers[index] });
  }

  return (
    <div className="clock">
      <p className="clock-label">{label}</p>
      <div className="clock-readout">
        <button type="button" className={mode === "hour" ? "is-on" : ""} onClick={() => setMode("hour")}>{faceHour(time.hour)}</button>
        <span>:</span>
        <button type="button" className={mode === "minute" ? "is-on" : ""} onClick={() => setMode("minute")}>{pad(time.minute)}</button>
        <div className="ampm">
          {["AM", "PM"].map((period) => (
            <button
              key={period}
              type="button"
              className={periodOf(time.hour) === period ? "is-on" : ""}
              onClick={() => onChange({ ...time, hour: withPeriod(time.hour, period) })}
            >
              {period}
            </button>
          ))}
        </div>
      </div>
      <p className="hint">{mode === "hour" ? "Tap the hour on the clock." : "Tap the minutes."}</p>
      <div className="clock-face" role="group" aria-label={`${label} ${mode}`}>
        <span className="clock-hand" style={{ transform: `rotate(${hand}deg)` }} />
        <span className="clock-hub" />
        {numbers.map((number, index) => (
          <button
            key={number}
            type="button"
            className={index === selectedIndex ? "clock-num is-on" : "clock-num"}
            style={spot(index, 38)}
            onClick={() => choose(index)}
          >
            {mode === "hour" ? number : pad(number)}
          </button>
        ))}
      </div>
    </div>
  );
}

export function ScheduleClock({ value, onChange }) {
  const parsed = parseSchedule(value);
  const [open, setOpen] = useState(parsed?.open || { hour: 6, minute: 0 });
  const [close, setClose] = useState(parsed?.close || { hour: 18, minute: 0 });

  useEffect(() => {
    const next = parseSchedule(value);
    if (!next) return;
    setOpen(next.open);
    setClose(next.close);
  }, [value]);

  return (
    <div className="schedule-clocks">
      <Clock label="Opens" time={open} onChange={(next) => { setOpen(next); onChange(formatSchedule(next, close)); }} />
      <Clock label="Closes" time={close} onChange={(next) => { setClose(next); onChange(formatSchedule(open, next)); }} />
    </div>
  );
}
