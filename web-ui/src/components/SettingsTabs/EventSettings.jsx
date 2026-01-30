import React, { useEffect, useState } from "react";
import { apiPatch } from "../../api.js";

const EventSettings = ({ eventId, overview, onReload }) => {
  const [name, setName] = useState("");
  const [requiresApproval, setRequiresApproval] = useState(false);
  const [limits, setLimits] = useState(null);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (overview && overview.event) {
      setName(overview.event.name || "");
      setRequiresApproval(!!overview.event.requiresApproval);
      setLimits({ ...(overview.event.limits || {}) });
    }
  }, [overview]);

  const updateLimit = (key, value) => {
    setLimits((prev) => ({ ...prev, [key]: Number(value) }));
  };

  const save = async () => {
    setError("");
    setSaved(false);
    try {
      await apiPatch(`/api/events/${eventId}`, { name, requiresApproval, limits });
      setSaved(true);
      onReload();
    } catch (err) {
      setError(err.message);
    }
  };

  if (!limits) {
    return <div className="panel panel--compact">Loading...</div>;
  }

  return (
    <div className="panel panel--compact">
      <div className="panel__header">Event settings</div>
      <div className="panel__body form form--grid">
        {error ? <div className="alert">{error}</div> : null}
        <label>
          Event name
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          <input
            type="checkbox"
            checked={requiresApproval}
            onChange={(e) => setRequiresApproval(e.target.checked)}
          />
          Require approval for new users
        </label>
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
        <div className="form__actions">
          <button className="btn btn--primary" type="button" onClick={save}>
            Save event
          </button>
          {saved ? <span className="pill pill--active">Saved</span> : null}
        </div>
      </div>
    </div>
  );
};

export default EventSettings;
