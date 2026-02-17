import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import 'leaflet-minimap/dist/Control.MiniMap.min.css';
import 'leaflet-mouse-position/src/L.Control.MousePosition.css';
import 'leaflet-minimap';
import 'leaflet-mouse-position';
import { useLocations } from '../context/LocationContext.jsx';

/**
 * Create motion-state-aware marker icon with staleness treatment
 * @param {object} position - Location position data
 * @param {boolean} isStale - Whether marker is stale (5+ min no update)
 * @returns {L.DivIcon} - Leaflet DivIcon instance
 */
function createMarkerIcon(position, isStale) {
  const motionState = (position.motionState || 'still').toLowerCase();
  const staleClass = isStale ? ' user-marker--stale' : '';
  const className = `user-marker user-marker--${motionState}${staleClass}`;

  // Three distinct SVG pictograms per locked decision
  const svgIcons = {
    still: `<svg viewBox="0 0 24 24" width="14" height="14" fill="white">
      <circle cx="12" cy="8" r="3.5"/>
      <path d="M12 14c-4.4 0-8 2-8 4.5V20h16v-1.5c0-2.5-3.6-4.5-8-4.5z"/>
    </svg>`,
    walking: `<svg viewBox="0 0 24 24" width="14" height="14" fill="white">
      <circle cx="13" cy="5" r="2.5"/>
      <path d="M14.5 10.5l-2 4.5 2.5 5h-2l-2-4.5-2 3v3H7v-4l3.5-4.5-1.5-3c-1.5.5-3 1.5-3 1.5l-1-1.5s2-1.5 4-2.5c1-.5 2-.5 2.5.5l.5 1c.5.5 1.5 1 2.5 1v2c-1 0-2-.5-2.5-1z"/>
    </svg>`,
    driving: `<svg viewBox="0 0 24 24" width="14" height="14" fill="white">
      <path d="M5 11l1.5-4.5C7 5.5 8 5 9 5h6c1 0 2 .5 2.5 1.5L19 11h1c1 0 1 1 1 2v3c0 1-1 2-2 2h-1c0 1.1-.9 2-2 2s-2-.9-2-2h-4c0 1.1-.9 2-2 2s-2-.9-2-2H5c-1 0-2-1-2-2v-3c0-1 0-2 1-2h1z"/>
      <circle cx="7.5" cy="15.5" r="1.5" fill="#FF9800"/>
      <circle cx="16.5" cy="15.5" r="1.5" fill="#FF9800"/>
    </svg>`
  };

  const svg = svgIcons[motionState] || svgIcons.still;

  return L.divIcon({
    className: className,
    html: `
      <div class="user-marker__label">${position.userName}</div>
      <div class="user-marker__pin">
        <div class="user-marker__icon">${svg}</div>
      </div>
    `,
    iconSize: [32, 40],
    iconAnchor: [16, 40],
  });
}

const MapView = ({ eventId, ws, isMapVisible }) => {
  const mapRef = useRef(null);
  const containerRef = useRef(null);
  const activeLayerRef = useRef('satellite');
  const markersRef = useRef(new Map());
  const hasQueriedRef = useRef(false);
  const { locations, updateLocation, setAllLocations, mergeLocations, removeLocation } = useLocations();

  // Constants
  const DEFAULT_CENTER = [-33.8688, 151.2093]; // Sydney, Australia
  const DEFAULT_ZOOM = 12;
  const GEOLOCATION_TIMEOUT = 5000; // 5 seconds
  const STORAGE_KEY = `cv.dispatch.map.${eventId}`;
  const STALE_THRESHOLD = 5 * 60 * 1000; // 5 minutes

  useEffect(() => {
    // Guard: prevent double initialization
    if (!containerRef.current || mapRef.current) {
      return;
    }

    // Restore saved state from localStorage
    let restoredCenter = DEFAULT_CENTER;
    let restoredZoom = DEFAULT_ZOOM;
    let restoredLayer = 'satellite';
    let hasRestoredState = false;

    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const state = JSON.parse(saved);
        if (state.center && Array.isArray(state.center) && state.center.length === 2) {
          restoredCenter = state.center;
        }
        if (typeof state.zoom === 'number') {
          restoredZoom = state.zoom;
        }
        if (state.layer) {
          restoredLayer = state.layer;
          activeLayerRef.current = state.layer;
        }
        hasRestoredState = true;
      }
    } catch (error) {
      console.warn('Failed to restore map state from localStorage:', error);
    }

    // Create map instance
    const map = L.map(containerRef.current, {
      center: restoredCenter,
      zoom: restoredZoom,
      keyboard: false, // Disabled per user decision — avoid conflicts with app shortcuts
      scrollWheelZoom: true,
      doubleClickZoom: true,
      zoomControl: false, // Will add manually at bottom-right
      attributionControl: false, // Will add manually collapsed
    });

    mapRef.current = map;

    // Tile layers
    const satelliteBase = L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      {
        attribution: 'Esri, Maxar, Earthstar Geographics',
        maxZoom: 19,
      }
    );

    const labelsOverlay = L.tileLayer(
      'https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
      {
        attribution: '',
        maxZoom: 19,
      }
    );

    const satelliteWithLabels = L.layerGroup([satelliteBase, labelsOverlay]);

    const streetLayer = L.tileLayer(
      'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        attribution: '© OpenStreetMap contributors',
        maxZoom: 19,
      }
    );

    // Add appropriate base layer
    if (restoredLayer === 'street') {
      streetLayer.addTo(map);
    } else {
      satelliteWithLabels.addTo(map);
    }

    // Layer control (top-right)
    L.control.layers(
      {
        'Satellite': satelliteWithLabels,
        'Street': streetLayer,
      },
      null,
      { position: 'topright' }
    ).addTo(map);

    // Mouse position / coordinates display (bottom-left, at the very bottom)
    if (L.Control.MousePosition) {
      L.control.mousePosition({
        position: 'bottomleft',
        separator: ', ',
        lngFirst: false,
        numDigits: 4,
        emptyString: '',
      }).addTo(map);
    }

    // Attribution control (bottom-right, at the very bottom)
    L.control.attribution({
      position: 'bottomright',
      prefix: false,
    }).addTo(map);

    // Scale bar (bottom-left, metric only — above coordinates)
    L.control.scale({
      position: 'bottomleft',
      imperial: false,
    }).addTo(map);

    // Zoom control (bottom-right — above attribution)
    L.control.zoom({ position: 'bottomright' }).addTo(map);

    // Minimap (bottom-right, collapsed by default)
    const minimapTileLayer = L.tileLayer(
      'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        attribution: '',
        maxZoom: 19,
      }
    );

    if (L.Control.MiniMap) {
      new L.Control.MiniMap(minimapTileLayer, {
        position: 'bottomright',
        width: 150,
        height: 150,
        zoomLevelOffset: -5,
        toggleDisplay: true,
        minimized: true,
      }).addTo(map);
    }

    // Geolocation (only if no saved state)
    if (!hasRestoredState && 'geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          map.setView([position.coords.latitude, position.coords.longitude], DEFAULT_ZOOM);
        },
        (error) => {
          // Silent failure — map already initialized with DEFAULT_CENTER
          console.debug('Geolocation failed:', error.message);
        },
        {
          timeout: GEOLOCATION_TIMEOUT,
          enableHighAccuracy: false,
          maximumAge: 300000, // 5 minutes
        }
      );
    }

    // localStorage persistence — save center, zoom, and active layer
    const saveMapState = () => {
      const center = map.getCenter();
      const zoom = map.getZoom();
      const state = {
        center: [center.lat, center.lng],
        zoom: zoom,
        layer: activeLayerRef.current,
      };

      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      } catch (error) {
        console.warn('Failed to save map state to localStorage:', error);
      }
    };

    // Track active layer
    const handleBaseLayerChange = (event) => {
      if (event.name === 'Satellite') {
        activeLayerRef.current = 'satellite';
      } else if (event.name === 'Street') {
        activeLayerRef.current = 'street';
      }
      saveMapState();
    };

    // Event listeners for state persistence
    map.on('moveend', saveMapState);
    map.on('zoomend', saveMapState);
    map.on('baselayerchange', handleBaseLayerChange);

    // Zoom-dependent label visibility (zoom >= 15 shows labels)
    const handleZoomEnd = () => {
      const zoom = map.getZoom();
      const container = map.getContainer();
      if (zoom >= 15) {
        container.classList.add('show-marker-labels');
      } else {
        container.classList.remove('show-marker-labels');
      }
    };
    map.on('zoomend', handleZoomEnd);
    // Also trigger once on init
    handleZoomEnd();

    // ResizeObserver — handle panel collapse/expand
    const resizeObserver = new ResizeObserver(() => {
      if (mapRef.current) {
        mapRef.current.invalidateSize();
      }
    });
    resizeObserver.observe(containerRef.current);

    // Cleanup function — CRITICAL for React Strict Mode
    return () => {
      map.off('moveend', saveMapState);
      map.off('zoomend', saveMapState);
      map.off('zoomend', handleZoomEnd);
      map.off('baselayerchange', handleBaseLayerChange);
      resizeObserver.disconnect();
      map.remove();
      mapRef.current = null;
    };
  }, [eventId, STORAGE_KEY]); // eventId changes should reinitialize map with new storage key

  // WebSocket LOCATION_BROADCAST listener
  useEffect(() => {
    // Guard: return early if WebSocket not ready
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      return;
    }

    const handleMessage = (event) => {
      try {
        const message = JSON.parse(event.data);

        // Handle LOCATION_BROADCAST messages
        if (message.type === 'location-broadcast' && message.data) {
          updateLocation(message.data.userId, message.data);
        }
      } catch (error) {
        console.error('[MapView] Failed to parse location message:', error);
      }
    };

    ws.addEventListener('message', handleMessage);

    return () => {
      ws.removeEventListener('message', handleMessage);
    };
  }, [ws, updateLocation]);

  // LOCATION_QUERY on map visibility
  useEffect(() => {
    // Guard: return early if already queried, map not visible, or WebSocket not ready
    if (hasQueriedRef.current || !isMapVisible || !ws || ws.readyState !== WebSocket.OPEN) {
      return;
    }

    const queryId = 'loc-query-' + Date.now();

    const handleQueryResponse = (event) => {
      try {
        const message = JSON.parse(event.data);

        // Match on correlation ID and check for positions data
        if (message.id === queryId && message.data?.positions) {
          const positions = message.data.positions;

          // Use staggered batch updates for large position sets
          if (positions.length > 5) {
            positions.forEach((pos, index) => {
              setTimeout(() => updateLocation(pos.userId, pos), index * 50);
            });
          } else {
            // Small sets can be merged directly
            mergeLocations(positions);
          }

          hasQueriedRef.current = true;
          ws.removeEventListener('message', handleQueryResponse);
        }
      } catch (error) {
        console.error('[MapView] Failed to parse location query response:', error);
      }
    };

    ws.addEventListener('message', handleQueryResponse);

    // Send LOCATION_QUERY with correlation ID
    ws.send(JSON.stringify({ type: 'location-query', id: queryId }));

    return () => {
      ws.removeEventListener('message', handleQueryResponse);
    };
  }, [ws, isMapVisible, updateLocation, mergeLocations]);

  // Marker rendering
  useEffect(() => {
    // Guard: return early if map not initialized
    if (!mapRef.current) {
      return;
    }

    const map = mapRef.current;

    // Update existing markers and create new ones
    for (const [userId, position] of locations.entries()) {
      const existingMarker = markersRef.current.get(userId);

      // Compute staleness (5-minute threshold)
      const isStale = (Date.now() - new Date(position.timestamp).getTime()) > STALE_THRESHOLD;

      if (existingMarker) {
        // Update position (CSS transition handles animation)
        existingMarker.setLatLng([position.latitude, position.longitude]);

        // Generate new icon
        const newIcon = createMarkerIcon(position, isStale);

        // Update icon if className changed (motion state or staleness changed)
        const currentIcon = existingMarker.getIcon();
        if (currentIcon.options.className !== newIcon.options.className) {
          existingMarker.setIcon(newIcon);
        }

        // Update marker metadata for Plan 03's cluster count filtering
        existingMarker.options.isStale = isStale;
        existingMarker.options.userId = userId;
        existingMarker.options.title = position.userName;
      } else {
        // Create new marker with motion-state-aware icon
        const icon = createMarkerIcon(position, isStale);
        const marker = L.marker([position.latitude, position.longitude], { icon }).addTo(map);

        // Store metadata for Plan 03's cluster count filtering
        marker.options.isStale = isStale;
        marker.options.userId = userId;
        marker.options.title = position.userName;

        markersRef.current.set(userId, marker);
      }
    }

    // Remove markers that no longer exist in locations
    for (const [userId, marker] of markersRef.current.entries()) {
      if (!locations.has(userId)) {
        map.removeLayer(marker);
        markersRef.current.delete(userId);
      }
    }
  }, [locations, STALE_THRESHOLD]);

  // Stale marker cleanup timer (every 5 minutes, removes markers older than 1 hour)
  useEffect(() => {
    const intervalId = setInterval(() => {
      const now = Date.now();
      const oneHourThreshold = 60 * 60 * 1000;

      for (const [userId, position] of locations.entries()) {
        const age = now - new Date(position.timestamp).getTime();
        if (age > oneHourThreshold) {
          console.warn(`[MapView] Removing stale marker for user ${userId}, age: ${Math.floor(age / 60000)}m`);
          removeLocation(userId);
        }
      }
    }, 5 * 60 * 1000); // 5 minutes

    return () => clearInterval(intervalId);
  }, [locations, removeLocation]);

  // Cleanup all markers on unmount
  useEffect(() => {
    return () => {
      if (mapRef.current) {
        for (const marker of markersRef.current.values()) {
          mapRef.current.removeLayer(marker);
        }
        markersRef.current.clear();
      }
    };
  }, []);

  return <div ref={containerRef} style={{ width: '100%', height: '100%' }} />;
};

export default MapView;
