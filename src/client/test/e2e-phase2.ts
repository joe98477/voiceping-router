/**
 * Phase 2 E2E Test Page
 * Browser-based test for Phase 2 features: role-based permissions, priority PTT, admin controls
 */

// Simple logger utility for test panels
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

// Simple WebSocket signaling client for testing
class TestSignalingClient {
  private ws: WebSocket | null = null;
  private token: string;
  private logger: Logger;
  private messageHandlers: Map<string, (msg: any) => void> = new Map();

  public onConnected: (() => void) | null = null;
  public onDisconnected: (() => void) | null = null;
  public onMessage: ((msg: any) => void) | null = null;

  constructor(token: string, logger: Logger) {
    this.token = token;
    this.logger = logger;
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws?token=${this.token}`;

      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        this.logger.log('Connected to server', 'success');
        if (this.onConnected) this.onConnected();
        resolve();
      };

      this.ws.onerror = (err) => {
        this.logger.log('Connection error', 'error');
        reject(err);
      };

      this.ws.onclose = () => {
        this.logger.log('Disconnected from server', 'warning');
        if (this.onDisconnected) this.onDisconnected();
      };

      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          this.handleMessage(msg);
        } catch (err) {
          this.logger.log('Failed to parse message', 'error');
        }
      };
    });
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  send(type: string, data?: any, id?: string): Promise<any> {
    return new Promise((resolve, reject) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        reject(new Error('Not connected'));
        return;
      }

      const messageId = id || `${type}-${Date.now()}-${Math.random()}`;
      const message = { type, id: messageId, data };

      // Set up response handler
      const timeout = setTimeout(() => {
        this.messageHandlers.delete(messageId);
        reject(new Error('Request timeout'));
      }, 10000);

      this.messageHandlers.set(messageId, (response) => {
        clearTimeout(timeout);
        this.messageHandlers.delete(messageId);
        if (response.error) {
          reject(new Error(response.error));
        } else {
          resolve(response);
        }
      });

      this.ws.send(JSON.stringify(message));
    });
  }

  sendNoResponse(type: string, data?: any) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      this.logger.log('Cannot send: not connected', 'error');
      return;
    }

    const message = { type, data };
    this.ws.send(JSON.stringify(message));
  }

  private handleMessage(msg: any) {
    // Check for response to a request
    if (msg.id && this.messageHandlers.has(msg.id)) {
      const handler = this.messageHandlers.get(msg.id)!;
      handler(msg);
      return;
    }

    // Log the message
    if (msg.type === 'error') {
      this.logger.log(`Error: ${msg.error || 'Unknown error'}`, 'error');
    } else if (msg.type === 'speaker-changed') {
      this.logger.log(`Speaker changed: ${msg.data?.currentSpeaker || 'none'}`, 'info');
    } else if (msg.type === 'ptt-denied') {
      this.logger.log(`PTT denied: ${msg.data?.reason || 'unknown'}`, 'warning');
    } else if (msg.type === 'ptt-interrupted') {
      this.logger.log(`PTT interrupted by ${msg.data?.interruptedBy || 'unknown'}`, 'warning');
    } else if (msg.type === 'permission-update') {
      this.logger.log(`Permission update: +${msg.data?.added?.length || 0} -${msg.data?.removed?.length || 0}`, 'info');
    } else if (msg.type === 'force-disconnect') {
      this.logger.log(`Force disconnected: ${msg.data?.reason || 'unknown'}`, 'error');
    } else {
      this.logger.log(`Received: ${msg.type}`, 'info');
    }

    // Call generic message handler
    if (this.onMessage) {
      this.onMessage(msg);
    }
  }
}

// Test panel controller
class TestPanel {
  private role: 'ADMIN' | 'DISPATCH' | 'GENERAL';
  private userId: string;
  private channels: string[] = ['test-channel-1', 'test-channel-2'];
  private joinedChannels: Set<string> = new Set();
  private client: TestSignalingClient | null = null;
  private logger: Logger;
  private currentChannel: string | null = null;
  private pttActive: boolean = false;
  private emergencyHoldTimer: NodeJS.Timeout | null = null;

  // DOM elements
  private statusDot: HTMLElement;
  private statusText: HTMLElement;
  private connectBtn: HTMLButtonElement;
  private disconnectBtn: HTMLButtonElement;
  private channelsContainer: HTMLElement;
  private pttBtn: HTMLButtonElement | null = null;
  private priorityPttBtn: HTMLButtonElement | null = null;
  private emergencyBtn: HTMLButtonElement | null = null;
  private forceDisconnectSelect: HTMLSelectElement | null = null;
  private forceDisconnectBtn: HTMLButtonElement | null = null;
  private banSelect: HTMLSelectElement | null = null;
  private banBtn: HTMLButtonElement | null = null;
  private unbanBtn: HTMLButtonElement | null = null;

  constructor(
    role: 'ADMIN' | 'DISPATCH' | 'GENERAL',
    userId: string,
    prefix: string
  ) {
    this.role = role;
    this.userId = userId;
    this.logger = new Logger(`${prefix}Log`);

    // Get DOM elements
    this.statusDot = document.getElementById(`${prefix}StatusDot`)!;
    this.statusText = document.getElementById(`${prefix}StatusText`)!;
    this.connectBtn = document.getElementById(`${prefix}ConnectBtn`) as HTMLButtonElement;
    this.disconnectBtn = document.getElementById(`${prefix}DisconnectBtn`) as HTMLButtonElement;
    this.channelsContainer = document.getElementById(`${prefix}Channels`)!;
    this.pttBtn = document.getElementById(`${prefix}PttBtn`) as HTMLButtonElement | null;

    // Role-specific elements
    if (role === 'DISPATCH') {
      this.priorityPttBtn = document.getElementById(`${prefix}PriorityPttBtn`) as HTMLButtonElement;
      this.emergencyBtn = document.getElementById(`${prefix}EmergencyBtn`) as HTMLButtonElement;
      this.forceDisconnectSelect = document.getElementById(`${prefix}ForceDisconnectUser`) as HTMLSelectElement;
      this.forceDisconnectBtn = document.getElementById(`${prefix}ForceDisconnectBtn`) as HTMLButtonElement;
    } else if (role === 'ADMIN') {
      this.forceDisconnectSelect = document.getElementById(`${prefix}ForceDisconnectUser`) as HTMLSelectElement;
      this.forceDisconnectBtn = document.getElementById(`${prefix}ForceDisconnectBtn`) as HTMLButtonElement;
      this.banSelect = document.getElementById(`${prefix}BanUser`) as HTMLSelectElement;
      this.banBtn = document.getElementById(`${prefix}BanBtn`) as HTMLButtonElement;
      this.unbanBtn = document.getElementById(`${prefix}UnbanBtn`) as HTMLButtonElement;
    }

    this.setupEventListeners();
    this.renderChannels();
  }

  private setupEventListeners() {
    this.connectBtn.addEventListener('click', () => this.connect());
    this.disconnectBtn.addEventListener('click', () => this.disconnect());

    if (this.pttBtn) {
      this.pttBtn.addEventListener('mousedown', () => this.startPtt(false));
      this.pttBtn.addEventListener('mouseup', () => this.stopPtt(false));
      this.pttBtn.addEventListener('mouseleave', () => this.stopPtt(false));
    }

    if (this.priorityPttBtn) {
      this.priorityPttBtn.addEventListener('mousedown', () => this.startPtt(true));
      this.priorityPttBtn.addEventListener('mouseup', () => this.stopPtt(true));
      this.priorityPttBtn.addEventListener('mouseleave', () => this.stopPtt(true));
    }

    if (this.emergencyBtn) {
      this.emergencyBtn.addEventListener('mousedown', () => this.startEmergencyBroadcast());
      this.emergencyBtn.addEventListener('mouseup', () => this.cancelEmergencyBroadcast());
      this.emergencyBtn.addEventListener('mouseleave', () => this.cancelEmergencyBroadcast());
    }

    if (this.forceDisconnectBtn) {
      this.forceDisconnectBtn.addEventListener('click', () => this.forceDisconnectUser());
    }

    if (this.banBtn) {
      this.banBtn.addEventListener('click', () => this.banUser());
    }

    if (this.unbanBtn) {
      this.unbanBtn.addEventListener('click', () => this.unbanUser());
    }
  }

  private async connect() {
    try {
      this.logger.log('Connecting...', 'info');

      // Generate token
      const token = await this.generateToken();

      // Create client
      this.client = new TestSignalingClient(token, this.logger);

      this.client.onConnected = () => {
        this.updateStatus(true);
      };

      this.client.onDisconnected = () => {
        this.updateStatus(false);
        this.client = null;
      };

      await this.client.connect();
    } catch (err: any) {
      this.logger.log(`Connection failed: ${err.message}`, 'error');
    }
  }

  private disconnect() {
    if (this.client) {
      this.client.disconnect();
      this.client = null;
    }
    this.updateStatus(false);
  }

  private updateStatus(connected: boolean) {
    if (connected) {
      this.statusDot.classList.add('connected');
      this.statusText.textContent = 'Connected';
      this.connectBtn.disabled = true;
      this.disconnectBtn.disabled = false;
      if (this.pttBtn) this.pttBtn.disabled = false;
      if (this.priorityPttBtn) this.priorityPttBtn.disabled = false;
      if (this.emergencyBtn) this.emergencyBtn.disabled = false;
    } else {
      this.statusDot.classList.remove('connected');
      this.statusText.textContent = 'Disconnected';
      this.connectBtn.disabled = false;
      this.disconnectBtn.disabled = true;
      if (this.pttBtn) this.pttBtn.disabled = true;
      if (this.priorityPttBtn) this.priorityPttBtn.disabled = true;
      if (this.emergencyBtn) this.emergencyBtn.disabled = true;
      this.joinedChannels.clear();
      this.renderChannels();
    }
  }

  private renderChannels() {
    this.channelsContainer.innerHTML = '';

    for (const channelId of this.channels) {
      const item = document.createElement('div');
      item.className = `channel-item ${this.joinedChannels.has(channelId) ? 'joined' : ''}`;

      const name = document.createElement('div');
      name.className = 'channel-name';
      name.textContent = channelId;

      const actions = document.createElement('div');
      actions.className = 'channel-actions';

      const joinBtn = document.createElement('button');
      joinBtn.className = 'btn-success btn-small';
      joinBtn.textContent = 'Join';
      joinBtn.disabled = this.joinedChannels.has(channelId);
      joinBtn.addEventListener('click', () => this.joinChannel(channelId));

      const leaveBtn = document.createElement('button');
      leaveBtn.className = 'btn-danger btn-small';
      leaveBtn.textContent = 'Leave';
      leaveBtn.disabled = !this.joinedChannels.has(channelId);
      leaveBtn.addEventListener('click', () => this.leaveChannel(channelId));

      actions.appendChild(joinBtn);
      actions.appendChild(leaveBtn);

      item.appendChild(name);
      item.appendChild(actions);

      this.channelsContainer.appendChild(item);
    }
  }

  private async joinChannel(channelId: string) {
    if (!this.client) {
      this.logger.log('Not connected', 'error');
      return;
    }

    try {
      this.logger.log(`Joining ${channelId}...`, 'info');
      await this.client.send('join-channel', { channelId });
      this.joinedChannels.add(channelId);
      this.currentChannel = channelId;
      this.renderChannels();
      this.logger.log(`Joined ${channelId}`, 'success');
    } catch (err: any) {
      this.logger.log(`Join failed: ${err.message}`, 'error');
    }
  }

  private async leaveChannel(channelId: string) {
    if (!this.client) {
      this.logger.log('Not connected', 'error');
      return;
    }

    try {
      this.logger.log(`Leaving ${channelId}...`, 'info');
      await this.client.send('leave-channel', { channelId });
      this.joinedChannels.delete(channelId);
      if (this.currentChannel === channelId) {
        this.currentChannel = this.joinedChannels.values().next().value || null;
      }
      this.renderChannels();
      this.logger.log(`Left ${channelId}`, 'success');
    } catch (err: any) {
      this.logger.log(`Leave failed: ${err.message}`, 'error');
    }
  }

  private startPtt(priority: boolean) {
    if (!this.client || !this.currentChannel || this.pttActive) {
      return;
    }

    this.pttActive = true;
    const type = priority ? 'priority-ptt-start' : 'ptt-start';

    this.client.sendNoResponse(type, { channelId: this.currentChannel });
    this.logger.log(`${priority ? 'Priority ' : ''}PTT started on ${this.currentChannel}`, 'success');

    if (this.pttBtn && !priority) {
      this.pttBtn.classList.add('active');
    }
    if (this.priorityPttBtn && priority) {
      this.priorityPttBtn.classList.add('active');
    }
  }

  private stopPtt(priority: boolean) {
    if (!this.client || !this.currentChannel || !this.pttActive) {
      return;
    }

    this.pttActive = false;
    const type = priority ? 'priority-ptt-stop' : 'ptt-stop';

    this.client.sendNoResponse(type, { channelId: this.currentChannel });
    this.logger.log(`${priority ? 'Priority ' : ''}PTT stopped`, 'info');

    if (this.pttBtn && !priority) {
      this.pttBtn.classList.remove('active');
    }
    if (this.priorityPttBtn && priority) {
      this.priorityPttBtn.classList.remove('active');
    }
  }

  private startEmergencyBroadcast() {
    if (!this.client || this.emergencyHoldTimer) {
      return;
    }

    this.logger.log('Emergency broadcast: Hold for 2 seconds...', 'warning');

    this.emergencyHoldTimer = setTimeout(() => {
      if (this.client) {
        this.client.sendNoResponse('emergency-broadcast-start');
        this.logger.log('Emergency broadcast ACTIVATED', 'error');
        if (this.emergencyBtn) {
          this.emergencyBtn.classList.add('active');
        }
      }
      this.emergencyHoldTimer = null;
    }, 2000);
  }

  private cancelEmergencyBroadcast() {
    if (this.emergencyHoldTimer) {
      clearTimeout(this.emergencyHoldTimer);
      this.emergencyHoldTimer = null;
      this.logger.log('Emergency broadcast cancelled', 'info');
    } else if (this.client && this.emergencyBtn?.classList.contains('active')) {
      this.client.sendNoResponse('emergency-broadcast-stop');
      this.logger.log('Emergency broadcast stopped', 'info');
      if (this.emergencyBtn) {
        this.emergencyBtn.classList.remove('active');
      }
    }
  }

  private async forceDisconnectUser() {
    if (!this.client || !this.forceDisconnectSelect) {
      return;
    }

    const targetUserId = this.forceDisconnectSelect.value;
    if (!targetUserId) {
      this.logger.log('No user selected', 'warning');
      return;
    }

    try {
      this.logger.log(`Force disconnecting ${targetUserId}...`, 'warning');
      await this.client.send('force-disconnect', {
        targetUserId,
        reason: 'Admin action from test page',
      });
      this.logger.log(`Force disconnected ${targetUserId}`, 'success');
    } catch (err: any) {
      this.logger.log(`Force disconnect failed: ${err.message}`, 'error');
    }
  }

  private async banUser() {
    if (!this.client || !this.banSelect) {
      return;
    }

    const targetUserId = this.banSelect.value;
    if (!targetUserId) {
      this.logger.log('No user selected', 'warning');
      return;
    }

    try {
      this.logger.log(`Banning ${targetUserId}...`, 'warning');
      await this.client.send('ban-user', {
        targetUserId,
        reason: 'Banned from test page',
        durationMs: 3600000, // 1 hour
      });
      this.logger.log(`Banned ${targetUserId}`, 'success');
    } catch (err: any) {
      this.logger.log(`Ban failed: ${err.message}`, 'error');
    }
  }

  private async unbanUser() {
    if (!this.client || !this.banSelect) {
      return;
    }

    const targetUserId = this.banSelect.value;
    if (!targetUserId) {
      this.logger.log('No user selected', 'warning');
      return;
    }

    try {
      this.logger.log(`Unbanning ${targetUserId}...`, 'info');
      await this.client.send('unban-user', { targetUserId });
      this.logger.log(`Unbanned ${targetUserId}`, 'success');
    } catch (err: any) {
      this.logger.log(`Unban failed: ${err.message}`, 'error');
    }
  }

  private async generateToken(): Promise<string> {
    // For simplicity, we'll use a simple JWT generation on the client side
    // In a real scenario, tokens should come from the backend
    const payload = {
      userId: this.userId,
      userName: `${this.role} User`,
      eventId: 'test-event-1',
      role: this.role,
      globalRole: this.role === 'ADMIN' ? 'ADMIN' : 'USER',
      channelIds: this.channels,
    };

    // For testing, we'll make a request to a token generation endpoint
    // Since we don't have one, we'll return a placeholder
    // In production, this would call: POST /dev/generate-token
    return `test-token-${this.userId}`;
  }

  clearLog() {
    this.logger.clear();
  }
}

// Initialize panels
const adminPanel = new TestPanel('ADMIN', 'admin-1', 'admin');
const dispatchPanel = new TestPanel('DISPATCH', 'dispatch-1', 'dispatch');
const generalPanel = new TestPanel('GENERAL', 'general-1', 'general');

// Global controls
const seedDataBtn = document.getElementById('seedDataBtn')!;
const clearLogsBtn = document.getElementById('clearLogsBtn')!;
const generateTokenBtn = document.getElementById('generateTokenBtn')!;
const tokenOutput = document.getElementById('tokenOutput')!;
const tokenDisplay = document.getElementById('tokenDisplay')!;

seedDataBtn.addEventListener('click', async () => {
  try {
    const response = await fetch('/dev/seed-test-data', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });

    if (response.ok) {
      alert('Test data seeded successfully!');
    } else {
      const error = await response.text();
      alert(`Failed to seed data: ${error}`);
    }
  } catch (err: any) {
    alert(`Error: ${err.message}`);
  }
});

clearLogsBtn.addEventListener('click', () => {
  adminPanel.clearLog();
  dispatchPanel.clearLog();
  generalPanel.clearLog();
});

generateTokenBtn.addEventListener('click', () => {
  const userId = (document.getElementById('tokenUserId') as HTMLInputElement).value;
  const role = (document.getElementById('tokenRole') as HTMLSelectElement).value;
  const eventId = (document.getElementById('tokenEventId') as HTMLInputElement).value;
  const channelIds = (document.getElementById('tokenChannelIds') as HTMLInputElement).value
    .split(',')
    .map(c => c.trim())
    .filter(c => c.length > 0);

  if (!userId) {
    alert('User ID is required');
    return;
  }

  // Simple token generation (base64 encoded payload for demo purposes)
  // In production, this would be a proper JWT signed by the server
  const payload = JSON.stringify({
    userId,
    userName: `${role} User`,
    eventId,
    role,
    globalRole: role === 'ADMIN' ? 'ADMIN' : 'USER',
    channelIds,
  });

  const token = btoa(payload);
  tokenDisplay.textContent = token;
  tokenOutput.style.display = 'block';
});

console.log('Phase 2 E2E Test Page Loaded');
console.log('Test users: admin-1, dispatch-1, general-1');
console.log('Test channels: test-channel-1, test-channel-2');
