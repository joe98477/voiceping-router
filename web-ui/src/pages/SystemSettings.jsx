import React, { useEffect, useState } from "react";
import { apiGet, apiGetStatus, apiPatch } from "../api.js";

const SystemSettings = ({ onLogout }) => {
  const [form, setForm] = useState({
    smtpHost: "",
    smtpPort: 587,
    smtpUser: "",
    smtpFrom: ""
  });
  const [smtpPass, setSmtpPass] = useState("");
  const [smtpPassSet, setSmtpPassSet] = useState(false);
  const [clearPass, setClearPass] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [status, setStatus] = useState(null);
  const [statusError, setStatusError] = useState("");
  const [statusLoading, setStatusLoading] = useState(false);

  useEffect(() => {
    apiGet("/api/admin/settings")
      .then((data) => {
        setForm({
          smtpHost: data.smtpHost || "",
          smtpPort: data.smtpPort || 587,
          smtpUser: data.smtpUser || "",
          smtpFrom: data.smtpFrom || ""
        });
        setSmtpPassSet(!!data.smtpPassSet);
      })
      .catch((err) => setError(err.message));
  }, []);

  const loadStatus = async () => {
    setStatusLoading(true);
    setStatusError("");
    const result = await apiGetStatus("/api/admin/status");
    if (result.data) {
      setStatus(result.data);
    }
    if (!result.ok) {
      setStatusError(result.error || "Unable to load status");
    }
    setStatusLoading(false);
  };

  useEffect(() => {
    loadStatus();
  }, []);

  const updateField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const save = async () => {
    setSaved(false);
    setError("");
    const payload = {
      smtpHost: form.smtpHost,
      smtpUser: form.smtpUser,
      smtpFrom: form.smtpFrom
    };
    const portValue = Number(form.smtpPort);
    if (form.smtpPort !== "" && !Number.isNaN(portValue)) {
      payload.smtpPort = portValue;
    }
    if (clearPass) {
      payload.smtpPass = "";
    } else if (smtpPass) {
      payload.smtpPass = smtpPass;
    }
    try {
      const data = await apiPatch("/api/admin/settings", payload);
      setForm({
        smtpHost: data.smtpHost || "",
        smtpPort: data.smtpPort || 587,
        smtpUser: data.smtpUser || "",
        smtpFrom: data.smtpFrom || ""
      });
      setSmtpPass("");
      setClearPass(false);
      setSmtpPassSet(!!data.smtpPassSet);
      setSaved(true);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="screen">
      <header className="topbar">
        <div>
          <div className="badge">System</div>
          <h2>Settings</h2>
          <p>SMTP configuration for invites and password resets.</p>
        </div>
        <div className="topbar__actions">
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>
      {error ? <div className="alert">{error}</div> : null}
      <div className="grid grid--admin">
        <section className="panel">
          <div className="panel__header">Email (SMTP)</div>
          <div className="panel__body form form--grid">
            <label>
              SMTP host
              <input
                type="text"
                value={form.smtpHost}
                onChange={(e) => updateField("smtpHost", e.target.value)}
                placeholder="smtp.example.com"
              />
            </label>
            <label>
              SMTP port
              <input
                type="number"
                value={form.smtpPort}
                onChange={(e) => updateField("smtpPort", e.target.value)}
              />
            </label>
            <label>
              SMTP user
              <input
                type="text"
                value={form.smtpUser}
                onChange={(e) => updateField("smtpUser", e.target.value)}
              />
            </label>
            <label>
              SMTP from
              <input
                type="text"
                value={form.smtpFrom}
                onChange={(e) => updateField("smtpFrom", e.target.value)}
                placeholder="no-reply@voiceping.local"
              />
            </label>
            <label>
              SMTP password {smtpPassSet ? "(set)" : "(not set)"}
              <input
                type="password"
                value={smtpPass}
                onChange={(e) => setSmtpPass(e.target.value)}
                placeholder="Leave empty to keep current"
              />
            </label>
            <label className="form__checkbox">
              <input
                type="checkbox"
                checked={clearPass}
                onChange={(e) => setClearPass(e.target.checked)}
              />
              Clear SMTP password
            </label>
            <div className="form__help">
              Leave host empty to disable email sending. The system will keep running without SMTP.
            </div>
            <button className="btn btn--primary" type="button" onClick={save}>
              Save settings
            </button>
            {saved ? <div className="pill pill--active">Saved</div> : null}
          </div>
        </section>
        <section className="panel">
          <div className="panel__header">System status</div>
          <div className="panel__body">
            <div className="form__actions">
              <button className="btn btn--secondary" type="button" onClick={loadStatus}>
                {statusLoading ? "Refreshing..." : "Refresh status"}
              </button>
            </div>
            {statusError ? <div className="alert">{statusError}</div> : null}
            {status ? (
              <div className="status-list status-list--compact">
                <div className="status-row">
                  <div className="status-row__label">Database</div>
                  <div className="status-row__detail">
                    {status.services?.database?.error || "OK"}
                  </div>
                  <div className={status.services?.database?.ok ? "pill pill--ok" : "pill pill--down"}>
                    {status.services?.database?.ok ? "Up" : "Down"}
                  </div>
                </div>
                <div className="status-row">
                  <div className="status-row__label">Redis</div>
                  <div className="status-row__detail">
                    {status.services?.redis?.error || "OK"}
                  </div>
                  <div className={status.services?.redis?.ok ? "pill pill--ok" : "pill pill--down"}>
                    {status.services?.redis?.ok ? "Up" : "Down"}
                  </div>
                </div>
                <div className="status-row">
                  <div className="status-row__label">Redis publisher</div>
                  <div className="status-row__detail">
                    {status.services?.redisPublisher?.error || "OK"}
                  </div>
                  <div className={status.services?.redisPublisher?.ok ? "pill pill--ok" : "pill pill--down"}>
                    {status.services?.redisPublisher?.ok ? "Up" : "Down"}
                  </div>
                </div>
                <div className="status-row">
                  <div className="status-row__label">Router</div>
                  <div className="status-row__detail">
                    {status.services?.router?.error || `${status.services?.router?.latencyMs || 0}ms`}
                  </div>
                  <div className={status.services?.router?.ok ? "pill pill--ok" : "pill pill--down"}>
                    {status.services?.router?.ok ? "Up" : "Down"}
                  </div>
                </div>
              </div>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  );
};

export default SystemSettings;
