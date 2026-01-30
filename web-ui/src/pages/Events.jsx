import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiGet } from "../api.js";

const Events = ({ user, onLogout }) => {
  const [events, setEvents] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    apiGet("/api/events")
      .then(setEvents)
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div className="screen">
      <header className="topbar">
        <div>
          <div className="badge">Events</div>
          <h2>Welcome, {user.displayName || user.email}</h2>
          <p>Select an event to manage or join.</p>
        </div>
        <button className="btn" onClick={onLogout}>
          Log out
        </button>
      </header>
      {error ? <div className="alert">{error}</div> : null}
      <div className="grid grid--events">
        {events.map((event) => (
          <div key={event.id} className="card card--event">
            <div className="card__header">
              <h3>{event.name}</h3>
              <span className="pill">{event.requiresApproval ? "Approval Required" : "Open"}</span>
            </div>
            <div className="card__body">
              <p>Event ID: {event.id}</p>
              <div className="card__actions">
                <Link className="btn btn--secondary" to={`/event/${event.id}/dispatch`}>
                  Dispatch View
                </Link>
                {user.globalRole === "ADMIN" ? (
                  <Link className="btn btn--primary" to={`/event/${event.id}/admin`}>
                    Admin Settings
                  </Link>
                ) : null}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Events;
