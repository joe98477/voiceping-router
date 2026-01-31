import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiGet, apiPatch } from "../api.js";
import SettingsDrawer from "../components/SettingsDrawer.jsx";
import InfoPopover from "../components/InfoPopover.jsx";
import Icon from "../components/Icon.jsx";
import { statusLabel, statusToKey } from "../utils/status.js";
import { mdiCog, mdiHeadset, mdiSend, mdiVolumeHigh, mdiVolumeOff } from "../icons.js";

const Console = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");
  const [listeningChannelIds, setListeningChannelIds] = useState([]);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [isOnline, setIsOnline] = useState(() => navigator.onLine);
  const [listeningTargets, setListeningTargets] = useState(() => new Set());
  const [transmittingTargets, setTransmittingTargets] = useState(() => new Set());
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
  const listeningChannelIdsRef = useRef(listeningChannelIds);

  const loadOverview = () => {
    setError("");
    apiGet(`/api/events/${eventId}/overview`)
      .then((data) => {
        setOverview(data);
        setListeningChannelIds(data.listenerChannelIds || []);
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
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  useEffect(() => {
    localStorage.setItem("vp.viewSettings", JSON.stringify(viewSettings));
  }, [viewSettings]);

  useEffect(() => {
    listeningChannelIdsRef.current = listeningChannelIds;
  }, [listeningChannelIds]);

  const currentMember = overview?.roster?.find((person) => person.id === user.id);
  const hasDispatchPermission = user.globalRole === "ADMIN" || currentMember?.role === "DISPATCH";
  const controlsEnabled = isOnline && hasDispatchPermission;
  const disabledReason = !isOnline ? "Offline" : hasDispatchPermission ? "" : "No permission";

  const approveUser = async (userId) => {
    await apiPatch(`/api/events/${eventId}/users/${userId}/approve`);
    loadOverview();
  };

  const toggleChannelListen = async (channelId) => {
    const currentChannels = listeningChannelIdsRef.current;
    const isListening = currentChannels.includes(channelId);
    const nextChannelIds = isListening
      ? currentChannels.filter((id) => id !== channelId)
      : [...currentChannels, channelId];
    listeningChannelIdsRef.current = nextChannelIds;
    setListeningChannelIds(nextChannelIds);
    setError("");
    try {
      await apiPatch(`/api/events/${eventId}/users/${user.id}/channels`, { channelIds: nextChannelIds });
    } catch (err) {
      setError(err.message);
      loadOverview();
    }
  };

  const toggleListen = (targetKey) => {
    setListeningTargets((prev) => {
      const next = new Set(prev);
      if (next.has(targetKey)) {
        next.delete(targetKey);
      } else {
        next.add(targetKey);
      }
      return next;
    });
  };

  const toggleTransmit = (targetKey) => {
    setTransmittingTargets((prev) => {
      const next = new Set(prev);
      if (next.has(targetKey)) {
        next.delete(targetKey);
      } else {
        next.add(targetKey);
      }
      return next;
    });
  };

  useEffect(() => {
    if (!controlsEnabled) {
      setListeningTargets(new Set());
      setTransmittingTargets(new Set());
    }
  }, [controlsEnabled]);

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
        </div>
        <div className="topbar__actions">
          <span className="pill">Pending: {overview.pendingCount}</span>
          <button className="icon-btn" onClick={() => setSettingsOpen(true)} aria-label="Open settings">
            <Icon path={mdiCog} />
          </button>
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
      <div className={`grid grid--console ${viewSettings.density === "dense" ? "grid--dense" : ""}`}>
        {viewSettings.showRoster ? (
          <section className="panel panel--roster">
            <div className="panel__header">Roster</div>
            <div className="panel__body">
              {overview.roster.map((person) => (
                <div
                  key={person.id}
                  className={`roster-item status-card dispatch-card status-card--${statusToKey(
                    overview.statuses?.users?.[person.id]
                  )}`}
                >
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
        ) : null}
        {viewSettings.showTeams ? (
          <section className="panel panel--teams">
            <div className="panel__header">Teams</div>
            <div className="panel__body">
              {overview.teams.map((team) => (
                <div
                  key={team.id}
                  className={`team-card status-card dispatch-card status-card--${statusToKey(
                    overview.statuses?.teams?.[team.id]
                  )}`}
                >
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
                <div className="team-card__header">
                  <div className="team-card__title">{team.name}</div>
                    <span
                      className={`pill pill--status-${statusToKey(overview.statuses?.teams?.[team.id])}`}
                    >
                      {statusLabel(overview.statuses?.teams?.[team.id])}
                    </span>
                </div>
                <div className="card-controls">
                  <button
                    type="button"
                    className={`card-control ${listeningTargets.has(`team:${team.id}`) ? "card-control--active" : ""}`}
                    onClick={() => toggleListen(`team:${team.id}`)}
                    aria-pressed={listeningTargets.has(`team:${team.id}`)}
                    aria-label={`Listen to ${team.name}`}
                    disabled={!controlsEnabled}
                    title={controlsEnabled ? "Listen" : disabledReason}
                  >
                    <Icon path={mdiHeadset} size={16} />
                  </button>
                  <button
                    type="button"
                    className={`card-control card-control--transmit ${transmittingTargets.has(`team:${team.id}`) ? "card-control--active" : ""}`}
                    onClick={() => toggleTransmit(`team:${team.id}`)}
                    aria-pressed={transmittingTargets.has(`team:${team.id}`)}
                    aria-label={`Transmit to ${team.name}`}
                    disabled={!controlsEnabled}
                    title={controlsEnabled ? "Transmit" : disabledReason}
                  >
                    <Icon path={mdiSend} size={16} />
                  </button>
                </div>
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
                <div
                  key={channel.id}
                  className={`channel-card status-card dispatch-card status-card--${statusToKey(
                    overview.statuses?.channels?.[channel.id]
                  )}`}
                >
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
                  <div className="channel-card__header">
                    <div>
                      <div className="channel-card__title">{channel.name}</div>
                      <button
                        type="button"
                        className={`icon-btn icon-btn--small channel-card__toggle ${
                          listeningChannelIds.includes(channel.id) ? "is-listening" : "is-muted"
                        }`}
                        onClick={() => toggleChannelListen(channel.id)}
                        aria-pressed={listeningChannelIds.includes(channel.id)}
                        aria-label={
                          listeningChannelIds.includes(channel.id)
                            ? `Mute ${channel.name}`
                            : `Listen to ${channel.name}`
                        }
                        title={
                          listeningChannelIds.includes(channel.id)
                            ? "Mute channel"
                            : "Listen to channel"
                        }
                      >
                        <Icon
                          path={listeningChannelIds.includes(channel.id) ? mdiVolumeHigh : mdiVolumeOff}
                          size={16}
                        />
                      </button>
                    </div>
                    <span
                      className={`pill pill--status-${statusToKey(overview.statuses?.channels?.[channel.id])}`}
                    >
                      {statusLabel(overview.statuses?.channels?.[channel.id])}
                    </span>
                  </div>
                  <div className="channel-card__meta">
                    <span>{channel.type === "EVENT_ADMIN" ? "Admin" : "Team"}</span>
                  </div>
                  <div className="card-controls">
                    <button
                      type="button"
                      className={`card-control ${listeningTargets.has(`channel:${channel.id}`) ? "card-control--active" : ""}`}
                      onClick={() => toggleListen(`channel:${channel.id}`)}
                      aria-pressed={listeningTargets.has(`channel:${channel.id}`)}
                      aria-label={`Listen to ${channel.name}`}
                      disabled={!controlsEnabled}
                      title={controlsEnabled ? "Listen" : disabledReason}
                    >
                      <Icon path={mdiHeadset} size={16} />
                    </button>
                    <button
                      type="button"
                      className={`card-control card-control--transmit ${transmittingTargets.has(`channel:${channel.id}`) ? "card-control--active" : ""}`}
                      onClick={() => toggleTransmit(`channel:${channel.id}`)}
                      aria-pressed={transmittingTargets.has(`channel:${channel.id}`)}
                      aria-label={`Transmit to ${channel.name}`}
                      disabled={!controlsEnabled}
                      title={controlsEnabled ? "Transmit" : disabledReason}
                    >
                      <Icon path={mdiSend} size={16} />
                    </button>
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
