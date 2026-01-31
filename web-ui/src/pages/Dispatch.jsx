import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiGet, apiPatch } from "../api.js";
import InfoPopover from "../components/InfoPopover.jsx";

const Dispatch = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");

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
      {error ? <div className="alert">{error}</div> : null}
      <div className="grid grid--dispatch">
        <section className="panel">
          <div className="panel__header">Roster</div>
          <div className="panel__body">
            {overview.roster.map((person) => (
              <div key={person.id} className="roster-item dispatch-card">
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
        <section className="panel">
          <div className="panel__header">Teams</div>
          <div className="panel__body">
            {overview.teams.map((team) => (
              <div key={team.id} className="team-card dispatch-card">
                <InfoPopover
                  title={team.name}
                  details={[
                    { label: "Team ID", value: team.id },
                    {
                      label: "Users",
                      value: overview.teamMemberCounts?.[team.id] ?? 0
                    },
                    {
                      label: "Channels",
                      value: overview.channels.filter((channel) => channel.teamId === team.id).length
                    },
                    { label: "Connections", value: "No live telemetry" },
                    { label: "Bandwidth", value: "Not available" },
                    { label: "Latency", value: "Not available" },
                    { label: "Errors", value: "None reported" }
                  ]}
                />
                <div className="team-card__title">{team.name}</div>
                <div className="team-card__meta">Team ID: {team.id}</div>
              </div>
            ))}
          </div>
        </section>
        <section className="panel">
          <div className="panel__header">Channels</div>
          <div className="panel__body">
            {overview.channels.map((channel) => (
              <div key={channel.id} className="channel-card dispatch-card">
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
                <div className="channel-card__title">{channel.name}</div>
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
