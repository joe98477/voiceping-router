import React from 'react';

/**
 * Toast notification utility (module-level)
 * Creates temporary toast message at bottom center of screen
 * Removes existing toast before creating new one (single toast at a time)
 *
 * @param {string} message - Message to display
 * @param {number} duration - Duration in ms (default 3000)
 */
function showToast(message, duration = 3000) {
  // Remove existing toast if present
  const existing = document.querySelector('.map-toast');
  if (existing) {
    existing.remove();
  }

  // Create toast element
  const toast = document.createElement('div');
  toast.className = 'map-toast';
  toast.textContent = message;
  document.body.appendChild(toast);

  // Trigger CSS transition after DOM insertion
  setTimeout(() => {
    toast.classList.add('show');
  }, 10);

  // Remove toast after duration
  setTimeout(() => {
    toast.classList.remove('show');
    // Wait for fade-out transition before removing from DOM
    setTimeout(() => {
      toast.remove();
    }, 300);
  }, duration);
}

/**
 * Fit all markers in view with padding and zoom cap
 * Shows toast if no markers exist
 *
 * @param {L.Map} map - Leaflet map instance
 * @param {L.MarkerClusterGroup} clusterGroup - Cluster group containing markers
 * @returns {boolean} - True if fit succeeded, false if no markers
 */
function fitAllMarkers(map, clusterGroup) {
  if (!map || !clusterGroup) {
    return false;
  }

  const layers = clusterGroup.getLayers();
  if (layers.length === 0) {
    showToast('No locations to show');
    return false;
  }

  // Get bounds from cluster group
  const bounds = clusterGroup.getBounds();

  // Determine maxZoom: single marker = 14 (overview cap), multiple markers = 18
  const maxZoom = layers.length === 1 ? 14 : 18;

  // Fly to bounds with asymmetric padding (extra space at top for toolbar)
  map.flyToBounds(bounds, {
    paddingTopLeft: [50, 80], // 80px top padding for toolbar
    paddingBottomRight: [50, 50],
    maxZoom: maxZoom,
    animate: true,
    duration: 1.0,
  });

  return true;
}

/**
 * MapToolbar component
 * Glassmorphic floating toolbar at top center of map
 * Contains: Fit All button, search slot placeholder, settings icon
 *
 * @param {object} props
 * @param {L.Map} props.map - Leaflet map instance
 * @param {L.MarkerClusterGroup} props.clusterGroup - Cluster group containing markers
 * @param {Map} props.locations - Location data map
 * @param {function} props.onSettingsOpen - Callback when settings icon clicked (wired in Plan 03)
 */
const MapToolbar = ({ map, clusterGroup, locations, onSettingsOpen }) => {
  const handleFitAll = () => {
    fitAllMarkers(map, clusterGroup);
  };

  return (
    <div className="map-toolbar">
      <button
        className="map-toolbar__button"
        onClick={handleFitAll}
        aria-label="Fit all markers in view"
      >
        Fit All
      </button>

      {/* Placeholder for MapSearch component (Plan 02) */}
      <div className="map-toolbar__search-slot"></div>

      {/* Settings icon button (wired in Plan 03) */}
      <button
        className="map-toolbar__icon-button"
        onClick={onSettingsOpen}
        aria-label="Open map settings"
      >
        ⚙
      </button>
    </div>
  );
};

export default MapToolbar;
