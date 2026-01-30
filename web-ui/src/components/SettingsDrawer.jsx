import React, { useMemo, useState } from "react";
import ViewSettings from "./SettingsTabs/ViewSettings.jsx";
import EventSettings from "./SettingsTabs/EventSettings.jsx";
import TeamsSettings from "./SettingsTabs/TeamsSettings.jsx";
import ChannelsSettings from "./SettingsTabs/ChannelsSettings.jsx";
import UsersSettings from "./SettingsTabs/UsersSettings.jsx";
import InvitesSettings from "./SettingsTabs/InvitesSettings.jsx";
import SystemSettings from "./SettingsTabs/SystemSettings.jsx";

const SettingsDrawer = ({ isOpen, onClose, user, eventId, overview, onReload, viewSettings, onViewSettingsChange }) => {
  const isAdmin = user && user.globalRole === "ADMIN";
  const tabs = useMemo(() => {
    const items = [
      { id: "view", label: "View", component: ViewSettings }
    ];
    if (isAdmin) {
      items.push(
        { id: "event", label: "Event", component: EventSettings },
        { id: "teams", label: "Teams", component: TeamsSettings },
        { id: "channels", label: "Channels", component: ChannelsSettings },
        { id: "users", label: "Users", component: UsersSettings },
        { id: "invites", label: "Invites", component: InvitesSettings },
        { id: "system", label: "System", component: SystemSettings }
      );
    }
    return items;
  }, [isAdmin]);
  const [activeTab, setActiveTab] = useState(tabs[0].id);

  if (!isOpen) {
    return null;
  }

  const ActiveComponent = tabs.find((tab) => tab.id === activeTab)?.component || ViewSettings;

  return (
    <div className="drawer-overlay" onClick={onClose}>
      <div className="drawer" onClick={(event) => event.stopPropagation()}>
        <header className="drawer__header">
          <div>
            <div className="badge">Settings</div>
            <h3>{isAdmin ? "Admin console" : "Dispatch console"}</h3>
          </div>
          <button className="btn btn--secondary" onClick={onClose}>
            Close
          </button>
        </header>
        <div className="drawer__tabs">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              className={`drawer__tab ${tab.id === activeTab ? "drawer__tab--active" : ""}`}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="drawer__content">
          <ActiveComponent
            eventId={eventId}
            overview={overview}
            onReload={onReload}
            viewSettings={viewSettings}
            onViewSettingsChange={onViewSettingsChange}
          />
        </div>
      </div>
    </div>
  );
};

export default SettingsDrawer;
