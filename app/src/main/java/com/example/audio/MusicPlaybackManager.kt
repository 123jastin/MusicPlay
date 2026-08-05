package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
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

    private var appContext: Context? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
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
        startMediaPlayerProgressLoop()
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun startMediaPlayerProgressLoop() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(200)
                val mp = mediaPlayer
                val track = _currentTrack.value
                if (mp != null && _isPlaying.value && track != null && track.contentUri.isNotBlank()) {
                    try {
                        if (mp.isPlaying && mp.duration > 0) {
                            val posMs = mp.currentPosition
                            val durMs = mp.duration
                            _playbackProgress.value = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                            _currentPositionSec.value = (posMs / 1000)
                        }
                    } catch (e: Exception) {
                        // Ignore transient state errors
                    }
                }
            }
        }
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
                val track = _currentTrack.value
                // Only run synthesis when playing a synth track (without contentUri)
                if (_isPlaying.value && track != null && track.contentUri.isBlank()) {
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
                                val carrier = sin(phase * keyRootFreq * 2.0 * Math.PI / SAMPLE_RATE)
                                val subOsc = sin(phase * (keyRootFreq * 0.5) * 2.0 * Math.PI / SAMPLE_RATE) * 0.6
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0) {
                                    val leadFreq = keyRootFreq * 2.0 * melodyIntervalMultiplier
                                    lead = sin(phase * leadFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.4
                                }
                                sample = (carrier + subOsc + lead) * 0.35
                            }
                            track.genre.contains("Synthwave", ignoreCase = true) || track.genre.contains("Outrun", ignoreCase = true) -> {
                                val bassFreq = if ((beatPosition.toInt() % 2) == 0) keyRootFreq else keyRootFreq * 1.12
                                val bass = if (sin(phase * bassFreq * 2.0 * Math.PI / SAMPLE_RATE) > 0.0) 0.35 else -0.35
                                
                                var lead = 0.0
                                if (melodyIntervalMultiplier > 0.0 && (barStep % 2 == 0)) {
                                    val leadFreq = keyRootFreq * 3.0 * melodyIntervalMultiplier
                                    val leadPhase = (phase * leadFreq / SAMPLE_RATE) % 1.0
                                    lead = (leadPhase - 0.5) * 0.3
                                }
                                sample = (bass + lead) * 0.3
                            }
                            track.genre.contains("Lofi", ignoreCase = true) -> {
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
                                val crackle = (Math.random() - 0.5) * 0.03 * if (Math.random() > 0.98) 1.2 else 0.15
                                sample = (pad + melody + crackle) * 0.4
                            }
                            track.genre.contains("Nature", ignoreCase = true) -> {
                                val seaLfo = (sin(phase * 0.07 * 2.0 * Math.PI / SAMPLE_RATE) + 1.0) * 0.5
                                val whiteNoise = (Math.random() - 0.5) * 0.3 * seaLfo
                                var chime = 0.0
                                if (Math.random() > 0.9992) {
                                    val chimeFreq = 1200.0 + Math.random() * 800.0
                                    chime = sin(phase * chimeFreq * 2.0 * Math.PI / SAMPLE_RATE) * 0.55
                                }
                                sample = (whiteNoise + chime) * 0.45
                            }
                            else -> {
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

                        val bassFreqCut = 120.0
                        val trebleFreqCut = 1500.0
                        
                        val currentSampleFreq = keyRootFreq * (if (melodyIntervalMultiplier > 0.0) melodyIntervalMultiplier else 1.0)
                        val sampleGainFactor = when {
                            currentSampleFreq < bassFreqCut -> eqMultipliers[0] * 0.8f + eqMultipliers[1] * 0.2f
                            currentSampleFreq > trebleFreqCut -> eqMultipliers[4] * 0.7f + eqMultipliers[3] * 0.3f
                            else -> eqMultipliers[2]
                        }
                        
                        sample *= sampleGainFactor

                        if (sample > 1.0) sample = 1.0
                        if (sample < -1.0) sample = -1.0

                        buffer[i] = (sample * 32767.0).toInt().toShort()

                        phase += 1.0
                        if (phase > SAMPLE_RATE * 1000) {
                            phase = 0.0
                        }
                    }

                    try {
                        audioTrack?.write(buffer, 0, BUFFER_SIZE)
                    } catch (e: Exception) {
                        Log.e("MusicPlaybackManager", "Error feeding PCM stream: ${e.message}")
                    }

                    scope.launch {
                        val currentProgress = _playbackProgress.value
                        val newProgress = currentProgress + (BUFFER_SIZE.toFloat() / SAMPLE_RATE) / track.durationSec
                        if (newProgress >= 1f) {
                            _playbackProgress.value = 0f
                            _currentPositionSec.value = 0
                            playNext()
                        } else {
                            _playbackProgress.value = newProgress
                            _currentPositionSec.value = (newProgress * track.durationSec).toInt()
                        }
                    }
                } else {
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
        Log.d("MusicPlaybackManager", "Playing track: ${track.title} in queue size ${queueList.size} index $index")

        appContext?.let { ctx ->
            com.example.service.MusicPlaybackService.startService(ctx)
        }

        if (track.contentUri.isNotBlank() && appContext != null) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(appContext!!, Uri.parse(track.contentUri))
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        _isPlaying.value = true
                    }
                    setOnCompletionListener {
                        playNext()
                    }
                    setOnErrorListener { _, _, _ ->
                        _isPlaying.value = true
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlaybackManager", "MediaPlayer play error: ${e.message}")
                _isPlaying.value = true
            }
        } else {
            if (audioTrack == null) {
                initAudioTrack()
            }
            _isPlaying.value = true
        }
    }

    fun togglePlayPause() {
        if (_currentTrack.value == null && activeQueueList.isNotEmpty()) {
            val firstTrack = activeQueueList.first()
            playTrack(firstTrack, activeQueueList, 0)
            return
        }
        if (_currentTrack.value != null) {
            val playing = !_isPlaying.value
            _isPlaying.value = playing
            mediaPlayer?.let { mp ->
                try {
                    if (playing) {
                        if (!mp.isPlaying) mp.start()
                    } else {
                        if (mp.isPlaying) mp.pause()
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlaybackManager", "Toggle error: ${e.message}")
                }
            }
        }
    }

    fun playNext() {
        if (activeQueueList.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % activeQueueList.size
            val nextTrack = activeQueueList[currentIndex]
            playTrack(nextTrack, activeQueueList, currentIndex)
        }
    }

    fun playPrev() {
        if (activeQueueList.isNotEmpty()) {
            currentIndex = if (currentIndex - 1 < 0) activeQueueList.size - 1 else currentIndex - 1
            val prevTrack = activeQueueList[currentIndex]
            playTrack(prevTrack, activeQueueList, currentIndex)
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
        val targetProgress = progress.coerceIn(0f, 1f)
        _playbackProgress.value = targetProgress
        _currentPositionSec.value = (targetProgress * currentTrackDuration).toInt()

        mediaPlayer?.let { mp ->
            try {
                if (mp.duration > 0) {
                    val targetMs = (targetProgress * mp.duration).toInt()
                    mp.seekTo(targetMs)
                }
            } catch (e: Exception) {
                // silent catch
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            scope.launch {
                while (_sleepTimerMinutes.value > 0 && _isPlaying.value) {
                    kotlinx.coroutines.delay(60000)
                    val nextMinutes = _sleepTimerMinutes.value - 1
                    _sleepTimerMinutes.value = nextMinutes
                    if (nextMinutes <= 0) {
                        _isPlaying.value = false
                        mediaPlayer?.pause()
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
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {}
        }
        mediaPlayer = null
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
