/**
 * Audio feedback system for PTT events
 * Plays configurable audio tones for transmission start, stop, and busy states
 */

export class AudioFeedback {
  private tones = new Map<string, HTMLAudioElement>();
  private basePath: string;
  private volume: number = 0.7;
  private muted: boolean = false;
  private queuedTones: string[] = [];

  constructor(basePath: string = '/audio') {
    this.basePath = basePath;
  }

  /**
   * Preload all default tones
   */
  async preload(): Promise<void> {
    const defaultTones = ['transmit-start', 'transmit-stop', 'busy-tone'];

    const loadPromises = defaultTones.map((toneName) =>
      this.loadTone(toneName, `${this.basePath}/${toneName}.mp3`)
    );

    await Promise.allSettled(loadPromises);
    console.log('Audio feedback tones preloaded');
  }

  /**
   * Load a single tone file
   */
  private async loadTone(name: string, url: string): Promise<void> {
    try {
      const audio = new Audio(url);
      audio.volume = this.volume;

      // Preload the audio file
      await new Promise<void>((resolve, reject) => {
        audio.addEventListener('canplaythrough', () => resolve(), { once: true });
        audio.addEventListener('error', (e) => {
          console.warn(`Failed to load audio tone: ${name} from ${url}`, e);
          reject(e);
        });

        audio.load();
      });

      this.tones.set(name, audio);
      console.log(`Audio tone loaded: ${name}`);
    } catch (error) {
      // Audio feedback is nice-to-have, don't crash on failure
      console.warn(`Could not preload tone ${name}:`, error);
    }
  }

  /**
   * Register a custom tone at runtime
   */
  async registerTone(name: string, url: string): Promise<void> {
    await this.loadTone(name, url);
  }

  /**
   * Play an audio tone by name
   */
  play(toneName: string): void {
    if (this.muted) {
      console.log(`Tone ${toneName} muted`);
      return;
    }

    const audio = this.tones.get(toneName);
    if (!audio) {
      console.warn(`Tone not found: ${toneName}`);
      return;
    }

    // Reset to start and play
    audio.currentTime = 0;
    audio
      .play()
      .catch((error) => {
        // Handle autoplay policy restrictions
        if (error.name === 'NotAllowedError') {
          console.warn(
            `Autoplay blocked for tone ${toneName}. Queueing for next user interaction.`
          );
          this.queuedTones.push(toneName);
        } else {
          console.error(`Failed to play tone ${toneName}:`, error);
        }
      });
  }

  /**
   * Attempt to play queued tones (call after user interaction)
   */
  playQueued(): void {
    if (this.queuedTones.length === 0) {
      return;
    }

    console.log(`Playing ${this.queuedTones.length} queued tones`);
    const queued = [...this.queuedTones];
    this.queuedTones = [];

    queued.forEach((toneName) => {
      const audio = this.tones.get(toneName);
      if (audio) {
        audio.currentTime = 0;
        audio.play().catch((error) => {
          console.error(`Failed to play queued tone ${toneName}:`, error);
        });
      }
    });
  }

  /**
   * Set volume for all tones (0-1)
   */
  setVolume(volume: number): void {
    this.volume = Math.max(0, Math.min(1, volume));
    this.tones.forEach((audio) => {
      audio.volume = this.volume;
    });
    console.log(`Audio feedback volume set to ${this.volume}`);
  }

  /**
   * Mute all tones
   */
  mute(): void {
    this.muted = true;
    console.log('Audio feedback muted');
  }

  /**
   * Unmute all tones
   */
  unmute(): void {
    this.muted = false;
    console.log('Audio feedback unmuted');
  }

  /**
   * Check if muted
   */
  isMuted(): boolean {
    return this.muted;
  }

  /**
   * Load event-specific tone with fallback to default
   */
  async loadEventTone(eventId: string, toneName: string): Promise<void> {
    const eventPath = `${this.basePath}/events/${eventId}/${toneName}.mp3`;

    try {
      await this.loadTone(toneName, eventPath);
      console.log(`Event-specific tone loaded: ${toneName} for event ${eventId}`);
    } catch (error) {
      console.warn(
        `Event-specific tone not found, using default: ${toneName}`
      );
      // Fall back to default tone (already loaded in preload)
    }
  }
}
