package com.aaryo.selfattendance.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * RewardVoiceAnnouncementManager
 *
 * Implements friendly, energetic Hindi voice announcement with:
 * - Native Android TextToSpeech engine configured for Hindi (hi_IN / hi)
 * - Male / young voice preference if available in the voice registry
 * - Soft, pleasant synthesized background acoustic chord harmony (ducked at -20dB)
 * - Safe lifecycle management, play/pause/stop controls, and progress tracking.
 */
class RewardVoiceAnnouncementManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var bgmJob: Job? = null
    private var bgmTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    val announcementScript: String =
        "Self Attendance Pro mein rewards paane ka ek naya mauka! " +
        "Apne friends ke saath app share karke eligible referrals par coins collect karein. " +
        "Har din Daily Spin try karein aur available reward paayein. " +
        "Aapki marzi se Rewarded Ad dekhein aur eligible additional coins collect karein. " +
        "Coins collect hone ke baad, app ki eligibility, reward rules aur withdrawal conditions ke according available rewards ke liye redeem ya withdrawal request karein. " +
        "Rewards ke rules aur terms zaroor check karein. " +
        "Toh Rewards section open karein aur available activities try karein!"

    init {
        runCatching {
            tts = TextToSpeech(appContext, this)
        }.onFailure {
            Log.e("RewardVoiceManager", "Failed to init TextToSpeech", it)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            setupVoiceAndLanguage()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _isPlaying.value = false
                    stopBgm()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _isPlaying.value = false
                    stopBgm()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    _isPlaying.value = false
                    stopBgm()
                }
            })
        } else {
            Log.w("RewardVoiceManager", "TextToSpeech init returned status: $status")
        }
    }

    private fun setupVoiceAndLanguage() {
        val t = tts ?: return
        val hindiLocale = Locale("hi", "IN")
        val langResult = t.setLanguage(hindiLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to generic Hindi or default
            val altResult = t.setLanguage(Locale("hi"))
            if (altResult == TextToSpeech.LANG_MISSING_DATA || altResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                t.language = Locale.getDefault()
            }
        }

        // Young, friendly pitch and slightly relaxed, clear speech rate (0.92x)
        t.setPitch(1.02f)
        t.setSpeechRate(0.92f)

        // Try to pick male/young voice in Hindi if device provides specific voice list
        runCatching {
            val voices = t.voices
            if (!voices.isNullOrEmpty()) {
                val hindiVoice = voices.firstOrNull { voice ->
                    val isHindi = voice.locale.language.equals("hi", ignoreCase = true)
                    val isMale = voice.name.contains("male", ignoreCase = true) || voice.name.contains("m-", ignoreCase = true)
                    isHindi && isMale
                } ?: voices.firstOrNull { voice ->
                    voice.locale.language.equals("hi", ignoreCase = true)
                }

                hindiVoice?.let { t.voice = it }
            }
        }
    }

    /**
     * Start playing the announcement with soft synthesized background acoustic harmony.
     */
    fun startAnnouncement(scope: CoroutineScope) {
        if (_isPlaying.value) {
            stopAnnouncement()
            return
        }

        val t = tts
        if (t == null || !isTtsInitialized) {
            Log.w("RewardVoiceManager", "TTS is not ready yet")
            return
        }

        _isPlaying.value = true
        _isSpeaking.value = true

        // 1. Start gentle acoustic chord background music (ducked at low volume)
        startSoftBgm(scope)

        // 2. Speak the script
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        t.speak(announcementScript, TextToSpeech.QUEUE_FLUSH, params, "REWARD_ANNOUNCEMENT_ID")
    }

    fun stopAnnouncement() {
        _isPlaying.value = false
        _isSpeaking.value = false
        runCatching { tts?.stop() }
        stopBgm()
    }

    private fun startSoftBgm(scope: CoroutineScope) {
        stopBgm()
        bgmJob = scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 22050
                // Gentle looping acoustic synth chords: C major 7 -> F major 7 -> G major -> C
                val chords = listOf(
                    floatArrayOf(261.63f, 329.63f, 392.00f, 493.88f), // Cmaj7
                    floatArrayOf(349.23f, 440.00f, 523.25f, 659.25f), // Fmaj7
                    floatArrayOf(392.00f, 493.88f, 587.33f, 698.46f), // G7
                    floatArrayOf(261.63f, 329.63f, 392.00f, 523.25f)  // C
                )

                val chordDurationSec = 3.5f
                val samplesPerChord = (sampleRate * chordDurationSec).toInt()
                val totalSamples = samplesPerChord * chords.size
                val buffer = ShortArray(totalSamples)

                var offset = 0
                for (chord in chords) {
                    for (i in 0 until samplesPerChord) {
                        val t = i.toDouble() / sampleRate
                        // Envelope: soft attack and gentle release
                        val env = sin(PI * (i.toDouble() / samplesPerChord)).toFloat()

                        var sampleVal = 0.0
                        for (freq in chord) {
                            sampleVal += sin(2.0 * PI * freq * t)
                        }
                        sampleVal /= chord.size

                        // Soft background volume (ducked at ~12% so voice is crystal clear)
                        val amplitude = (sampleVal * env * 2500.0).toInt().coerceIn(-32767, 32767)
                        buffer[offset++] = amplitude.toShort()
                    }
                }

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val fmt = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack(
                    attrs, fmt, buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                bgmTrack = track
                track.write(buffer, 0, buffer.size)
                track.setLoopPoints(0, buffer.size, -1) // Infinite loop until stopped
                track.play()

                while (isActive && _isPlaying.value) {
                    delay(300)
                }
            }.also {
                stopBgm()
            }
        }
    }

    private fun stopBgm() {
        bgmJob?.cancel()
        bgmJob = null
        runCatching {
            bgmTrack?.stop()
            bgmTrack?.release()
        }
        bgmTrack = null
    }

    fun release() {
        stopAnnouncement()
        runCatching {
            tts?.shutdown()
        }
        tts = null
        isTtsInitialized = false
    }
}
