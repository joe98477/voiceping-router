import React, { useState } from "react";
import { apiPost } from "../api.js";

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    try {
      await apiPost("/api/auth/login", { email, password });
      await onLogin();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="screen screen--center">
      <div className="card card--auth">
        <div className="card__header">
          <div className="badge">ConnectVoice</div>
          <h1>Dispatch Console</h1>
          <p>Secure sign-in for admins, dispatchers, and users.</p>
        </div>
        <form className="form" onSubmit={submit}>
          <label>
            Email
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
          </label>
          <label>
            Password
            <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" required />
          </label>
          {error ? <div className="form__error">{error}</div> : null}
          <button className="btn btn--primary" type="submit">
            Sign in
          </button>
        </form>
      </div>
    </div>
  );
};

export default Login;
