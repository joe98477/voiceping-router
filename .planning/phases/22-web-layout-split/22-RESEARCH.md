# Phase 22: Web Layout Split - Research

**Researched:** 2026-02-17
**Domain:** CSS Grid layout, responsive design, React UI state management
**Confidence:** HIGH

## Summary

Phase 22 implements a split-panel dispatch console layout using CSS Grid with a fixed-width channels panel (~500px) on the left and a flexible map panel on the right. The layout requires responsive breakpoint handling at 1200px, switching from side-by-side panels to bottom-tab navigation below the breakpoint. The channels panel is collapsible with smooth CSS transitions, and panel state can optionally persist in localStorage.

The core technologies are already in the codebase: vanilla React with functional components and hooks, CSS Grid for layout structure, and localStorage for state persistence (already used for muted channels in DispatchConsole.jsx). No new dependencies are required. The layout pattern follows mobile-first responsive design principles with CSS media queries.

**Primary recommendation:** Use CSS Grid with `grid-template-columns: 500px 1fr` for the split layout, implement collapse via width transition with `transform` animation, handle responsive switching with `@media (max-width: 1199px)` media query, and use React useState hook with lazy initialization for collapse state (localStorage persistence is optional per user decisions).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Channel Panel Sizing:**
- Fixed width ~500px — comfortable 2-column fit for channel cards
- Shrink channel card min-width to ~180px (from 220px) to fit 2 columns in the narrower panel
- Channels panel scrolls independently — map stays fixed at full height
- Header and stats bar span full width across both panels at the top; the split starts below them
- Stats bar items spread across full page width (not left-aligned)
- Team section headers (chevron + name + mute button) stay as-is — they fit at 500px
- No visible divider between channels and map panels — background difference is sufficient

**Map Panel Layout:**
- Map aspect ratio is 4:3 (landscape) — not full-height
- Map aligned to bottom of the right panel
- Empty placeholder div above the map for future controls (defined min-height, no background — invisible)
- Map area has sharp edges (no rounded corners) — ready for Leaflet edge-to-edge rendering

**Map Panel Placeholder (Pre-Phase 23):**
- Dark empty area — no text, no icons, no visual content
- Slightly different background shade (subtle rgba surface) to distinguish the map panel boundary
- CSS transition-ready for fade-in when Leaflet mounts in Phase 23

**Panel Collapse/Expand:**
- Channels panel is collapsible via chevron button on the divider edge
- Fixed width + collapse only — no draggable resizer
- Collapse animation: smooth slide (~200-300ms)
- Collapsed state: ~40px narrow strip with team initials/icons and activity dots
- Audio monitoring continues when panel is collapsed — collapse is purely visual
- Collapse/expand state: Claude's discretion on localStorage persistence

**Below-Breakpoint Behavior (< 1200px):**
- Switch from split-panel to tab mode at 1200px breakpoint
- Fixed bottom tab bar with Channels and Map tab icons (mobile-app feel)
- Default tab: Channels (primary dispatch tool)
- Audio monitoring always active regardless of which tab is shown

### Claude's Discretion

- Exact pixel width tuning around ~500px
- Collapse chevron button styling and positioning
- Collapsed strip team icon design
- Smooth slide animation timing and easing
- Stats bar item spacing in full-width mode
- Bottom tab bar icon and styling choices
- Placeholder div min-height for above-map area
- Map panel exact background shade

### Deferred Ideas (OUT OF SCOPE)

- Quick-access controls above the map panel — future phase (space reserved with placeholder div)
- Collapse state persistence in localStorage — may implement in Phase 26 (Map Controls and Polish) with other settings persistence
</user_constraints>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| CSS Grid | Native | Two-column split layout | Browser-native, no dependencies, widely supported since 2017, standard for grid layouts |
| React Hooks | 18.3.1 | UI state management (collapse/tab) | Already in codebase, standard pattern for functional components |
| CSS Transitions | Native | Smooth collapse/expand animation | Browser-native, performant, standard for UI animations |
| CSS Media Queries | Native | Responsive breakpoint switching | Browser-native, mobile-first standard, no polyfill needed |
| localStorage API | Native | Optional state persistence | Browser-native, already used in codebase (DispatchConsole.jsx:68-77) |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| CSS aspect-ratio | Native | 4:3 map container sizing | Baseline widely available since Sept 2021, cleaner than padding-top hack |
| CSS Flexbox | Native | Stats bar item distribution, bottom tab bar layout | Complement to Grid for internal component layout |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| CSS Grid | Flexbox | Flexbox less suitable for two-column fixed+flexible layout; Grid is cleaner and more semantic |
| CSS Transitions | JavaScript animation libraries (Framer Motion, React Spring) | Overkill for simple width/height transitions; CSS is lighter and more performant |
| useState | Zustand/Jotai for collapse state | Unnecessary complexity for single UI toggle; local state is sufficient |
| localStorage | URL query params for collapse state | State would be lost on copy/paste URL; localStorage is more appropriate for UI preferences |

**Installation:**
No new packages required. All technologies are browser-native or already in the codebase (React 18.3.1).

## Architecture Patterns

### Recommended Project Structure
```
web-ui/src/
├── pages/
│   └── DispatchConsole.jsx        # Add layout grid wrapper
├── components/
│   ├── ChannelGrid.jsx            # Adjust card min-width to ~180px
│   └── DispatchChannelCard.jsx    # Shrink to fit 2-column layout
├── styles.css                      # Add split layout, collapse, responsive rules
└── theme/connectvoice.css          # Map panel placeholder background shade
```

### Pattern 1: CSS Grid Split Layout (Fixed + Flexible)

**What:** Two-column grid with fixed-width left panel and flexible right panel

**When to use:** Any sidebar + main content layout where sidebar has known width requirement

**Example:**
```css
/* Source: MDN CSS Grid Basic Concepts */
.dispatch-console {
  display: grid;
  grid-template-columns: 500px 1fr;
  gap: 0; /* No gap per user decision - background difference is sufficient */
  grid-template-rows: auto auto 1fr; /* header, stats bar, main content */
}

.dispatch-console__main-content {
  display: grid;
  grid-template-columns: 500px 1fr; /* Split starts below stats bar */
}

.channels-panel {
  overflow-y: auto; /* Independent scrolling */
}

.map-panel {
  display: grid;
  grid-template-rows: 1fr auto; /* Flexible space above, fixed-ratio map below */
  align-items: end; /* Align map to bottom */
}
```

### Pattern 2: CSS aspect-ratio for Map Container

**What:** Modern CSS property for maintaining aspect ratio without padding hacks

**When to use:** Any container that needs fixed aspect ratio (images, video embeds, map containers)

**Example:**
```css
/* Source: MDN aspect-ratio property */
.map-container {
  aspect-ratio: 4 / 3; /* 4:3 landscape ratio */
  width: 100%; /* Take full width of parent */
  background: rgba(16, 24, 40, 0.6); /* Subtle dark shade per user decision */
  /* Sharp edges per user decision - no border-radius */
}

.map-placeholder {
  min-height: 80px; /* Reserved space above map - exact value at Claude's discretion */
  /* No background - invisible placeholder per user decision */
}
```

### Pattern 3: Collapsible Panel with CSS Transition

**What:** Smooth width transition from full width to collapsed narrow strip

**When to use:** Sidebar collapse/expand, drawer animations, any panel hiding

**Example:**
```css
/* Source: CSS-Tricks transitions, W3Schools collapse sidebar */
.channels-panel {
  width: 500px;
  transition: width 250ms cubic-bezier(0.4, 0.0, 0.2, 1); /* Material Design standard easing */
  overflow: hidden; /* Hide content during collapse */
}

.channels-panel--collapsed {
  width: 40px; /* Narrow strip per user decision */
}

/* Collapse button on divider edge */
.channels-panel__collapse-btn {
  position: absolute;
  right: -12px; /* Half outside panel edge */
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 48px;
  cursor: pointer;
  /* Styling at Claude's discretion */
}

/* Collapsed strip shows team icons/activity dots */
.channels-panel--collapsed .channel-grid__team-section {
  /* Show only team initials + activity indicators */
  /* Implementation details at Claude's discretion */
}
```

### Pattern 4: Responsive Breakpoint with Tab Switching

**What:** Media query switches from split-panel to tab mode below 1200px

**When to use:** Desktop-optimized layouts that need mobile tab navigation fallback

**Example:**
```css
/* Source: BrowserStack responsive design breakpoints, Mobile Navigation Patterns 2026 */
/* Desktop: split panels */
@media (min-width: 1200px) {
  .dispatch-console__main-content {
    grid-template-columns: 500px 1fr;
  }
  .mobile-tab-bar {
    display: none; /* Hide tab bar on desktop */
  }
}

/* Mobile/tablet: tab mode */
@media (max-width: 1199px) {
  .dispatch-console__main-content {
    grid-template-columns: 1fr; /* Single column */
  }

  .channels-panel,
  .map-panel {
    display: none; /* Hide both by default */
  }

  .channels-panel.active,
  .map-panel.active {
    display: block; /* Show only active tab */
  }

  .mobile-tab-bar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    display: flex;
    justify-content: space-around; /* Distribute tab buttons */
    background: var(--cv-surface);
    border-top: 1px solid var(--cv-border);
    padding: 8px;
    z-index: 10;
  }
}
```

### Pattern 5: React State with localStorage Persistence (Optional)

**What:** useState with lazy initialization from localStorage, synced on change

**When to use:** UI preferences that should persist across sessions (already used in DispatchConsole.jsx for muted channels)

**Example:**
```jsx
// Source: Existing pattern from DispatchConsole.jsx:66-78
const [isCollapsed, setIsCollapsed] = useState(() => {
  try {
    const stored = localStorage.getItem('cv.dispatch.panelCollapsed');
    return stored === 'true';
  } catch {
    return false; // Default to expanded
  }
});

useEffect(() => {
  localStorage.setItem('cv.dispatch.panelCollapsed', isCollapsed ? 'true' : 'false');
}, [isCollapsed]);

// Toggle handler
const toggleCollapse = () => setIsCollapsed(prev => !prev);
```

### Pattern 6: Stats Bar Full-Width Distribution

**What:** Flexbox with `justify-content: space-between` or `space-around` for even distribution

**When to use:** Horizontal stat/metric bars that should spread across full width

**Example:**
```css
/* Current implementation uses flex with gaps - adjust to spread evenly */
.dispatch-stats {
  display: flex;
  justify-content: space-around; /* Even distribution per user decision */
  align-items: center;
  /* Remove gap; spacing handled by justify-content */
  padding: 10px 16px;
  background: var(--surface);
  border-radius: 12px;
  box-shadow: 0 4px 12px var(--shadow);
  flex-wrap: wrap; /* Maintain wrap for narrow screens */
}
```

### Anti-Patterns to Avoid

- **Setting both width and height with aspect-ratio:** At least one dimension must be `auto` for aspect-ratio to work. If both are fixed, the aspect ratio is ignored.
- **Using max-width in media queries for mobile-first:** Use `min-width` for mobile-first approach (start small, enhance up). `max-width` is desktop-first (start large, strip down).
- **Animating width/height without `overflow: hidden`:** Content will wrap/reflow during transition, creating janky animation. Always set `overflow: hidden` on transitioning panels.
- **Forgetting touch targets on mobile tabs:** Bottom tab buttons must be at least 44×44px (48×48px recommended) per mobile accessibility guidelines.
- **Collapsing panels without preserving functionality:** Audio monitoring must continue when panel is collapsed per user decision - collapse is purely visual.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Aspect ratio containers | Custom JS resize observers with padding-top hacks | CSS `aspect-ratio: 4 / 3` | Native browser optimization, cleaner code, widely supported since 2021 |
| Media query detection | Custom JS window.matchMedia listeners | CSS media queries with mobile-first approach | Declarative, no JS needed, better performance, SSR-friendly |
| Smooth transitions | requestAnimationFrame-based animation loops | CSS transitions with cubic-bezier easing | GPU-accelerated, smoother, less code, better battery life |
| localStorage sync | Custom storage abstraction library | useState lazy initializer + useEffect pattern | Already proven in codebase (DispatchConsole.jsx), simple, no dependencies |
| Responsive breakpoints | JavaScript-based responsive component library | CSS Grid + Flexbox + media queries | Browser-native, faster, no runtime overhead |

**Key insight:** Browser-native CSS and Web APIs have matured significantly. For layouts and UI state, vanilla CSS + React hooks handle 95% of use cases without external libraries. External libraries add bundle size, maintenance burden, and version lock-in for marginal developer convenience gains.

## Common Pitfalls

### Pitfall 1: Grid Column Sizing Confusion (Fixed vs. Flexible)

**What goes wrong:** Using `fr` units for both columns when one should be fixed width, or using fixed widths for both columns when one should be flexible

**Why it happens:** Misunderstanding the difference between `px`, `fr`, and `auto` in grid-template-columns

**How to avoid:**
- Fixed sidebar: Use `px` or `rem` units (e.g., `500px`)
- Flexible content: Use `fr` units (e.g., `1fr`)
- Pattern: `grid-template-columns: 500px 1fr` for fixed left + flexible right

**Warning signs:**
- Content panel doesn't fill available space
- Sidebar grows/shrinks unexpectedly on window resize
- Horizontal scrollbar appears when it shouldn't

### Pitfall 2: aspect-ratio Not Working

**What goes wrong:** Setting `aspect-ratio: 4 / 3` on an element but the ratio is ignored

**Why it happens:** Both width and height are explicitly set, so browser can't apply the ratio constraint

**How to avoid:** Ensure at least one dimension is `auto` or unset
```css
/* WRONG - both dimensions fixed, aspect-ratio ignored */
.map-container {
  width: 800px;
  height: 600px;
  aspect-ratio: 4 / 3;
}

/* CORRECT - width set, height auto, aspect-ratio applied */
.map-container {
  width: 100%;
  aspect-ratio: 4 / 3;
}
```

**Warning signs:**
- Container dimensions don't match expected ratio
- Container height doesn't respond to width changes

### Pitfall 3: Collapse Animation Janky/Jumping

**What goes wrong:** Panel collapse animation stutters, content jumps around, or transition feels jerky

**Why it happens:** Content reflows during width transition, or transition properties not optimized

**How to avoid:**
- Set `overflow: hidden` on transitioning element
- Use `cubic-bezier()` easing for natural motion (avoid `linear`)
- Transition `width` or use `transform: scaleX()` for better performance
- Don't transition too many properties at once

```css
/* BETTER - smooth width transition */
.channels-panel {
  width: 500px;
  transition: width 250ms cubic-bezier(0.4, 0.0, 0.2, 1);
  overflow: hidden;
}

/* EVEN BETTER - GPU-accelerated transform */
.channels-panel {
  width: 500px;
  transform-origin: left;
  transition: transform 250ms cubic-bezier(0.4, 0.0, 0.2, 1);
  overflow: hidden;
}
.channels-panel--collapsed {
  transform: scaleX(0.08); /* 40px / 500px = 0.08 */
}
```

**Warning signs:**
- Animation feels choppy or stutters
- Content briefly visible during collapse
- CPU/battery usage spikes during animation

### Pitfall 4: Media Query Breakpoint Off-by-One

**What goes wrong:** Content appears in both desktop and mobile modes at exactly 1200px, or neither mode activates

**Why it happens:** Overlapping or gap in `min-width` and `max-width` ranges (e.g., `min-width: 1200px` and `max-width: 1200px` both match at 1200px)

**How to avoid:**
- Use adjacent boundaries: `min-width: 1200px` for desktop, `max-width: 1199px` for mobile
- Or use single breakpoint: `@media (min-width: 1200px)` for desktop, default styles for mobile (mobile-first approach)

```css
/* WRONG - overlapping at 1200px */
@media (min-width: 1200px) { /* Desktop */ }
@media (max-width: 1200px) { /* Mobile */ }

/* CORRECT - adjacent boundaries */
@media (min-width: 1200px) { /* Desktop */ }
@media (max-width: 1199px) { /* Mobile */ }

/* BEST - mobile-first, single breakpoint */
/* Default styles for mobile */
@media (min-width: 1200px) {
  /* Desktop enhancements */
}
```

**Warning signs:**
- Layout breaks exactly at breakpoint width
- Both mobile and desktop styles apply simultaneously
- Neither mobile nor desktop styles apply at breakpoint

### Pitfall 5: localStorage Quota Exceeded

**What goes wrong:** localStorage.setItem() throws `QuotaExceededError` exception

**Why it happens:** localStorage has 5-10MB limit per origin; storing large objects or not cleaning up old data

**How to avoid:**
- Store minimal data (booleans, IDs, small JSON objects)
- Wrap setItem in try/catch (already done in codebase pattern)
- Don't store base64-encoded images or large arrays
- For this phase: single boolean for collapse state is ~20 bytes - no risk

```javascript
// Pattern from DispatchConsole.jsx - already handles errors gracefully
try {
  localStorage.setItem('cv.dispatch.panelCollapsed', isCollapsed ? 'true' : 'false');
} catch (err) {
  // Fail silently - collapse state will reset on reload, not critical
  console.warn('Could not persist panel state:', err);
}
```

**Warning signs:**
- Console errors about quota exceeded
- State persistence silently failing
- User in private browsing mode (localStorage may be disabled)

### Pitfall 6: Bottom Tab Bar Overlapping Content

**What goes wrong:** Bottom tab bar covers content at bottom of viewport, content not scrollable to reveal hidden area

**Why it happens:** Fixed positioning removes element from flow; parent doesn't account for tab bar height

**How to avoid:**
- Add bottom padding to scrollable container equal to tab bar height + safe area
- Or use `env(safe-area-inset-bottom)` for devices with notches/home indicators

```css
/* Account for tab bar height on mobile */
@media (max-width: 1199px) {
  .dispatch-console__main-content {
    padding-bottom: calc(60px + env(safe-area-inset-bottom, 0px)); /* Tab bar height + safe area */
  }

  .mobile-tab-bar {
    position: fixed;
    bottom: 0;
    height: 60px;
    padding-bottom: env(safe-area-inset-bottom, 0px); /* Handle iPhone home indicator */
  }
}
```

**Warning signs:**
- Last channel card cut off on mobile
- Can't scroll to see bottom content
- Tab bar overlaps map controls

## Code Examples

Verified patterns from official sources and existing codebase:

### CSS Grid Split Layout
```css
/* Source: MDN CSS Grid Basic Concepts - https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Grid_layout/Basic_concepts */
.dispatch-console {
  display: grid;
  grid-template-rows: auto auto 1fr; /* header, stats, main */
  gap: 14px;
}

.dispatch-console__main-content {
  display: grid;
  grid-template-columns: 500px 1fr; /* Fixed channels panel + flexible map panel */
  min-height: 0; /* Allow grid items to shrink below content size */
}

.channels-panel {
  overflow-y: auto; /* Independent scrolling */
  background: var(--cv-surface); /* Existing theme variable */
}

.map-panel {
  background: rgba(16, 24, 40, 0.6); /* Subtle shade to distinguish boundary */
  display: grid;
  grid-template-rows: 1fr auto; /* Placeholder space + map */
  align-items: end; /* Align map to bottom */
}
```

### Map Container with aspect-ratio
```css
/* Source: MDN aspect-ratio - https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/aspect-ratio */
.map-placeholder {
  min-height: 80px; /* Reserved for future controls */
}

.map-container {
  aspect-ratio: 4 / 3; /* 4:3 landscape */
  width: 100%;
  background: rgba(16, 24, 40, 0.85); /* Dark placeholder */
  transition: opacity 300ms ease; /* Ready for Leaflet fade-in in Phase 23 */
}
```

### Channel Card Adjusted for 2-Column Layout
```css
/* Current: minmax(220px, 1fr) - fits ~2 columns at full width */
/* Adjusted: minmax(180px, 1fr) - fits 2 columns in 500px panel */
.channel-grid__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); /* Shrink from 220px */
  gap: 10px;
  padding: 10px;
}
```

### Collapsible Panel with Smooth Transition
```jsx
/* React component pattern - mix of existing codebase style + web search findings */
const DispatchConsole = ({ user, onLogout }) => {
  // Optional localStorage persistence (at Claude's discretion)
  const [isCollapsed, setIsCollapsed] = useState(() => {
    try {
      const stored = localStorage.getItem('cv.dispatch.panelCollapsed');
      return stored === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('cv.dispatch.panelCollapsed', isCollapsed ? 'true' : 'false');
    } catch (err) {
      // Fail silently - not critical
    }
  }, [isCollapsed]);

  return (
    <div className="dispatch-console">
      <header>{/* ... */}</header>
      <div className="dispatch-stats">{/* ... */}</div>

      <div className="dispatch-console__main-content">
        <div className={`channels-panel ${isCollapsed ? 'channels-panel--collapsed' : ''}`}>
          {/* Channel grid content */}
          <button
            className="channels-panel__collapse-btn"
            onClick={() => setIsCollapsed(prev => !prev)}
            aria-label={isCollapsed ? 'Expand channels panel' : 'Collapse channels panel'}
          >
            {isCollapsed ? '▶' : '◀'}
          </button>
        </div>

        <div className="map-panel">
          <div className="map-placeholder"></div>
          <div className="map-container"></div>
        </div>
      </div>
    </div>
  );
};
```

### Responsive Tabs for Mobile
```jsx
/* Mobile tab state */
const [activeTab, setActiveTab] = useState('channels'); // Default to Channels per user decision

return (
  <>
    <div className="dispatch-console__main-content">
      <div className={`channels-panel ${activeTab === 'channels' ? 'active' : ''}`}>
        {/* ChannelGrid always rendered - audio continues regardless of tab */}
      </div>
      <div className={`map-panel ${activeTab === 'map' ? 'active' : ''}`}>
        {/* Map placeholder */}
      </div>
    </div>

    {/* Mobile tab bar - hidden on desktop via media query */}
    <div className="mobile-tab-bar">
      <button
        className={`tab-btn ${activeTab === 'channels' ? 'tab-btn--active' : ''}`}
        onClick={() => setActiveTab('channels')}
      >
        📻 Channels
      </button>
      <button
        className={`tab-btn ${activeTab === 'map' ? 'tab-btn--active' : ''}`}
        onClick={() => setActiveTab('map')}
      >
        🗺️ Map
      </button>
    </div>
  </>
);
```

```css
/* Source: Mobile Navigation Patterns 2026, Material Design Bottom Navigation */
.mobile-tab-bar {
  display: none; /* Hidden on desktop */
}

@media (max-width: 1199px) {
  .dispatch-console__main-content {
    grid-template-columns: 1fr; /* Single column */
    padding-bottom: calc(60px + env(safe-area-inset-bottom, 0px));
  }

  .channels-panel,
  .map-panel {
    display: none;
  }

  .channels-panel.active,
  .map-panel.active {
    display: block;
  }

  .mobile-tab-bar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    display: flex;
    justify-content: space-around;
    background: var(--cv-surface);
    border-top: 1px solid var(--cv-border);
    padding: 8px;
    padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
    z-index: 10;
  }

  .tab-btn {
    min-width: 80px;
    min-height: 48px; /* 48px touch target per mobile accessibility */
    padding: 8px 16px;
    border-radius: 12px;
    background: transparent;
    border: none;
    color: var(--cv-text-muted);
    cursor: pointer;
  }

  .tab-btn--active {
    background: rgba(84, 181, 255, 0.2);
    color: var(--cv-accent);
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Padding-top percentage hack for aspect ratio | CSS `aspect-ratio` property | Sept 2021 (baseline widely available) | Cleaner code, no wrapper divs, more intuitive |
| JavaScript-based responsive switching | CSS media queries with mobile-first | Established since 2010s, reinforced 2026 | Better performance, SSR-friendly, declarative |
| Desktop-first design (max-width queries) | Mobile-first design (min-width queries) | Industry shift ~2015, dominant by 2020 | Simpler CSS, better mobile performance, progressive enhancement |
| Redux/Context for all state | useState for UI state, server state libraries for data | 2020-2024 shift | Lighter bundles, simpler code, avoid prop drilling for local UI state |
| Fixed pixel breakpoints (768px, 1024px) | Content-based breakpoints (where layout breaks) | Ongoing evolution, emphasized 2024-2026 | More maintainable, adapts better to new devices |

**Deprecated/outdated:**
- **Padding-top percentage aspect ratio hack:** Use native `aspect-ratio` property instead (widely supported since Sept 2021)
- **Float-based layouts:** Use CSS Grid or Flexbox for modern layouts (Grid since 2017, Flexbox since 2015)
- **Device-specific breakpoints (iPhone 6, iPad Air, etc.):** Use content-based breakpoints and fluid layouts instead

## Open Questions

1. **Exact collapse animation timing**
   - What we know: User spec says ~200-300ms, cubic-bezier easing recommended for natural motion
   - What's unclear: Should we use 200ms (snappier), 250ms (balanced), or 300ms (smoother)?
   - Recommendation: Start with 250ms `cubic-bezier(0.4, 0.0, 0.2, 1)` (Material Design standard easing), adjust in testing if feels too slow/fast

2. **Collapsed strip team icon design**
   - What we know: ~40px width, show team initials/icons + activity dots
   - What's unclear: How to fit team names/icons in 40px width? Rotate text? Use single-letter abbreviations? Color-coded dots only?
   - Recommendation: Stack team initials vertically in collapsed strip, use colored activity dot next to initial (green pulsing when active, orange when idle). Example: `[R ●]` for "Rescue" team with activity

3. **Stats bar item spacing**
   - What we know: User wants items spread across full width (not left-aligned)
   - What's unclear: Use `space-between`, `space-around`, or `space-evenly`?
   - Recommendation: Use `justify-content: space-around` for balanced distribution with edge spacing. Existing stats bar has 7 items + dividers - `space-around` prevents cramped edges

4. **Map placeholder minimum height above map**
   - What we know: Reserved for future quick-access controls, no background (invisible)
   - What's unclear: How much vertical space to reserve? Too much wastes screen real estate, too little may require rework in Phase 26
   - Recommendation: Start with `min-height: 80px` (~2 rows of controls at 40px each). Map panel uses `grid-template-rows: 1fr auto` so placeholder flexes to fill remaining space above 4:3 map

5. **localStorage persistence implementation**
   - What we know: User deferred decision to Phase 26, but marked as "Claude's discretion" for Phase 22
   - What's unclear: Implement now for consistency with other localStorage usage (muted channels), or defer entirely?
   - Recommendation: Implement now using existing pattern from DispatchConsole.jsx (lines 66-78). Single boolean is trivial storage, improves UX (collapse state persists on refresh), and maintains consistency with muted channels pattern. Can be removed in Phase 26 if user decides against it.

## Sources

### Primary (HIGH confidence)
- [MDN CSS Grid Layout Basic Concepts](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Grid_layout/Basic_concepts) - Grid structure, fr units, fixed+flexible columns
- [MDN aspect-ratio Property](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/aspect-ratio) - Browser support (Sept 2021), syntax, usage patterns
- [MDN Using Media Queries](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Media_queries/Using) - Media query syntax, mobile-first approach
- [MDN transition-timing-function](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/transition-timing-function) - Cubic-bezier easing functions
- Existing codebase - DispatchConsole.jsx, ChannelGrid.jsx, styles.css (current patterns and conventions)

### Secondary (MEDIUM confidence)
- [BrowserStack Responsive Design Breakpoints 2025](https://www.browserstack.com/guide/responsive-design-breakpoints) - Common breakpoint values (1200px for desktops)
- [Mobile Navigation Patterns 2026 – Phone Simulator](https://phone-simulator.com/blog/mobile-navigation-patterns-in-2026) - Bottom tab bar patterns, 44×44px touch targets
- [Material Design Bottom Navigation](https://m1.material.io/components/bottom-navigation.html) - 3-5 core actions, icon guidelines, tap behavior
- [CSS-Tricks aspect-ratio](https://css-tricks.com/almanac/properties/a/aspect-ratio/) - Browser support verification, fallback patterns
- [W3Schools Collapsed Sidebar](https://www.w3schools.com/howto/howto_js_collapse_sidebar.asp) - Collapse animation patterns
- [Josh Collinsworth - Understanding Easing and Cubic-Bezier Curves](https://joshcollinsworth.com/blog/easing-curves) - Cubic-bezier timing functions explained

### Tertiary (LOW confidence)
- [Syncfusion React State Management 2026](https://www.syncfusion.com/blogs/post/react-state-management-libraries) - useState vs Zustand/Jotai tradeoffs
- [CSS-Tricks Two Column Layouts](https://css-tricks.com/css-grid-layout-guide/) - Grid layout patterns (verified against MDN)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All technologies browser-native or already in codebase (React 18.3.1), no new dependencies
- Architecture: HIGH - CSS Grid, aspect-ratio, media queries are well-documented and widely supported; React patterns already proven in codebase
- Pitfalls: HIGH - Common issues well-documented in MDN, CSS-Tricks, and existing codebase patterns (localStorage error handling)

**Research date:** 2026-02-17
**Valid until:** 60 days (stable technologies - CSS Grid, aspect-ratio, React hooks unlikely to change)
