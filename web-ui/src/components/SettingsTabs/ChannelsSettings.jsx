import React, { useEffect, useState } from "react";
import { apiPost } from "../../api.js";

const ChannelsSettings = ({ eventId, overview, onReload }) => {
  const [adminName, setAdminName] = useState("");
  const [teamName, setTeamName] = useState("");
  const [teamId, setTeamId] = useState(overview.teams[0]?.id || "");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!teamId && overview.teams.length > 0) {
      setTeamId(overview.teams[0].id);
    }
  }, [overview.teams, teamId]);

  const createAdminChannel = async () => {
    if (!adminName.trim()) {
      setError("Channel name required.");
      return;
    }
    setError("");
    try {
      await apiPost(`/api/events/${eventId}/channels`, { name: adminName.trim() });
      setAdminName("");
      onReload();
    } catch (err) {
      setError(err.message);
    }
  };

  const createTeamChannel = async () => {
    if (!teamId) {
      setError("Select a team.");
      return;
    }
    if (!teamName.trim()) {
      setError("Channel name required.");
      return;
    }
    setError("");
    try {
      await apiPost(`/api/teams/${teamId}/channels`, { name: teamName.trim() });
      setTeamName("");
      onReload();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="panel panel--compact">
      <div className="panel__header">Channels</div>
      <div className="panel__body">
        {error ? <div className="alert">{error}</div> : null}
        <div className="form form--stack">
          <div className="form__section">
            <h4>Admin channel</h4>
            <div className="form form--inline">
              <input
                placeholder="New admin channel"
                value={adminName}
                onChange={(e) => setAdminName(e.target.value)}
              />
              <button className="btn btn--primary" type="button" onClick={createAdminChannel}>
                Add
              </button>
            </div>
          </div>
          <div className="form__section">
            <h4>Team channel</h4>
            <div className="form form--inline">
              <select value={teamId} onChange={(e) => setTeamId(e.target.value)}>
                <option value="">Select team</option>
                {overview.teams.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
              <input
                placeholder="New team channel"
                value={teamName}
                onChange={(e) => setTeamName(e.target.value)}
              />
              <button className="btn btn--primary" type="button" onClick={createTeamChannel}>
                Add
              </button>
            </div>
          </div>
        </div>
        <div className="list">
          {overview.channels.map((channel) => (
            <div key={channel.id} className="list__item">
              <div>
                <div className="list__title">{channel.name}</div>
                <div className="list__meta">
                  {channel.type === "EVENT_ADMIN" ? "Admin" : "Team"} • ID: {channel.id}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default ChannelsSettings;
