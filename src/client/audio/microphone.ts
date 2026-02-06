/**
 * Microphone access and audio stream management
 * Handles getUserMedia with PTT-optimized constraints
 */

export class MicrophoneManager {
  private stream: MediaStream | null = null;
  private track: MediaStreamTrack | null = null;

  /**
   * Check microphone permission status
   */
  async checkPermission(): Promise<'granted' | 'denied' | 'prompt'> {
    try {
      // Use Permissions API if available
      if ('permissions' in navigator && 'query' in navigator.permissions) {
        const result = await navigator.permissions.query({ name: 'microphone' as PermissionName });
        return result.state as 'granted' | 'denied' | 'prompt';
      }
    } catch (error) {
      // Safari and some browsers don't support Permissions API for microphone
      console.warn('Permissions API not available, defaulting to prompt');
    }

    // Fall back to 'prompt' if Permissions API not supported
    return 'prompt';
  }

  /**
   * Get audio track with PTT-optimized constraints
   */
  async getAudioTrack(): Promise<MediaStreamTrack> {
    try {
      // Request microphone access with PTT-optimized constraints
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000,
          channelCount: 1,
        },
        video: false,
      });

      // Extract audio track
      const audioTracks = this.stream.getAudioTracks();
      if (audioTracks.length === 0) {
        throw new Error('No audio track found in stream');
      }

      this.track = audioTracks[0];

      console.log('Microphone access granted, audio track obtained');
      return this.track;
    } catch (error) {
      if (error instanceof Error) {
        // Handle specific getUserMedia errors
        if (error.name === 'NotAllowedError') {
          throw new Error(
            'Microphone permission denied. Please allow microphone access in browser settings.'
          );
        } else if (error.name === 'NotFoundError') {
          throw new Error('No microphone found. Please connect a microphone.');
        } else if (error.name === 'NotReadableError') {
          throw new Error('Microphone is in use by another application.');
        } else {
          throw new Error(`Failed to access microphone: ${error.message}`);
        }
      }
      throw error;
    }
  }

  /**
   * Mute the audio track (stops sending audio without releasing microphone)
   */
  muteTrack(): void {
    if (this.track) {
      this.track.enabled = false;
      console.log('Microphone muted');
    }
  }

  /**
   * Unmute the audio track
   */
  unmuteTrack(): void {
    if (this.track) {
      this.track.enabled = true;
      console.log('Microphone unmuted');
    }
  }

  /**
   * Check if track is muted
   */
  isMuted(): boolean {
    return this.track ? !this.track.enabled : true;
  }

  /**
   * Stop the audio track (releases microphone hardware)
   */
  stopTrack(): void {
    if (this.track) {
      this.track.stop();
      console.log('Audio track stopped');
    }

    if (this.stream) {
      // Stop all tracks in the stream
      this.stream.getTracks().forEach((track) => track.stop());
      console.log('Media stream stopped');
    }

    this.track = null;
    this.stream = null;
  }

  /**
   * Check if track is active
   */
  isActive(): boolean {
    return this.track ? this.track.readyState === 'live' : false;
  }

  /**
   * Release all resources (stops microphone)
   */
  release(): void {
    this.stopTrack();
    console.log('Microphone manager resources released');
  }

  /**
   * Get the current audio track (if exists)
   */
  getTrack(): MediaStreamTrack | null {
    return this.track;
  }

  /**
   * Get the media stream (if exists)
   */
  getStream(): MediaStream | null {
    return this.stream;
  }
}
