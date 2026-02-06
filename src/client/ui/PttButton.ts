/**
 * PTT Button UI component with hold-to-talk and toggle modes
 * Framework-agnostic vanilla TypeScript class
 */

import { PttMode, PttState } from '../../shared/types';

export interface PttButtonOptions {
  mode: PttMode;
  onPttStart: () => void;
  onPttStop: () => void;
}

export interface SpeakerInfo {
  name: string;
}

export class PttButton {
  private container: HTMLElement;
  private button: HTMLButtonElement;
  private statusElement: HTMLElement;
  private options: PttButtonOptions;
  private currentState: PttState = PttState.IDLE;
  private toggleActive: boolean = false;
  private enabled: boolean = false;
  private blockTimeout: number | null = null;

  constructor(container: HTMLElement, options: PttButtonOptions) {
    this.container = container;
    this.options = options;

    // Create button element
    this.button = document.createElement('button');
    this.button.className = 'ptt-button ptt-idle';
    this.button.textContent = 'Push to Talk';
    this.button.disabled = true; // Start disabled until init() is called

    // Create status element for busy state messages
    this.statusElement = document.createElement('div');
    this.statusElement.className = 'ptt-status';
    this.statusElement.style.display = 'none';

    // Add to container
    this.container.appendChild(this.button);
    this.container.appendChild(this.statusElement);

    // Wire event handlers based on mode
    this.wireEventHandlers();
  }

  /**
   * Wire event handlers based on PTT mode
   */
  private wireEventHandlers(): void {
    if (this.options.mode === PttMode.HOLD_TO_TALK) {
      this.wireHoldMode();
    } else {
      this.wireToggleMode();
    }
  }

  /**
   * Wire hold-to-talk mode event handlers
   */
  private wireHoldMode(): void {
    // Remove any existing listeners
    this.clearEventListeners();

    // Mouse events
    this.button.addEventListener('mousedown', this.handleHoldStart);
    this.button.addEventListener('mouseup', this.handleHoldStop);
    this.button.addEventListener('mouseleave', this.handleHoldStop);

    // Touch events
    this.button.addEventListener('touchstart', this.handleTouchStart);
    this.button.addEventListener('touchend', this.handleTouchStop);
    this.button.addEventListener('touchcancel', this.handleTouchStop);
  }

  /**
   * Wire toggle mode event handlers
   */
  private wireToggleMode(): void {
    // Remove any existing listeners
    this.clearEventListeners();

    // Click event for toggle
    this.button.addEventListener('click', this.handleToggleClick);
  }

  /**
   * Handle hold mode press start
   */
  private handleHoldStart = (event: MouseEvent): void => {
    event.preventDefault();
    if (!this.enabled || this.currentState === PttState.BLOCKED) {
      return;
    }
    this.options.onPttStart();
  };

  /**
   * Handle hold mode press stop
   */
  private handleHoldStop = (event: MouseEvent): void => {
    event.preventDefault();
    if (!this.enabled || this.currentState !== PttState.TRANSMITTING) {
      return;
    }
    this.options.onPttStop();
  };

  /**
   * Handle touch start for hold mode
   */
  private handleTouchStart = (event: TouchEvent): void => {
    event.preventDefault(); // Prevent scrolling during PTT
    if (!this.enabled || this.currentState === PttState.BLOCKED) {
      return;
    }
    this.options.onPttStart();
  };

  /**
   * Handle touch stop for hold mode
   */
  private handleTouchStop = (event: TouchEvent): void => {
    event.preventDefault();
    if (!this.enabled || this.currentState !== PttState.TRANSMITTING) {
      return;
    }
    this.options.onPttStop();
  };

  /**
   * Handle toggle mode click
   */
  private handleToggleClick = (event: MouseEvent): void => {
    event.preventDefault();
    if (!this.enabled || this.currentState === PttState.BLOCKED) {
      return;
    }

    if (this.toggleActive) {
      // Toggle off
      this.toggleActive = false;
      this.options.onPttStop();
    } else {
      // Toggle on
      this.toggleActive = true;
      this.options.onPttStart();
    }
  };

  /**
   * Clear all event listeners
   */
  private clearEventListeners(): void {
    // Remove hold mode listeners
    this.button.removeEventListener('mousedown', this.handleHoldStart);
    this.button.removeEventListener('mouseup', this.handleHoldStop);
    this.button.removeEventListener('mouseleave', this.handleHoldStop);
    this.button.removeEventListener('touchstart', this.handleTouchStart);
    this.button.removeEventListener('touchend', this.handleTouchStop);
    this.button.removeEventListener('touchcancel', this.handleTouchStop);

    // Remove toggle mode listeners
    this.button.removeEventListener('click', this.handleToggleClick);
  }

  /**
   * Set PTT mode (hold or toggle)
   */
  setMode(mode: PttMode): void {
    this.options.mode = mode;
    this.toggleActive = false;
    this.wireEventHandlers();
    console.log(`PTT mode set to: ${mode}`);
  }

  /**
   * Set visual state of button
   */
  setState(state: PttState, speakerInfo?: SpeakerInfo): void {
    this.currentState = state;

    // Clear any existing block timeout
    if (this.blockTimeout) {
      clearTimeout(this.blockTimeout);
      this.blockTimeout = null;
    }

    // Remove all state classes
    this.button.classList.remove('ptt-idle', 'ptt-transmitting', 'ptt-blocked');

    // Apply new state class
    switch (state) {
      case PttState.IDLE:
        this.button.classList.add('ptt-idle');
        this.button.textContent = 'Push to Talk';
        this.statusElement.style.display = 'none';
        this.toggleActive = false;
        break;

      case PttState.TRANSMITTING:
        this.button.classList.add('ptt-transmitting');
        this.button.textContent = 'Transmitting...';
        this.statusElement.style.display = 'none';
        break;

      case PttState.BLOCKED:
        this.button.classList.add('ptt-blocked');
        this.button.textContent = 'Channel Busy';

        // Show speaker info if provided
        if (speakerInfo) {
          this.statusElement.textContent = `${speakerInfo.name} is speaking`;
          this.statusElement.style.display = 'block';
        }

        // Auto-revert to IDLE after 3 seconds
        this.blockTimeout = window.setTimeout(() => {
          this.setState(PttState.IDLE);
        }, 3000);
        break;
    }

    console.log(`PTT button state: ${state}`);
  }

  /**
   * Enable or disable button
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
    this.button.disabled = !enabled;

    if (!enabled) {
      // Revert to idle when disabled
      this.setState(PttState.IDLE);
    }

    console.log(`PTT button ${enabled ? 'enabled' : 'disabled'}`);
  }

  /**
   * Get current state
   */
  getState(): PttState {
    return this.currentState;
  }

  /**
   * Destroy button and remove all event listeners
   */
  destroy(): void {
    this.clearEventListeners();

    if (this.blockTimeout) {
      clearTimeout(this.blockTimeout);
      this.blockTimeout = null;
    }

    this.button.remove();
    this.statusElement.remove();

    console.log('PTT button destroyed');
  }
}
