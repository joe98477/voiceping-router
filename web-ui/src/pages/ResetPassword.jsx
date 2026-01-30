import React, { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { apiPost } from "../api.js";

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    setStatus("");
    try {
      await apiPost("/api/auth/reset-password", { token, password });
      setStatus("Password updated. Please log in.");
    } catch (err) {
      setStatus(err.message);
    }
  };

  return (
    <div className="screen screen--center">
      <div className="card card--auth">
        <div className="card__header">
          <div className="badge">Reset</div>
          <h1>Set new password</h1>
        </div>
        <form className="form" onSubmit={submit}>
          <label>
            New password
            <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" required />
          </label>
          {status ? <div className="form__note">{status}</div> : null}
          <button className="btn btn--primary" type="submit">
            Update password
          </button>
        </form>
      </div>
    </div>
  );
};

export default ResetPassword;
