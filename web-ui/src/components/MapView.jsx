import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import 'leaflet-minimap/dist/Control.MiniMap.min.css';
import 'leaflet-mouse-position/src/L.Control.MousePosition.css';
import 'leaflet-minimap';
import 'leaflet-mouse-position';

const MapView = ({ eventId }) => {
  const mapRef = useRef(null);
  const containerRef = useRef(null);
  const activeLayerRef = useRef('satellite');

  // Constants
  const DEFAULT_CENTER = [-33.8688, 151.2093]; // Sydney, Australia
  const DEFAULT_ZOOM = 12;
  const GEOLOCATION_TIMEOUT = 5000; // 5 seconds
  const STORAGE_KEY = `cv.dispatch.map.${eventId}`;

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
      map.off('baselayerchange', handleBaseLayerChange);
      resizeObserver.disconnect();
      map.remove();
      mapRef.current = null;
    };
  }, [eventId, STORAGE_KEY]); // eventId changes should reinitialize map with new storage key

  return <div ref={containerRef} style={{ width: '100%', height: '100%' }} />;
};

export default MapView;
