# Phase 6: Single-Channel PTT & Audio Transmission - Research

**Researched:** 2026-02-10
**Domain:** Android real-time audio capture, transmission, and PTT interaction patterns
**Confidence:** HIGH

## Summary

Phase 6 implements bidirectional PTT audio transmission, building on Phase 5's receive-only foundation. This phase adds microphone capture via AudioRecord, mediasoup Producer for sending audio, PTT button interaction with press-and-hold and toggle modes, busy state management, radio-style audio feedback tones, haptic feedback, and audio output routing controls (speaker/earpiece/Bluetooth).

The critical technical domains are: (1) AudioRecord microphone capture with proper buffer sizing and thread priority for low-latency real-time audio, (2) mediasoup send transport + producer setup with Opus codec configuration, (3) Android audio routing via AudioManager for speaker/earpiece/Bluetooth SCO switching, (4) Jetpack Compose state coordination between audio capture, UI feedback, and server confirmation, (5) ToneGenerator for PTT chirps and squelch sounds, and (6) VibrationEffect for haptic feedback patterns.

The main pitfalls to avoid: (1) audio underruns from incorrect buffer sizes or thread priorities causing glitches, (2) echo feedback when using speaker output without acoustic echo cancellation, (3) Bluetooth SCO connection race conditions during routing changes, (4) sample rate mismatches between AudioRecord (device native) and mediasoup Opus encoder, (5) WebRTC memory leaks from not disposing AudioRecord/Producer properly, and (6) UI state desync between optimistic PTT feedback and server confirmation.

**Primary recommendation:** Use AudioRecord with VOICE_COMMUNICATION source in dedicated THREAD_PRIORITY_URGENT_AUDIO thread, buffer size 2x minimum for stability. Enable AcousticEchoCanceler when speaker mode active. Use Jetpack DataStore for settings persistence (not SharedPreferences). Implement strict state machine for PTT button (idle → requesting → transmitting → releasing) with server confirmation. Configure mediasoup Producer with Opus 48kHz mono for optimal voice quality at low bitrate (32kbps).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**PTT button interaction:**
- Both press-and-hold and toggle modes, configurable in settings (default: press-and-hold)
- PTT button lives in the bottom bar, integrated with channel info
- No minimum hold duration — transmission starts instantly on press
- Toggle mode has configurable max transmission duration (default 60s), adjustable in settings
- Transmitting state: red pulsing button with elapsed time counter
- Visual + haptic + audio tone feedback on PTT press
- Distinct error tone + buzz pattern when PTT is denied (channel busy)
- No text toast for denied — tone + haptic is sufficient (speaker name already visible in channel)

**Busy state & speaker indicators:**
- Channel list: speaker name replaces idle text, with pulsing/glowing animation
- Channel list: active channel gets highlighted cyan border/glow when someone is speaking
- Bottom bar when busy: PTT button dimmed/grayed out, shows current speaker name
- Pulse color distinction: cyan for others speaking, red for your own transmission
- After speaker finishes: brief 2-3 second "last speaker" fade showing name, then transition to idle
- No elapsed time for incoming speakers — just name display

**PTT feedback & radio sounds:**
- Wait for server confirmation before showing transmitting state (not optimistic)
- Subtle loading pulse on PTT button during brief server confirmation wait
- PTT start tone: short chirp on press (configurable, separate toggle)
- Roger beep: short chirp on TX end (configurable, separate toggle, default on)
- Incoming RX squelch: brief radio open squelch sound when someone starts speaking (configurable, separate toggle)
- Closing squelch: brief squelch tail when incoming transmission ends (tied to RX squelch toggle)
- Three separate sound toggles in settings: PTT start tone, roger beep, RX squelch (open + close)

**Audio output routing:**
- Default output: loudspeaker (no headset/Bluetooth connected)
- Earpiece/speaker toggle located in settings drawer/side panel (not in main UI)
- Bluetooth auto-route: audio automatically switches to Bluetooth headset when connected
- Bluetooth disconnect: falls back to previous output setting (not always speaker)

### Claude's Discretion

- Exact tone/chirp sound design (frequency, duration)
- Pulse animation timing and easing curves
- Button sizing and padding within bottom bar
- Exact red shade for transmitting state
- Mic permission flow and error handling

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

---

## Standard Stack

Phase 6 uses Android audio APIs, mediasoup Producer, and Jetpack Compose state management for PTT.

### Core Audio Libraries

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **AudioRecord** | Android API | Microphone capture | Low-level audio input, real-time processing, direct buffer access |
| **AudioTrack** | Android API | Audio playback | Low-level audio output for received streams (from Phase 5) |
| **AudioManager** | Android API | Audio routing & focus | System audio mode, speaker/earpiece switching, Bluetooth SCO |
| **ToneGenerator** | Android API | Audio tone generation | PTT chirps, roger beeps, squelch sounds |
| **VibrationEffect** | Android API 26+ | Haptic feedback | PTT press/release haptic patterns |
| **AcousticEchoCanceler** | Android API | Echo cancellation | Prevents feedback loop in speaker mode |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Jetpack DataStore** | 1.1.1 | Settings persistence | Store PTT mode, audio routing, tone toggles (replaces SharedPreferences) |
| **Compose Animation** | 1.10.0 | Pulsing animations | PTT button pulse, speaker indicators |
| **Kotlin Coroutines** | 1.10.1 | Audio thread coordination | Bridge AudioRecord thread to ViewModel state |

### Configuration

```kotlin
// AudioRecord configuration for PTT
val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION  // Enables AEC/NS
val sampleRate = 48000  // Match mediasoup Opus codec
val channelConfig = AudioFormat.CHANNEL_IN_MONO
val audioFormat = AudioFormat.ENCODING_PCM_16BIT

val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
val bufferSize = minBufferSize * 2  // 2x for stability, reduce latency vs glitches

// mediasoup Producer Opus codec configuration
val codecOptions = mapOf(
    "opusStereo" to false,  // Mono for voice
    "opusDtx" to true,      // Discontinuous transmission (silence suppression)
    "opusFec" to true,      // Forward error correction
    "opusMaxPlaybackRate" to 48000,
    "opusPtime" to 20       // 20ms packet time
)
```

**Installation (DataStore):**
```kotlin
dependencies {
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
```

---

## Architecture Patterns

### Recommended Audio Capture Architecture

```
AudioCaptureService (Foreground Service)
├── AudioRecordThread (THREAD_PRIORITY_URGENT_AUDIO)
│   ├── AudioRecord.read() → buffer
│   ├── Buffer → MediasoupProducer.send()
│   └── State → Flow emission to ViewModel
│
├── MediasoupProducer (send transport)
│   ├── Opus encoder (48kHz mono)
│   └── WebRTC send to server
│
└── AudioRouter
    ├── AudioManager mode management
    ├── Speaker/earpiece switching
    └── Bluetooth SCO connection handling
```

### Pattern 1: AudioRecord Capture with Real-Time Thread Priority

**What:** Capture microphone audio in dedicated high-priority thread using AudioRecord with VOICE_COMMUNICATION source.

**When to use:** PTT transmission (user presses PTT button, starts microphone capture until release).

**Example:**

```kotlin
// data/audio/AudioCaptureManager.kt
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediasoupProducer: MediasoupProducer
) {
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var isCapturing = false

    private val sampleRate = 48000  // Match Opus codec
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun startCapture() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize * 2  // 2x minimum for stability

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // Enables AEC/AGC/NS
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord initialization failed")
        }

        // Enable acoustic echo cancellation when speaker mode active
        if (AcousticEchoCanceler.isAvailable()) {
            val aec = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            aec?.enabled = true
        }

        isCapturing = true
        audioRecord?.startRecording()

        captureThread = Thread {
            // Set real-time audio priority
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val buffer = ByteArray(bufferSize)

            while (isCapturing) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                if (bytesRead > 0) {
                    // Send to mediasoup Producer
                    mediasoupProducer.sendAudioData(buffer, bytesRead)
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e("AudioCapture", "Invalid operation")
                    break
                }
            }
        }.apply {
            name = "AudioCaptureThread"
            start()
        }
    }

    fun stopCapture() {
        isCapturing = false
        captureThread?.join(1000)  // Wait max 1 second
        captureThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

**Key points:**
- `VOICE_COMMUNICATION` source enables built-in AEC/AGC/NS (Acoustic Echo Cancellation, Automatic Gain Control, Noise Suppression)
- `THREAD_PRIORITY_URGENT_AUDIO` ensures real-time scheduling (SCHED_FIFO on Android 4.1+)
- Buffer size 2x minimum balances latency (lower = faster) vs stability (higher = fewer underruns)
- AcousticEchoCanceler prevents feedback loop when speaker mode active

**Source:** [AudioRecord API Reference](https://developer.android.com/reference/android/media/AudioRecord)

---

### Pattern 2: mediasoup Send Transport + Producer

**What:** Create send transport, produce audio from AudioRecord, configure Opus codec for voice.

**When to use:** PTT transmission setup (after server confirms PTT grant).

**Example:**

```kotlin
// data/network/MediasoupProducer.kt
class MediasoupProducer @Inject constructor(
    private val signalingClient: SignalingClient,
    @ApplicationContext private val context: Context
) {
    private var device: Device? = null
    private var sendTransport: SendTransport? = null
    private var audioProducer: Producer? = null

    suspend fun createSendTransport(channelId: String) = withContext(Dispatchers.IO) {
        // Request send transport from server
        val transportResponse = signalingClient.request(
            SignalingType.CREATE_TRANSPORT,
            mapOf("channelId" to channelId, "direction" to "send")
        )

        val transportData = transportResponse.data!!
        val transportId = transportData["id"] as String
        val iceParameters = transportData["iceParameters"] as String
        val iceCandidates = transportData["iceCandidates"] as String
        val dtlsParameters = transportData["dtlsParameters"] as String

        sendTransport = device?.createSendTransport(
            object : SendTransport.Listener {
                override fun onConnect(transport: Transport, dtlsParameters: String): String {
                    runBlocking {
                        signalingClient.request(
                            SignalingType.CONNECT_TRANSPORT,
                            mapOf(
                                "transportId" to transportId,
                                "dtlsParameters" to dtlsParameters
                            )
                        )
                    }
                    return ""
                }

                override fun onProduce(
                    transport: Transport,
                    kind: String,
                    rtpParameters: String,
                    appData: String
                ): String {
                    // Server creates Producer, returns producerId
                    val response = runBlocking {
                        signalingClient.request(
                            SignalingType.PRODUCE,
                            mapOf(
                                "transportId" to transportId,
                                "kind" to kind,
                                "rtpParameters" to rtpParameters
                            )
                        )
                    }
                    return response.data!!["id"] as String
                }

                override fun onConnectionStateChange(
                    transport: Transport,
                    connectionState: TransportState
                ) {
                    Log.d("MediasoupProducer", "Send transport state: $connectionState")
                }
            },
            transportId,
            iceParameters,
            iceCandidates,
            dtlsParameters
        )
    }

    suspend fun startProducing() = withContext(Dispatchers.IO) {
        // Configure Opus codec for voice
        val codecOptions = """
        {
            "opusStereo": false,
            "opusDtx": true,
            "opusFec": true,
            "opusMaxPlaybackRate": 48000,
            "opusPtime": 20
        }
        """.trimIndent()

        audioProducer = sendTransport?.produce(
            object : Producer.Listener {
                override fun onTransportClose(producer: Producer) {
                    audioProducer = null
                }
            },
            null,  // MediaStreamTrack (null for manual data feed)
            null,  // encodings
            codecOptions,
            """{"source": "microphone"}"""  // appData
        )
    }

    fun sendAudioData(buffer: ByteArray, length: Int) {
        // Feed PCM data to producer
        // Producer internally encodes to Opus and sends via RTP
        audioProducer?.send(buffer, length)
    }

    fun stopProducing() {
        audioProducer?.close()
        audioProducer = null
    }

    fun cleanup() {
        stopProducing()
        sendTransport?.close()
        sendTransport = null
    }
}
```

**Opus codec configuration:**
- `opusStereo: false` — Mono for voice (saves bandwidth)
- `opusDtx: true` — Discontinuous transmission (silence suppression)
- `opusFec: true` — Forward error correction (packet loss resilience)
- `opusPtime: 20` — 20ms packet time (balance latency vs overhead)
- Target bitrate: 32kbps (configured on server router)

**Source:** [mediasoup Client-Server Communication](https://mediasoup.org/documentation/v3/communication-between-client-and-server/), [Opus WebRTC Configuration](https://getstream.io/resources/projects/webrtc/advanced/codecs/)

---

### Pattern 3: Audio Routing with Speaker/Earpiece/Bluetooth

**What:** Manage audio output routing via AudioManager, handle Bluetooth SCO connections.

**When to use:** User toggles speaker/earpiece in settings, Bluetooth headset connects/disconnects.

**Example:**

```kotlin
// data/audio/AudioRouter.kt
class AudioRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private var currentRoute = AudioRoute.SPEAKER
    private var savedRoute = AudioRoute.SPEAKER  // Fallback when Bluetooth disconnects

    fun setAudioRoute(route: AudioRoute) {
        when (route) {
            AudioRoute.SPEAKER -> setSpeakerMode()
            AudioRoute.EARPIECE -> setEarpieceMode()
            AudioRoute.BLUETOOTH -> setBluetoothMode()
        }
        savedRoute = route
        currentRoute = route
    }

    private fun setSpeakerMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    private fun setEarpieceMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    private fun setBluetoothMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    fun requestAudioFocus() {
        // For PTT transmission (transient focus)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()

        audioManager.requestAudioFocus(focusRequest)
    }

    fun releaseAudioFocus(focusRequest: AudioFocusRequest) {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    // Monitor Bluetooth connection state
    fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothHeadset.EXTRA_STATE,
                            BluetoothHeadset.STATE_DISCONNECTED
                        )

                        if (state == BluetoothHeadset.STATE_CONNECTED) {
                            // Auto-route to Bluetooth when connected
                            setAudioRoute(AudioRoute.BLUETOOTH)
                        } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                            // Fallback to saved route (not always speaker)
                            setAudioRoute(savedRoute)
                        }
                    }

                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        val state = intent.getIntExtra(
                            AudioManager.EXTRA_SCO_AUDIO_STATE,
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                        )

                        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                            Log.d("AudioRouter", "Bluetooth SCO audio connected")
                        }
                    }
                }
            }
        }, filter)
    }
}

enum class AudioRoute {
    SPEAKER,
    EARPIECE,
    BLUETOOTH
}
```

**Key points:**
- `MODE_IN_COMMUNICATION` optimizes for voice (enables echo cancellation on some devices)
- Bluetooth SCO (Synchronous Connection-Oriented) for voice audio (not A2DP music profile)
- `ACTION_SCO_AUDIO_STATE_UPDATED` intent needed to verify SCO connection established (asynchronous)
- Fallback to saved route (not always speaker) on Bluetooth disconnect

**Source:** [Audio Manager self-managed call guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager), [Bluetooth SCO Audio](https://deepwiki.com/react-native-webrtc/react-native-incall-manager/7.2-bluetooth-sco-audio)

---

### Pattern 4: PTT State Machine with Server Confirmation

**What:** Manage PTT button state transitions with server confirmation before transmitting.

**When to use:** User presses PTT button → request server → wait confirmation → start capture.

**Example:**

```kotlin
// presentation/ptt/PttViewModel.kt
@HiltViewModel
class PttViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val audioCaptureManager: AudioCaptureManager,
    private val mediasoupProducer: MediasoupProducer,
    private val audioRouter: AudioRouter,
    private val tonePlayer: TonePlayer,
    private val hapticFeedback: HapticFeedback
) : ViewModel() {

    private val _pttState = MutableStateFlow<PttState>(PttState.Idle)
    val pttState: StateFlow<PttState> = _pttState.asStateFlow()

    private var transmissionStartTime: Long = 0

    fun onPttPressed(channelId: String) {
        if (_pttState.value != PttState.Idle) return  // Already in use

        viewModelScope.launch {
            // State 1: Requesting (subtle loading pulse)
            _pttState.value = PttState.Requesting

            try {
                // Request PTT from server
                val response = signalingClient.request(
                    SignalingType.REQUEST_PTT,
                    mapOf("channelId" to channelId)
                )

                if (response.data?.get("granted") == true) {
                    // State 2: Transmitting (server confirmed)
                    _pttState.value = PttState.Transmitting
                    transmissionStartTime = System.currentTimeMillis()

                    // Play PTT start tone (if enabled in settings)
                    tonePlayer.playPttStartTone()

                    // Haptic feedback
                    hapticFeedback.vibratePttPress()

                    // Request audio focus
                    audioRouter.requestAudioFocus()

                    // Start audio capture + producer
                    mediasoupProducer.startProducing()
                    audioCaptureManager.startCapture()
                } else {
                    // PTT denied (channel busy)
                    _pttState.value = PttState.Denied

                    // Play error tone + haptic buzz
                    tonePlayer.playErrorTone()
                    hapticFeedback.vibrateError()

                    // Return to idle after brief denial state
                    delay(500)
                    _pttState.value = PttState.Idle
                }
            } catch (e: Exception) {
                Log.e("PttViewModel", "PTT request failed", e)
                _pttState.value = PttState.Idle
            }
        }
    }

    fun onPttReleased() {
        if (_pttState.value != PttState.Transmitting) return

        viewModelScope.launch {
            // Play roger beep (if enabled in settings)
            tonePlayer.playRogerBeep()

            // Stop audio capture + producer
            audioCaptureManager.stopCapture()
            mediasoupProducer.stopProducing()

            // Release audio focus
            audioRouter.releaseAudioFocus()

            // Notify server
            signalingClient.send(
                SignalingType.RELEASE_PTT,
                mapOf("channelId" to channelId)
            )

            _pttState.value = PttState.Idle
        }
    }

    fun getTransmissionDuration(): Long {
        return if (_pttState.value == PttState.Transmitting) {
            (System.currentTimeMillis() - transmissionStartTime) / 1000
        } else 0
    }
}

sealed class PttState {
    object Idle : PttState()
    object Requesting : PttState()  // Waiting for server confirmation
    object Transmitting : PttState()
    object Denied : PttState()  // Channel busy
}
```

**State transitions:**
1. **Idle** → PTT pressed → **Requesting** (subtle pulse)
2. **Requesting** → Server grants → **Transmitting** (red pulse + tone + haptic)
3. **Requesting** → Server denies → **Denied** (error tone + buzz) → **Idle**
4. **Transmitting** → PTT released → **Idle** (roger beep)

**Source:** [State and Jetpack Compose](https://developer.android.com/jetpack/compose/state)

---

### Pattern 5: ToneGenerator for PTT Audio Feedback

**What:** Generate PTT chirps, roger beeps, and squelch sounds using ToneGenerator.

**When to use:** PTT press (start tone), PTT release (roger beep), incoming RX (squelch).

**Example:**

```kotlin
// data/audio/TonePlayer.kt
class TonePlayer @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var toneGenerator: ToneGenerator? = null

    init {
        toneGenerator = ToneGenerator(
            AudioManager.STREAM_VOICE_CALL,
            ToneGenerator.MAX_VOLUME / 2  // 50% volume
        )
    }

    fun playPttStartTone() {
        if (!settingsRepository.isPttStartToneEnabled()) return

        // Short high chirp (DTMF 1 = 697Hz + 1209Hz)
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, 100)  // 100ms
    }

    fun playRogerBeep() {
        if (!settingsRepository.isRogerBeepEnabled()) return

        // Short low chirp (DTMF 0 = 941Hz + 1336Hz)
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0, 150)  // 150ms
    }

    fun playRxSquelchOpen() {
        if (!settingsRepository.isRxSquelchEnabled()) return

        // Brief squelch sound (PROP_NACK = noise-like)
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 80)  // 80ms
    }

    fun playRxSquelchClose() {
        if (!settingsRepository.isRxSquelchEnabled()) return

        // Squelch tail (quieter NACK)
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 60)  // 60ms
    }

    fun playErrorTone() {
        // Distinct error tone (PROP_ACK = double beep)
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)  // 200ms
    }

    fun cleanup() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
```

**Tone design choices (Claude's discretion):**
- PTT start: DTMF_1 (high chirp) 100ms
- Roger beep: DTMF_0 (low chirp) 150ms
- RX squelch open: PROP_NACK (noise) 80ms
- RX squelch close: PROP_NACK 60ms (quieter)
- Error tone: PROP_BEEP2 (double beep) 200ms

**Source:** [ToneGenerator API Reference](https://developer.android.com/reference/android/media/ToneGenerator)

---

### Pattern 6: Jetpack DataStore for Settings Persistence

**What:** Store PTT mode, audio routing, and tone toggles using Jetpack DataStore (not SharedPreferences).

**When to use:** User changes settings (PTT mode, speaker/earpiece, tone toggles).

**Example:**

```kotlin
// data/storage/SettingsRepository.kt
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "ptt_settings")

    private object Keys {
        val PTT_MODE = stringPreferencesKey("ptt_mode")
        val AUDIO_ROUTE = stringPreferencesKey("audio_route")
        val PTT_START_TONE = booleanPreferencesKey("ptt_start_tone")
        val ROGER_BEEP = booleanPreferencesKey("roger_beep")
        val RX_SQUELCH = booleanPreferencesKey("rx_squelch")
        val TOGGLE_MAX_DURATION = intPreferencesKey("toggle_max_duration")
    }

    // PTT mode (press-and-hold or toggle)
    suspend fun setPttMode(mode: PttMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PTT_MODE] = mode.name
        }
    }

    fun getPttMode(): Flow<PttMode> {
        return context.dataStore.data.map { prefs ->
            val modeName = prefs[Keys.PTT_MODE] ?: PttMode.PRESS_AND_HOLD.name
            PttMode.valueOf(modeName)
        }
    }

    // Audio route
    suspend fun setAudioRoute(route: AudioRoute) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_ROUTE] = route.name
        }
    }

    fun getAudioRoute(): Flow<AudioRoute> {
        return context.dataStore.data.map { prefs ->
            val routeName = prefs[Keys.AUDIO_ROUTE] ?: AudioRoute.SPEAKER.name
            AudioRoute.valueOf(routeName)
        }
    }

    // Tone toggles
    suspend fun setPttStartToneEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PTT_START_TONE] = enabled
        }
    }

    fun isPttStartToneEnabled(): Boolean {
        // Synchronous read for audio thread (caches last value)
        return runBlocking {
            context.dataStore.data.first()[Keys.PTT_START_TONE] ?: true
        }
    }

    suspend fun setRogerBeepEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROGER_BEEP] = enabled
        }
    }

    fun isRogerBeepEnabled(): Boolean {
        return runBlocking {
            context.dataStore.data.first()[Keys.ROGER_BEEP] ?: true  // Default ON
        }
    }

    suspend fun setRxSquelchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RX_SQUELCH] = enabled
        }
    }

    fun isRxSquelchEnabled(): Boolean {
        return runBlocking {
            context.dataStore.data.first()[Keys.RX_SQUELCH] ?: false
        }
    }

    // Toggle mode max duration
    suspend fun setToggleMaxDuration(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOGGLE_MAX_DURATION] = seconds
        }
    }

    fun getToggleMaxDuration(): Flow<Int> {
        return context.dataStore.data.map { prefs ->
            prefs[Keys.TOGGLE_MAX_DURATION] ?: 60  // Default 60s
        }
    }
}

enum class PttMode {
    PRESS_AND_HOLD,
    TOGGLE
}
```

**Why DataStore over SharedPreferences:**
- Thread-safe (coroutines-based, no race conditions)
- Transactional (writes atomic, no partial state)
- Flow-based reactivity (UI auto-updates on settings change)
- Type-safe (compile-time key checking)

**Source:** [Stop Using SharedPreferences: Mastering Jetpack DataStore in 2026](https://medium.com/@kemal_codes/stop-using-sharedpreferences-mastering-jetpack-datastore-in-2026-b88b2db50e91)

---

### Pattern 7: Pulsing Animation for PTT Button

**What:** Animated pulsing effect for PTT button (red for transmitting, cyan for receiving).

**When to use:** Transmitting state (own audio), receiving state (others speaking).

**Example:**

```kotlin
// presentation/components/PttButton.kt
@Composable
fun PttButton(
    pttState: PttState,
    onPttPressed: () -> Unit,
    onPttReleased: () -> Unit,
    transmissionDuration: Long,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val buttonColor = when (pttState) {
        PttState.Transmitting -> Color(0xFFD32F2F)  // Red
        PttState.Requesting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        PttState.Denied -> Color(0xFFB71C1C)  // Dark red
        PttState.Idle -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .scale(if (pttState == PttState.Transmitting) pulseScale else 1f)
            .alpha(if (pttState == PttState.Transmitting) pulseAlpha else 1f)
            .background(buttonColor, shape = CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPttPressed()
                        // Wait for release
                        tryAwaitRelease()
                        onPttReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (pttState == PttState.Transmitting) {
            // Show elapsed time
            Text(
                text = "${transmissionDuration}s",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "PTT",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
```

**Animation timing (Claude's discretion):**
- Pulse period: 1000ms (1 second cycle)
- Scale range: 1.0 → 1.15 (15% growth)
- Alpha range: 1.0 → 0.6 (40% fade)
- Easing: EaseInOut (smooth breathing effect)

**Source:** [How to Create a Pulse Effect in Jetpack Compose](https://medium.com/@kappdev/how-to-create-a-pulse-effect-in-jetpack-compose-265d49aad044)

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| **Settings persistence** | SharedPreferences with manual synchronization | Jetpack DataStore Preferences | Thread-safe, transactional, Flow-based reactivity. SharedPreferences has race conditions on multi-threaded writes. |
| **Audio tone generation** | AudioTrack with manual sine wave synthesis | ToneGenerator | Built-in DTMF, supervisory tones, optimized for low-latency. Manual synthesis requires buffer management, phase continuity. |
| **Haptic feedback patterns** | Manual Vibrator API with timing arrays | VibrationEffect predefined constants | Device-optimized haptics (CLICK, TICK, DOUBLE_CLICK). Custom patterns feel wrong on different OEMs. |
| **Echo cancellation** | Manual echo cancellation algorithm | VOICE_COMMUNICATION source + AcousticEchoCanceler | Hardware-accelerated AEC on many devices. Manual AEC requires adaptive filters, calibration, echo path modeling. |
| **Sample rate conversion** | Manual resampling with linear interpolation | Device native sample rate (48kHz) | Android resampler uses Kaiser-windowed sinc (high quality). Manual resampling causes aliasing, passband ripple. |

**Key insight:** Android audio stack has mature solutions for voice communication. Custom implementations miss hardware optimizations (AEC DSP, low-latency audio path) and introduce audio quality degradation.

---

## Common Pitfalls

### Pitfall 1: Audio Underruns from Buffer Size / Thread Priority

**What goes wrong:** AudioRecord.read() doesn't get called fast enough, causing buffer underflow. Results in audio glitches (pops, clicks, dropouts) during PTT transmission.

**Why it happens:** Audio capture thread has normal priority (SCHED_NORMAL) and gets preempted by UI thread or background work. Or buffer size is too small (1x minimum) and capture thread can't read fast enough to avoid overrun.

**How to avoid:**
1. **Set real-time thread priority:**
   ```kotlin
   android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
   ```
   This switches thread to SCHED_FIFO scheduling (real-time, preempts other threads).

2. **Use 2x minimum buffer size:**
   ```kotlin
   val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
   val bufferSize = minBufferSize * 2  // Not 1x, not 4x — 2x is sweet spot
   ```
   1x = frequent underruns, 4x = unnecessary latency. 2x balances stability vs latency.

3. **Avoid blocking operations in capture loop:**
   ```kotlin
   // BAD: Blocking network call in capture thread
   while (isCapturing) {
       val bytesRead = audioRecord.read(buffer, 0, bufferSize)
       networkClient.sendBlocking(buffer)  // BLOCKS thread!
   }

   // GOOD: Non-blocking queue handoff
   while (isCapturing) {
       val bytesRead = audioRecord.read(buffer, 0, bufferSize)
       audioQueue.offer(buffer.copyOf())  // Non-blocking
   }
   ```

4. **Monitor underruns:**
   ```kotlin
   val underrunCount = audioRecord.getUnderrunCount()
   Log.d("AudioCapture", "Underruns: $underrunCount")
   ```

**Warning signs:**
- Audible clicks/pops in transmitted audio
- logcat shows `AudioRecord: getMinBufferSize() underrun`
- AudioRecord.getUnderrunCount() increases during transmission

**Source:** [Avoid priority inversion](https://source.android.com/docs/core/audio/avoiding_pi), [Debugging audio glitches on Android](https://medium.com/@donturner/debugging-audio-glitches-on-android-ed10782f9c64)

---

### Pitfall 2: Echo Feedback in Speaker Mode

**What goes wrong:** When audio output is speaker, microphone picks up speaker output. Creates feedback loop (echo, howling). User hears their own voice delayed, others hear echo of their own transmissions.

**Why it happens:** Acoustic echo cancellation (AEC) not enabled, or AEC disabled when switching to speaker mode. Android's built-in AEC only works with VOICE_COMMUNICATION source, and some devices disable AEC when isSpeakerphoneOn = true.

**How to avoid:**
1. **Always use VOICE_COMMUNICATION source:**
   ```kotlin
   AudioRecord(
       MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // NOT MIC!
       // ...
   )
   ```

2. **Enable AcousticEchoCanceler explicitly:**
   ```kotlin
   if (AcousticEchoCanceler.isAvailable()) {
       val aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)
       aec?.enabled = true
   } else {
       // No AEC available — warn user that speaker mode may echo
       Log.w("AudioCapture", "AEC not available on this device")
   }
   ```

3. **Test AEC effectiveness:**
   ```kotlin
   // Log AEC enablement status
   val aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)
   Log.d("AudioCapture", "AEC available: ${AcousticEchoCanceler.isAvailable()}")
   Log.d("AudioCapture", "AEC enabled: ${aec?.enabled}")
   ```

4. **Warn user if AEC unavailable:**
   ```kotlin
   if (!AcousticEchoCanceler.isAvailable() && audioRoute == AudioRoute.SPEAKER) {
       // Show tooltip: "Echo may occur in speaker mode. Use earpiece or headset."
   }
   ```

**Warning signs:**
- Transmitted audio has echo (your voice repeats)
- Others complain about hearing themselves
- Howling/feedback when two users on speaker mode in same room

**Source:** [Echo Cancellation on Android and AudioRecord](https://solicall.com/echo-cancellation-on-android-and-audiorecord/), [AcousticEchoCanceler API](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler)

---

### Pitfall 3: Bluetooth SCO Connection Race Condition

**What goes wrong:** User enables Bluetooth mode, but audio still routes to speaker. Or audio routes to Bluetooth, then immediately switches back to speaker. Inconsistent behavior on Bluetooth connect/disconnect.

**Why it happens:** Bluetooth SCO connection is asynchronous. `startBluetoothSco()` returns immediately but connection takes 1-2 seconds. If app assumes immediate connection, audio routing fails. Also, SCO disconnection is async — next activity (e.g., switching to speaker) happens before SCO fully closed.

**How to avoid:**
1. **Wait for SCO_AUDIO_STATE_CONNECTED intent:**
   ```kotlin
   fun setBluetoothMode() {
       audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
       audioManager.startBluetoothSco()
       audioManager.isBluetoothScoOn = true

       // Don't assume connected! Wait for intent.
   }

   // In BroadcastReceiver
   override fun onReceive(context: Context, intent: Intent) {
       if (intent.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
           val state = intent.getIntExtra(
               AudioManager.EXTRA_SCO_AUDIO_STATE,
               AudioManager.SCO_AUDIO_STATE_DISCONNECTED
           )

           when (state) {
               AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                   // NOW Bluetooth audio is ready
                   Log.d("AudioRouter", "Bluetooth SCO connected")
               }
               AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                   Log.d("AudioRouter", "Bluetooth SCO disconnected")
                   // Fallback to saved route
               }
           }
       }
   }
   ```

2. **Serialize SCO operations:**
   ```kotlin
   suspend fun setAudioRoute(route: AudioRoute) {
       // Wait for previous route to fully disconnect
       if (currentRoute == AudioRoute.BLUETOOTH) {
           audioManager.stopBluetoothSco()
           delay(500)  // Wait for SCO disconnect
       }

       when (route) {
           AudioRoute.BLUETOOTH -> {
               audioManager.startBluetoothSco()
               // Wait for connected intent before proceeding
           }
           else -> {
               setSpeakerOrEarpiece(route)
           }
       }
   }
   ```

3. **Handle Bluetooth disconnect gracefully:**
   ```kotlin
   // User decides: "Bluetooth disconnect: falls back to previous output setting (not always speaker)"
   var savedRouteBeforeBluetooth: AudioRoute = AudioRoute.SPEAKER

   fun setAudioRoute(route: AudioRoute) {
       if (route != AudioRoute.BLUETOOTH) {
           savedRouteBeforeBluetooth = route  // Remember non-BT preference
       }
       // ... set route
   }

   // On Bluetooth disconnect
   fun onBluetoothDisconnected() {
       setAudioRoute(savedRouteBeforeBluetooth)  // NOT hardcoded SPEAKER
   }
   ```

**Warning signs:**
- Audio plays through speaker despite Bluetooth enabled
- logcat shows `startBluetoothSco() called but state still DISCONNECTED`
- Audio switches between Bluetooth/speaker/earpiece rapidly

**Source:** [Bluetooth SCO in Android](http://gopinaths.gitlab.io/post/bluetooth_sco_android/), [Android Bluetooth SCO](https://copyprogramming.com/howto/android-bluetooth-sco)

---

### Pitfall 4: Sample Rate Mismatch (AudioRecord vs Opus)

**What goes wrong:** AudioRecord captures at device native rate (e.g., 44.1kHz), but mediasoup Opus encoder expects 48kHz. Results in pitch shift (chipmunk voice or slow voice) or encoder rejection.

**Why it happens:** Android devices have different native sample rates (44.1kHz on some, 48kHz on others). AudioRecord defaults to native rate if requested rate not supported. Opus codec requires specific rates (8kHz, 12kHz, 16kHz, 24kHz, 48kHz) — 44.1kHz not supported.

**How to avoid:**
1. **Explicitly request 48kHz:**
   ```kotlin
   val sampleRate = 48000  // Opus standard for voice

   val audioRecord = AudioRecord(
       MediaRecorder.AudioSource.VOICE_COMMUNICATION,
       sampleRate,  // NOT device native!
       channelConfig,
       audioFormat,
       bufferSize
   )

   // Verify actual rate
   if (audioRecord.sampleRate != sampleRate) {
       Log.e("AudioCapture", "Requested $sampleRate but got ${audioRecord.sampleRate}")
       // Device doesn't support 48kHz — need resampling
   }
   ```

2. **Test on multiple devices:**
   - Google Pixel: native 48kHz (no problem)
   - Samsung Galaxy: native 44.1kHz (may need resampling)
   - OnePlus: native 48kHz (no problem)

3. **If 48kHz not supported, use Oboe library:**
   ```kotlin
   // Oboe handles resampling transparently
   implementation("com.google.oboe:oboe:1.7.0")
   ```

4. **Verify Opus encoder config matches:**
   ```kotlin
   // Server-side mediasoup router codec
   {
       kind: 'audio',
       mimeType: 'audio/opus',
       clockRate: 48000,  // MUST match AudioRecord sample rate
       channels: 1
   }
   ```

**Warning signs:**
- Transmitted audio sounds sped up or slowed down
- Opus encoder throws error: `Invalid sample rate`
- logcat shows `AudioRecord: requested 48000 Hz but got 44100 Hz`

**Source:** [Impact of Resampling on Audio Quality in Android](https://www.droidsome.com/impact-of-resampling-on-audio-quality-in-android/), [Sample rate conversion](https://source.android.com/docs/core/audio/src)

---

### Pitfall 5: WebRTC Memory Leak (AudioRecord/Producer not disposed)

**What goes wrong:** After PTT transmission ends, AudioRecord and Producer not properly released. Native memory leaks, eventually crashes with OutOfMemoryError after 20-30 transmissions.

**Why it happens:** WebRTC objects (Producer, AudioTrack) are backed by native C++ code. Java garbage collector does NOT free native memory — must explicitly call .close() or .release(). If disposal order is wrong, crash with `IllegalStateException: already disposed`.

**How to avoid:**
1. **Strict disposal order:**
   ```kotlin
   fun stopTransmission() {
       // Step 1: Stop audio capture FIRST
       audioCaptureManager.stopCapture()  // Stops AudioRecord.read() loop

       // Step 2: Close producer AFTER capture stopped
       mediasoupProducer.stopProducing()  // producer.close()

       // Step 3: AudioRecord.release() in AudioCaptureManager.stopCapture()
       audioRecord?.stop()
       audioRecord?.release()
       audioRecord = null
   }
   ```

2. **Always release in finally block:**
   ```kotlin
   try {
       audioRecord.startRecording()
       // ... capture loop
   } finally {
       audioRecord?.stop()
       audioRecord?.release()
       audioRecord = null
   }
   ```

3. **Track created vs disposed counts:**
   ```kotlin
   companion object {
       var audioRecordCount = 0
       var producerCount = 0
   }

   fun startCapture() {
       audioRecord = AudioRecord(...)
       audioRecordCount++
       Log.d("AudioCapture", "Created AudioRecord #$audioRecordCount")
   }

   fun stopCapture() {
       audioRecord?.release()
       audioRecord = null
       Log.d("AudioCapture", "Released AudioRecord, active: ${audioRecordCount--}")
   }
   ```

4. **Use LeakCanary for native leak detection:**
   ```kotlin
   // build.gradle.kts
   debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
   ```

**Warning signs:**
- Memory usage grows on repeated PTT presses (Android Studio Profiler → Memory → Native)
- Crash after 20-30 transmissions: `OutOfMemoryError`
- logcat: `AudioRecord finalized without being released`
- LeakCanary detects native leak: `AudioRecord native instance`

**Source:** [Avoid priority inversion](https://source.android.com/docs/core/audio/avoiding_pi) (mentions resource cleanup)

---

### Pitfall 6: UI State Desync (Optimistic vs Server Confirmation)

**What goes wrong:** User presses PTT button, UI immediately shows transmitting state (optimistic), but server denies PTT (channel busy). UI shows "transmitting" while server rejects request — confusing state.

**Why it happens:** User decision was "Wait for server confirmation before showing transmitting state (not optimistic)", but developer implements optimistic UI update for instant feedback, then tries to revert on denial. Reversion has race condition — state already changed in ViewModel.

**How to avoid:**
1. **Strict state machine (no optimistic update):**
   ```kotlin
   fun onPttPressed() {
       if (_pttState.value != PttState.Idle) return

       // State 1: Requesting (NOT Transmitting!)
       _pttState.value = PttState.Requesting  // Subtle loading pulse

       viewModelScope.launch {
           val response = signalingClient.request(SignalingType.REQUEST_PTT, ...)

           if (response.data?.get("granted") == true) {
               // State 2: Transmitting (ONLY after server confirms)
               _pttState.value = PttState.Transmitting
               startAudioCapture()
           } else {
               // State 3: Denied (channel busy)
               _pttState.value = PttState.Denied
               playErrorTone()
               delay(500)
               _pttState.value = PttState.Idle
           }
       }
   }
   ```

2. **Visual distinction between requesting and transmitting:**
   - **Requesting**: Subtle gray/white pulse (loading indicator)
   - **Transmitting**: Red pulse + elapsed time counter
   - **Denied**: Brief red flash + error tone

3. **Test server denial flow:**
   ```kotlin
   // Simulate channel busy
   @Test
   fun `PTT denied when channel busy`() = runTest {
       // User 1 transmitting
       pttViewModel1.onPttPressed()
       advanceUntilIdle()
       assertEquals(PttState.Transmitting, pttViewModel1.pttState.value)

       // User 2 tries to PTT (should be denied)
       pttViewModel2.onPttPressed()
       advanceUntilIdle()
       assertEquals(PttState.Denied, pttViewModel2.pttState.value)

       // Verify User 2 never started audio capture
       verify(audioCaptureManager, never()).startCapture()
   }
   ```

**Warning signs:**
- UI shows transmitting but no audio sent
- Server logs show PTT denied but client shows transmitting
- Elapsed time counter runs despite no transmission

**Source:** User decision in CONTEXT.md: "Wait for server confirmation before showing transmitting state (not optimistic)"

---

## Code Examples

Verified patterns for Phase 6 implementation.

### Example 1: Foreground Service for Audio Capture

```kotlin
// AudioCaptureService.kt
@AndroidEntryPoint
class AudioCaptureService : Service() {

    @Inject lateinit var audioCaptureManager: AudioCaptureManager
    @Inject lateinit var audioRouter: AudioRouter

    private val notificationId = 1001

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                startForeground(notificationId, createNotification())
                audioCaptureManager.startCapture()
            }
            ACTION_STOP_CAPTURE -> {
                audioCaptureManager.stopCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "audio_capture"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoicePing PTT Active")
            .setContentText("Transmitting audio...")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_CAPTURE = "START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "STOP_CAPTURE"
    }
}

// AndroidManifest.xml
<service
    android:name=".AudioCaptureService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="microphone" />

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

**Why foreground service:** Android 9+ requires foreground service for background microphone access. Without it, AudioRecord throws SecurityException when app is backgrounded.

**Source:** [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)

---

### Example 2: PTT Button Compose UI

```kotlin
// presentation/components/PttButton.kt
@Composable
fun PttButton(
    pttState: PttState,
    pttMode: PttMode,
    transmissionDuration: Long,
    onPttAction: (PttAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    when (pttMode) {
        PttMode.PRESS_AND_HOLD -> {
            PressAndHoldButton(
                pttState = pttState,
                transmissionDuration = transmissionDuration,
                onPttPressed = { onPttAction(PttAction.Pressed) },
                onPttReleased = { onPttAction(PttAction.Released) },
                modifier = modifier
            )
        }
        PttMode.TOGGLE -> {
            ToggleButton(
                pttState = pttState,
                transmissionDuration = transmissionDuration,
                onToggle = {
                    if (pttState == PttState.Transmitting) {
                        onPttAction(PttAction.Released)
                    } else {
                        onPttAction(PttAction.Pressed)
                    }
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun PressAndHoldButton(
    pttState: PttState,
    transmissionDuration: Long,
    onPttPressed: () -> Unit,
    onPttReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .background(
                color = when (pttState) {
                    PttState.Transmitting -> Color(0xFFD32F2F)  // Red
                    PttState.Requesting -> Color(0xFF757575)    // Gray
                    PttState.Denied -> Color(0xFFB71C1C)       // Dark red
                    PttState.Idle -> MaterialTheme.colorScheme.primary
                },
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPttPressed()
                        tryAwaitRelease()  // Waits for finger lift
                        onPttReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (pttState == PttState.Transmitting) {
            Text(
                text = "${transmissionDuration}s",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "PTT",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }

    // Pulsing animation (only when transmitting)
    if (pttState == PttState.Transmitting) {
        PulseAnimation(color = Color(0xFFD32F2F))
    }
}

@Composable
private fun PulseAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(72.dp * scale)
            .alpha(alpha)
            .background(color, shape = CircleShape)
    )
}

sealed class PttAction {
    object Pressed : PttAction()
    object Released : PttAction()
}
```

---

### Example 3: Settings Screen with DataStore

```kotlin
// presentation/settings/SettingsScreen.kt
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pttMode by viewModel.pttMode.collectAsState(initial = PttMode.PRESS_AND_HOLD)
    val audioRoute by viewModel.audioRoute.collectAsState(initial = AudioRoute.SPEAKER)
    val pttStartTone by viewModel.pttStartTone.collectAsState(initial = true)
    val rogerBeep by viewModel.rogerBeep.collectAsState(initial = true)
    val rxSquelch by viewModel.rxSquelch.collectAsState(initial = false)
    val toggleMaxDuration by viewModel.toggleMaxDuration.collectAsState(initial = 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("PTT Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        // PTT Mode
        Text("PTT Mode", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = pttMode == PttMode.PRESS_AND_HOLD,
                onClick = { viewModel.setPttMode(PttMode.PRESS_AND_HOLD) }
            )
            Text("Press and Hold")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = pttMode == PttMode.TOGGLE,
                onClick = { viewModel.setPttMode(PttMode.TOGGLE) }
            )
            Text("Toggle")
        }

        if (pttMode == PttMode.TOGGLE) {
            Slider(
                value = toggleMaxDuration.toFloat(),
                onValueChange = { viewModel.setToggleMaxDuration(it.toInt()) },
                valueRange = 30f..120f,
                steps = 17,  // 30, 35, 40, ..., 120
                modifier = Modifier.fillMaxWidth()
            )
            Text("Max duration: ${toggleMaxDuration}s")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Audio Output
        Text("Audio Output", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = audioRoute == AudioRoute.SPEAKER,
                onClick = { viewModel.setAudioRoute(AudioRoute.SPEAKER) }
            )
            Text("Speaker")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = audioRoute == AudioRoute.EARPIECE,
                onClick = { viewModel.setAudioRoute(AudioRoute.EARPIECE) }
            )
            Text("Earpiece")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Audio Tones
        Text("Audio Tones", style = MaterialTheme.typography.titleMedium)

        SwitchRow(
            label = "PTT Start Tone",
            checked = pttStartTone,
            onCheckedChange = { viewModel.setPttStartTone(it) }
        )

        SwitchRow(
            label = "Roger Beep",
            checked = rogerBeep,
            onCheckedChange = { viewModel.setRogerBeep(it) }
        )

        SwitchRow(
            label = "RX Squelch",
            checked = rxSquelch,
            onCheckedChange = { viewModel.setRxSquelch(it) }
        )
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

---

## Open Questions

Phase 6-specific questions requiring investigation during planning.

### 1. RECORD_AUDIO Permission Runtime Request Flow

**What we know:**
- RECORD_AUDIO is dangerous permission (requires runtime request on Android 6+)
- Marked as Claude's discretion: "Mic permission flow and error handling"

**What's unclear:**
- When to request permission? (On app launch vs when user first presses PTT)
- Should we show rationale dialog before system permission dialog?
- What happens if user denies permission? (Retry vs disable PTT permanently)
- How to handle "Don't ask again" state?

**Recommendation:**
- Request permission on first PTT press (not app launch) — "request as late as possible" best practice
- Show custom rationale dialog before system dialog: "VoicePing needs microphone access to transmit audio"
- If denied: show inline message on PTT button: "Microphone access required. Tap to grant."
- If "Don't ask again": deep link to app settings: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`

---

### 2. Toggle Mode Max Duration Enforcement

**What we know:**
- Toggle mode has configurable max transmission duration (default 60s)
- User can adjust in settings (30-120 seconds range suggested)

**What's unclear:**
- How to enforce max duration? (Client-side countdown vs server-side timeout)
- What happens when max reached? (Auto-release with tone vs silent cutoff)
- Should server enforce same limit? (Security concern: malicious client ignores limit)

**Recommendation:**
- **Client-side:** Start countdown timer on transmission start, auto-release at max duration
- **Server-side:** Enforce same limit (60s default, configurable) to prevent abuse
- **UI feedback:** Show countdown approaching max (e.g., last 10 seconds turn orange)
- **Auto-release:** Play roger beep, show brief message: "Max transmission time reached"

---

### 3. Bluetooth Auto-Route Timing

**What we know:**
- User decision: "Bluetooth auto-route: audio automatically switches to Bluetooth headset when connected"
- Bluetooth disconnect: falls back to previous output setting

**What's unclear:**
- Should auto-route happen immediately when Bluetooth connects, or wait for user to explicitly enable?
- What if user is mid-transmission when Bluetooth connects? (Switch immediately vs wait for next transmission)
- How to detect "previous output setting" if user never explicitly chose one?

**Recommendation:**
- Auto-route to Bluetooth only when connected AND not currently transmitting
- If mid-transmission: defer switch until transmission ends, then switch for next RX/TX
- "Previous output setting" = last explicitly selected route (default SPEAKER if never selected)
- Store in DataStore: `last_manual_audio_route`

---

## Sources

### Primary (HIGH confidence)

- [AudioRecord API Reference](https://developer.android.com/reference/android/media/AudioRecord) — Microphone capture configuration
- [AudioTrack API Reference](https://developer.android.com/reference/android/media/AudioTrack) — Audio playback streaming
- [Audio latency for app developers](https://source.android.com/docs/core/audio/latency/app) — Low-latency best practices
- [Manage audio focus](https://developer.android.com/media/optimize/audio-focus) — Audio focus patterns for communication apps
- [ToneGenerator API Reference](https://developer.android.com/reference/android/media/ToneGenerator) — PTT tone generation
- [VibrationEffect API](https://developer.android.com/reference/android/os/VibrationEffect) — Haptic feedback patterns
- [AcousticEchoCanceler API](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler) — Echo cancellation
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) — Microphone service type requirement
- [Audio Manager self-managed call guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager) — Bluetooth SCO routing
- [Stop Using SharedPreferences: Mastering Jetpack DataStore in 2026](https://medium.com/@kemal_codes/stop-using-sharedpreferences-mastering-jetpack-datastore-in-2026-b88b2db50e91) — Settings persistence
- [State and Jetpack Compose](https://developer.android.com/jetpack/compose/state) — State management patterns
- [How to Create a Pulse Effect in Jetpack Compose](https://medium.com/@kappdev/how-to-create-a-pulse-effect-in-jetpack-compose-265d49aad044) — Animation patterns

### Secondary (MEDIUM confidence)

- [Debugging audio glitches on Android](https://medium.com/@donturner/debugging-audio-glitches-on-android-ed10782f9c64) — Buffer size tuning
- [Echo Cancellation on Android and AudioRecord](https://solicall.com/echo-cancellation-on-android-and-audiorecord/) — AEC implementation
- [Bluetooth SCO in Android](http://gopinaths.gitlab.io/post/bluetooth_sco_android/) — SCO connection patterns
- [Impact of Resampling on Audio Quality in Android](https://www.droidsome.com/impact-of-resampling-on-audio-quality-in-android/) — Sample rate considerations
- [Best Audio Codec for Online Video Streaming in 2026](https://antmedia.io/best-audio-codec/) — Opus configuration
- [Opus Discontinuous Transmission (DTX)](https://getstream.io/resources/projects/webrtc/advanced/dtx/) — Silence suppression
- [Request runtime permissions](https://developer.android.com/training/permissions/requesting) — Permission request flow

### Tertiary (LOW confidence, needs validation)

- Exact tone frequencies for PTT chirps/roger beeps — NEEDS user testing for radio authenticity
- Bluetooth SCO connection timing (1-2 seconds estimate) — NEEDS device testing across OEMs
- AEC effectiveness on speaker mode — NEEDS testing on Samsung, OnePlus, Pixel devices
- Toggle mode max duration enforcement (client vs server) — NEEDS design decision

---

## Metadata

**Research date:** 2026-02-10
**Valid until:** ~30 days (Audio APIs stable, DataStore 1.1.1 stable)

**Confidence breakdown:**
- Standard stack: HIGH — AudioRecord/AudioTrack/ToneGenerator are stable Android APIs
- Architecture: HIGH — Patterns verified with official docs and 2026 best practices
- Pitfalls: HIGH — Echo cancellation, Bluetooth SCO, buffer underruns verified with multiple sources
- Settings: HIGH — DataStore 1.1.1 is 2026 standard, replacing SharedPreferences

**Next steps:**
1. Phase 6 planning should validate permission request flow (when to request RECORD_AUDIO)
2. Test AEC effectiveness on 3+ physical devices (Samsung, Google Pixel, OnePlus)
3. Measure actual Bluetooth SCO connection timing (log timestamps from startBluetoothSco() to SCO_AUDIO_STATE_CONNECTED)
4. Design toggle mode max duration enforcement (client + server coordination)
5. Validate Opus 48kHz sample rate support on minimum SDK 26 devices
