import React, { useEffect, useState } from "react";
import { apiGet, apiPatch } from "../../api.js";

const SystemSettings = () => {
  const [form, setForm] = useState({
    smtpHost: "",
    smtpPort: 587,
    smtpUser: "",
    smtpPass: "",
    smtpFrom: "",
    smtpPassSet: false
  });
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    apiGet("/api/admin/settings")
      .then((data) =>
        setForm({
          smtpHost: data.smtpHost || "",
          smtpPort: data.smtpPort || 587,
          smtpUser: data.smtpUser || "",
          smtpPass: "",
          smtpFrom: data.smtpFrom || "",
          smtpPassSet: data.smtpPassSet
        })
      )
      .catch((err) => setError(err.message));
  }, []);

  const update = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const save = async () => {
    setSaved(false);
    setError("");
    try {
      const data = await apiPatch("/api/admin/settings", {
        smtpHost: form.smtpHost,
        smtpPort: Number(form.smtpPort),
        smtpUser: form.smtpUser,
        smtpPass: form.smtpPass,
        smtpFrom: form.smtpFrom
      });
      setForm((prev) => ({ ...prev, smtpPass: "", smtpPassSet: data.smtpPassSet }));
      setSaved(true);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="panel panel--compact">
      <div className="panel__header">System settings</div>
      <div className="panel__body form form--grid">
        {error ? <div className="alert">{error}</div> : null}
        <label>
          SMTP host
          <input value={form.smtpHost} onChange={(e) => update("smtpHost", e.target.value)} />
        </label>
        <label>
          SMTP port
          <input type="number" value={form.smtpPort} onChange={(e) => update("smtpPort", e.target.value)} />
        </label>
        <label>
          SMTP user
          <input value={form.smtpUser} onChange={(e) => update("smtpUser", e.target.value)} />
        </label>
        <label>
          SMTP password {form.smtpPassSet ? "(set)" : "(not set)"}
          <input type="password" value={form.smtpPass} onChange={(e) => update("smtpPass", e.target.value)} />
        </label>
        <label>
          SMTP from
          <input value={form.smtpFrom} onChange={(e) => update("smtpFrom", e.target.value)} />
        </label>
        <div className="form__actions">
          <button className="btn btn--primary" type="button" onClick={save}>
            Save settings
          </button>
          {saved ? <span className="pill pill--active">Saved</span> : null}
        </div>
      </div>
    </div>
  );
};

export default SystemSettings;
