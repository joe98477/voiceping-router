import React, { useState } from "react";
import { apiPost } from "../../api.js";

const InvitesSettings = ({ eventId, overview }) => {
  const [form, setForm] = useState({
    teamId: "",
    channelIds: [],
    expiresInMinutes: 15,
    maxUses: 0,
    email: ""
  });
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  const update = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const toggleChannel = (channelId) => {
    setForm((prev) => {
      const next = new Set(prev.channelIds);
      if (next.has(channelId)) {
        next.delete(channelId);
      } else {
        next.add(channelId);
      }
      return { ...prev, channelIds: Array.from(next) };
    });
  };

  const submit = async () => {
    setError("");
    setResult(null);
    try {
      const data = await apiPost(`/api/events/${eventId}/invites`, {
        teamId: form.teamId || undefined,
        channelIds: form.channelIds,
        expiresInMinutes: Number(form.expiresInMinutes),
        maxUses: Number(form.maxUses),
        email: form.email || undefined
      });
      setResult(data);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="panel panel--compact">
      <div className="panel__header">Invites</div>
      <div className="panel__body form form--stack">
        {error ? <div className="alert">{error}</div> : null}
        <label>
          Team (optional)
          <select value={form.teamId} onChange={(e) => update("teamId", e.target.value)}>
            <option value="">None</option>
            {overview.teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>
        </label>
        <div className="checkbox-grid">
          <div>
            <div className="checkbox-grid__title">Default channels</div>
            {overview.channels.map((channel) => (
              <label key={channel.id} className="checkbox-grid__item">
                <input
                  type="checkbox"
                  checked={form.channelIds.includes(channel.id)}
                  onChange={() => toggleChannel(channel.id)}
                />
                {channel.name}
              </label>
            ))}
          </div>
        </div>
        <label>
          Invite expiry (minutes)
          <input
            type="number"
            value={form.expiresInMinutes}
            onChange={(e) => update("expiresInMinutes", e.target.value)}
          />
        </label>
        <label>
          Max uses (0 = unlimited)
          <input type="number" value={form.maxUses} onChange={(e) => update("maxUses", e.target.value)} />
        </label>
        <label>
          Email invite (optional)
          <input value={form.email} onChange={(e) => update("email", e.target.value)} />
        </label>
        <button className="btn btn--primary" type="button" onClick={submit}>
          Create invite
        </button>
        {result ? (
          <div className="pill pill--active">
            Token: {result.token} • Expires: {new Date(result.expiresAt).toLocaleString()}
          </div>
        ) : null}
      </div>
    </div>
  );
};

export default InvitesSettings;
