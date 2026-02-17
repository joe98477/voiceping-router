import React, { useState, useRef, useEffect, useMemo } from 'react';

/**
 * MapSearch component
 * Autocomplete search for users and channels with keyboard navigation, channel member expansion, and fly-to behavior
 *
 * @param {object} props
 * @param {Map} props.locations - Map from LocationContext (Map<userId, position>)
 * @param {Array} props.channels - Array from overview.channels (each has id, name, teamId, members)
 * @param {function} props.onSelectUser - Callback when user is selected (userId) => void
 */
const MapSearch = ({ locations, channels, onSelectUser }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const [expandedChannels, setExpandedChannels] = useState(new Set());

  const inputRef = useRef(null);
  const dropdownRef = useRef(null);

  const STALE_THRESHOLD = 5 * 60 * 1000; // 5 minutes

  // Filtering logic: build user and channel results
  const { userResults, channelResults } = useMemo(() => {
    if (!searchTerm || !searchTerm.trim()) {
      return { userResults: [], channelResults: [] };
    }

    const term = searchTerm.toLowerCase().trim();

    // User search
    const users = [];
    for (const [userId, position] of locations.entries()) {
      const name = position.userName || 'Unknown';
      const nameLower = name.toLowerCase();
      if (nameLower.includes(term)) {
        const hasLocation = !!(position.latitude && position.longitude);
        const isStale = (Date.now() - new Date(position.timestamp).getTime()) > STALE_THRESHOLD;
        const isOnline = !isStale;

        users.push({
          type: 'user',
          id: userId,
          name: name,
          hasLocation: hasLocation,
          isOnline: isOnline,
          teamName: position.teamName,
          channelNames: position.channelNames,
        });
      }
    }

    // Sort users by three-tier relevance
    users.sort((a, b) => {
      const aName = a.name.toLowerCase();
      const bName = b.name.toLowerCase();
      const aExact = aName === term ? 0 : aName.startsWith(term) ? 1 : 2;
      const bExact = bName === term ? 0 : bName.startsWith(term) ? 1 : 2;

      if (aExact !== bExact) return aExact - bExact;
      return aName.localeCompare(bName);
    });

    // Channel search
    const channelMatches = [];
    if (channels && Array.isArray(channels)) {
      for (const channel of channels) {
        const channelName = channel.name || '';
        if (channelName.toLowerCase().includes(term)) {
          // Build members list
          const members = [];
          if (channel.members && Array.isArray(channel.members)) {
            for (const member of channel.members) {
              // Extract userId (handle string or object)
              const userId = typeof member === 'string' ? member : member.userId || member.id;
              if (!userId) continue;

              const position = locations.get(userId);
              if (position) {
                const hasLocation = !!(position.latitude && position.longitude);
                const isStale = (Date.now() - new Date(position.timestamp).getTime()) > STALE_THRESHOLD;
                const isOnline = !isStale;
                members.push({
                  type: 'channel-member',
                  id: userId,
                  name: position.userName || `User ${userId}`,
                  hasLocation: hasLocation,
                  isOnline: isOnline,
                });
              } else {
                // User without location data
                members.push({
                  type: 'channel-member',
                  id: userId,
                  name: `User ${userId}`,
                  hasLocation: false,
                  isOnline: false,
                });
              }
            }
          }

          channelMatches.push({
            type: 'channel',
            id: channel.id,
            name: channelName,
            members: members,
          });
        }
      }
    }

    return { userResults: users, channelResults: channelMatches };
  }, [searchTerm, locations, channels, STALE_THRESHOLD]);

  // Flat results for keyboard navigation
  const flatResults = useMemo(() => {
    const results = [];

    // Users section
    if (userResults.length > 0) {
      results.push({ type: 'header', label: 'Users' });
      results.push(...userResults);
    }

    // Channels section
    if (channelResults.length > 0) {
      results.push({ type: 'header', label: 'Channels' });

      for (const channel of channelResults) {
        results.push(channel);

        // If channel is expanded, add member sub-items
        if (expandedChannels.has(channel.id)) {
          results.push(...channel.members);
        }
      }
    }

    return results;
  }, [userResults, channelResults, expandedChannels]);

  // Auto-open dropdown when search term changes and results exist
  useEffect(() => {
    if (searchTerm.trim() && flatResults.length > 0) {
      setIsOpen(true);
      setFocusedIndex(-1);
    } else {
      setIsOpen(false);
      setFocusedIndex(-1);
    }
  }, [searchTerm, flatResults.length]);

  // Click-away handler
  useEffect(() => {
    const handleClickAway = (event) => {
      if (
        inputRef.current && !inputRef.current.contains(event.target) &&
        dropdownRef.current && !dropdownRef.current.contains(event.target)
      ) {
        setIsOpen(false);
        setFocusedIndex(-1);
      }
    };

    document.addEventListener('mousedown', handleClickAway);
    return () => {
      document.removeEventListener('mousedown', handleClickAway);
    };
  }, []);

  // Keyboard navigation
  const handleKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();

      // Open dropdown if closed
      if (!isOpen && searchTerm.trim() && flatResults.length > 0) {
        setIsOpen(true);
        return;
      }

      // Find next non-header index
      let nextIndex = focusedIndex + 1;
      while (nextIndex < flatResults.length && flatResults[nextIndex].type === 'header') {
        nextIndex++;
      }
      if (nextIndex < flatResults.length) {
        setFocusedIndex(nextIndex);
      }
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();

      // Find previous non-header index
      let prevIndex = focusedIndex - 1;
      while (prevIndex >= 0 && flatResults[prevIndex].type === 'header') {
        prevIndex--;
      }
      if (prevIndex >= 0) {
        setFocusedIndex(prevIndex);
      }
    } else if (event.key === 'Enter') {
      event.preventDefault();

      if (focusedIndex >= 0 && focusedIndex < flatResults.length) {
        const item = flatResults[focusedIndex];

        if ((item.type === 'user' || item.type === 'channel-member') && item.hasLocation) {
          // Select user
          onSelectUser(item.id);
        } else if (item.type === 'channel') {
          // Toggle channel expansion
          setExpandedChannels((prev) => {
            const updated = new Set(prev);
            if (updated.has(item.id)) {
              updated.delete(item.id);
            } else {
              updated.add(item.id);
            }
            return updated;
          });
        }
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      setIsOpen(false);
      setFocusedIndex(-1);
      // Search term stays
    }
  };

  // Scroll focused item into view
  useEffect(() => {
    if (focusedIndex >= 0 && dropdownRef.current) {
      const focusedElement = dropdownRef.current.querySelector(`#search-result-${focusedIndex}`);
      if (focusedElement) {
        focusedElement.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [focusedIndex]);

  // Highlight matched substring
  const highlightMatch = (text, term) => {
    if (!term) return text;
    const index = text.toLowerCase().indexOf(term.toLowerCase());
    if (index === -1) return text;

    const prefix = text.slice(0, index);
    const match = text.slice(index, index + term.length);
    const suffix = text.slice(index + term.length);

    return (
      <>
        {prefix}
        <strong>{match}</strong>
        {suffix}
      </>
    );
  };

  // Render individual item
  const renderItem = (item, index) => {
    if (item.type === 'header') {
      return (
        <div key={`header-${item.label}`} className="map-search__header">
          {item.label}
        </div>
      );
    }

    if (item.type === 'user' || item.type === 'channel-member') {
      const isFocused = index === focusedIndex;
      const isDisabled = !item.hasLocation;
      const className = `map-search__result ${isFocused ? 'focused' : ''} ${isDisabled ? 'disabled' : ''}`;

      return (
        <div
          key={`${item.type}-${item.id}`}
          id={`search-result-${index}`}
          className={className}
          role="option"
          aria-selected={isFocused}
          onClick={() => {
            if (!isDisabled) {
              onSelectUser(item.id);
            }
          }}
        >
          <span className="map-search__name">
            {highlightMatch(item.name, searchTerm.trim())}
          </span>
          <span className={`map-search__status ${item.isOnline ? 'map-search__status--online' : 'map-search__status--offline'}`}>
            {item.isOnline ? '●' : '●'}
          </span>
          {!item.hasLocation && (
            <span className="map-search__no-location">No location</span>
          )}
        </div>
      );
    }

    if (item.type === 'channel') {
      const isFocused = index === focusedIndex;
      const isExpanded = expandedChannels.has(item.id);
      const className = `map-search__result map-search__result--channel ${isFocused ? 'focused' : ''}`;

      return (
        <div
          key={`channel-${item.id}`}
          id={`search-result-${index}`}
          className={className}
          role="option"
          aria-selected={isFocused}
          onClick={() => {
            setExpandedChannels((prev) => {
              const updated = new Set(prev);
              if (updated.has(item.id)) {
                updated.delete(item.id);
              } else {
                updated.add(item.id);
              }
              return updated;
            });
          }}
        >
          <span className="map-search__name">
            {highlightMatch(item.name, searchTerm.trim())}
          </span>
          <span className="map-search__member-count">{item.members.length}</span>
          <span className={`map-search__chevron ${isExpanded ? 'map-search__chevron--expanded' : ''}`}>
            ▶
          </span>
        </div>
      );
    }

    return null;
  };

  return (
    <div className="map-search" role="combobox" aria-expanded={isOpen} aria-haspopup="listbox">
      <input
        ref={inputRef}
        type="text"
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        onKeyDown={handleKeyDown}
        onFocus={() => {
          if (searchTerm.trim() && flatResults.length > 0) {
            setIsOpen(true);
          }
        }}
        placeholder="Search users or channels..."
        className="map-search__input"
        aria-autocomplete="list"
        aria-controls="map-search-dropdown"
        aria-activedescendant={focusedIndex >= 0 ? `search-result-${focusedIndex}` : undefined}
      />
      {isOpen && flatResults.length > 0 && (
        <div ref={dropdownRef} id="map-search-dropdown" role="listbox" className="map-search__dropdown">
          {flatResults.map((item, index) => renderItem(item, index))}
        </div>
      )}
    </div>
  );
};

export default MapSearch;
