package com.nazario.drumchart

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Campioni reali di batteria acustica (nella cartella assets/kit), tre dinamiche per strumento.
 * Fonti, entrambe con licenza CC0 (pubblico dominio):
 *  - Karoryfer Samples "Gogodze Phu Vol II": cassa, rullante, tom, hi-hat;
 *  - Versilian Community Sample Library (VCSL): piatti sospesi usati come crash e ride.
 */
object DrumKit {
    private val layers = HashMap<Drum, List<FloatArray>>()

    @Synchronized
    fun ensure(ctx: Context) {
        if (layers.isNotEmpty()) return
        Drum.entries.forEach { d ->
            val l = (0..2).mapNotNull { i ->
                runCatching { ctx.assets.open("kit/${d.key}_$i.pcm").use { it.readBytes() } }.getOrNull()?.let { toFloat(it) }
            }
            if (l.size == 3) layers[d] = l
        }
    }

    private fun toFloat(b: ByteArray): FloatArray {
        val sb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(sb.remaining()) { sb.get(it) / 32768f }
    }

    /** Campione e volume per un colpo: ghost = strato piano, normale e accento = strato forte. */
    fun hit(d: Drum, ch: Char): Pair<FloatArray, Float>? {
        val l = layers[d] ?: return null
        return when (ch) {
            'g' -> l[0] to 1.0f
            'A' -> l[2] to 1.0f
            else -> l[2] to 0.72f
        }
    }
}

/**
 * Suoni di batteria sintetizzati (nessun campione audio) e riproduzione in loop della griglia.
 * Serve solo come controllo durante la scrittura: suoni semplici, non realistici.
 */
object DrumSynth {
    const val RATE = 44100
    private val cache = HashMap<Drum, FloatArray>()

    private fun buf(sec: Float) = FloatArray((sec * RATE).toInt())

    /** Rumore bianco "schiarito" (passa-alto semplice), per piatti e rullante. */
    private fun brightNoise(n: Int, seed: Int): FloatArray {
        val r = Random(seed)
        val out = FloatArray(n)
        var prev = 0f
        for (i in 0 until n) {
            val x = r.nextFloat() * 2f - 1f
            out[i] = x - prev
            prev = x
        }
        return out
    }

    /** Tono con discesa di frequenza (cassa e tom). */
    private fun sweep(sec: Float, f0: Double, f1: Double, decay: Double, sweepRate: Double): FloatArray {
        val b = buf(sec)
        var phase = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val f = f1 + (f0 - f1) * exp(-t * sweepRate)
            phase += 2 * PI * f / RATE
            b[i] = (sin(phase) * exp(-t * decay)).toFloat()
        }
        return b
    }

    private fun noiseHit(sec: Float, decay: Double, amp: Float, seed: Int): FloatArray {
        val b = buf(sec)
        val nz = brightNoise(b.size, seed)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = (nz[i] * amp * exp(-t * decay)).toFloat()
        }
        return b
    }

    fun sound(d: Drum): FloatArray = cache.getOrPut(d) {
        when (d) {
            Drum.BD -> sweep(0.4f, 130.0, 48.0, 7.0, 30.0).also { b ->
                for (i in 0 until minOf(200, b.size)) b[i] += (1f - i / 200f) * 0.4f // attacco
            }
            Drum.SN -> {
                val tone = sweep(0.25f, 230.0, 180.0, 18.0, 40.0)
                val nz = noiseHit(0.25f, 13.0, 0.75f, 2)
                FloatArray(tone.size) { tone[it] * 0.5f + nz[it] }
            }
            Drum.HH -> noiseHit(0.07f, 55.0, 0.5f, 3)
            Drum.HO -> noiseHit(0.45f, 6.5, 0.45f, 4)
            Drum.HF -> noiseHit(0.06f, 70.0, 0.35f, 5)
            Drum.CR -> noiseHit(1.6f, 2.6, 0.55f, 6)
            Drum.RD -> {
                val b = buf(0.8f)
                val nz = brightNoise(b.size, 7)
                for (i in b.indices) {
                    val t = i.toDouble() / RATE
                    val bell = sin(2 * PI * 620 * t) * 0.25 + sin(2 * PI * 935 * t) * 0.18 + sin(2 * PI * 1480 * t) * 0.12
                    b[i] = ((bell + nz[i] * 0.18) * exp(-t * 4.5)).toFloat()
                }
                b
            }
            Drum.T1 -> sweep(0.45f, 240.0, 190.0, 8.0, 20.0)
            Drum.T2 -> sweep(0.5f, 195.0, 150.0, 7.5, 20.0)
            Drum.FT -> sweep(0.6f, 140.0, 100.0, 6.5, 18.0)
        }
    }

    private fun click(high: Boolean): FloatArray {
        val b = buf(0.03f)
        val f = if (high) 2000.0 else 1400.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = (sin(2 * PI * f * t) * exp(-t * 150) * 0.35).toFloat()
        }
        return b
    }

    /** Durata di una casella: il BPM si riferisce al movimento (semiminima, o semiminima puntata in 6/8 e 12/8). */
    fun stepFrames(g: Groove, bpm: Int): Int = (60.0 / bpm / g.m.group * RATE).toInt()

    /** Mixa tutta la griglia in un buffer che si ripete in loop senza stacchi. */
    fun render(g: Groove, bpm: Int, withClick: Boolean): ShortArray {
        val sf = stepFrames(g, bpm)
        val total = sf * g.steps
        val mix = FloatArray(total)
        fun add(src: FloatArray, at: Int, gain: Float) {
            for (i in src.indices) {
                val k = (at + i) % total // la coda rientra all'inizio del loop
                mix[k] += src[i] * gain
            }
        }
        fun addCut(src: FloatArray, at: Int, gain: Float, maxLen: Int) {
            val n = minOf(src.size, maxLen)
            val fade = minOf(600, n)
            for (i in 0 until n) {
                val f = if (i >= n - fade && n < src.size) (n - i).toFloat() / fade else 1f
                mix[(at + i) % total] += src[i] * gain * f
            }
        }
        /** Distanza (in frame) dal prossimo colpo di hi-hat chiuso o pedale: lì l'hi-hat aperto si chiude. */
        fun hatChoke(from: Int): Int {
            for (k in 1..g.steps) {
                val st = (from + k) % g.steps
                if (g.hit(Drum.HH, st) || g.hit(Drum.HF, st)) return k * sf
            }
            return Int.MAX_VALUE
        }
        for (s in 0 until g.steps) {
            Drum.entries.forEach { d ->
                val ch = g.at(d, s)
                if (ch !in HIT_CHARS) return@forEach
                val real = DrumKit.hit(d, ch)
                if (real != null) {
                    val (smp, gain) = real
                    if (d == Drum.HO) addCut(smp, s * sf, gain, hatChoke(s)) else add(smp, s * sf, gain)
                } else {
                    // riserva: suono sintetico
                    add(sound(d), s * sf, when (ch) { 'A' -> 1.0f; 'g' -> 0.22f; else -> 0.7f })
                }
            }
            if (withClick && s % g.m.group == 0) add(click(s % g.m.steps == 0), s * sf, 1f)
        }
        var peak = 0f
        for (v in mix) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        val norm = if (peak > 0.95f) 0.95f / peak else 1f
        return ShortArray(total) { (mix[it] * norm * Short.MAX_VALUE).toInt().toShort() }
    }
}

/** Riproduzione in loop con AudioTrack statico. */
class LoopPlayer {
    private var track: AudioTrack? = null
    var frames = 0
        private set

    fun play(pcm: ShortArray) {
        stop()
        if (pcm.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(DrumSynth.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, pcm.size, -1)
        t.play()
        frames = pcm.size
        track = t
    }

    /** Casella in riproduzione, o -1 se fermo. */
    fun position(stepFrames: Int): Int {
        val t = track ?: return -1
        if (frames == 0 || stepFrames <= 0) return -1
        return (t.playbackHeadPosition % frames) / stepFrames
    }

    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        frames = 0
    }
}
