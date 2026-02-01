import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiGet, apiPatch, apiPost } from "../api.js";
import SettingsDrawer from "../components/SettingsDrawer.jsx";
import InfoPopover from "../components/InfoPopover.jsx";
import Icon from "../components/Icon.jsx";
import { statusLabel, statusToKey } from "../utils/status.js";
import { mdiCog, mdiHeadset, mdiSend, mdiVolumeHigh, mdiVolumeOff, mdiRepeat } from "../icons.js";
import { VoicePingAudioClient, getDeviceId, getRouterWsUrl } from "../utils/voicepingAudio.js";

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
  const [audioStatus, setAudioStatus] = useState({ connected: false, permission: "unknown" });
  const [audioError, setAudioError] = useState("");
  const [micDevices, setMicDevices] = useState([]);
  const [selectedMicId, setSelectedMicId] = useState("");
  const [loopbackChannelIds, setLoopbackChannelIds] = useState(() => new Set());
  const [viewSettings, setViewSettings] = useState(() => {
    const fallback = {
      showRoster: true,
      showTeams: true,
      showChannels: true,
      density: "comfortable",
      sound: true
    };
    try {
      const raw = localStorage.getItem("cv.viewSettings") || localStorage.getItem("vp.viewSettings");
      return raw ? { ...fallback, ...JSON.parse(raw) } : fallback;
    } catch (err) {
      return fallback;
    }
  });
  const listeningChannelIdsRef = useRef(listeningChannelIds);
  const audioClientRef = useRef(null);
  const [clock, setClock] = useState(() => new Date());

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

  const refreshAudioDevices = async () => {
    if (!window.isSecureContext && window.location.hostname !== "localhost") {
      setAudioError("Microphone access requires HTTPS or localhost");
      return;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
      setAudioError("Browser does not support microphone access");
      return;
    }
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const inputs = devices.filter((device) => device.kind === "audioinput");
      setMicDevices(inputs);
      setAudioError("");
      if (!selectedMicId && inputs[0]) {
        setSelectedMicId(inputs[0].deviceId);
      }
    } catch (err) {
      setAudioError("Unable to list microphone devices");
    }
  };

  const enableAudio = async () => {
    setAudioError("");
    if (!window.isSecureContext && window.location.hostname !== "localhost") {
      setAudioError("Microphone access requires HTTPS or localhost");
      return false;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setAudioError("Browser does not support microphone access");
      return false;
    }
    if (!window.AudioEncoder || !window.MediaStreamTrackProcessor) {
      setAudioError("Browser does not support low-latency audio capture");
      return false;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((track) => track.stop());
      setAudioStatus((prev) => ({ ...prev, permission: "granted" }));
      await refreshAudioDevices();
      setAudioError("");
      if (audioClientRef.current) {
        audioClientRef.current.resumeOutput();
      }
      return true;
    } catch (err) {
      setAudioStatus((prev) => ({ ...prev, permission: "denied" }));
      setAudioError("Microphone permission denied");
      return false;
    }
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
    if (!navigator.permissions || !navigator.permissions.query) {
      return;
    }
    navigator.permissions
      .query({ name: "microphone" })
      .then((status) => {
        setAudioStatus((prev) => ({ ...prev, permission: status.state }));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    refreshAudioDevices();
  }, []);

  useEffect(() => {
    localStorage.setItem("cv.viewSettings", JSON.stringify(viewSettings));
  }, [viewSettings]);

  useEffect(() => {
    listeningChannelIdsRef.current = listeningChannelIds;
  }, [listeningChannelIds]);

  useEffect(() => {
    if (!overview) {
      return;
    }
    let cancelled = false;
    const connectAudio = async () => {
      setAudioError("");
      try {
        const { token } = await apiPost("/api/router/token", { eventId });
        if (cancelled) {
          return;
        }
        const client = new VoicePingAudioClient({
          wsUrl: getRouterWsUrl(),
          token,
          userId: user.id,
          deviceId: getDeviceId(),
          onStatus: (status) => {
            setAudioStatus((prev) => ({ ...prev, ...status }));
            if (status.connected) {
              setAudioError("");
            }
          },
          onError: (message) => setAudioError(message)
        });
        client.updateRouting({ channels: overview.channels || [] });
        client.updateFilters({ listeningChannelIds, listeningTargets });
        client.updateLoopback(Array.from(loopbackChannelIds));
        client.connect();
        audioClientRef.current = client;
      } catch (err) {
        if (err.status === 403) {
          setAudioError("Audio unavailable: you are not active in this event");
        } else {
          setAudioError(err.message || "Unable to connect audio");
        }
      }
    };
    connectAudio();
    return () => {
      cancelled = true;
      if (audioClientRef.current) {
        audioClientRef.current.disconnect();
        audioClientRef.current = null;
      }
    };
  }, [overview, eventId, user.id]);

  useEffect(() => {
    if (!audioClientRef.current || !overview) {
      return;
    }
    audioClientRef.current.updateRouting({ channels: overview.channels || [] });
  }, [overview]);

  useEffect(() => {
    if (!audioClientRef.current) {
      return;
    }
    audioClientRef.current.updateFilters({ listeningChannelIds, listeningTargets });
  }, [listeningChannelIds, listeningTargets]);

  useEffect(() => {
    if (!audioClientRef.current) {
      return;
    }
    audioClientRef.current.updateLoopback(Array.from(loopbackChannelIds));
  }, [loopbackChannelIds]);

  useEffect(() => {
    if (audioStatus.permission === "granted" && audioClientRef.current) {
      audioClientRef.current.resumeOutput();
    }
  }, [audioStatus.permission]);

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

  const resolveTargetIds = (targetKey) => {
    if (!overview) {
      return [];
    }
    if (targetKey.startsWith("channel:")) {
      return [targetKey.split(":")[1]];
    }
    if (targetKey.startsWith("team:")) {
      const teamId = targetKey.split(":")[1];
      return overview.channels.filter((channel) => channel.teamId === teamId).map((channel) => channel.id);
    }
    return [];
  };

  const beginTransmit = async (targetKey) => {
    if (!controlsEnabled) {
      return;
    }
    setAudioError("");
    if (audioStatus.permission !== "granted") {
      const ok = await enableAudio();
      if (!ok) {
        return;
      }
    }
    if (!audioClientRef.current) {
      setAudioError("Audio connection not ready");
      return;
    }
    if (!audioStatus.connected) {
      setAudioError("Audio connection is offline");
      return;
    }
    const targetIds = resolveTargetIds(targetKey);
    if (targetIds.length === 0) {
      setAudioError("No channels available for this target");
      return;
    }
    setTransmittingTargets(new Set([targetKey]));
    audioClientRef.current.startTransmit({ targetIds, micDeviceId: selectedMicId });
  };

  const endTransmit = async (targetKey) => {
    if (!transmittingTargets.has(targetKey)) {
      return;
    }
    setTransmittingTargets(new Set());
    if (audioClientRef.current) {
      await audioClientRef.current.stopTransmit();
    }
  };

  const toggleLoopback = (channelId) => {
    setLoopbackChannelIds((prev) => {
      const next = new Set(prev);
      if (next.has(channelId)) {
        next.delete(channelId);
      } else {
        next.add(channelId);
      }
      return next;
    });
  };

  useEffect(() => {
    if (!controlsEnabled) {
      setListeningTargets(new Set());
      setTransmittingTargets(new Set());
      if (audioClientRef.current) {
        audioClientRef.current.stopTransmit();
      }
    }
  }, [controlsEnabled]);

  useEffect(() => {
    return () => {
      if (audioClientRef.current) {
        audioClientRef.current.stopTransmit();
      }
    };
  }, []);

  useEffect(() => {
    const interval = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(interval);
  }, []);

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
            <button
              className="icon-btn"
              onClick={() => setSettingsOpen(true)}
              aria-label="Open settings"
            >
              <Icon path={mdiCog} />
            </button>
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
          {viewSettings.showTeams ? (
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
                        onPointerDown={(event) => {
                          event.preventDefault();
                          event.currentTarget.setPointerCapture(event.pointerId);
                          beginTransmit(`team:${team.id}`);
                        }}
                        onPointerUp={() => endTransmit(`team:${team.id}`)}
                        onPointerLeave={() => endTransmit(`team:${team.id}`)}
                        onPointerCancel={() => endTransmit(`team:${team.id}`)}
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
        </div>
        <div className="control-plane__column">
          <section className="control-plane__panel panel">
            <div className="control-plane__panel-header">Audio Controls</div>
            <div className="audio-toolbar">
              <div className="audio-toolbar__row">
                <span className={`pill ${audioStatus.connected ? "pill--ok" : "pill--down"}`}>
                  {audioStatus.connected ? "Connected" : "Disconnected"}
                </span>
                <span className={`pill ${audioStatus.permission === "granted" ? "pill--ok" : "pill--down"}`}>
                  {audioStatus.permission === "granted"
                    ? "Mic ready"
                    : audioStatus.permission === "denied"
                    ? "Mic blocked"
                    : "Mic not enabled"}
                </span>
                <button className="btn btn--secondary" type="button" onClick={enableAudio}>
                  Enable audio
                </button>
                <button className="btn btn--secondary" type="button" onClick={refreshAudioDevices}>
                  Refresh devices
                </button>
              </div>
              <div className="audio-toolbar__row">
                <label>
                  Microphone
                  <select value={selectedMicId} onChange={(e) => setSelectedMicId(e.target.value)}>
                    {micDevices.length === 0 ? <option value="">No microphones</option> : null}
                    {micDevices.map((device) => (
                      <option key={device.deviceId} value={device.deviceId}>
                        {device.label || "Microphone"}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              {audioError ? <div className="alert">{audioError}</div> : null}
            </div>
          </section>
          {viewSettings.showChannels ? (
            <section className="control-plane__panel panel">
              <div className="control-plane__panel-header">Live Activity</div>
              <div className="control-plane__list">
                {overview.channels.map((channel) => (
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
                        className={`pill pill--status-${statusToKey(overview.statuses?.channels?.[channel.id])}`}
                      >
                        {statusLabel(overview.statuses?.channels?.[channel.id])}
                      </span>
                    </div>
                    <div className="dispatch-card__footer">
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
                          listeningChannelIds.includes(channel.id) ? "Mute channel" : "Listen to channel"
                        }
                      >
                        <Icon
                          path={listeningChannelIds.includes(channel.id) ? mdiVolumeHigh : mdiVolumeOff}
                          size={16}
                        />
                      </button>
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
                          className={`card-control ${loopbackChannelIds.has(channel.id) ? "card-control--active" : ""}`}
                          onClick={() => toggleLoopback(channel.id)}
                          aria-pressed={loopbackChannelIds.has(channel.id)}
                          aria-label={`Loopback test for ${channel.name}`}
                          disabled={!controlsEnabled}
                          title={controlsEnabled ? "Loopback test" : disabledReason}
                        >
                          <Icon path={mdiRepeat} size={16} />
                        </button>
                        <button
                          type="button"
                          className={`card-control card-control--transmit ${
                            transmittingTargets.has(`channel:${channel.id}`) ? "card-control--active" : ""
                          }`}
                          onPointerDown={(event) => {
                            event.preventDefault();
                            event.currentTarget.setPointerCapture(event.pointerId);
                            beginTransmit(`channel:${channel.id}`);
                          }}
                          onPointerUp={() => endTransmit(`channel:${channel.id}`)}
                          onPointerLeave={() => endTransmit(`channel:${channel.id}`)}
                          onPointerCancel={() => endTransmit(`channel:${channel.id}`)}
                          aria-pressed={transmittingTargets.has(`channel:${channel.id}`)}
                          aria-label={`Transmit to ${channel.name}`}
                          disabled={!controlsEnabled}
                          title={controlsEnabled ? "Transmit" : disabledReason}
                        >
                          <Icon path={mdiSend} size={16} />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ) : null}
        </div>
        <div className="control-plane__column">
          {viewSettings.showRoster ? (
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
          ) : null}
        </div>
      </div>
      <footer className="control-plane__footer panel">
        <div className="control-plane__footer-left">
          <span>Monitor: {listeningChannelIds.length}</span>
          <span>Audio: {audioStatus.connected ? "Connected" : "Disconnected"}</span>
          <span>Mic: {audioStatus.permission === "granted" ? "Ready" : "Blocked"}</span>
        </div>
        <button className="ptt-button" type="button" disabled>
          Push To Talk
        </button>
        <div className="control-plane__footer-right">
          <span>Emergency: Standby</span>
          <span>Supervisor override: Ready</span>
        </div>
      </footer>
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
