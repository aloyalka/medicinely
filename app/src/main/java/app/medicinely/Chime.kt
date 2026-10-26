package app.medicinely

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/** The chime library. Sounds are generated on the phone and played on the alarm channel, so silent mode doesn't mute them. */
object Chime {
    val names = arrayOf(
        "Soft bell", "Ding-dong", "Rising trio", "Music box",
        "Glass ping", "Wind chimes", "Temple gong", "Soft pager"
    )
    private const val SR = 44100

    private fun tone(b: FloatArray, f: Double, t: Double, dur: Double, g: Double, triangle: Boolean) {
        val start = (t * SR).toInt()
        val n = (dur * SR).toInt()
        val attack = (0.01 * SR).toInt()
        val k = ln(0.0001 / g) / (n - attack)
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val env = if (i < attack) g * i / attack else g * exp(k * (i - attack))
            val ph = 2 * PI * f * i / SR
            val w = if (triangle) (2 / PI) * asin(sin(ph)) else sin(ph)
            b[idx] += (env * w).toFloat()
        }
    }

    private fun bell(b: FloatArray, f: Double, t: Double, dur: Double, g: Double) {
        tone(b, f, t, dur, g, false)
        tone(b, f * 2.01, t, dur * 0.6, g * 0.35, false)
        tone(b, f * 3.02, t, dur * 0.35, g * 0.15, false)
    }

    fun render(index: Int): ShortArray {
        val b = FloatArray((3.2 * SR).toInt())
        when (index) {
            0 -> bell(b, 659.0, 0.0, 2.2, 0.5)
            1 -> { bell(b, 784.0, 0.0, 1.6, 0.45); bell(b, 659.0, 0.45, 2.0, 0.45) }
            2 -> doubleArrayOf(523.0, 659.0, 784.0).forEachIndexed { i, f -> tone(b, f, i * 0.16, 0.9, 0.35, true) }
            3 -> doubleArrayOf(1047.0, 784.0, 1319.0, 1047.0).forEachIndexed { i, f -> tone(b, f, i * 0.2, 1.2, 0.25, false) }
            4 -> { tone(b, 1760.0, 0.0, 2.5, 0.25, false); tone(b, 1766.0, 0.0, 2.5, 0.15, false) }
            5 -> {
                val jitter = doubleArrayOf(0.0, 0.03, 0.01, 0.04, 0.02)
                doubleArrayOf(1175.0, 1568.0, 1319.0, 1760.0, 1397.0).forEachIndexed { i, f ->
                    bell(b, f, i * 0.13 + jitter[i], 1.6, 0.18)
                }
            }
            6 -> { tone(b, 220.0, 0.0, 3.0, 0.5, false); tone(b, 607.0, 0.0, 3.0, 0.25, false); tone(b, 1188.0, 0.0, 3.0, 0.125, false) }
            else -> doubleArrayOf(587.0, 880.0, 587.0, 880.0).forEachIndexed { i, f -> tone(b, f, i * 0.18, 0.35, 0.3, true) }
        }
        var peak = 0f
        for (v in b) if (abs(v) > peak) peak = abs(v)
        val scale = if (peak > 0f) 0.9f / peak else 1f
        return ShortArray(b.size) { (b[it] * scale * 32767).toInt().toShort() }
    }

    /** Plays a chime. Refuses to play during a nap unless [ignoreNap] (used only for test buttons). */
    fun play(ctx: Context, index: Int, ignoreNap: Boolean = false) {
        val p = Prefs(ctx)
        if (!ignoreNap && p.napUntilMs > System.currentTimeMillis()) return
        val pcm = render(index)
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val old = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = maxOf(1, (max * p.volume / 100.0).roundToInt())
        try { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) } catch (e: Exception) { }
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            Handler(Looper.getMainLooper()).postDelayed({
                try { track.stop() } catch (e: Exception) { }
                track.release()
                try { am.setStreamVolume(AudioManager.STREAM_ALARM, old, 0) } catch (e: Exception) { }
            }, 3500L)
        } catch (e: Exception) {
            try { am.setStreamVolume(AudioManager.STREAM_ALARM, old, 0) } catch (e2: Exception) { }
        }
    }
}
