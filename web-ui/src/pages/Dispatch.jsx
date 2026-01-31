import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { apiGet, apiPatch } from "../api.js";
import { statusLabel, statusToKey } from "../utils/status.js";

const Dispatch = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");

  const loadOverview = () => {
    apiGet(`/api/events/${eventId}/overview`)
      .then(setOverview)
      .catch((err) => setError(err.message));
  };

  useEffect(() => {
    loadOverview();
  }, [eventId]);

  const approveUser = async (userId) => {
    await apiPatch(`/api/events/${eventId}/users/${userId}/approve`);
    loadOverview();
  };

  if (!overview) {
    return <div className="screen screen--center">Loading…</div>;
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
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>
      <div className="status-legend">
        <span className="pill pill--status-active">Active</span>
        <span className="status-legend__text">Active traffic</span>
        <span className="pill pill--status-standby">Standby</span>
        <span className="status-legend__text">Idle but connected</span>
        <span className="pill pill--status-offline">Offline</span>
        <span className="status-legend__text">No users connected</span>
      </div>
      {error ? <div className="alert">{error}</div> : null}
      <div className="grid grid--dispatch">
        <section className="panel">
          <div className="panel__header">Roster</div>
          <div className="panel__body">
            {overview.roster.map((person) => (
              <div key={person.id} className="roster-item info-card">
              <div
                key={person.id}
                className={`roster-item status-card status-card--${statusToKey(
                  overview.statuses?.users?.[person.id]
                )}`}
              >
                <div>
                  <div className="info-card__title">{person.displayName || person.email}</div>
                  <div className="info-card__meta">{person.role}</div>
                </div>
                <div className="roster-item__actions">
                  <span
                    className={`pill pill--status-${statusToKey(overview.statuses?.users?.[person.id])}`}
                  >
                    {statusLabel(overview.statuses?.users?.[person.id])}
                  </span>
                  {person.status === "PENDING" ? (
                    <>
                      <span className="pill pill--pending">Pending approval</span>
                      <button className="btn btn--tiny" onClick={() => approveUser(person.id)}>
                        Approve
                      </button>
                    </>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </section>
        <section className="panel">
          <div className="panel__header">Teams</div>
          <div className="panel__body">
            {overview.teams.map((team) => (
              <div key={team.id} className="team-card info-card">
                <div className="info-card__title">{team.name}</div>
                <div className="info-card__meta">Team ID: {team.id}</div>
              <div
                key={team.id}
                className={`team-card status-card status-card--${statusToKey(
                  overview.statuses?.teams?.[team.id]
                )}`}
              >
                <div className="team-card__header">
                  <div className="team-card__title">{team.name}</div>
                  <span
                    className={`pill pill--status-${statusToKey(overview.statuses?.teams?.[team.id])}`}
                  >
                    {statusLabel(overview.statuses?.teams?.[team.id])}
                  </span>
                </div>
                <div className="team-card__meta">Team ID: {team.id}</div>
              </div>
            ))}
          </div>
        </section>
        <section className="panel">
          <div className="panel__header">Channels</div>
          <div className="panel__body">
            {overview.channels.map((channel) => (
              <div key={channel.id} className="channel-card info-card">
                <div className="info-card__title">{channel.name}</div>
                <div className="info-card__meta">
              <div
                key={channel.id}
                className={`channel-card status-card status-card--${statusToKey(
                  overview.statuses?.channels?.[channel.id]
                )}`}
              >
                <div className="channel-card__header">
                  <div className="channel-card__title">{channel.name}</div>
                  <span
                    className={`pill pill--status-${statusToKey(overview.statuses?.channels?.[channel.id])}`}
                  >
                    {statusLabel(overview.statuses?.channels?.[channel.id])}
                  </span>
                </div>
                <div className="channel-card__meta">
                  <span>{channel.type === "EVENT_ADMIN" ? "Admin" : "Team"}</span>
                  <span>ID: {channel.id}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};

export default Dispatch;
