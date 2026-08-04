package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.example.data.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin

class MusicPlaybackManager {

    companion object {
        const val SAMPLE_RATE = 22050 // High quality but lightweight
        private const val BUFFER_SIZE = 1024

        @Volatile
        private var instance: MusicPlaybackManager? = null

        fun getInstance(): MusicPlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlaybackManager().also { instance = it }
            }
        }
    }

    private var audioTrack: AudioTrack? = null
    private var synthesisThread: Thread? = null
    private var isThreadRunning = false

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // UI state flows
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0f to 1f
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0)
    val currentPositionSec = _currentPositionSec.asStateFlow()

    // Sound adjustments
    private val _speed = MutableStateFlow(1.0f) // 0.25f - 3.0f
    val speed = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f) // 0.5f - 2.0f
    val pitch = _pitch.asStateFlow()

    // 5-band EQ gains: elements represent dB modifiers between -12dB and +12dB (UI)
    // Coerced to multiplier: 10^(dB/20)
    private val _eqGains = MutableStateFlow(floatArrayOf(0f, 0f, 0f, 0f, 0f))
    val eqGains = _eqGains.asStateFlow()

    // Sleep timer in minutes remaining (0 means disabled)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    // Current playlist or queue index tracking
    var currentIndex: Int = 0
    var activeQueueList = listOf<MusicTrack>()

    // Local tracker variables for synthesis state
    private var phase = 0.0
    private var beatPosition = 0.0
    private var synthTrackId = -1

    init {
        initAudioTrack()
        startSynthesisLoop()
    }

    private fun initAudioTrack() {
        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE * 2,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("MusicPlaybackManager", "Error initializing AudioTrack: ${e.message}")
        }
    }

    private fun startSynthesisLoop() {
        isThreadRunning = true
        synthesisThread = Thread {
            val buffer = ShortArray(BUFFER_SIZE)
            while (isThreadRunning) {
                if (_isPlaying.value && _currentTrack.value != null) {
                    val track = _currentTrack.value!!
                    val pitchFactor = _pitch.value
                    val speedFactor = _speed.value
                    val eqMultipliers = _eqGains.value.map { db -> Math.pow(10.0, db / 20.0).toFloat() }.toFloatArray()

                    synthTrackId = track.id

                    // Synthesize PCM samples based on track theme and current phase
                    for (i in 0 until BUFFER_SIZE) {
                        var sample = 0.0

                        // Calculate current tempo beats
                        val tempoBpm = when (track.id % 5) {
                            0 -> 120.0
                            1 -> 80.0
                            2 -> 65.0
                            3 -> 95.0
                            else -> 110.0
                        } * speedFactor

                        // Advance beat counter
                        beatPosition += (1.0 / SAMPLE_RATE) * (tempoBpm / 60.0)
                        if (beatPosition >= 16.0) {
                            beatPosition -= 16.0
                        }

                        // Select key notes depending on the track ID
                        val keyRootFreq = when (track.id % 4) {
                            0 -> 130.81 // C3
                            1 -> 146.83 // D3
                            2 -> 110.00 // A2
                            else -> 116.54 // A#2
                        } * pitchFactor

                        // Melody tracker depending on beat position (Pentatonic notes mapping)
                        val barStep = (beatPosition * 4.0).toInt() % 16
                        val melodyIntervalMultiplier = when (barStep) {
                            0 -> 1.0  // Root
                            2 -> 1.25 // Major Third / Min Third approx
                            4 -> 1.5  // Fifth
                            6 -> 1.66 // Sixth
                            8 -> 2.0  // Octave
                            10 -> 2.5 // High third
                            12 -> 1.5 // Fifth
                            14 -> 1.8 // Seventh
                            else -> 0.0 // Silent gap note
                        }

                        // Synthesis based on genre type
                        when {
                            track.genre.contains("Ambient", ignoreCase = true) -> {
                                // Subtly breathing drone + soft triangle wave lead
                                val carrier = sin(phase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE)
                                val subOsc = sin(phase * (keyRootFreq * 0.5) * 2.0 * Math.PI / SAMPLE_RATE) * 0.6
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0) {
                                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                                    // Soft voice / sine lead
                                    lead = sin(phase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                                }
                                sample = (carrier + subOsc + lead) * 0.35
                            }
                            track.genre.contains("Synthwave", ignoreCase = true) || track.genre.contains("Outrun", ignoreCase = true) -> {
                                // Bouncing retro synth square wave bassline + chirpy lead
                                val bassFreq = if ((beatPosition.toInt() % 2) == 0) keyRootFreq else keyRootFreq * 1.12
                                val bass = if (sin(phase * bassFreq * 2.0 * Math.PI / SAMPLE_RATE) > 0.0) 0.35 else -0.35
                                
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0 && (barStep % 2 == 0)) {
                                    val leadFreq = keyRootFreq * 3.0 * melodyIntervalMultiplier
                                    // Sawtooth-like wave
                                    val leadPhase = (phase * leadFreq / SAMPLE_RATE) % 1.0
                                    lead = (leadPhase - 0.5) * 0.3
                                }
                                sample = (bass + lead) * 0.3
                            }
                            track.genre.contains("Lofi", ignoreCase = true) -> {
                                // Chill warm sine electric piano sound + vinyl noise emulation
                                val chordFreq1 = keyRootFreq * 1.5
                                val chordFreq2 = keyRootFreq * 2.0
                                val pad = (sin(phase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE) + 
                                           sin(phase * chordFreq1 * 2.0 * Math.PI / SAMPLE_RATE) * 0.7 +
                                           sin(phase * chordFreq2 * 2.0 * Math.PI / SAMPLE_RATE) * 0.5) * 0.25
                                
                                var melody = 0.0
                                if (melodyIntervalMultiplier > 0.0) {
                                    val melFreq = keyRootFreq * 4.0 * melodyIntervalMultiplier
                                    melody = sin(phase * melFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.3 * (1.0 - (beatPosition % 0.5) * 2.0)
                                }
                                // Soft vinyl crackle emulation
                                val crackle = (Math.random() - 0.5) * 0.03 * if (Math.random() > 0.98) 1.2 else 0.15
                                sample = (pad + melody + crackle) * 0.4
                            }
                            track.genre.contains("Nature", ignoreCase = true) -> {
                                // Ocean wave rolling white noise + wind chimes
                                val seaLfo = (sin(phase * 0.07 * 2.0 * Math.PI / SAMPLE_RATE) + 1.0) * 0.5 // Slow breathing
                                val whiteNoise = (Math.random() - 0.5) * 0.3 * seaLfo
                                
                                // Wind chime tinkle
                                var chime = 0.0
                                if (Math.random() > 0.9992) {
                                    val chimeFreq = 1200.0 + Math.random() * 800.0
                                    chime = sin(phase * chimeFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.55
                                }
                                sample = (whiteNoise + chime) * 0.45
                            }
                            else -> {
                                // Default melodic acoustic pulse (organ-like)
                                val fundamental = sin(phase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                                val overtone = sin(phase * keyRootFreq * 3.0 * 2.0 * Math.PI / SAMPLE_RATE) * 0.15
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0) {
                                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                                    lead = sin(phase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.25
                                }
                                sample = (fundamental + overtone + lead) * 0.4
                            }
                        }

                        // Apply 5-Band Equalizer modifier simulation
                        // We map sample components roughly:
                        // Low (Bass component) -> EQ Band 0
                        // Mid-Low (lower presence) -> EQ Band 1 & 2
                        // High (Treble chime/melody) -> EQ Band 3 & 4
                        // To keep it clean, we divide the sample into 3 frequency layers (Bass, Mid, Treble) using simple filters
                        val bassFreqCut = 120.0
                        val trebleFreqCut = 1500.0
                        
                        // Let's filter sample into simple components based on current phase and approximate bounds
                        val currentSampleFreq = keyRootFreq * (if (melodyIntervalMultiplier > 0.0) melodyIntervalMultiplier else 1.0)
                        val sampleGainFactor = when {
                            currentSampleFreq < bassFreqCut -> eqMultipliers[0] * 0.8f + eqMultipliers[1] * 0.2f
                            currentSampleFreq > trebleFreqCut -> eqMultipliers[4] * 0.7f + eqMultipliers[3] * 0.3f
                            else -> eqMultipliers[2]
                        }
                        
                        // Apply simulated band boost/cut
                        sample *= sampleGainFactor

                        // Clamp sample to prevent digital clipping
                        if (sample > 1.0) sample = 1.0
                        if (sample < -1.0) sample = -1.0

                        // Write to buffer (PCM 16-bit Short)
                        buffer[i] = (sample * 32767.0).toInt().toShort()

                        // Update phase
                        phase += 1.0
                        if (phase > SAMPLE_RATE * 1000) { // Keep bounds and prevent overflow
                            phase = 0.0
                        }
                    }

                    // Write PCM buffer to stream
                    try {
                        audioTrack?.write(buffer, 0, BUFFER_SIZE)
                    } catch (e: Exception) {
                        Log.e("MusicPlaybackManager", "Error feeding PCM stream: ${e.message}")
                    }

                    // Direct tick updates on UI thread for elapsed playback progress and current track length
                    scope.launch {
                        // Progress is relative to duration
                        val currentProgress = _playbackProgress.value
                        val newProgress = currentProgress + (BUFFER_SIZE.toFloat() / SAMPLE_RATE) / track.durationSec
                        if (newProgress >= 1f) {
                            // Track completed! Advance to next track in queue automatically!
                            _playbackProgress.value = 0f
                            _currentPositionSec.value = 0
                            playNext()
                        } else {
                            _playbackProgress.value = newProgress
                            _currentPositionSec.value = (newProgress * track.durationSec).toInt()
                        }
                    }
                } else {
                    // Loop is active, but playback paused. Sleep thread briefly to keep energy efficient.
                    try {
                        Thread.sleep(60)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun playTrack(track: MusicTrack, queueList: List<MusicTrack>, index: Int) {
        _currentTrack.value = track
        activeQueueList = queueList
        currentIndex = index
        _playbackProgress.value = 0f
        _currentPositionSec.value = 0
        Log.d("MusicPlaybackManager", "Playing track: ${track.title} in queue size ${queueList.size} index ${index}")
        
        // Ensure AudioTrack is initialized and playing
        if (audioTrack == null) {
            initAudioTrack()
        }
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        if (_currentTrack.value == null && activeQueueList.isNotEmpty()) {
            _currentTrack.value = activeQueueList.first()
            currentIndex = 0
        }
        if (_currentTrack.value != null) {
            _isPlaying.value = !_isPlaying.value
        }
    }

    fun playNext() {
        if (activeQueueList.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % activeQueueList.size
            _currentTrack.value = activeQueueList[currentIndex]
            _playbackProgress.value = 0f
            _currentPositionSec.value = 0
            _isPlaying.value = true
        }
    }

    fun playPrev() {
        if (activeQueueList.isNotEmpty()) {
            currentIndex = if (currentIndex - 1 < 0) activeQueueList.size - 1 else currentIndex - 1
            _currentTrack.value = activeQueueList[currentIndex]
            _playbackProgress.value = 0f
            _currentPositionSec.value = 0
            _isPlaying.value = true
        }
    }

    fun setSpeed(value: Float) {
        _speed.value = value.coerceIn(0.25f, 3.0f)
    }

    fun setPitch(value: Float) {
        _pitch.value = value.coerceIn(0.5f, 2.0f)
    }

    fun setEqualizerPreset(preset: String) {
        val gains = when (preset.lowercase()) {
            "flat" -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            "bass booster" -> floatArrayOf(8f, 5f, 0f, -2f, -4f)
            "vocal booster" -> floatArrayOf(-3f, 1f, 6f, 5f, 1f)
            "pop" -> floatArrayOf(-1.5f, 2f, 5f, 3f, -1f)
            "rock" -> floatArrayOf(5f, 3f, -1f, 3f, 6f)
            "classical" -> floatArrayOf(4f, 3f, 1f, 3f, 4f)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }
        updateEqualizerGains(gains)
    }

    fun updateEqualizerGains(gains: FloatArray) {
        _eqGains.value = gains.copyOf()
    }

    fun seekToProgress(progress: Float) {
        val currentTrackDuration = _currentTrack.value?.durationSec ?: 100
        _playbackProgress.value = progress.coerceIn(0f, 1f)
        _currentPositionSec.value = (progress * currentTrackDuration).toInt()
    }

    fun startSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            // Launch a coroutine to countdown every minute
            scope.launch {
                while (_sleepTimerMinutes.value > 0 && _isPlaying.value) {
                    kotlinx.coroutines.delay(60000)
                    val nextMinutes = _sleepTimerMinutes.value - 1
                    _sleepTimerMinutes.value = nextMinutes
                    if (nextMinutes <= 0) {
                        _isPlaying.value = false
                        _sleepTimerMinutes.value = 0
                        break
                    }
                }
            }
        }
    }

    fun stopSleepTimer() {
        _sleepTimerMinutes.value = 0
    }

    fun stopAll() {
        isThreadRunning = false
        _isPlaying.value = false
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // silent catch
        }
        audioTrack = null
        synthesisThread?.interrupt()
    }
}
