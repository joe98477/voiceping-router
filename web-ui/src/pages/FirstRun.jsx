import React, { useMemo, useState } from "react";
import { apiPost } from "../api.js";

const FirstRun = ({ onComplete }) => {
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const strengthLabel = useMemo(() => {
    if (!password) {
      return "Enter a passphrase (12+ characters).";
    }
    if (password.length < 12) {
      return "Too short.";
    }
    if (password.length < 16) {
      return "Good length.";
    }
    return "Strong.";
  }, [password]);

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    if (!displayName.trim()) {
      setError("Display name is required.");
      return;
    }
    if (password.length < 12) {
      setError("Password must be at least 12 characters.");
      return;
    }
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setSaving(true);
    try {
      await apiPost("/api/auth/first-run", { displayName: displayName.trim(), password });
      await onComplete();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="screen screen--center">
      <div className="card card--auth">
        <div className="badge">First time setup</div>
        <h2>Secure your admin account</h2>
        <p>Set a display name and a strong password before continuing.</p>
        {error ? <div className="alert">{error}</div> : null}
        <form className="form" onSubmit={submit}>
          <label>
            Display name
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
          </label>
          <label>
            New password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <span className="hint">{strengthLabel}</span>
          </label>
          <label>
            Confirm password
            <input
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
          </label>
          <button className="btn btn--primary" type="submit" disabled={saving}>
            {saving ? "Saving..." : "Continue"}
          </button>
        </form>
      </div>
    </div>
  );
};

export default FirstRun;
