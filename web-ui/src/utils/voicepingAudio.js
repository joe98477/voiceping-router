import notepack from "notepack.io";
import { OpusDecoder } from "opus-decoder";

const ChannelType = {
  GROUP: 0,
  PRIVATE: 1
};

const MessageType = {
  START: 1,
  STOP: 2,
  AUDIO: 3,
  START_ACK: 6,
  STOP_ACK: 7,
  START_FAILED: 8,
  UNAUTHORIZED_GROUP: 27
};

const DEFAULT_SAMPLE_RATE = 48000;
const DEFAULT_BITRATE = 24000;
const DEFAULT_JITTER_TARGET_MS = 100;
const DEFAULT_JITTER_RESET_MS = 500;
const DEBUG_AUDIO = import.meta.env.VITE_DEBUG_AUDIO === "true";

const decodeMessage = (data) => {
  let decoded;
  try {
    decoded = notepack.decode(new Uint8Array(data));
  } catch (err) {
    return null;
  }
  if (!Array.isArray(decoded) || decoded.length < 5) {
    return null;
  }
  const [channelType, messageType, fromId, toId, messageIdOrPayload, payloadMaybe] = decoded;
  if (decoded.length >= 6) {
    return {
      channelType,
      messageType,
      fromId,
      toId,
      messageId: messageIdOrPayload,
      payload: payloadMaybe
    };
  }
  return {
    channelType,
    messageType,
    fromId,
    toId,
    messageId: null,
    payload: messageIdOrPayload
  };
};

const encodeMessage = ({ channelType, messageType, fromId, toId, messageId, payload }) => {
  const frame = messageId
    ? [channelType, messageType, fromId, toId, messageId, payload]
    : [channelType, messageType, fromId, toId, payload];
  return notepack.encode(frame);
};

const isWebCodecsSupported = () => !!(window.AudioEncoder && window.MediaStreamTrackProcessor);

class AudioOutput {
  constructor(sampleRate = DEFAULT_SAMPLE_RATE) {
    this.sampleRate = sampleRate;
    this.context = new AudioContext({ latencyHint: "interactive", sampleRate });
    this.decoder = new OpusDecoder({ sampleRate, channels: 1 });
    this.playhead = this.context.currentTime;
    this.ready = false;
    this.pending = [];
    this.lastPacketAt = null;
    this.jitterTargetMs = DEFAULT_JITTER_TARGET_MS;
    this.jitterResetMs = DEFAULT_JITTER_RESET_MS;
    this.stats = {
      played: 0,
      droppedEmpty: 0,
      droppedDecode: 0,
      droppedInvalid: 0,
      droppedCreateBuffer: 0
    };
    this.decoder.ready
      .then(() => {
        this.ready = true;
        const queued = this.pending;
        this.pending = [];
        queued.forEach(({ packet, delay }) => this.playOpusPacket(packet, delay));
      })
      .catch(() => {});
  }

  async ensureRunning() {
    if (this.context.state === "suspended") {
      await this.context.resume();
    }
  }

  playOpusPacket(packet, delaySeconds = 0) {
    if (!packet) {
      this.stats.droppedEmpty += 1;
      return;
    }
    if (!this.ready) {
      this.pending.push({ packet, delay: delaySeconds });
      return;
    }
    const frame = packet instanceof Uint8Array ? packet : new Uint8Array(packet);
    if (frame.byteLength === 0) {
      this.stats.droppedEmpty += 1;
      return;
    }
    let decoded = null;
    try {
      decoded = this.decoder.decodeFrame(frame);
    } catch (err) {
      this.stats.droppedDecode += 1;
      if (DEBUG_AUDIO) {
        console.debug("audio decode failed", err);
      }
      return;
    }
    if (!decoded) {
      this.stats.droppedDecode += 1;
      return;
    }
    const channels = Array.isArray(decoded) ? decoded : [decoded];
    if (!channels[0] || channels[0].length === 0) {
      this.stats.droppedInvalid += 1;
      return;
    }
    const length = channels[0].length;
    if (!Number.isFinite(length) || length <= 0) {
      this.stats.droppedInvalid += 1;
      return;
    }
    let buffer = null;
    try {
      buffer = this.context.createBuffer(channels.length, length, this.sampleRate);
    } catch (err) {
      this.stats.droppedCreateBuffer += 1;
      if (DEBUG_AUDIO) {
        console.debug("audio createBuffer failed", err);
      }
      return;
    }
    channels.forEach((data, index) => {
      buffer.getChannelData(index).set(data);
    });
    const source = this.context.createBufferSource();
    source.buffer = buffer;
    source.connect(this.context.destination);
    const now = this.context.currentTime;
    if (!this.lastPacketAt || (now - this.lastPacketAt) * 1000 > this.jitterResetMs) {
      this.playhead = Math.max(this.playhead, now + this.jitterTargetMs / 1000);
    }
    const startAt = Math.max(now, this.playhead) + delaySeconds;
    source.start(startAt);
    this.playhead = startAt + buffer.duration;
    this.lastPacketAt = now;
    this.stats.played += 1;
  }
}

class AudioTransmitter {
  constructor({ onPacket, onError, sampleRate = DEFAULT_SAMPLE_RATE }) {
    this.onPacket = onPacket;
    this.onError = onError;
    this.sampleRate = sampleRate;
    this.encoder = null;
    this.reader = null;
    this.stream = null;
    this.running = false;
    this.stats = {
      encoded: 0,
      droppedEmpty: 0,
      lastChunkBytes: 0
    };
  }

  async start({ deviceId }) {
    if (!isWebCodecsSupported()) {
      throw new Error("WebCodecs not supported in this browser");
    }
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        deviceId: deviceId ? { exact: deviceId } : undefined,
        channelCount: 1,
        sampleRate: this.sampleRate,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    });
    const track = this.stream.getAudioTracks()[0];
    const processor = new MediaStreamTrackProcessor({ track });
    this.reader = processor.readable.getReader();
    this.encoder = new AudioEncoder({
      output: (chunk) => {
        if (!chunk || chunk.byteLength <= 0) {
          this.stats.droppedEmpty += 1;
          if (DEBUG_AUDIO) {
            console.debug("audio encoder produced empty chunk");
          }
          return;
        }
        const data = new Uint8Array(chunk.byteLength);
        chunk.copyTo(data);
        this.stats.encoded += 1;
        this.stats.lastChunkBytes = data.byteLength;
        this.onPacket(data);
      },
      error: (err) => {
        if (this.onError) {
          this.onError(err);
        }
      }
    });
    const config = {
      codec: "opus",
      sampleRate: this.sampleRate,
      numberOfChannels: 1,
      bitrate: DEFAULT_BITRATE
    };
    let support = await AudioEncoder.isConfigSupported(config);
    if (!support.supported && this.sampleRate !== 48000) {
      config.sampleRate = 48000;
      support = await AudioEncoder.isConfigSupported(config);
    }
    if (!support.supported) {
      throw new Error("Opus encoding not supported");
    }
    this.sampleRate = config.sampleRate;
    this.encoder.configure(config);
    this.running = true;
    while (this.running) {
      const { value, done } = await this.reader.read();
      if (done || !value) {
        break;
      }
      this.encoder.encode(value);
      value.close();
    }
  }

  async stop() {
    this.running = false;
    if (this.reader) {
      try {
        await this.reader.cancel();
      } catch (err) {
        // ignore
      }
      this.reader = null;
    }
    if (this.encoder) {
      try {
        await this.encoder.flush();
      } catch (err) {
        // ignore
      }
      this.encoder.close();
      this.encoder = null;
    }
    if (this.stream) {
      this.stream.getTracks().forEach((track) => track.stop());
      this.stream = null;
    }
  }
}

export class VoicePingAudioClient {
  constructor({ wsUrl, token, userId, deviceId, onStatus, onError }) {
    this.wsUrl = wsUrl;
    this.token = token;
    this.userId = userId;
    this.deviceId = deviceId;
    this.onStatus = onStatus;
    this.onError = onError;
    this.ws = null;
    this.listeningChannelIds = [];
    this.listeningTargets = new Set();
    this.channelMap = new Map();
    this.teamChannelMap = new Map();
    this.loopbackChannelIds = new Set();
    this.output = new AudioOutput();
    this.transmitter = null;
    this.activeTx = null;
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    const protocols = [this.token, this.deviceId];
    this.ws = new WebSocket(this.wsUrl, protocols);
    this.ws.binaryType = "arraybuffer";
    this.ws.onopen = () => {
      if (this.onStatus) {
        this.onStatus({ connected: true });
      }
    };
    this.ws.onclose = () => {
      if (this.onStatus) {
        this.onStatus({ connected: false });
      }
    };
    this.ws.onerror = () => {
      if (this.onError) {
        this.onError("WebSocket error");
      }
    };
    this.ws.onmessage = (event) => {
      this.handleMessage(event.data);
    };
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  updateRouting({ channels }) {
    this.channelMap = new Map(channels.map((channel) => [channel.id, channel]));
    const teamMap = new Map();
    channels.forEach((channel) => {
      if (channel.teamId) {
        if (!teamMap.has(channel.teamId)) {
          teamMap.set(channel.teamId, []);
        }
        teamMap.get(channel.teamId).push(channel.id);
      }
    });
    this.teamChannelMap = teamMap;
  }

  updateFilters({ listeningChannelIds, listeningTargets }) {
    this.listeningChannelIds = listeningChannelIds || [];
    this.listeningTargets = listeningTargets || new Set();
  }

  updateLoopback(channelIds) {
    this.loopbackChannelIds = new Set(channelIds || []);
  }

  resumeOutput() {
    this.output.ensureRunning();
  }

  async startTransmit({ targetIds, micDeviceId }) {
    if (!targetIds || targetIds.length === 0) {
      return;
    }
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      if (this.onError) {
        this.onError("Audio connection is not open");
      }
      return;
    }
    await this.stopTransmit();
    this.activeTx = {
      targetIds,
      packets: [],
      pendingStart: new Set(targetIds)
    };
    targetIds.forEach((channelId) => {
      this.sendMessage({
        channelType: ChannelType.GROUP,
        messageType: MessageType.START,
        fromId: this.userId,
        toId: channelId,
        payload: "dispatch"
      });
    });
    this.transmitter = new AudioTransmitter({
      onPacket: (packet) => {
        if (!this.activeTx) {
          return;
        }
        this.activeTx.packets.push(packet);
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          this.activeTx.targetIds.forEach((channelId) => {
            this.sendMessage({
              channelType: ChannelType.GROUP,
              messageType: MessageType.AUDIO,
              fromId: this.userId,
              toId: channelId,
              payload: packet
            });
          });
        }
      },
      onError: (err) => {
        if (this.onError) {
          this.onError(err.message || String(err));
        }
      }
    });
    try {
      await this.transmitter.start({ deviceId: micDeviceId });
    } catch (err) {
      if (this.onError) {
        this.onError(err.message || String(err));
      }
      await this.stopTransmit();
    }
  }

  async stopTransmit() {
    if (!this.activeTx) {
      return;
    }
    const { targetIds, packets } = this.activeTx;
    this.activeTx = null;
    targetIds.forEach((channelId) => {
      this.sendMessage({
        channelType: ChannelType.GROUP,
        messageType: MessageType.STOP,
        fromId: this.userId,
        toId: channelId,
        payload: ""
      });
    });
    if (this.transmitter) {
      await this.transmitter.stop();
      this.transmitter = null;
    }
    this.scheduleLoopback(targetIds, packets);
  }

  scheduleLoopback(channelIds, packets) {
    if (!packets || packets.length === 0) {
      return;
    }
    const shouldLoopback = channelIds.some((channelId) => this.loopbackChannelIds.has(channelId));
    if (!shouldLoopback) {
      return;
    }
    const validPackets = packets.filter((packet) => packet && packet.byteLength > 0);
    if (validPackets.length === 0) {
      return;
    }
    this.output.ensureRunning();
    const delay = 2;
    validPackets.forEach((packet, index) => {
      this.output.playOpusPacket(packet, index === 0 ? delay : 0);
    });
  }

  handleMessage(data) {
    const msg = decodeMessage(data);
    if (!msg) {
      return;
    }
    if (msg.messageType === MessageType.START_FAILED) {
      if (this.onError) {
        this.onError("Channel busy");
      }
      return;
    }
    if (msg.messageType === MessageType.UNAUTHORIZED_GROUP) {
      if (this.onError) {
        this.onError("Unauthorized for channel");
      }
      return;
    }
    if (msg.messageType === MessageType.AUDIO) {
      if (msg.channelType !== ChannelType.GROUP) {
        return;
      }
      const channelId = msg.toId;
      if (!this.shouldPlayChannel(channelId)) {
        return;
      }
      if (!msg.payload) {
        return;
      }
      const payload = msg.payload instanceof Uint8Array ? msg.payload : new Uint8Array(msg.payload);
      if (payload.byteLength === 0) {
        return;
      }
      this.output.ensureRunning();
      this.output.playOpusPacket(payload);
    }
  }

  shouldPlayChannel(channelId) {
    if (!this.listeningChannelIds.includes(channelId)) {
      return false;
    }
    if (!this.listeningTargets || this.listeningTargets.size === 0) {
      return true;
    }
    if (this.listeningTargets.has(`channel:${channelId}`)) {
      return true;
    }
    const channel = this.channelMap.get(channelId);
    if (channel && channel.teamId && this.listeningTargets.has(`team:${channel.teamId}`)) {
      return true;
    }
    return false;
  }

  sendMessage(message) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }
    const packed = encodeMessage(message);
    this.ws.send(packed);
  }
}

export const getRouterWsUrl = () => {
  const envUrl = import.meta.env.VITE_ROUTER_WS;
  if (envUrl) {
    return envUrl;
  }
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.hostname}:3000`;
};

export const getDeviceId = () => {
  const key = "vp.deviceId";
  const existing = localStorage.getItem(key);
  if (existing) {
    return existing;
  }
  const next = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : `vp-${Date.now()}`;
  localStorage.setItem(key, next);
  return next;
};
