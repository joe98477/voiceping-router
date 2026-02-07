/**
 * AdminDrawer - Right-sliding side drawer for admin control-plane features
 * Slides from right when opened, contains admin tabs
 */

import React, { useMemo, useState } from 'react';
import EventSettings from './SettingsTabs/EventSettings.jsx';
import TeamsSettings from './SettingsTabs/TeamsSettings.jsx';
import ChannelsSettings from './SettingsTabs/ChannelsSettings.jsx';
import UsersSettings from './SettingsTabs/UsersSettings.jsx';
import InvitesSettings from './SettingsTabs/InvitesSettings.jsx';
import SystemSettings from './SettingsTabs/SystemSettings.jsx';

/**
 * AdminDrawer component
 *
 * @param {object} props
 * @param {boolean} props.isOpen - Whether drawer is open
 * @param {function} props.onClose - Close callback
 * @param {object} props.user - User object
 * @param {string} props.eventId - Event ID
 * @param {object} props.overview - Overview data
 * @param {function} props.onReload - Reload overview callback
 */
const AdminDrawer = ({ isOpen, onClose, user, eventId, overview, onReload }) => {
  const isAdmin = user && user.globalRole === 'ADMIN';

  const tabs = useMemo(() => {
    const items = [];
    if (isAdmin) {
      items.push(
        { id: 'event', label: 'Event', component: EventSettings },
        { id: 'teams', label: 'Teams', component: TeamsSettings },
        { id: 'channels', label: 'Channels', component: ChannelsSettings },
        { id: 'users', label: 'Users', component: UsersSettings },
        { id: 'invites', label: 'Invites', component: InvitesSettings },
        { id: 'system', label: 'System', component: SystemSettings }
      );
    }
    return items;
  }, [isAdmin]);

  const [activeTab, setActiveTab] = useState(tabs[0]?.id || '');

  if (!isOpen || tabs.length === 0) {
    return null;
  }

  const ActiveComponent = tabs.find(t => t.id === activeTab)?.component || tabs[0].component;

  return (
    <div className="admin-drawer-overlay" onClick={onClose}>
      <div className="admin-drawer" onClick={(e) => e.stopPropagation()}>
        <header className="admin-drawer__header">
          <div>
            <div className="badge">Admin</div>
            <h3>Control Plane</h3>
          </div>
          <button className="btn btn--secondary" onClick={onClose}>
            Close
          </button>
        </header>
        <div className="admin-drawer__tabs">
          {tabs.map(tab => (
            <button
              key={tab.id}
              className={`drawer__tab ${tab.id === activeTab ? 'drawer__tab--active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="admin-drawer__content">
          <ActiveComponent
            eventId={eventId}
            overview={overview}
            onReload={onReload}
          />
        </div>
      </div>
    </div>
  );
};

export default AdminDrawer;
