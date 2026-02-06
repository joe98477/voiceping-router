import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiGet, apiPost } from "../api.js";

const Events = ({ user, onLogout }) => {
  const [events, setEvents] = useState([]);
  const [error, setError] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createForm, setCreateForm] = useState({
    name: "",
    requiresApproval: false,
    limits: {
      maxUsers: 1000,
      maxTeams: 100,
      maxChannels: 200,
      maxChannelsPerTeam: 50,
      maxDispatch: 20
    }
  });

  useEffect(() => {
    apiGet("/api/events")
      .then(setEvents)
      .catch((err) => setError(err.message));
  }, []);

  const updateLimit = (key, value) => {
    setCreateForm((prev) => ({
      ...prev,
      limits: { ...prev.limits, [key]: Number(value) }
    }));
  };

  const createEvent = async () => {
    setCreateError("");
    if (!createForm.name.trim()) {
      setCreateError("Event name required.");
      return;
    }
    try {
      await apiPost("/api/events", {
        name: createForm.name.trim(),
        requiresApproval: createForm.requiresApproval,
        limits: createForm.limits
      });
      setShowCreate(false);
      setCreateForm((prev) => ({ ...prev, name: "" }));
      const data = await apiGet("/api/events");
      setEvents(data);
    } catch (err) {
      setCreateError(err.message);
    }
  };

  return (
    <div className="screen">
      <header className="topbar">
        <div>
          <div className="badge">Events</div>
          <h2>Welcome, {user.displayName || user.email}</h2>
          <p>Select an event to manage or join.</p>
        </div>
        <div className="topbar__actions">
          {user.globalRole === "ADMIN" ? (
            <>
              <button className="btn btn--secondary" onClick={() => setShowCreate(true)}>
                Create Event
              </button>
              <Link className="btn btn--secondary" to="/admin/settings">
                System Settings
              </Link>
            </>
          ) : null}
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>
      {error ? <div className="alert">{error}</div> : null}
      <div className="grid grid--events">
        {events.map((event) => (
          <div key={event.id} className="card card--event">
            <div className="card__header">
              <h3>{event.name}</h3>
              <span className="pill">{event.requiresApproval ? "Approval Required" : "Open"}</span>
            </div>
            <div className="card__body">
              <div className="card__actions">
                <Link className="btn btn--secondary" to={`/event/${event.id}/channels`}>
                  My Channels
                </Link>
                <Link className="btn btn--secondary" to={`/event/${event.id}/dispatch`}>
                  Dispatch View
                </Link>
                {user.globalRole === "ADMIN" ? (
                  <Link className="btn btn--primary" to={`/event/${event.id}/admin`}>
                    Admin Settings
                  </Link>
                ) : null}
              </div>
            </div>
          </div>
        ))}
      </div>
      {showCreate ? (
        <div className="drawer-overlay" onClick={() => setShowCreate(false)}>
          <div className="drawer" onClick={(event) => event.stopPropagation()}>
            <header className="drawer__header">
              <div>
                <div className="badge">Create event</div>
                <h3>New event</h3>
              </div>
              <button className="btn btn--secondary" onClick={() => setShowCreate(false)}>
                Close
              </button>
            </header>
            <div className="drawer__content">
              {createError ? <div className="alert">{createError}</div> : null}
              <div className="form form--grid">
                <label>
                  Event name
                  <input
                    value={createForm.name}
                    onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
                  />
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={createForm.requiresApproval}
                    onChange={(e) =>
                      setCreateForm((prev) => ({ ...prev, requiresApproval: e.target.checked }))
                    }
                  />
                  Require approval for new users
                </label>
                <label>
                  Users per event
                  <input
                    type="number"
                    value={createForm.limits.maxUsers}
                    onChange={(e) => updateLimit("maxUsers", e.target.value)}
                  />
                </label>
                <label>
                  Teams per event
                  <input
                    type="number"
                    value={createForm.limits.maxTeams}
                    onChange={(e) => updateLimit("maxTeams", e.target.value)}
                  />
                </label>
                <label>
                  Channels per event
                  <input
                    type="number"
                    value={createForm.limits.maxChannels}
                    onChange={(e) => updateLimit("maxChannels", e.target.value)}
                  />
                </label>
                <label>
                  Channels per team
                  <input
                    type="number"
                    value={createForm.limits.maxChannelsPerTeam}
                    onChange={(e) => updateLimit("maxChannelsPerTeam", e.target.value)}
                  />
                </label>
                <label>
                  Dispatchers per event
                  <input
                    type="number"
                    value={createForm.limits.maxDispatch}
                    onChange={(e) => updateLimit("maxDispatch", e.target.value)}
                  />
                </label>
                <button className="btn btn--primary" type="button" onClick={createEvent}>
                  Create
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default Events;
