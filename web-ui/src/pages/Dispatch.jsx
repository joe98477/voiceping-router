import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiGet, apiPatch } from "../api.js";
import InfoPopover from "../components/InfoPopover.jsx";
import { statusLabel, statusToKey } from "../utils/status.js";

const Dispatch = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");
  const [trafficByChannel, setTrafficByChannel] = useState({});
  const [clock, setClock] = useState(() => new Date());

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

  const loadTraffic = () => {
    apiGet(`/api/events/${eventId}/traffic`)
      .then((data) => {
        const next = (data.channels || []).reduce((acc, entry) => {
          acc[entry.channelId] = entry;
          return acc;
        }, {});
        setTrafficByChannel(next);
      })
      .catch(() => {});
  };

  useEffect(() => {
    loadOverview();
  }, [eventId]);

  useEffect(() => {
    loadTraffic();
    const interval = setInterval(loadTraffic, 2000);
    return () => clearInterval(interval);
  }, [eventId]);

  useEffect(() => {
    const interval = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(interval);
  }, []);

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
    <div className="control-plane">
      <header className="control-plane__topbar panel">
        <div className="control-plane__brand">
          <span>ConnectVoice</span>
          <h1>Dispatch Control Plane</h1>
          <div className="control-plane__subtitle">{overview.event.name}</div>
        </div>
        <div className="control-plane__stats">
          <div className="control-plane__stat">
            <span>Active Incidents</span>
            <strong>{overview.teams.length}</strong>
          </div>
          <div className="control-plane__stat">
            <span>Online Units</span>
            <strong>{overview.roster.length}</strong>
          </div>
          <div className="control-plane__stat">
            <span>Channels</span>
            <strong>{overview.channels.length}</strong>
          </div>
        </div>
        <div>
          <div className="control-plane__clock">{clock.toLocaleTimeString()}</div>
          <div className="control-plane__actions">
            <span className="pill">Pending: {overview.pendingCount}</span>
            <button className="btn" onClick={onLogout}>
              Log out
            </button>
          </div>
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
      <div className="control-plane__columns">
        <div className="control-plane__column">
          <section className="control-plane__panel panel">
            <div className="control-plane__panel-header">Talkgroups</div>
            <div className="control-plane__list">
              {overview.teams.map((team) => (
                <div key={team.id} className="talkgroup-item">
                  <div className="talkgroup-item__row">
                    <div className="team-card__title">{team.name}</div>
                    <span
                      className={`pill pill--status-${statusToKey(overview.statuses?.teams?.[team.id])}`}
                    >
                      {statusLabel(overview.statuses?.teams?.[team.id])}
                    </span>
                  </div>
                  <div className="talkgroup-item__meta">
                    Channels: {overview.channels.filter((channel) => channel.teamId === team.id).length}
                  </div>
                </div>
              ))}
            </div>
          </section>
        </div>
        <div className="control-plane__column">
          <section className="control-plane__panel panel">
            <div className="control-plane__panel-header">Live Activity</div>
            <div className="control-plane__list">
              {overview.channels.map((channel) => {
                const traffic = trafficByChannel[channel.id];
                const isActive = !!traffic?.active;
                return (
                  <div key={channel.id} className="live-activity-item">
                    <div className="dispatch-card__header">
                      <div className="dispatch-card__title-row">
                        <div className="live-activity-item__title">{channel.name}</div>
                        <InfoPopover
                          title={channel.name}
                          details={[
                            { label: "Channel ID", value: channel.id },
                            {
                              label: "Users",
                              value: overview.channelMemberCounts?.[channel.id] ?? 0
                            },
                            { label: "Type", value: channel.type === "EVENT_ADMIN" ? "Admin" : "Team" },
                            {
                              label: "Team",
                              value: channel.teamId
                                ? overview.teams.find((team) => team.id === channel.teamId)?.name || "Unknown"
                                : "Event"
                            },
                            { label: "Connections", value: "No live telemetry" },
                            { label: "Bandwidth", value: "Not available" },
                            { label: "Latency", value: "Not available" },
                            { label: "Errors", value: "None reported" }
                          ]}
                        />
                      </div>
                      <span
                        className={`pill pill--status-${statusToKey(
                          overview.statuses?.channels?.[channel.id]
                        )}`}
                      >
                        {statusLabel(overview.statuses?.channels?.[channel.id])}
                      </span>
                    </div>
                    <div className="talkgroup-item__meta">
                      {isActive ? "Active traffic" : "Idle"}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        </div>
        <div className="control-plane__column">
          <section className="control-plane__panel panel">
            <div className="control-plane__panel-header">Roster</div>
            <div className="control-plane__list">
              {overview.roster.map((person) => (
                <div
                  key={person.id}
                  className={`roster-panel-item status-card status-card--${statusToKey(
                    overview.statuses?.users?.[person.id]
                  )}`}
                >
                  <div className="dispatch-card__header">
                    <div className="dispatch-card__title-row">
                      <div className="info-card__title">{person.displayName || person.email}</div>
                      <InfoPopover
                        title={person.displayName || person.email}
                        details={[
                          { label: "User ID", value: person.id },
                          { label: "Role", value: person.role },
                          { label: "Status", value: person.status },
                          { label: "Connections", value: "No live telemetry" },
                          { label: "Bandwidth", value: "Not available" },
                          { label: "Latency", value: "Not available" },
                          { label: "Errors", value: "None reported" }
                        ]}
                      />
                    </div>
                    <span
                      className={`pill pill--status-${statusToKey(overview.statuses?.users?.[person.id])}`}
                    >
                      {statusLabel(overview.statuses?.users?.[person.id])}
                    </span>
                  </div>
                  <div className="info-card__meta">{person.role}</div>
                  {person.status === "PENDING" ? (
                    <div className="dispatch-card__footer">
                      <span className="pill pill--pending">Pending approval</span>
                      <button className="btn btn--tiny" onClick={() => approveUser(person.id)}>
                        Approve
                      </button>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>
      <footer className="control-plane__footer panel">
        <div className="control-plane__footer-left">
          <span>Monitor: {overview.roster.length}</span>
          <span>Incident: {overview.event.name}</span>
        </div>
        <button className="ptt-button" type="button" disabled>
          Push To Talk
        </button>
        <div className="control-plane__footer-right">
          <span>Emergency: Standby</span>
          <span>Supervisor override: Ready</span>
        </div>
      </footer>
    </div>
  );
};

export default Dispatch;
