import React from "react";

const renderStatusRow = (label, item) => {
  const ok = item && item.ok;
  const pillClass = ok ? "pill pill--ok" : "pill pill--down";
  const detail = item && item.error ? item.error : ok ? "OK" : "Unavailable";
  return (
    <div className="status-row" key={label}>
      <div className="status-row__label">{label}</div>
      <div className="status-row__detail">{detail}</div>
      <div className={pillClass}>{ok ? "Up" : "Down"}</div>
    </div>
  );
};

const Startup = ({ status, error, showDetails, onRetry }) => {
  const services = status ? status.services || {} : {};
  return (
    <div className="screen screen--center">
      <div className="card card--auth">
        <div className="card__header">
          <div className="badge">ConnectVoice</div>
          <h1>Starting services</h1>
          <p>Preparing the dispatch system. This should only take a moment.</p>
        </div>
        {error ? <div className="alert">{error}</div> : null}
        {showDetails ? (
          <div className="status-list">
            {renderStatusRow("Database", services.database)}
            {renderStatusRow("Redis", services.redis)}
            {renderStatusRow("Redis publisher", services.redisPublisher)}
            {renderStatusRow("Router", services.router)}
          </div>
        ) : null}
        <div className="form__actions">
          <button className="btn btn--secondary" type="button" onClick={onRetry}>
            Retry
          </button>
        </div>
      </div>
    </div>
  );
};

export default Startup;
