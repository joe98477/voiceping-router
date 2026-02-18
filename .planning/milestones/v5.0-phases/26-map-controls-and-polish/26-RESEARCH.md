# Phase 26: Map Controls and Polish - Research

**Researched:** 2026-02-17
**Domain:** Leaflet map view control, React autocomplete search, localStorage settings persistence, glassmorphic UI
**Confidence:** HIGH

## Summary

Phase 26 completes the v5.0 Dispatch Map View by adding interactive controls and UX polish: auto-fit bounds, user/channel search with autocomplete, configurable popup field settings, and a glassmorphic floating toolbar. This phase integrates standard Leaflet map control methods (fitBounds, flyToBounds) with modern React patterns for search UX and settings persistence.

Key findings: (1) Leaflet's `flyToBounds()` with padding and maxZoom options provides smooth auto-fit animation with edge padding to prevent marker clipping, (2) React autocomplete with keyboard navigation follows ARIA patterns using arrow keys + Enter selection without external libraries, (3) localStorage settings persistence using custom `useLocalStorage` hook prevents rapid writes with debouncing, (4) glassmorphic toolbar uses `backdrop-filter: blur()` with semi-transparent backgrounds per 2026 UI trends, (5) slide-out settings panel uses CSS `transform: translateX()` with transitions for smooth animations.

**Primary recommendation:** Use Leaflet's built-in `flyToBounds()` for auto-fit with 50px padding and maxZoom cap, implement vanilla React autocomplete with fuzzy filtering (case-insensitive substring matching), create custom `useLocalStorage` hook for settings persistence with silent fallback to defaults, build glassmorphic toolbar with `backdrop-filter: blur(10px)` and `background: rgba(255,255,255,0.2)`, and animate settings panel with CSS `transform: translateX(100%)` transition.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Auto-fit behavior:**
- Auto-fit on initial load to show all markers from currently monitored channels
- "Fit All" button in a floating toolbar — also channel-filtered (matches initial behavior)
- Overview zoom cap when only one marker exists (don't zoom in too close)
- Smooth fly-to animation (not instant snap)
- Padding around markers (e.g., 50px) so markers aren't clipped at map edges
- If no markers to fit, show a brief toast/notification: "No locations to show"

**Floating toolbar:**
- Position: top center of the map
- Houses: Fit All button, search bar, and settings icon
- Semi-transparent glassmorphic style with backdrop blur — lets satellite map show through
- Always visible (no auto-hide)
- No layer toggle needed — Phase 23 already has Leaflet's built-in layer control

**User search UX:**
- Search input always expanded/visible in the toolbar (not icon-that-expands)
- Autocomplete dropdown with live filtering as user types
- Case-insensitive partial matching (matches anywhere in the name)
- Results ordered by closest match relevance
- Searches both user names and channel names
- Separate sections in dropdown: "Users" section and "Channels" section with headers
- Channel results expand to reveal individual members to select
- User results show name + online/offline status icon (online = location update within 5 minutes, consistent with staleness threshold)
- Users without location data shown in dropdown but grayed out with "No location" — not clickable
- On user selection: fly-to marker only (no auto-open popup)
- Search term stays after selecting a result (dropdown stays available for picking another)
- Escape closes dropdown only (text stays)
- Full keyboard navigation: arrow keys + Enter to select
- Max 8 visible items before scrolling
- Search always starts empty on page load (not persisted)

**Popup field settings:**
- Settings icon in toolbar opens a slide-out panel from the right edge of the map
- Toggle switches (modern on/off) for each popup field
- User name always shown (not toggleable); all other fields toggleable: location, motion, channel, PTT status, connection quality, battery
- All fields default to ON
- Controls popup content only (tooltip content stays unchanged)
- Live preview — changes apply instantly as toggles are flipped, no save button
- "Reset to defaults" button included
- Click-away dismisses the panel
- Settings panel always starts closed on page load

**State persistence:**
- Popup field preferences stored in global localStorage key: `cv.dispatch.popup.settings`
- Global scope (not event-scoped) — dispatcher preferences are personal, not per-event
- Phase 23 event-scoped zoom/center persistence left unchanged
- Silent fallback to all-fields-on defaults if localStorage corrupted or missing
- No version/migration handling — new fields get defaults, removed fields ignored
- Panel collapse state persistence still deferred (not in this phase)

### Claude's Discretion

- Toolbar icon style (icons-only vs icons+labels) — pick what fits
- Exact toolbar dimensions and spacing
- Toast notification style and duration for "No locations to show"
- Exact autocomplete debounce timing
- Search result highlighting of matched text
- Slide-out panel width and animation timing
- Settings panel visual layout and spacing

### Deferred Ideas (OUT OF SCOPE)

- Panel collapse state persistence — deferred from Phase 22, still not in scope
- Search term persistence across reloads — decided against for clean UX

</user_constraints>

## Standard Stack

### Core (Already Installed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| leaflet | 1.9.4 | flyToBounds, getBounds, map view control | Already installed (Phase 23), fitBounds/flyToBounds are core Leaflet methods |
| react | 18.3.1 | Custom hooks, state management, keyboard events | Already installed, no external autocomplete library needed |
| leaflet.markercluster | 1.5.3 | getBounds() for cluster groups | Already installed (Phase 25), needed to calculate bounds including clustered markers |

### No Additional Dependencies Required

All functionality available in existing stack and native browser APIs:

- **Leaflet map control methods** — `flyToBounds()`, `getBounds()`, `fitBounds()` built into Leaflet core
- **React hooks** — `useState`, `useEffect`, `useRef`, `useCallback`, `useMemo` for autocomplete and settings
- **localStorage API** — native browser API, no library needed
- **CSS backdrop-filter** — native CSS, broad browser support in 2026 (97%+ per caniuse.com)
- **CSS transitions** — native browser capability for slide-out panel animation
- **Fuzzy search** — case-insensitive substring matching with `.toLowerCase().includes()`, no Fuse.js needed for simple partial matching

**Installation:**
```bash
# No new packages needed
# All dependencies already in web-ui/package.json
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Vanilla autocomplete | react-search-autocomplete, downshift, react-aria | External libraries add bundle size; vanilla implementation is 50-100 LOC for simple substring matching with keyboard navigation |
| Custom useLocalStorage hook | use-local-storage-state, usehooks-ts | External hook libraries add dependency; custom hook is ~20 LOC with JSON parse/stringify and error handling |
| CSS transitions | react-spring, framer-motion | Animation libraries add 20-50kB; CSS transitions are declarative, GPU-accelerated, and sufficient for slide-out panel |
| flyToBounds() | fitBounds() | flyToBounds has smooth animation (user decision), fitBounds snaps instantly; both accept same padding/maxZoom options |
| Substring matching | Fuse.js fuzzy search | Fuse.js adds 12kB for true fuzzy matching (Levenshtein distance); user decision is "partial matching anywhere in name" which is simple substring matching |

## Architecture Patterns

### Recommended Project Structure

```
web-ui/src/
├── components/
│   ├── MapView.jsx                      # Existing — add toolbar, search, settings panel
│   ├── MapToolbar.jsx                   # NEW — glassmorphic toolbar with Fit All, search, settings
│   ├── MapSearch.jsx                    # NEW — autocomplete search input with dropdown
│   └── PopupSettingsPanel.jsx           # NEW — slide-out panel with toggle switches
├── context/
│   └── LocationContext.jsx              # Existing — add generatePopupContent with settings filter
├── hooks/
│   └── useLocalStorage.js               # NEW — custom hook for settings persistence
└── styles.css                           # Existing — add glassmorphic, search, panel styles
```

**Key principle:** Separate components for toolbar, search, and settings panel enable independent testing and reusability while keeping MapView.jsx focused on map rendering logic.

### Pattern 1: Leaflet Auto-Fit Bounds with Padding and Zoom Cap

**What:** Use `flyToBounds()` with padding option and maxZoom cap to smoothly animate map view to show all markers with edge padding

**When to use:** On initial load and "Fit All" button click to show all visible markers from monitored channels

**Example:**

```javascript
// Source: https://leafletjs.com/reference.html (Map methods)
/**
 * Auto-fit map to show all markers with smooth animation
 * @param {L.Map} map - Leaflet map instance
 * @param {Map<userId, position>} locations - LocationContext locations Map
 * @returns {boolean} - true if fitted, false if no markers
 */
function fitAllMarkers(map, locations) {
  // Filter to markers with location data (ignore users without location)
  const markerPositions = Array.from(locations.values()).filter(
    pos => pos.latitude && pos.longitude
  );

  if (markerPositions.length === 0) {
    // No markers to fit — show toast notification
    showToast('No locations to show');
    return false;
  }

  // Create LatLngBounds from marker positions
  const bounds = L.latLngBounds(
    markerPositions.map(pos => [pos.latitude, pos.longitude])
  );

  // Determine maxZoom based on marker count (prevent zooming too close for single marker)
  const maxZoom = markerPositions.length === 1 ? 14 : 18; // Overview zoom cap for single marker

  // Smooth fly-to animation with padding to prevent edge clipping
  map.flyToBounds(bounds, {
    padding: [50, 50],      // 50px padding on all edges
    maxZoom: maxZoom,       // Cap zoom level for single marker
    animate: true,          // Smooth animation
    duration: 1.0,          // 1 second animation (default: 0.25s)
    easeLinearity: 0.25     // Bezier curve factor
  });

  return true;
}

// Alternative with markerClusterGroup
function fitAllMarkersWithClustering(map, clusterGroup) {
  if (!clusterGroup || clusterGroup.getLayers().length === 0) {
    showToast('No locations to show');
    return false;
  }

  // Get bounds from cluster group (includes all markers)
  const bounds = clusterGroup.getBounds();

  const maxZoom = clusterGroup.getLayers().length === 1 ? 14 : 18;

  map.flyToBounds(bounds, {
    padding: [50, 50],
    maxZoom: maxZoom,
    animate: true,
    duration: 1.0
  });

  return true;
}
```

**Key insight:** `flyToBounds()` provides smooth animation (vs `fitBounds()` instant snap). Padding prevents markers from being clipped at map edges. maxZoom cap prevents excessive zoom-in when only one marker exists (user decision: "overview zoom cap").

**Sources:**
- [Leaflet Documentation - Map methods](https://leafletjs.com/reference.html)
- [Leaflet markercluster getBounds](https://github.com/Leaflet/Leaflet.markercluster)

### Pattern 2: React Autocomplete with Keyboard Navigation (ARIA Pattern)

**What:** Vanilla React autocomplete with live filtering, keyboard navigation (arrow keys + Enter), and ARIA attributes for accessibility

**When to use:** User/channel search with autocomplete dropdown (user decision: no external library, simple substring matching)

**Example:**

```javascript
// Source: ARIA autocomplete pattern + React best practices
import { useState, useRef, useEffect, useMemo } from 'react';

const MapSearch = ({ locations, channels, onSelectUser }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const inputRef = useRef(null);
  const dropdownRef = useRef(null);

  // Filter users and channels based on search term (case-insensitive partial matching)
  const filteredResults = useMemo(() => {
    if (!searchTerm.trim()) return { users: [], channels: [] };

    const term = searchTerm.toLowerCase();

    // Search users by name
    const users = Array.from(locations.values())
      .filter(pos => pos.userName.toLowerCase().includes(term))
      .map(pos => ({
        type: 'user',
        id: pos.userId,
        name: pos.userName,
        hasLocation: !!(pos.latitude && pos.longitude),
        isOnline: !pos.isStale, // Online = location update within 5 minutes
        teamName: pos.teamName,
        channelNames: pos.channelNames
      }))
      .sort((a, b) => {
        // Sort by relevance: exact match > starts with > contains
        const aName = a.name.toLowerCase();
        const bName = b.name.toLowerCase();
        const aExact = aName === term;
        const bExact = bName === term;
        if (aExact && !bExact) return -1;
        if (!aExact && bExact) return 1;
        const aStarts = aName.startsWith(term);
        const bStarts = bName.startsWith(term);
        if (aStarts && !bStarts) return -1;
        if (!aStarts && bStarts) return 1;
        return aName.localeCompare(bName);
      });

    // Search channels by name
    const matchedChannels = (channels || [])
      .filter(ch => ch.name.toLowerCase().includes(term))
      .map(ch => ({
        type: 'channel',
        id: ch.id,
        name: ch.name,
        members: ch.members || []
      }));

    return { users, channels: matchedChannels };
  }, [searchTerm, locations, channels]);

  // Flatten results for keyboard navigation (users section, then channels section)
  const flatResults = useMemo(() => {
    const items = [];
    if (filteredResults.users.length > 0) {
      items.push({ type: 'header', label: 'Users' });
      items.push(...filteredResults.users.slice(0, 8)); // Max 8 visible before scrolling
    }
    if (filteredResults.channels.length > 0) {
      items.push({ type: 'header', label: 'Channels' });
      items.push(...filteredResults.channels.slice(0, 8));
    }
    return items;
  }, [filteredResults]);

  // Handle keyboard navigation
  const handleKeyDown = (e) => {
    if (!isOpen || flatResults.length === 0) {
      if (e.key === 'ArrowDown') {
        setIsOpen(true);
        setFocusedIndex(0);
        e.preventDefault();
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setFocusedIndex(prev => {
          let next = prev + 1;
          // Skip headers
          while (next < flatResults.length && flatResults[next].type === 'header') {
            next++;
          }
          return next < flatResults.length ? next : prev;
        });
        break;

      case 'ArrowUp':
        e.preventDefault();
        setFocusedIndex(prev => {
          let next = prev - 1;
          // Skip headers
          while (next >= 0 && flatResults[next].type === 'header') {
            next--;
          }
          return next >= 0 ? next : 0;
        });
        break;

      case 'Enter':
        e.preventDefault();
        if (focusedIndex >= 0 && focusedIndex < flatResults.length) {
          const item = flatResults[focusedIndex];
          if (item.type === 'user' && item.hasLocation) {
            handleSelectUser(item);
          }
        }
        break;

      case 'Escape':
        e.preventDefault();
        setIsOpen(false);
        setFocusedIndex(-1);
        // Keep search term (user decision)
        break;
    }
  };

  const handleSelectUser = (user) => {
    if (!user.hasLocation) return; // Grayed out, not clickable
    onSelectUser(user.id); // Parent component calls map.flyTo([lat, lng])
    // Search term stays (user decision: "dropdown stays available for picking another")
  };

  // Auto-open dropdown when typing
  useEffect(() => {
    if (searchTerm.trim() && (filteredResults.users.length > 0 || filteredResults.channels.length > 0)) {
      setIsOpen(true);
      setFocusedIndex(-1); // Reset focus when results change
    } else {
      setIsOpen(false);
    }
  }, [searchTerm, filteredResults]);

  // Click-away to close dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target) &&
          inputRef.current && !inputRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="map-search" role="combobox" aria-expanded={isOpen} aria-haspopup="listbox">
      <input
        ref={inputRef}
        type="text"
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Search users or channels..."
        className="map-search__input"
        aria-autocomplete="list"
        aria-controls="search-dropdown"
        aria-activedescendant={focusedIndex >= 0 ? `result-${focusedIndex}` : undefined}
      />

      {isOpen && flatResults.length > 0 && (
        <div
          ref={dropdownRef}
          id="search-dropdown"
          role="listbox"
          className="map-search__dropdown"
        >
          {flatResults.map((item, index) => {
            if (item.type === 'header') {
              return (
                <div key={`header-${item.label}`} className="map-search__header">
                  {item.label}
                </div>
              );
            }

            if (item.type === 'user') {
              const isFocused = index === focusedIndex;
              const className = `map-search__result ${isFocused ? 'focused' : ''} ${!item.hasLocation ? 'disabled' : ''}`;

              return (
                <div
                  key={item.id}
                  id={`result-${index}`}
                  role="option"
                  aria-selected={isFocused}
                  className={className}
                  onClick={() => handleSelectUser(item)}
                  onMouseEnter={() => setFocusedIndex(index)}
                >
                  <span className="map-search__name">{item.name}</span>
                  <span className={`map-search__status ${item.isOnline ? 'online' : 'offline'}`}>
                    {item.isOnline ? '●' : '○'}
                  </span>
                  {!item.hasLocation && (
                    <span className="map-search__no-location">No location</span>
                  )}
                </div>
              );
            }

            // Channel item (expandable to show members — deferred to implementation)
            return null;
          })}
        </div>
      )}
    </div>
  );
};
```

**CSS for autocomplete dropdown:**

```css
/* Source: ARIA autocomplete pattern styling */
.map-search {
  position: relative;
  flex: 1;
  max-width: 300px;
}

.map-search__input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.15);
  color: white;
  font-size: 14px;
}

.map-search__input::placeholder {
  color: rgba(255, 255, 255, 0.6);
}

.map-search__dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  max-height: 320px; /* 8 items * 40px = 320px before scrolling */
  overflow-y: auto;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  z-index: 1000;
}

.map-search__header {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: bold;
  color: #666;
  background: rgba(0, 0, 0, 0.05);
}

.map-search__result {
  padding: 10px 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #333;
}

.map-search__result.focused {
  background: rgba(255, 152, 0, 0.2); /* Orange highlight */
}

.map-search__result.disabled {
  color: #999;
  cursor: not-allowed;
  opacity: 0.6;
}

.map-search__status.online {
  color: #4CAF50; /* Green */
}

.map-search__status.offline {
  color: #999; /* Gray */
}

.map-search__no-location {
  margin-left: auto;
  font-size: 12px;
  color: #999;
}
```

**Key insight:** ARIA attributes (`role="combobox"`, `aria-expanded`, `aria-activedescendant`) provide screen reader accessibility. Keyboard navigation uses arrow keys to change `focusedIndex` state and Enter to select. Escape closes dropdown but keeps search term per user decision.

**Sources:**
- [React Aria Autocomplete Pattern](https://react-spectrum.adobe.com/react-aria/Autocomplete.html)
- [ARIA Autocomplete Best Practices](https://www.telerik.com/kendo-react-ui/components/dropdowns/autocomplete/keyboard-navigation)
- [Accessible Dropdown with React](https://ibrahimaq.com/blogs/how-to-create-a-custom-accessible-dropdown-with-react-and-typescript/)

### Pattern 3: Custom useLocalStorage Hook with Silent Fallback

**What:** Custom React hook for localStorage persistence with JSON serialization, error handling, and silent fallback to defaults

**When to use:** Popup field settings persistence (user decision: global scope, silent fallback, no version handling)

**Example:**

```javascript
// Source: https://usehooks-ts.com/react-hook/use-local-storage
import { useState, useEffect, useCallback } from 'react';

/**
 * Custom hook for localStorage persistence with silent fallback
 * @param {string} key - localStorage key (e.g., 'cv.dispatch.popup.settings')
 * @param {object} defaultValue - Default value if key missing or corrupted
 * @returns {[value, setValue]} - Tuple of current value and setter function
 */
function useLocalStorage(key, defaultValue) {
  // Initialize state with lazy initialization (read from localStorage on first render)
  const [value, setValue] = useState(() => {
    try {
      const stored = localStorage.getItem(key);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (error) {
      // Silent fallback on parse error (corrupted JSON)
      console.warn(`Failed to parse localStorage key "${key}":`, error);
    }
    return defaultValue;
  });

  // Update localStorage whenever value changes
  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      // Silent fallback on write error (quota exceeded, private browsing)
      console.warn(`Failed to write localStorage key "${key}":`, error);
    }
  }, [key, value]);

  // Reset to defaults function
  const reset = useCallback(() => {
    setValue(defaultValue);
  }, [defaultValue]);

  return [value, setValue, reset];
}

export default useLocalStorage;

// Usage in PopupSettingsPanel.jsx
const DEFAULT_SETTINGS = {
  showLocation: true,
  showMotion: true,
  showChannel: true,
  showPTTStatus: true,
  showConnection: true,
  showBattery: true
  // userName always shown (not toggleable per user decision)
};

function PopupSettingsPanel({ isOpen, onClose }) {
  const [settings, setSettings, resetSettings] = useLocalStorage(
    'cv.dispatch.popup.settings',
    DEFAULT_SETTINGS
  );

  const handleToggle = (field) => {
    setSettings(prev => ({ ...prev, [field]: !prev[field] }));
    // Live preview: LocationContext.generatePopupContent reads settings immediately
  };

  return (
    <div className={`settings-panel ${isOpen ? 'open' : ''}`}>
      <h3>Popup Fields</h3>
      <div className="settings-panel__field">
        <label>User Name</label>
        <span className="settings-panel__always-on">Always shown</span>
      </div>
      <div className="settings-panel__field">
        <label>Location</label>
        <input
          type="checkbox"
          checked={settings.showLocation}
          onChange={() => handleToggle('showLocation')}
        />
      </div>
      {/* ... other toggles ... */}
      <button onClick={resetSettings}>Reset to Defaults</button>
      <button onClick={onClose}>Close</button>
    </div>
  );
}
```

**Key insight:** Lazy initialization with `useState(() => ...)` reads localStorage only on first render (not on every re-render). Silent try/catch prevents app crashes from corrupted JSON or quota exceeded errors. `useEffect` writes to localStorage on every value change (debouncing optional for rapid changes, but not needed for toggle switches).

**Sources:**
- [useLocalStorage - usehooks-ts](https://usehooks-ts.com/react-hook/use-local-storage)
- [Persisting React State in localStorage - Josh W. Comeau](https://www.joshwcomeau.com/react/persisting-react-state-in-localstorage/)
- [Mastering State Persistence with Local Storage in React](https://medium.com/@roman_j/mastering-state-persistence-with-local-storage-in-react-a-complete-guide-1cf3f56ab15c)

### Pattern 4: Glassmorphic Toolbar with Backdrop Blur

**What:** Semi-transparent floating toolbar with `backdrop-filter: blur()` and translucent background per 2026 glassmorphism UI trends

**When to use:** Toolbar positioned at top center of map (user decision: glassmorphic style, always visible)

**Example:**

```css
/* Source: https://ui.glass/generator/ + 2026 glassmorphism trends */
.map-toolbar {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;

  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;

  /* Glassmorphic effect */
  background: rgba(255, 255, 255, 0.2); /* Semi-transparent white (10-40% opacity) */
  backdrop-filter: blur(10px); /* Frosted glass blur effect */
  -webkit-backdrop-filter: blur(10px); /* Safari support */

  /* Subtle border and shadow */
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* Button styles inside toolbar */
.map-toolbar__button {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 152, 0, 0.9); /* Orange button */
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.map-toolbar__button:hover {
  background: rgba(255, 152, 0, 1); /* Full opacity on hover */
}

.map-toolbar__icon-button {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.3);
  color: white;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.map-toolbar__icon-button:hover {
  background: rgba(255, 255, 255, 0.5);
}
```

**Browser support in 2026:** `backdrop-filter` has 97%+ global support (Chrome 76+, Firefox 103+, Safari 9+). Graceful degradation: browsers without support show semi-transparent background without blur (still readable).

**Key insight:** Opacity between 10-40% provides "distinct yet connected" glassmorphic effect. `backdrop-filter: blur(10px)` creates frosted glass appearance. Soft shadows (`box-shadow`) and subtle borders (`rgba` border) complete the 2026 glassmorphism aesthetic.

**Sources:**
- [Glassmorphism CSS Generator - Glass UI](https://ui.glass/generator/)
- [Glassmorphism: What It Is and How to Use It in 2026](https://invernessdesignstudio.com/glassmorphism-what-it-is-and-how-to-use-it-in-2026)
- [Dark Glassmorphism: The Aesthetic That Will Define UI in 2026](https://medium.com/@developer_89726/dark-glassmorphism-the-aesthetic-that-will-define-ui-in-2026-93aa4153088f)

### Pattern 5: Slide-Out Settings Panel with CSS Transform

**What:** Settings panel animates from right edge using `transform: translateX(100%)` with CSS transitions

**When to use:** Settings panel that slides in/out on settings icon click (user decision: slide from right edge, click-away dismisses)

**Example:**

```javascript
// Source: Step-by-step React sliding drawer pattern
import { useState, useEffect, useRef } from 'react';

function PopupSettingsPanel({ isOpen, onClose }) {
  const panelRef = useRef(null);

  // Click-away to close
  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (e) => {
      if (panelRef.current && !panelRef.current.contains(e.target)) {
        onClose();
      }
    };

    // Delay event listener to avoid immediate close on open click
    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleClickOutside);
    }, 100);

    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen, onClose]);

  return (
    <div ref={panelRef} className={`settings-panel ${isOpen ? 'open' : ''}`}>
      <h3>Popup Fields</h3>
      {/* ... toggle switches ... */}
    </div>
  );
}
```

**CSS for slide-out animation:**

```css
/* Source: https://medium.com/@axionoso/step-by-step-guide-to-react-sliding-drawer-e0f8facf3bab */
.settings-panel {
  position: fixed;
  top: 0;
  right: 0;
  width: 320px;
  height: 100vh;
  background: white;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.2);
  z-index: 1001; /* Above toolbar */

  /* Hidden state: off-screen to the right */
  transform: translateX(100%);
  transition: transform 0.3s ease-out;

  padding: 20px;
  overflow-y: auto;
}

.settings-panel.open {
  /* Open state: slide into view */
  transform: translateX(0);
}

/* Toggle switch styling (modern on/off) */
.settings-panel__field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}

.settings-panel__field label {
  font-size: 14px;
  color: #333;
}

/* Custom toggle switch (checkbox styled as switch) */
.settings-panel__field input[type="checkbox"] {
  appearance: none;
  width: 48px;
  height: 24px;
  background: #ccc;
  border-radius: 12px;
  position: relative;
  cursor: pointer;
  transition: background 0.2s;
}

.settings-panel__field input[type="checkbox"]:checked {
  background: #FF9800; /* Orange when ON */
}

.settings-panel__field input[type="checkbox"]::before {
  content: '';
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: white;
  top: 2px;
  left: 2px;
  transition: transform 0.2s;
}

.settings-panel__field input[type="checkbox"]:checked::before {
  transform: translateX(24px); /* Slide knob to right when ON */
}

/* Reset button */
.settings-panel button {
  margin-top: 20px;
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  background: #FF9800;
  color: white;
  font-size: 14px;
  cursor: pointer;
}

.settings-panel button:hover {
  background: #F57C00;
}
```

**Key insight:** `transform: translateX(100%)` positions panel off-screen to the right. Adding `.open` class changes to `translateX(0)` to slide into view. `transition: transform 0.3s ease-out` provides smooth animation. Click-away uses `mousedown` event with 100ms delay to prevent immediate close from open click.

**Sources:**
- [Step by Step Guide to React Sliding-Drawer](https://medium.com/@axionoso/step-by-step-guide-to-react-sliding-drawer-e0f8facf3bab)
- [Building an Animated Slide-in Drawer With React-Spring](https://medium.com/geekculture/building-an-animated-slide-in-drawer-with-react-spring-22a6a54bc4cd)
- [Painless React Animations via CSS Transitions](https://ozmoroz.com/2019/03/react-css-transitions/)

### Pattern 6: Toast Notification for "No Locations to Show"

**What:** Temporary message that appears at bottom center when auto-fit fails due to no markers

**When to use:** When "Fit All" button clicked but no location data available (user decision: brief toast notification)

**Example:**

```javascript
// Simple toast notification without external library
function showToast(message, duration = 3000) {
  // Create toast element
  const toast = document.createElement('div');
  toast.className = 'map-toast';
  toast.textContent = message;
  document.body.appendChild(toast);

  // Trigger animation (CSS class)
  setTimeout(() => toast.classList.add('show'), 10);

  // Auto-remove after duration
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => document.body.removeChild(toast), 300); // Wait for fade-out
  }, duration);
}
```

**CSS for toast notification:**

```css
/* Source: https://www.w3schools.com/howto/howto_js_snackbar.asp */
.map-toast {
  position: fixed;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%) translateY(100px);
  opacity: 0;

  padding: 12px 24px;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  font-size: 14px;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);

  transition: opacity 0.3s, transform 0.3s;
  z-index: 2000; /* Above all map elements */
}

.map-toast.show {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}
```

**Key insight:** Toast positioned at bottom center (above map but below toolbar). `transform: translateY(100px)` hides it below viewport. Adding `.show` class slides it up with fade-in. Auto-remove with `setTimeout` cleanup. No external library needed for simple temporary message.

**Sources:**
- [How To Create a Snackbar / Toast - W3Schools](https://www.w3schools.com/howto/howto_js_snackbar.asp)
- [Create CSS Toast Notifications (2026)](https://frontend-hero.com/how-to-create-toast-notification)
- [Lightweight Toast & Growl Notifications](https://www.cssscript.com/toast-growl-macos-toastify/)

### Anti-Patterns to Avoid

- **Using fitBounds() instead of flyToBounds():** User decision is "smooth fly-to animation"; `fitBounds()` snaps instantly without animation
- **External autocomplete library for simple substring matching:** Adds bundle size; vanilla implementation with `.toLowerCase().includes()` is sufficient for partial matching (user decision: not true fuzzy search with Levenshtein distance)
- **Writing to localStorage on every keystroke:** Debounce rapid changes if implementing search term persistence (but user decided against term persistence)
- **Storing settings in event-scoped localStorage:** User decision is global scope (`cv.dispatch.popup.settings` not `cv.dispatch.popup.settings.${eventId}`) — dispatcher preferences are personal, not per-event
- **Animation libraries for slide-out panel:** CSS transitions are sufficient; react-spring/framer-motion add 20-50kB for simple translateX animation
- **Recalculating bounds on every location update:** Only calculate on "Fit All" button click and initial load; location broadcasts should not trigger auto-fit

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Map bounds calculation | Custom LatLngBounds logic | Leaflet's `L.latLngBounds()` and `markerClusterGroup.getBounds()` | Handles edge cases (single marker, markers at poles, date line crossing, projection edge cases) |
| Smooth map animation | requestAnimationFrame pan/zoom loop | Leaflet's `flyToBounds()` with duration/easeLinearity | Built-in GPU-accelerated animation, handles interruption (user pan during animation), cancellation, and easing curves |
| Fuzzy search ranking | Custom Levenshtein distance | Simple substring matching with `.includes()` | User decision is "partial matching anywhere in name" not true fuzzy search; Fuse.js adds 12kB for feature not needed |
| Settings migration | Version numbers and schema migration | Silent fallback to defaults | User decision: "no version/migration handling — new fields get defaults, removed fields ignored" |
| Toast notification queue | Custom queue manager | Simple imperative `showToast()` function | Single toast at a time is sufficient (no multiple stacked toasts needed) |

**Key insight:** Leaflet's built-in bounds calculation and flyToBounds animation handle edge cases (single marker maxZoom cap, date line crossing, interrupted animation) that custom implementations miss. Simple substring matching is sufficient for user decision "case-insensitive partial matching" without Fuse.js overhead.

## Common Pitfalls

### Pitfall 1: fitBounds with Single Marker Zooms Too Close

**What goes wrong:** When only one marker exists, `fitBounds()` zooms to maximum zoom level (18-19), showing excessive detail (single building)

**Why it happens:** Leaflet calculates "optimal zoom" to fit bounds; single point has zero-area bounds, defaults to maxZoom

**How to avoid:** Set maxZoom option based on marker count: `maxZoom: markerCount === 1 ? 14 : 18`

**Warning signs:** Map zooms to street-level detail when only one user has location data

**Solution:**

```javascript
const markerCount = locations.size;
const maxZoom = markerCount === 1 ? 14 : 18; // Overview zoom cap for single marker

map.flyToBounds(bounds, {
  padding: [50, 50],
  maxZoom: maxZoom, // Cap zoom for single marker
  animate: true
});
```

**Source:** User decision "overview zoom cap when only one marker exists"

### Pitfall 2: Padding Causes Markers to Be Clipped at Edges

**What goes wrong:** Markers at edge of map are clipped by Leaflet controls (zoom buttons, layer switcher, toolbar) even with fitBounds

**Why it happens:** Leaflet controls overlay the map; padding in pixels doesn't account for control sizes

**How to avoid:** Use asymmetric padding to account for UI overlays: `paddingTopLeft: [50, 80]`, `paddingBottomRight: [50, 50]` if toolbar at top

**Warning signs:** Top markers partially hidden behind toolbar, right markers behind layer control

**Solution:**

```javascript
// Account for toolbar height at top (toolbar is 60px tall + 20px margin = 80px)
map.flyToBounds(bounds, {
  paddingTopLeft: [50, 80],      // Extra top padding for toolbar
  paddingBottomRight: [50, 50],  // Standard padding on other edges
  maxZoom: maxZoom,
  animate: true
});
```

**Source:** [Leaflet fitBounds with padding zooms far out - GitHub Issue #4528](https://github.com/Leaflet/Leaflet/issues/4528)

### Pitfall 3: localStorage Quota Exceeded in Private Browsing

**What goes wrong:** Writing to localStorage throws `QuotaExceededError` in Safari private browsing mode (localStorage disabled)

**Why it happens:** Safari disables localStorage in private browsing; other browsers limit to 5-10MB quota

**How to avoid:** Wrap localStorage writes in try/catch with silent fallback (no user-facing error)

**Warning signs:** Settings don't persist, console shows `QuotaExceededError`

**Solution:**

```javascript
function useLocalStorage(key, defaultValue) {
  const [value, setValue] = useState(() => {
    try {
      const stored = localStorage.getItem(key);
      if (stored) return JSON.parse(stored);
    } catch (error) {
      console.warn(`localStorage read failed:`, error);
    }
    return defaultValue;
  });

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      // Silent fallback on QuotaExceededError or SecurityError
      console.warn(`localStorage write failed:`, error);
    }
  }, [key, value]);

  return [value, setValue];
}
```

**Source:** User decision "silent fallback to all-fields-on defaults if localStorage corrupted or missing"

### Pitfall 4: Autocomplete Dropdown Closes on Selection Click

**What goes wrong:** Clicking dropdown item closes dropdown before `onClick` handler fires

**Why it happens:** Click-away listener on `mousedown` event fires before `onClick` on dropdown item

**How to avoid:** Use `mousedown` for click-away detection (fires before `onClick`), or delay click-away listener by 100ms after opening

**Warning signs:** Dropdown closes immediately when clicking item, no selection happens

**Solution:**

```javascript
// Delay click-away listener to avoid immediate close
useEffect(() => {
  if (!isOpen) return;

  const handleClickOutside = (e) => {
    if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
      onClose();
    }
  };

  // Delay 100ms to avoid closing on same click that opened dropdown
  const timer = setTimeout(() => {
    document.addEventListener('mousedown', handleClickOutside);
  }, 100);

  return () => {
    clearTimeout(timer);
    document.removeEventListener('mousedown', handleClickOutside);
  };
}, [isOpen]);
```

**Source:** React click-away pattern best practices

### Pitfall 5: Keyboard Navigation Skips Headers in Dropdown

**What goes wrong:** Arrow down/up navigation focuses on "Users" header text instead of user items

**Why it happens:** Headers are in flatResults array but not selectable

**How to avoid:** Skip headers when incrementing/decrementing focusedIndex

**Warning signs:** Pressing arrow down selects header, pressing Enter does nothing

**Solution:**

```javascript
case 'ArrowDown':
  e.preventDefault();
  setFocusedIndex(prev => {
    let next = prev + 1;
    // Skip headers
    while (next < flatResults.length && flatResults[next].type === 'header') {
      next++;
    }
    return next < flatResults.length ? next : prev;
  });
  break;
```

**Source:** ARIA autocomplete pattern with section headers

### Pitfall 6: backdrop-filter Not Working in Firefox

**What goes wrong:** Glassmorphic blur effect doesn't appear in Firefox, toolbar background is semi-transparent but not blurred

**Why it happens:** Firefox requires `layout.css.backdrop-filter.enabled` flag in `about:config` (enabled by default in Firefox 103+, but older versions need manual enable)

**How to avoid:** Graceful degradation — design works with semi-transparent background even without blur effect

**Warning signs:** Toolbar readable on satellite imagery in Chrome/Safari but not blurred in older Firefox

**Solution:**

```css
/* Glassmorphic effect with graceful degradation */
.map-toolbar {
  background: rgba(255, 255, 255, 0.3); /* Semi-transparent fallback */
  backdrop-filter: blur(10px);          /* Modern browsers */
  -webkit-backdrop-filter: blur(10px);  /* Safari */
}

/* Fallback for browsers without backdrop-filter support */
@supports not (backdrop-filter: blur(10px)) {
  .map-toolbar {
    background: rgba(255, 255, 255, 0.8); /* Higher opacity for readability */
  }
}
```

**Source:** [Glassmorphism browser support 2026](https://ui.glass/generator/) — 97%+ global support, graceful degradation for older browsers

## Code Examples

Verified patterns from official sources:

### Auto-Fit All Markers with Cluster Support

```javascript
// Source: Leaflet documentation + Leaflet.markercluster
function fitAllMarkers(map, clusterGroup, locations) {
  // Check if any markers exist
  if (!clusterGroup || clusterGroup.getLayers().length === 0) {
    showToast('No locations to show', 3000);
    return false;
  }

  // Get bounds from cluster group (includes all markers)
  const bounds = clusterGroup.getBounds();

  // Determine maxZoom based on marker count
  const markerCount = clusterGroup.getLayers().length;
  const maxZoom = markerCount === 1 ? 14 : 18; // Overview cap for single marker

  // Smooth fly-to animation with padding
  map.flyToBounds(bounds, {
    paddingTopLeft: [50, 80],      // Account for toolbar at top
    paddingBottomRight: [50, 50],  // Standard padding
    maxZoom: maxZoom,
    animate: true,
    duration: 1.0,                 // 1 second animation
    easeLinearity: 0.25            // Bezier curve
  });

  return true;
}

// Usage in MapToolbar.jsx
<button className="map-toolbar__button" onClick={() => fitAllMarkers(map, clusterGroup, locations)}>
  Fit All
</button>
```

### Complete MapToolbar Component

```javascript
// web-ui/src/components/MapToolbar.jsx
import { useState } from 'react';
import MapSearch from './MapSearch.jsx';
import PopupSettingsPanel from './PopupSettingsPanel.jsx';

const MapToolbar = ({ map, clusterGroup, locations, channels, onSelectUser }) => {
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  const handleFitAll = () => {
    if (!clusterGroup || clusterGroup.getLayers().length === 0) {
      showToast('No locations to show');
      return;
    }

    const bounds = clusterGroup.getBounds();
    const markerCount = clusterGroup.getLayers().length;
    const maxZoom = markerCount === 1 ? 14 : 18;

    map.flyToBounds(bounds, {
      paddingTopLeft: [50, 80],
      paddingBottomRight: [50, 50],
      maxZoom: maxZoom,
      animate: true,
      duration: 1.0
    });
  };

  return (
    <>
      <div className="map-toolbar">
        <button className="map-toolbar__button" onClick={handleFitAll}>
          Fit All
        </button>

        <MapSearch
          locations={locations}
          channels={channels}
          onSelectUser={onSelectUser}
        />

        <button
          className="map-toolbar__icon-button"
          onClick={() => setIsSettingsOpen(true)}
          aria-label="Popup settings"
        >
          ⚙️
        </button>
      </div>

      <PopupSettingsPanel
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
      />
    </>
  );
};

export default MapToolbar;
```

### Fly to User Marker from Search Selection

```javascript
// In MapSearch.jsx onSelectUser callback
const handleSelectUser = (userId) => {
  const position = locations.get(userId);
  if (!position || !position.latitude || !position.longitude) return;

  // Fly to marker with smooth animation (no auto-open popup per user decision)
  map.flyTo([position.latitude, position.longitude], 16, {
    animate: true,
    duration: 0.8
  });

  // Search term stays (user decision)
};
```

### Settings-Aware Popup Content Generation

```javascript
// In LocationContext.jsx - modify generatePopupContent to use settings
export function generatePopupContent(position, settings) {
  const sections = [];

  // Identity section (always shown)
  let identityHTML = `<div class="marker-popup__section">
    <strong>Identity</strong>
    <div>Name: ${position.userName}</div>
    <div>Team: ${position.teamName || 'Unknown'}</div>`;

  if (settings.showChannel) {
    identityHTML += `<div>Channels: ${position.channelNames?.join(', ') || 'None'}</div>`;
  }
  identityHTML += `</div>`;
  sections.push(identityHTML);

  // Status section (conditional fields)
  const statusFields = [];
  if (settings.showBattery && position.batteryPercentage != null) {
    statusFields.push(`<div>Battery: ${position.batteryPercentage}%</div>`);
  }
  if (settings.showConnection) {
    const quality = deriveConnectionQuality(position);
    statusFields.push(`<div>Connection: ${quality}</div>`);
  }

  if (statusFields.length > 0) {
    sections.push(`<div class="marker-popup__section">
      <strong>Status</strong>
      ${statusFields.join('')}
    </div>`);
  }

  // Activity section (conditional fields)
  const activityFields = [];
  if (settings.showMotion) {
    activityFields.push(`<div>Motion: ${position.motionState || 'Unknown'}</div>`);
  }
  if (settings.showLocation && position.speed != null) {
    activityFields.push(`<div>Speed: ${position.speed.toFixed(1)} km/h</div>`);
  }

  if (activityFields.length > 0) {
    sections.push(`<div class="marker-popup__section">
      <strong>Activity</strong>
      ${activityFields.join('')}
    </div>`);
  }

  return `<div class="marker-popup">${sections.join('')}</div>`;
}

// Usage in MapView.jsx - read settings when generating popup
const [settings] = useLocalStorage('cv.dispatch.popup.settings', DEFAULT_SETTINGS);

marker.bindPopup(() => generatePopupContent(latestPosition, settings), {
  maxWidth: 300,
  closeButton: true,
  autoClose: true
});
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| fitBounds() instant snap | flyToBounds() smooth animation | Leaflet 1.0+ (2016) | User expectation for smooth transitions increased; flyToBounds provides Apple Maps-like experience |
| External autocomplete libraries | Vanilla React with ARIA patterns | 2024-2026 | Bundle size optimization trends; ARIA patterns standardized across browsers; simple substring matching doesn't need Fuse.js |
| Skeuomorphic UI | Glassmorphism with backdrop-filter | 2020-2026 | Apple's Liquid Glass interface (iOS 26, macOS Tahoe) mainstream; backdrop-filter 97%+ browser support |
| Slide-out with max-width animation | transform: translateX() | Modern CSS best practices | GPU-accelerated transforms outperform layout-triggering width changes |
| localStorage without error handling | Silent try/catch fallback | Privacy browsing awareness 2023+ | Safari private mode, quota limits require graceful degradation |

**Deprecated/outdated:**
- `fitBounds()` for user-initiated actions — now prefer `flyToBounds()` for smooth animation (fitBounds still valid for programmatic instant fit)
- External fuzzy search libraries for simple filtering — vanilla `.includes()` sufficient for substring matching
- React animation libraries for simple slide-out — CSS transitions are standard for declarative animations

## Open Questions

1. **Exact debounce timing for search input**
   - What we know: Best practice is 300-500ms for search debounce; ReactSearchAutocomplete defaults to 200ms
   - What's unclear: Optimal timing for dispatchers (expert users typing quickly vs novice users)
   - Recommendation: Start with 200ms (responsive), increase to 300ms if performance issues with large user lists (50+ users)

2. **Search result ranking algorithm**
   - What we know: User wants "results ordered by closest match relevance" with partial matching
   - What's unclear: Whether exact match > starts with > contains is sufficient, or need character position scoring
   - Recommendation: Implement three-tier ranking (exact > starts with > contains) sorted alphabetically within tiers; if user feedback requests better ranking, add position-based scoring (match at start of name ranks higher)

3. **Toast notification positioning with toolbar**
   - What we know: Toast appears at bottom center; toolbar at top center
   - What's unclear: Whether bottom center conflicts with other UI elements (map attribution, scale bar)
   - Recommendation: Position toast at `bottom: 60px` to clear Leaflet attribution (typically bottom-right); test with zoom controls at bottom-right

4. **Settings panel width on mobile**
   - What we know: Panel width is 320px; Phase 22 deferred mobile responsive layout
   - What's unclear: Whether Phase 26 should handle mobile slide-out panel (320px might cover full screen on small devices)
   - Recommendation: Keep 320px fixed width; Phase 22 mobile layout is deferred, so Phase 26 optimizes for desktop 1200px+ widescreen

5. **Channel member expansion in search dropdown**
   - What we know: "Channel results expand to reveal individual members to select"
   - What's unclear: Expand on click vs expand on hover vs always expanded with nested list
   - Recommendation: Expand on click (accordion pattern) to keep dropdown compact; show member count badge (e.g., "Operations (5)") collapsed, reveal member list when clicked

## Sources

### Primary (HIGH confidence)

- [Leaflet Reference Documentation](https://leafletjs.com/reference.html) - flyToBounds, fitBounds, getBounds, padding options, animation options
- [Leaflet.markercluster GitHub](https://github.com/Leaflet/Leaflet.markercluster) - getBounds() for cluster groups, zoomToBoundsOnClick option
- [React Aria Autocomplete](https://react-spectrum.adobe.com/react-aria/Autocomplete.html) - ARIA autocomplete pattern, keyboard navigation
- [useLocalStorage - usehooks-ts](https://usehooks-ts.com/react-hook/use-local-storage) - Custom hook pattern with JSON serialization
- [Glassmorphism CSS Generator - Glass UI](https://ui.glass/generator/) - backdrop-filter syntax, opacity ranges, browser support

### Secondary (MEDIUM confidence)

- [JavaScript Leaflet Map fitBounds with List of Layers](https://copyprogramming.com/howto/javascript-leaflet-map-fitbounds-list-of-layers) - Verified with Leaflet docs
- [React Dropdowns AutoComplete Keyboard Navigation - KendoReact](https://www.telerik.com/kendo-react-ui/components/dropdowns/autocomplete/keyboard-navigation) - ARIA best practices
- [Persisting React State in localStorage - Josh W. Comeau](https://www.joshwcomeau.com/react/persisting-react-state-in-localstorage/) - Best practices verified with usehooks-ts
- [Step by Step Guide to React Sliding-Drawer](https://medium.com/@axionoso/step-by-step-guide-to-react-sliding-drawer-e0f8facf3bab) - CSS transform pattern verified
- [Glassmorphism: What It Is and How to Use It in 2026](https://invernessdesignstudio.com/glassmorphism-what-it-is-and-how-to-use-it-in-2026) - 2026 UI trends
- [How To Create a Snackbar / Toast - W3Schools](https://www.w3schools.com/howto/howto_js_snackbar.asp) - Toast notification pattern

### Tertiary (LOW confidence, validation needed)

- [react-search-autocomplete npm](https://www.npmjs.com/package/react-search-autocomplete) - Debounce timing defaults (200ms), library features
- [Mastering Search in React: From Basic Filtering to Fuzzy Matching](https://medium.com/@kennediowusu/mastering-search-in-react-from-basic-filtering-to-fuzzy-matching-76932818a4f9) - Fuzzy search concepts
- [Dark Glassmorphism: The Aesthetic That Will Define UI in 2026](https://medium.com/@developer_89726/dark-glassmorphism-the-aesthetic-that-will-define-ui-in-2026-93aa4153088f) - Future trends, editorial opinion

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Leaflet flyToBounds and markercluster.getBounds() verified in official docs; no new dependencies needed
- Architecture: HIGH - ARIA autocomplete pattern, useLocalStorage hook, glassmorphic CSS, slide-out transform verified from multiple sources
- Pitfalls: HIGH - localStorage quota exceeded, fitBounds single marker zoom, padding edge clipping documented in official sources and GitHub issues

**Research date:** 2026-02-17
**Valid until:** 2026-03-17 (30 days for stable libraries, Leaflet 1.9.4 mature)
