import React, { useEffect, useRef } from 'react';

/**
 * Default popup field settings
 * All fields ON by default per user decision
 * userName always shown (not toggleable)
 */
export const DEFAULT_POPUP_SETTINGS = {
  showLocation: true,
  showMotion: true,
  showChannel: true,
  showPTTStatus: true,
  showConnection: true,
  showBattery: true,
};

/**
 * Toggle field configuration array for DRY rendering
 */
const TOGGLE_FIELDS = [
  { key: 'showLocation', label: 'Location (coordinates, accuracy)' },
  { key: 'showMotion', label: 'Motion State' },
  { key: 'showChannel', label: 'Channel Membership' },
  { key: 'showPTTStatus', label: 'PTT Status' },
  { key: 'showConnection', label: 'Connection Quality' },
  { key: 'showBattery', label: 'Battery Level' },
];

/**
 * PopupSettingsPanel component
 * Slide-out panel from right edge of map with toggle switches
 * Controls which fields appear in marker popups (tooltips unchanged)
 *
 * @param {object} props
 * @param {boolean} props.isOpen - Panel visibility state
 * @param {function} props.onClose - Callback to close panel
 * @param {object} props.settings - Current settings object
 * @param {function} props.onSettingsChange - Callback to toggle a specific field (field) => void
 * @param {function} props.onReset - Callback to reset all settings to defaults
 */
const PopupSettingsPanel = ({ isOpen, onClose, settings, onSettingsChange, onReset }) => {
  const panelRef = useRef(null);

  // Click-away detection (with delay to prevent immediate close on open)
  useEffect(() => {
    if (!isOpen) return;

    // Delay listener attachment to prevent instant close from open click
    const timerId = setTimeout(() => {
      const handleClickAway = (event) => {
        if (panelRef.current && !panelRef.current.contains(event.target)) {
          onClose();
        }
      };

      document.addEventListener('mousedown', handleClickAway);

      return () => {
        document.removeEventListener('mousedown', handleClickAway);
      };
    }, 100);

    return () => clearTimeout(timerId);
  }, [isOpen, onClose]);

  return (
    <div ref={panelRef} className={`settings-panel ${isOpen ? 'settings-panel--open' : ''}`}>
      <div className="settings-panel__header">
        <h3 className="settings-panel__title">Popup Fields</h3>
        <button className="settings-panel__close" onClick={onClose} aria-label="Close settings">
          ×
        </button>
      </div>

      <div className="settings-panel__description">
        Choose which fields appear in marker popups. Tooltips are not affected.
      </div>

      {/* Non-toggleable field (userName always shown) */}
      <div className="settings-panel__field settings-panel__field--fixed">
        <span className="settings-panel__label">User Name</span>
        <span className="settings-panel__always-on">Always shown</span>
      </div>

      {/* Toggleable fields */}
      {TOGGLE_FIELDS.map(field => (
        <div key={field.key} className="settings-panel__field">
          <label className="settings-panel__label" htmlFor={`toggle-${field.key}`}>
            {field.label}
          </label>
          <input
            type="checkbox"
            id={`toggle-${field.key}`}
            className="settings-panel__toggle"
            checked={settings[field.key] ?? true}
            onChange={() => onSettingsChange(field.key)}
          />
        </div>
      ))}

      <button className="settings-panel__reset" onClick={onReset}>
        Reset to Defaults
      </button>
    </div>
  );
};

export default PopupSettingsPanel;
