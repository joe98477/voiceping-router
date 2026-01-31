import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { apiGet, apiPatch } from "../api.js";

const Admin = ({ onLogout }) => {
  const { eventId } = useParams();
  const [event, setEvent] = useState(null);
  const [limits, setLimits] = useState(null);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    apiGet(`/api/events/${eventId}/overview`)
      .then((data) => {
        setEvent(data.event);
        setLimits(data.event.limits);
      })
      .catch((err) => setError(err.message));
  }, [eventId]);

  const updateLimit = (key, value) => {
    setLimits((prev) => ({ ...prev, [key]: Number(value) }));
  };

  const save = async () => {
    setSaved(false);
    await apiPatch(`/api/events/${eventId}`, { limits, requiresApproval: event.requiresApproval, name: event.name });
    setSaved(true);
  };

  if (!event || !limits) {
    return <div className="screen screen--center">Loading…</div>;
  }

  return (
    <div className="screen">
      <header className="topbar">
        <div>
          <div className="badge">Admin</div>
          <h2>{event.name}</h2>
          <p>Event settings & limits</p>
          <p className="hint">Event ID: {eventId}</p>
        </div>
        <div className="topbar__actions">
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>
      {error ? <div className="alert">{error}</div> : null}
      <div className="grid grid--admin">
        <section className="panel">
          <div className="panel__header">Limits</div>
          <div className="panel__body form form--grid">
            <label>
              Users per event
              <input type="number" value={limits.maxUsers} onChange={(e) => updateLimit("maxUsers", e.target.value)} />
            </label>
            <label>
              Teams per event
              <input type="number" value={limits.maxTeams} onChange={(e) => updateLimit("maxTeams", e.target.value)} />
            </label>
            <label>
              Channels per event
              <input
                type="number"
                value={limits.maxChannels}
                onChange={(e) => updateLimit("maxChannels", e.target.value)}
              />
            </label>
            <label>
              Channels per team
              <input
                type="number"
                value={limits.maxChannelsPerTeam}
                onChange={(e) => updateLimit("maxChannelsPerTeam", e.target.value)}
              />
            </label>
            <label>
              Dispatchers per event
              <input
                type="number"
                value={limits.maxDispatch}
                onChange={(e) => updateLimit("maxDispatch", e.target.value)}
              />
            </label>
            <button className="btn btn--primary" type="button" onClick={save}>
              Save limits
            </button>
            {saved ? <div className="pill pill--active">Saved</div> : null}
          </div>
        </section>
      </div>
    </div>
  );
};

export default Admin;
