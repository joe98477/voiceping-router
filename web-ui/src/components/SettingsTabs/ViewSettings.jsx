import React from "react";

const ViewSettings = ({ viewSettings, onViewSettingsChange }) => {
  const update = (key, value) => {
    const next = { ...viewSettings, [key]: value };
    onViewSettingsChange(next);
  };

  return (
    <div className="panel panel--compact">
      <div className="panel__header">Layout</div>
      <div className="panel__body form form--grid">
        <label>
          <input
            type="checkbox"
            checked={viewSettings.showRoster}
            onChange={(e) => update("showRoster", e.target.checked)}
          />
          Show roster
        </label>
        <label>
          <input
            type="checkbox"
            checked={viewSettings.showTeams}
            onChange={(e) => update("showTeams", e.target.checked)}
          />
          Show teams
        </label>
        <label>
          <input
            type="checkbox"
            checked={viewSettings.showChannels}
            onChange={(e) => update("showChannels", e.target.checked)}
          />
          Show channels
        </label>
        <label>
          Density
          <select value={viewSettings.density} onChange={(e) => update("density", e.target.value)}>
            <option value="comfortable">Comfortable</option>
            <option value="dense">Dense</option>
          </select>
        </label>
        <label>
          <input
            type="checkbox"
            checked={viewSettings.sound}
            onChange={(e) => update("sound", e.target.checked)}
          />
          Enable alert sounds
        </label>
      </div>
    </div>
  );
};

export default ViewSettings;
