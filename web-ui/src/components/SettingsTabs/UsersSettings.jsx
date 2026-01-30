import React, { useEffect, useState } from "react";
import { apiGet, apiPatch, apiPost } from "../../api.js";

const UsersSettings = ({ eventId, overview, onReload }) => {
  const [users, setUsers] = useState([]);
  const [createError, setCreateError] = useState("");
  const [assignError, setAssignError] = useState("");
  const [create, setCreate] = useState({
    email: "",
    displayName: "",
    password: "",
    globalRole: "NONE",
    mustChangePassword: true
  });
  const [assign, setAssign] = useState({
    userId: "",
    role: "USER",
    teamIds: [],
    channelIds: []
  });

  const loadUsers = () => {
    apiGet("/api/admin/users")
      .then(setUsers)
      .catch((err) => setCreateError(err.message));
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const updateCreate = (key, value) => {
    setCreate((prev) => ({ ...prev, [key]: value }));
  };

  const updateAssignList = (key, id) => {
    setAssign((prev) => {
      const nextSet = new Set(prev[key]);
      if (nextSet.has(id)) {
        nextSet.delete(id);
      } else {
        nextSet.add(id);
      }
      return { ...prev, [key]: Array.from(nextSet) };
    });
  };

  const submitCreate = async () => {
    setCreateError("");
    try {
      await apiPost("/api/admin/users", {
        email: create.email,
        displayName: create.displayName || undefined,
        password: create.password,
        globalRole: create.globalRole,
        mustChangePassword: create.mustChangePassword
      });
      setCreate({
        email: "",
        displayName: "",
        password: "",
        globalRole: "NONE",
        mustChangePassword: true
      });
      loadUsers();
    } catch (err) {
      setCreateError(err.message);
    }
  };

  const submitAssign = async () => {
    setAssignError("");
    try {
      await apiPost(`/api/events/${eventId}/users`, {
        userId: assign.userId,
        role: assign.role,
        teamIds: assign.teamIds,
        channelIds: assign.channelIds
      });
      setAssign((prev) => ({ ...prev, teamIds: [], channelIds: [] }));
      onReload();
    } catch (err) {
      setAssignError(err.message);
    }
  };

  const toggleMustChange = async (userId, currentValue) => {
    try {
      await apiPatch(`/api/admin/users/${userId}`, { mustChangePassword: !currentValue });
      loadUsers();
    } catch (err) {
      setCreateError(err.message);
    }
  };

  return (
    <div className="panel panel--compact">
      <div className="panel__header">Users & roles</div>
      <div className="panel__body form form--stack">
        {createError ? <div className="alert">{createError}</div> : null}
        <div className="form__section">
          <h4>Create user</h4>
          <div className="form form--grid">
            <label>
              Email
              <input value={create.email} onChange={(e) => updateCreate("email", e.target.value)} />
            </label>
            <label>
              Display name
              <input value={create.displayName} onChange={(e) => updateCreate("displayName", e.target.value)} />
            </label>
            <label>
              Temp password
              <input
                type="password"
                value={create.password}
                onChange={(e) => updateCreate("password", e.target.value)}
              />
            </label>
            <label>
              Global role
              <select value={create.globalRole} onChange={(e) => updateCreate("globalRole", e.target.value)}>
                <option value="NONE">Standard</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>
            <label>
              <input
                type="checkbox"
                checked={create.mustChangePassword}
                onChange={(e) => updateCreate("mustChangePassword", e.target.checked)}
              />
              Require password reset
            </label>
            <button className="btn btn--primary" type="button" onClick={submitCreate}>
              Create user
            </button>
          </div>
        </div>
        <div className="form__section">
          <h4>Assign to event</h4>
          {assignError ? <div className="alert">{assignError}</div> : null}
          <div className="form form--grid">
            <label>
              User
              <select
                value={assign.userId}
                onChange={(e) => setAssign((prev) => ({ ...prev, userId: e.target.value }))}
              >
                <option value="">Select user</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.displayName || user.email}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Event role
              <select
                value={assign.role}
                onChange={(e) => setAssign((prev) => ({ ...prev, role: e.target.value }))}
              >
                <option value="USER">User</option>
                <option value="DISPATCH">Dispatch</option>
              </select>
            </label>
          </div>
          <div className="checkbox-grid">
            <div>
              <div className="checkbox-grid__title">Teams</div>
              {overview.teams.map((team) => (
                <label key={team.id} className="checkbox-grid__item">
                  <input
                    type="checkbox"
                    checked={assign.teamIds.includes(team.id)}
                    onChange={() => updateAssignList("teamIds", team.id)}
                  />
                  {team.name}
                </label>
              ))}
            </div>
            <div>
              <div className="checkbox-grid__title">Channels</div>
              {overview.channels.map((channel) => (
                <label key={channel.id} className="checkbox-grid__item">
                  <input
                    type="checkbox"
                    checked={assign.channelIds.includes(channel.id)}
                    onChange={() => updateAssignList("channelIds", channel.id)}
                  />
                  {channel.name}
                </label>
              ))}
            </div>
          </div>
          <button className="btn btn--secondary" type="button" onClick={submitAssign}>
            Assign
          </button>
        </div>
        <div className="form__section">
          <h4>All users</h4>
          <div className="list">
            {users.map((user) => (
              <div key={user.id} className="list__item list__item--split">
                <div>
                  <div className="list__title">{user.displayName || user.email}</div>
                  <div className="list__meta">{user.globalRole}</div>
                </div>
                <button
                  className="btn btn--tiny"
                  type="button"
                  onClick={() => toggleMustChange(user.id, user.mustChangePassword)}
                >
                  {user.mustChangePassword ? "Reset required" : "Reset optional"}
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default UsersSettings;
