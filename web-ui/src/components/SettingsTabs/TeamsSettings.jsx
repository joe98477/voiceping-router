import React, { useState } from "react";
import { apiPost } from "../../api.js";

const TeamsSettings = ({ eventId, overview, onReload }) => {
  const [name, setName] = useState("");
  const [error, setError] = useState("");

  const createTeam = async () => {
    if (!name.trim()) {
      setError("Team name required.");
      return;
    }
    setError("");
    try {
      await apiPost(`/api/events/${eventId}/teams`, { name: name.trim() });
      setName("");
      onReload();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="panel panel--compact">
      <div className="panel__header">Teams</div>
      <div className="panel__body">
        {error ? <div className="alert">{error}</div> : null}
        <div className="form form--inline">
          <input
            placeholder="New team name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <button className="btn btn--primary" type="button" onClick={createTeam}>
            Add team
          </button>
        </div>
        <div className="list">
          {overview.teams.map((team) => (
            <div key={team.id} className="list__item">
              <div>
                <div className="list__title">{team.name}</div>
                <div className="list__meta">ID: {team.id}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default TeamsSettings;
