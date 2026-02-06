/**
 * PTT Demo Test Page
 * End-to-end testing for the complete PTT pipeline
 */

import { ConnectionManager } from '../connectionManager';
import type { ConnectionManagerOptions } from '../connectionManager';
import type { ChannelState } from '../../shared/types';

// Logger utility
class Logger {
  private logElement: HTMLElement;

  constructor(elementId: string) {
    this.logElement = document.getElementById(elementId)!;
  }

  log(message: string, type: 'info' | 'success' | 'error' | 'warning' = 'info') {
    const timestamp = new Date().toLocaleTimeString();
    const entry = document.createElement('div');
    entry.className = `log-entry ${type}`;
    entry.innerHTML = `<span class="timestamp">[${timestamp}]</span> ${message}`;
    this.logElement.appendChild(entry);
    this.logElement.scrollTop = this.logElement.scrollHeight;
  }

  clear() {
    this.logElement.innerHTML = '';
  }
}

// User panel controller
class UserPanel {
  private userId: number;
  private manager: ConnectionManager | null = null;
  private isToggleMode: boolean = false;
  private isTransmitting: boolean = false;
  private pttStartTime: number = 0;

  // DOM elements
  private tokenInput: HTMLInputElement;
  private statusDot: HTMLElement;
  private statusText: HTMLElement;
  private speakerDisplay: HTMLElement;
  private pttButton: HTMLButtonElement;
  private modeToggle: HTMLButtonElement;
  private connectButton: HTMLButtonElement;
  private disconnectButton: HTMLButtonElement;
  private rttDisplay: HTMLElement;
  private latencyDisplay: HTMLElement;
  private audioElement: HTMLAudioElement;

  constructor(userId: number, private logger: Logger) {
    this.userId = userId;

    // Get DOM elements
    this.tokenInput = document.getElementById(`token${userId}`) as HTMLInputElement;
    this.statusDot = document.getElementById(`status${userId}-dot`)!;
    this.statusText = document.getElementById(`status${userId}-text`)!;
    this.speakerDisplay = document.getElementById(`speaker${userId}-display`)!;
    this.pttButton = document.getElementById(`ptt${userId}-button`) as HTMLButtonElement;
    this.modeToggle = document.getElementById(`mode${userId}-toggle`) as HTMLButtonElement;
    this.connectButton = document.getElementById(`connect${userId}`) as HTMLButtonElement;
    this.disconnectButton = document.getElementById(`disconnect${userId}`) as HTMLButtonElement;
    this.rttDisplay = document.getElementById(`rtt${userId}`)!;
    this.latencyDisplay = document.getElementById(`latency${userId}`)!;
    this.audioElement = document.getElementById(`audio${userId}`) as HTMLAudioElement;

    // Set up event listeners
    this.setupEventListeners();
  }

  private setupEventListeners() {
    // Connect button
    this.connectButton.addEventListener('click', () => {
      this.connect();
    });

    // Disconnect button
    this.disconnectButton.addEventListener('click', () => {
      this.disconnect();
    });

    // Mode toggle
    this.modeToggle.addEventListener('click', () => {
      this.isToggleMode = !this.isToggleMode;
      this.modeToggle.textContent = this.isToggleMode ? 'Mode: Toggle' : 'Mode: Hold to Talk';
      this.logger.log(`User ${this.userId}: Switched to ${this.isToggleMode ? 'toggle' : 'hold-to-talk'} mode`, 'info');
    });

    // PTT button - Hold to talk mode
    this.pttButton.addEventListener('mousedown', () => {
      if (!this.isToggleMode) {
        this.startPTT();
      }
    });

    this.pttButton.addEventListener('mouseup', () => {
      if (!this.isToggleMode) {
        this.stopPTT();
      }
    });

    this.pttButton.addEventListener('mouseleave', () => {
      if (!this.isToggleMode && this.isTransmitting) {
        this.stopPTT();
      }
    });

    // PTT button - Toggle mode
    this.pttButton.addEventListener('click', () => {
      if (this.isToggleMode) {
        if (this.isTransmitting) {
          this.stopPTT();
        } else {
          this.startPTT();
        }
      }
    });

    // Audio element events for latency measurement
    this.audioElement.addEventListener('playing', () => {
      if (this.pttStartTime > 0) {
        const latency = performance.now() - this.pttStartTime;
        this.latencyDisplay.textContent = Math.round(latency).toString();
        this.logger.log(`User ${this.userId}: PTT latency ${Math.round(latency)}ms`, 'success');
        this.pttStartTime = 0;
      }
    });
  }

  private async connect() {
    const token = this.tokenInput.value.trim();
    if (!token) {
      this.logger.log(`User ${this.userId}: Token required`, 'error');
      alert('Please paste a JWT token');
      return;
    }

    const serverUrl = (document.getElementById('serverUrl') as HTMLInputElement).value;
    const channelId = (document.getElementById('channelId') as HTMLInputElement).value;

    try {
      this.logger.log(`User ${this.userId}: Connecting to ${serverUrl}...`, 'info');

      const options: ConnectionManagerOptions = {
        url: serverUrl,
        token: token,
        channelId: channelId,
        onStateChange: (state, details) => {
          this.updateConnectionState(state, details);
        },
        onError: (error) => {
          this.logger.log(`User ${this.userId}: Error - ${error.message}`, 'error');
        },
        onChannelStateUpdate: (channelState) => {
          this.updateChannelState(channelState);
        },
        onSpeakerChanged: (userId, userName) => {
          this.updateSpeaker(userId, userName);
        },
      };

      this.manager = new ConnectionManager(options);
      await this.manager.connect();

      this.pttButton.disabled = false;
      this.connectButton.disabled = true;
      this.disconnectButton.disabled = false;

      this.logger.log(`User ${this.userId}: Connected successfully`, 'success');
    } catch (error) {
      this.logger.log(`User ${this.userId}: Connection failed - ${error}`, 'error');
    }
  }

  private async disconnect() {
    if (!this.manager) return;

    try {
      this.logger.log(`User ${this.userId}: Disconnecting...`, 'info');
      await this.manager.disconnect();

      this.pttButton.disabled = true;
      this.connectButton.disabled = false;
      this.disconnectButton.disabled = true;
      this.isTransmitting = false;

      this.logger.log(`User ${this.userId}: Disconnected`, 'success');
    } catch (error) {
      this.logger.log(`User ${this.userId}: Disconnect error - ${error}`, 'error');
    }
  }

  private async startPTT() {
    if (!this.manager || this.isTransmitting) return;

    try {
      this.pttStartTime = performance.now();
      this.logger.log(`User ${this.userId}: PTT start requested`, 'info');

      await this.manager.startTransmitting();

      this.isTransmitting = true;
      this.pttButton.classList.add('transmitting');
      this.pttButton.textContent = 'TRANSMITTING';

      this.logger.log(`User ${this.userId}: PTT started`, 'success');
    } catch (error) {
      this.pttButton.classList.add('busy');
      this.pttButton.textContent = 'BUSY';
      this.logger.log(`User ${this.userId}: PTT denied - ${error}`, 'warning');

      // Reset busy state after 3 seconds
      setTimeout(() => {
        this.pttButton.classList.remove('busy');
        this.pttButton.textContent = 'PTT';
      }, 3000);
    }
  }

  private async stopPTT() {
    if (!this.manager || !this.isTransmitting) return;

    try {
      this.logger.log(`User ${this.userId}: PTT stop requested`, 'info');

      await this.manager.stopTransmitting();

      this.isTransmitting = false;
      this.pttButton.classList.remove('transmitting');
      this.pttButton.textContent = 'PTT';

      this.logger.log(`User ${this.userId}: PTT stopped`, 'success');
    } catch (error) {
      this.logger.log(`User ${this.userId}: PTT stop error - ${error}`, 'error');
    }
  }

  private updateConnectionState(state: string, details?: string) {
    // Update status dot
    this.statusDot.className = `status-dot ${state}`;

    // Update status text
    let statusText = state.charAt(0).toUpperCase() + state.slice(1);
    if (details) {
      statusText += ` - ${details}`;
    }
    this.statusText.textContent = statusText;

    this.logger.log(`User ${this.userId}: Connection state - ${state}${details ? ` (${details})` : ''}`, 'info');
  }

  private updateChannelState(channelState: ChannelState) {
    this.logger.log(`User ${this.userId}: Channel state updated - ${JSON.stringify(channelState)}`, 'info');
  }

  private updateSpeaker(userId: string | null, userName: string | null) {
    if (userId && userName) {
      this.speakerDisplay.textContent = `${userName} is speaking`;
      this.speakerDisplay.classList.add('speaking');
      this.logger.log(`User ${this.userId}: Speaker changed - ${userName}`, 'info');
    } else {
      this.speakerDisplay.textContent = 'Channel free';
      this.speakerDisplay.classList.remove('speaking');
      this.logger.log(`User ${this.userId}: Channel free`, 'info');
    }
  }
}

// Initialize demo page
document.addEventListener('DOMContentLoaded', () => {
  const logger = new Logger('logConsole');

  logger.log('PTT Demo Test Page initialized', 'success');
  logger.log('Generate tokens: npx tsx scripts/generate-test-token.ts --userId user1 --userName "Alice"', 'info');
  logger.log('Generate tokens: npx tsx scripts/generate-test-token.ts --userId user2 --userName "Bob"', 'info');

  // Create user panels
  const user1 = new UserPanel(1, logger);
  const user2 = new UserPanel(2, logger);

  // Expose to window for debugging
  (window as any).user1 = user1;
  (window as any).user2 = user2;
  (window as any).logger = logger;
});
