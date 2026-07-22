package com.aaryo.selfattendance.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * SpinSoundManager — Advanced Audio v2 (Global Release)
 *
 * Uses AudioTrack with full PCM synthesis for rich, professional sound effects.
 * No external audio files required — every sound is generated from math.
 *
 *  startSpinSound()         — mechanical ratchet ticking that decelerates with the wheel
 *  playWinSound()           — triumphant ascending major-chord arpeggio with shimmer tail
 *  playLoseSound()          — descending minor glide (sympathetic, not harsh)
 *  playCoinCollectSound()   — sparkle coin jingle [companion, no instance needed]
 *  playCoinCollectHaptic()  — triple-pulse haptic [companion, no instance needed]
 *  release()                — must be called in DisposableEffect.onDispose()
 *
 *  Resource safety: every AudioTrack is released in a try/finally block.
 *  CancellationException is always re-thrown so structured concurrency is preserved.
 */
class SpinSoundManager(context: Context) {

    private val appContext = context.applicationContext
    private var tickJob: Job? = null
    private val sampleRate = 44_100

    // ── Vibrator (API 31+ / legacy) ────────────────────────────────────────
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    // ── PCM synthesis helpers ──────────────────────────────────────────────

    /**
     * Build an AudioTrack in STATIC mode from a PCM buffer.
     * Caller MUST call safeRelease() in a finally block.
     */
    private fun makeTrack(samples: ShortArray): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        return AudioTrack(attrs, fmt, samples.size * 2,
            AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
            .also { it.write(samples, 0, samples.size) }
    }

    /** Always stops then releases; swallows secondary errors. */
    private fun AudioTrack.safeRelease() {
        runCatching { stop() }
        runCatching { release() }
    }

    /**
     * Synthesize a pitched tone with ADSR and optional harmonic overtones.
     */
    private fun synthesizeTone(
        freqHz: Double,
        durationMs: Int,
        attackMs: Int = 8,
        releaseMs: Int = 70,
        volume: Float = 0.72f,
        harmonics: List<Pair<Double, Float>> = emptyList()
    ): ShortArray {
        val n = (sampleRate * durationMs / 1000.0).toInt()
        val atkN = (sampleRate * attackMs / 1000.0).toInt()
        val relN = (sampleRate * releaseMs / 1000.0).toInt()
        val normDiv = 1.0 + harmonics.sumOf { it.second.toDouble() }
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            var s = sin(2.0 * PI * freqHz * t)
            for ((mult, amp) in harmonics) s += sin(2.0 * PI * freqHz * mult * t) * amp
            val env: Float = when {
                i < atkN       -> i.toFloat() / atkN
                i >= n - relN  -> (n - i).toFloat() / relN.coerceAtLeast(1)
                else            -> 1f
            }
            (s / normDiv * env * volume * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Synthesize a crisp mechanical click: dual-band noise burst with
     * 2.2 kHz primary resonance + 800 Hz body for satisfying tactile feel.
     */
    private fun synthesizeClick(lengthSamples: Int = 440, vol: Float = 0.82f): ShortArray {
        var lcg = 987_654_321L
        return ShortArray(lengthSamples) { i ->
            lcg = lcg * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            val noise = ((lcg ushr 33) and 0xFFFFL).toFloat() / 32_767.5f - 1f
            // Sharp attack, faster decay for snappier feel
            val env = if (i < 4) i / 4f else exp(-i * 0.024f)
            // Primary click: 2.2 kHz + body resonance at 800 Hz
            val res1 = sin(2.0 * PI * 2_200.0 * i / 44_100.0).toFloat() * 0.35f
            val res2 = sin(2.0 * PI * 800.0 * i / 44_100.0).toFloat() * 0.15f
            ((noise * 0.55f + res1 + res2) * env * vol * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    // Pre-synthesize three click variants: rapid, normal, slow — for richer spin texture
    private val clickFast  : ShortArray by lazy { synthesizeClick(340, 0.88f) }
    private val clickNormal: ShortArray by lazy { synthesizeClick(440, 0.80f) }
    private val clickSlow  : ShortArray by lazy { synthesizeClick(580, 0.62f) }

    // ── Vibration helper ───────────────────────────────────────────────────

    private fun vibrateOnce(durationMs: Long, amplitude: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    }

    // ── Spin ticking ──────────────────────────────────────────────────────

    /**
     * Launches a coroutine that plays synthesized mechanical clicks + haptic pulses,
     * decelerating from fast (~48 ms) to slow (~280 ms) over [durationMs].
     *
     * CancellationException is always re-thrown after AudioTrack cleanup.
     */
    fun startSpinSound(scope: CoroutineScope, durationMs: Long = 4_000L) {
        tickJob?.cancel()
        tickJob = scope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var counter = 0
            while (isActive) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= durationMs) break

                val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                val interval = (48L + (progress * progress * 232f).toLong())

                val samples = when {
                    progress < 0.35f -> clickFast
                    progress < 0.70f -> clickNormal
                    else             -> clickSlow
                }
                counter++

                val track = try { makeTrack(samples) } catch (_: Exception) { null }
                if (track != null) {
                    try {
                        track.setVolume((0.80f - progress * 0.22f).coerceAtLeast(0.3f))
                        track.play()
                        val playMs = (samples.size * 1000L / sampleRate).coerceAtLeast(6L)
                        delay(playMs)
                    } catch (e: CancellationException) {
                        track.safeRelease()
                        throw e               // re-throw — preserves structured concurrency
                    } finally {
                        track.safeRelease()   // always runs on normal completion too
                    }
                }

                vibrateOnce(
                    durationMs = (10L + (progress * 14f).toLong()),
                    amplitude  = (210 - (progress * 155f).toInt()).coerceIn(55, 210)
                )

                delay(interval)
            }
        }
    }

    fun stopSpinSound() {
        tickJob?.cancel()
        tickJob = null
    }

    // ── Result sounds ──────────────────────────────────────────────────────

    /**
     * Triumphant win fanfare v3:
     *   E5 → G5 → B5 → E6 → G6 — full major pentatonic climb.
     *   Each note gets richer harmonics and a slight velocity swell.
     *   Dual shimmer tails (octave sparkle) create a premium "jackpot" feel.
     */
    fun playWinSound() {
        CoroutineScope(Dispatchers.IO).launch {
            // Rising major-scale arpeggio with swelling volume
            val arpNotes = listOf(
                Triple(659.25,  95, 0.66f),   // E5
                Triple(783.99, 105, 0.70f),   // G5
                Triple(987.77, 115, 0.74f),   // B5
                Triple(1318.51,140, 0.78f),   // E6
                Triple(1567.98,260, 0.84f),   // G6  ← peak
            )
            // Rich overtone stack: octave + fifth + sub-harmonic warmth
            val richH = listOf(2.0 to 0.30f, 3.0 to 0.12f, 0.5 to 0.08f, 4.0 to 0.04f)

            for ((freq, dur, vol) in arpNotes) {
                val buf = synthesizeTone(freq, dur, attackMs = 4, releaseMs = 60,
                    volume = vol, harmonics = richH)
                val track = runCatching { makeTrack(buf) }.getOrNull() ?: continue
                try {
                    track.play()
                    delay((dur * 0.68).toLong())
                } finally {
                    track.safeRelease()
                }
            }

            // Shimmer tail — bright high C7 sparkle
            val shimA = synthesizeTone(2093.0, 180, attackMs = 2, releaseMs = 170,
                volume = 0.42f, harmonics = listOf(2.0 to 0.20f, 3.0 to 0.08f))
            val shimB = synthesizeTone(2637.0, 220, attackMs = 3, releaseMs = 200,
                volume = 0.28f, harmonics = listOf(2.0 to 0.12f))

            val tA = runCatching { makeTrack(shimA) }.getOrNull()
            val tB = runCatching { makeTrack(shimB) }.getOrNull()
            try {
                tA?.play()
                delay(40L)
                tB?.play()
                delay(220L)
            } finally {
                tA?.safeRelease()
                tB?.safeRelease()
            }
        }
    }

    /**
     * Empathetic lose sound v3:
     *   G5 → E5 → C5 → A4 — gentle minor descent, soft reverb-like tail.
     *   Longer attacks and slow decay avoid harshness; feels supportive.
     */
    fun playLoseSound() {
        CoroutineScope(Dispatchers.IO).launch {
            val notes = listOf(
                Triple(783.99, 145, 0.46f),  // G5
                Triple(659.25, 165, 0.42f),  // E5
                Triple(523.25, 185, 0.37f),  // C5
                Triple(440.00, 340, 0.30f),  // A4 — long fade out
            )
            // Warm minor harmonics: octave + minor third colour
            val minorH = listOf(2.0 to 0.18f, 1.5 to 0.10f, 3.0 to 0.05f)
            for ((freq, dur, vol) in notes) {
                val buf = synthesizeTone(freq, dur, attackMs = 18, releaseMs = 160,
                    volume = vol, harmonics = minorH)
                val track = runCatching { makeTrack(buf) }.getOrNull() ?: continue
                try {
                    track.play()
                    delay((dur * 0.72).toLong())
                } finally {
                    track.safeRelease()
                }
            }
        }
    }

    // ── Companion (static, no instance required) ───────────────────────────

    companion object {

        /**
         * Sparkle coin jingle v3: C5 → E5 → G5 → C6 → E6 magical ascending arc.
         * Extra high shimmer tail gives a satisfying "ding ding ding!" feel.
         * Every AudioTrack released in finally — safe under any exception.
         */
        fun playCoinCollectSound() {
            CoroutineScope(Dispatchers.IO).launch {
                val sr = 44_100
                // Bell-like harmonics: 2nd + 3rd overtone + gentle sub-octave warmth
                val sparkH = listOf(2.0 to 0.30f, 3.0 to 0.14f, 4.0 to 0.06f, 0.5 to 0.06f)
                val coinNotes = listOf(
                    Triple(523.25,  75, 0.62f),   // C5
                    Triple(659.25,  75, 0.64f),   // E5
                    Triple(783.99,  85, 0.66f),   // G5
                    Triple(1046.50, 95, 0.70f),   // C6
                    Triple(1318.51, 80, 0.68f),   // E6 — shimmer peak
                    Triple(1567.98, 260, 0.74f),  // G6
                )
                val normDiv = 1.0 + sparkH.sumOf { it.second.toDouble() }
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                val fmt = AudioFormat.Builder()
                    .setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()

                for ((freq, dur, vol) in coinNotes) {
                    val n = sr * dur / 1000
                    val atkN = sr * 5 / 1000
                    val relN = sr * 55 / 1000
                    val buf = ShortArray(n) { i ->
                        val t = i.toDouble() / sr
                        var s = sin(2.0 * PI * freq * t)
                        for ((m, a) in sparkH) s += sin(2.0 * PI * freq * m * t) * a
                        val env: Float = when {
                            i < atkN      -> i.toFloat() / atkN
                            i >= n - relN -> (n - i).toFloat() / relN.coerceAtLeast(1)
                            else           -> 1f
                        }
                        (s / normDiv * env * vol * Short.MAX_VALUE)
                            .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    val track = runCatching {
                        AudioTrack(attrs, fmt, buf.size * 2, AudioTrack.MODE_STATIC,
                            AudioManager.AUDIO_SESSION_ID_GENERATE).also { it.write(buf, 0, buf.size) }
                    }.getOrNull() ?: continue
                    try {
                        track.play()
                        delay((dur * 0.70).toLong())
                    } finally {
                        runCatching { track.stop() }
                        runCatching { track.release() }
                    }
                }
            }
        }

        /**
         * Triple-pulse haptic: light tap → light tap → strong buzz.
         */
        fun playCoinCollectHaptic(context: Context) {
            runCatching {
                val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                        .defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 40, 60, 40, 80, 130),
                            intArrayOf(0, 160, 0, 160, 0, 255),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 40, 60, 40, 80, 130), -1)
                }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Call from DisposableEffect.onDispose() to cancel any in-flight tick job. */
    fun release() {
        tickJob?.cancel()
        tickJob = null
    }
}
