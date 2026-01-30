import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiGet, apiPatch } from "../api.js";
import SettingsDrawer from "../components/SettingsDrawer.jsx";

const Console = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [viewSettings, setViewSettings] = useState(() => {
    const fallback = {
      showRoster: true,
      showTeams: true,
      showChannels: true,
      density: "comfortable",
      sound: true
    };
    try {
      const raw = localStorage.getItem("vp.viewSettings");
      return raw ? { ...fallback, ...JSON.parse(raw) } : fallback;
    } catch (err) {
      return fallback;
    }
  });

  const loadOverview = () => {
    setError("");
    apiGet(`/api/events/${eventId}/overview`)
      .then((data) => {
        setOverview(data);
        setError("");
      })
      .catch((err) => {
        if (err.status === 412) {
          navigate("/first-run", { replace: true });
          return;
        }
        setError(err.message);
      });
  };

  useEffect(() => {
    loadOverview();
  }, [eventId]);

  useEffect(() => {
    localStorage.setItem("vp.viewSettings", JSON.stringify(viewSettings));
  }, [viewSettings]);

  const approveUser = async (userId) => {
    await apiPatch(`/api/events/${eventId}/users/${userId}/approve`);
    loadOverview();
  };

  if (!overview && error) {
    return (
      <div className="screen screen--center">
        <div>
          <div className="alert">{error}</div>
          <button className="btn" onClick={loadOverview}>
            Retry
          </button>
        </div>
      </div>
    );
  }
  if (!overview) {
    return <div className="screen screen--center">Loading...</div>;
  }

  return (
    <div className="screen">
      <header className="topbar">
        <div>
          <div className="badge">Dispatch</div>
          <h2>{overview.event.name}</h2>
          <p>Event ID: {eventId}</p>
        </div>
        <div className="topbar__actions">
          <span className="pill">Pending: {overview.pendingCount}</span>
          <button className="icon-btn" onClick={() => setSettingsOpen(true)} aria-label="Open settings">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Zm9 3.5a7.7 7.7 0 0 0-.1-1l2-1.5-2-3.5-2.4 1a7.4 7.4 0 0 0-1.7-1l-.4-2.6H9.6l-.4 2.6a7.4 7.4 0 0 0-1.7 1l-2.4-1-2 3.5 2 1.5a7.7 7.7 0 0 0 0 2l-2 1.5 2 3.5 2.4-1a7.4 7.4 0 0 0 1.7 1l.4 2.6h4.8l.4-2.6a7.4 7.4 0 0 0 1.7-1l2.4 1 2-3.5-2-1.5c.1-.3.1-.7.1-1Z"
                fill="currentColor"
              />
            </svg>
          </button>
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>
      {error ? <div className="alert">{error}</div> : null}
      <div className={`grid grid--console ${viewSettings.density === "dense" ? "grid--dense" : ""}`}>
        {viewSettings.showRoster ? (
          <section className="panel panel--roster">
          <div className="panel__header">Roster</div>
          <div className="panel__body">
            {overview.roster.map((person) => (
              <div key={person.id} className="roster-item">
                <div>
                  <div className="roster-item__name">{person.displayName || person.email}</div>
                  <div className="roster-item__meta">{person.role}</div>
                </div>
                <div className={`pill pill--${person.status.toLowerCase()}`}>{person.status}</div>
                {person.status === "PENDING" ? (
                  <button className="btn btn--tiny" onClick={() => approveUser(person.id)}>
                    Approve
                  </button>
                ) : null}
              </div>
            ))}
          </div>
        </section>
        ) : null}
        {viewSettings.showTeams ? (
          <section className="panel panel--teams">
            <div className="panel__header">Teams</div>
            <div className="panel__body">
              {overview.teams.map((team) => (
                <div key={team.id} className="team-card">
                  <div className="team-card__title">{team.name}</div>
                  <div className="team-card__meta">Team ID: {team.id}</div>
                </div>
              ))}
            </div>
          </section>
        ) : null}
        {viewSettings.showChannels ? (
          <section className="panel panel--channels">
            <div className="panel__header">Channels</div>
            <div className="panel__body">
              {overview.channels.map((channel) => (
                <div key={channel.id} className="channel-card">
                  <div className="channel-card__title">{channel.name}</div>
                  <div className="channel-card__meta">
                    <span>{channel.type === "EVENT_ADMIN" ? "Admin" : "Team"}</span>
                    <span>ID: {channel.id}</span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </div>
      <SettingsDrawer
        isOpen={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        user={user}
        eventId={eventId}
        overview={overview}
        onReload={loadOverview}
        viewSettings={viewSettings}
        onViewSettingsChange={setViewSettings}
      />
    </div>
  );
};

export default Console;
