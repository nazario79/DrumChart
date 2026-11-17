package com.nazario.drumchart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

// ---------------- Modello ----------------

/**
 * Strumenti della griglia, dall'alto verso il basso come sul pentagramma.
 * pos = posizione sul rigo (0 = prima linea in basso, 8 = linea in alto, ogni unità mezzo spazio).
 */
enum class Drum(val key: String, val label: String, val pos: Int, val xHead: Boolean, val foot: Boolean) {
    CR("CR", "Crash", 10, true, false),
    RD("RD", "Ride", 8, true, false),
    HH("HH", "Hi-hat", 9, true, false),
    HO("HO", "HH aperto", 9, true, false),
    T1("T1", "Tom 1", 7, false, false),
    T2("T2", "Tom 2", 6, false, false),
    SN("SN", "Rullante", 5, false, false),
    FT("FT", "Timpano", 3, false, false),
    BD("BD", "Cassa", 1, false, true),
    HF("HF", "HH pedale", -1, true, true),
}

/** Una o due battute in 4/4 scritte a sedicesimi: per ogni strumento una stringa di 'x' e '.'. */
data class Groove(val bars: Int = 1, val rows: Map<String, String> = emptyMap()) {
    val steps: Int get() = bars * 16

    fun hit(d: Drum, step: Int): Boolean = rows[d.key]?.getOrNull(step) == 'x'

    fun toggle(d: Drum, step: Int): Groove {
        val c = (rows[d.key] ?: "").padEnd(steps, '.').take(steps).toCharArray()
        c[step] = if (c[step] == 'x') '.' else 'x'
        return copy(rows = rows + (d.key to String(c)))
    }

    fun withBars(n: Int): Groove = copy(
        bars = n,
        rows = rows.mapValues { it.value.padEnd(n * 16, '.').take(n * 16) },
    )

    fun isEmpty(): Boolean = rows.values.none { 'x' in it }

    fun toJson(): JSONObject {
        val r = JSONObject()
        rows.forEach { (k, v) -> if ('x' in v) r.put(k, v) }
        return JSONObject().put("bars", bars).put("rows", r)
    }
}

fun grooveFromJson(o: JSONObject): Groove {
    val r = o.optJSONObject("rows") ?: JSONObject()
    val map = mutableMapOf<String, String>()
    r.keys().forEach { k -> map[k] = r.optString(k, "") }
    return Groove(o.optInt("bars", 1).coerceIn(1, 2), map)
}

// ---------------- Disegno sul pentagramma ----------------

private const val SLOT = 2.4f      // larghezza di un sedicesimo, in spazi del rigo
private const val BAR_PAD = 1.6f   // margine dentro la battuta
private const val HEADER = 3.2f    // spazio per la chiave
private const val TOP = 5.5f       // margine sopra il rigo
private const val HEIGHT = 13.5f

private fun barWidth() = 16 * SLOT + 2 * BAR_PAD

@Composable
fun GrooveStaff(g: Groove, scale: Float = 1f, color: Color = Color.White, modifier: Modifier = Modifier) {
    val ls = 7.5f * scale // spazio fra le linee, in dp
    val w = (HEADER + g.bars * barWidth() + 0.5f) * ls
    val h = HEIGHT * ls
    Canvas(modifier.size(w.dp, h.dp)) { drawGroove(g, ls.dp.toPx(), color) }
}

private fun DrawScope.drawGroove(g: Groove, L: Float, color: Color) {
    val staffTop = TOP * L
    fun y(pos: Int) = staffTop + (8 - pos) * L / 2f
    val line = 0.12f * L
    val totalW = (HEADER + g.bars * barWidth()) * L

    // rigo
    for (i in 0..4) drawLine(color, Offset(0f, staffTop + i * L), Offset(totalW, staffTop + i * L), line)
    // chiave di percussioni
    drawRect(color, Offset(0.9f * L, y(6)), Size(0.4f * L, y(2) - y(6)))
    drawRect(color, Offset(1.7f * L, y(6)), Size(0.4f * L, y(2) - y(6)))
    // stanghette
    for (b in 0..g.bars) {
        val x = (HEADER + b * barWidth()) * L
        if (b == 0) continue
        drawLine(color, Offset(x, y(8)), Offset(x, y(0)), if (b == g.bars) 0.3f * L else line * 1.5f)
    }

    val hw = 1.3f * L          // larghezza della testa
    val stemLen = 3.4f * L
    val stemW = 0.13f * L
    val beamH = 0.45f * L
    val beamGap = 0.8f * L

    fun noteX(step: Int): Float {
        val bar = step / 16
        val inBar = step % 16
        return (HEADER + bar * barWidth() + BAR_PAD + inBar * SLOT) * L + hw / 2
    }

    fun head(cx: Float, d: Drum) {
        val cy = y(d.pos)
        if (d.pos >= 10) drawLine(color, Offset(cx - hw * 0.85f, y(10)), Offset(cx + hw * 0.85f, y(10)), line * 1.3f)
        if (d.xHead) {
            val a = hw * 0.42f
            val b = L * 0.42f
            drawLine(color, Offset(cx - a, cy - b), Offset(cx + a, cy + b), 0.2f * L, StrokeCap.Round)
            drawLine(color, Offset(cx - a, cy + b), Offset(cx + a, cy - b), 0.2f * L, StrokeCap.Round)
        } else {
            rotate(-20f, Offset(cx, cy)) {
                drawOval(color, Offset(cx - hw / 2, cy - L * 0.42f), Size(hw, L * 0.84f))
            }
        }
        if (d == Drum.HO) {
            drawCircle(color, 0.38f * L, Offset(cx, y(13)), style = Stroke(0.14f * L))
        }
    }

    fun dot(cx: Float, d: Drum) {
        val p = if (d.pos % 2 == 0) d.pos + 1 else d.pos // il punto va negli spazi
        drawCircle(color, 0.2f * L, Offset(cx + hw * 0.95f, y(p)))
    }

    fun flags(stemX: Float, endY: Float, up: Boolean, n: Int) {
        for (k in 0 until n) {
            val dir = if (up) 1f else -1f
            val y0 = endY + dir * k * beamGap
            val p = Path().apply {
                moveTo(stemX, y0)
                cubicTo(stemX + 0.2f * L, y0 + dir * 0.9f * L, stemX + 1.3f * L, y0 + dir * 1.2f * L, stemX + 0.9f * L, y0 + dir * 2.4f * L)
            }
            drawPath(p, color, style = Stroke(0.28f * L, cap = StrokeCap.Round))
        }
    }

    fun rest(x: Float, sixteenths: Int) {
        val mid = staffTop + 2 * L
        when (sixteenths) {
            4 -> {
                val p = Path().apply {
                    moveTo(x - 0.2f * L, mid - 1.5f * L)
                    lineTo(x + 0.55f * L, mid - 0.6f * L)
                    lineTo(x - 0.15f * L, mid + 0.1f * L)
                    lineTo(x + 0.5f * L, mid + 0.9f * L)
                    quadraticBezierTo(x - 0.5f * L, mid + 0.6f * L, x + 0.1f * L, mid + 1.6f * L)
                }
                drawPath(p, color, style = Stroke(0.3f * L, cap = StrokeCap.Round))
            }
            else -> {
                val n = if (sixteenths == 1) 2 else 1
                val top = mid - 0.6f * L
                drawLine(color, Offset(x + 0.45f * L, top), Offset(x - 0.2f * L, top + (1.6f + n) * L), 0.18f * L)
                for (k in 0 until n) drawCircle(color, 0.25f * L, Offset(x - 0.15f * L, top + 0.15f * L + k * L))
                if (sixteenths == 3) drawCircle(color, 0.18f * L, Offset(x + 0.9f * L, top + 0.4f * L))
            }
        }
    }

    for (bar in 0 until g.bars) for (beat in 0 until 4) {
        val base = bar * 16 + beat * 4
        for (up in listOf(true, false)) {
            val drums = Drum.entries.filter { it.foot != up }
            val onsets = (0..3).filter { s -> drums.any { g.hit(it, base + s) } }
            if (onsets.isEmpty()) {
                if (up) rest(noteX(base), 4)
                continue
            }
            if (up && onsets[0] > 0) rest(noteX(base), onsets[0])

            val durs = onsets.mapIndexed { i, s -> (onsets.getOrNull(i + 1) ?: 4) - s }
            val stems = mutableListOf<Pair<Float, Float>>() // x gambo, y estremo testa
            onsets.forEachIndexed { i, s ->
                val cx = noteX(base + s)
                val hits = drums.filter { g.hit(it, base + s) }
                hits.forEach { head(cx, it) }
                if (durs[i] == 3) dot(cx, hits.first())
                val sx = if (up) cx + hw / 2 - stemW / 2 else cx - hw / 2 + stemW / 2
                // up: testa più bassa (pos minore); down: testa più alta
                val anchor = if (up) y(hits.minOf { it.pos }) else y(hits.maxOf { it.pos })
                val farthest = if (up) y(hits.maxOf { it.pos }) else y(hits.minOf { it.pos })
                stems += sx to anchor
                // estremo del gambo singolo
                val end = if (up) farthest - stemLen else farthest + stemLen
                if (onsets.size == 1) {
                    drawLine(color, Offset(sx, anchor), Offset(sx, end), stemW)
                    when (durs[i]) {
                        1 -> flags(sx, end, up, 2)
                        2, 3 -> flags(sx, end, up, 1)
                    }
                }
            }
            if (onsets.size > 1) {
                // tutte le teste estreme per calcolare la trave piatta
                val ends = onsets.map { s ->
                    val hits = drums.filter { g.hit(it, base + s) }
                    if (up) y(hits.maxOf { it.pos }) - stemLen else y(hits.minOf { it.pos }) + stemLen
                }
                val beamY = if (up) ends.min() else ends.max()
                stems.forEach { (sx, anchor) -> drawLine(color, Offset(sx, anchor), Offset(sx, beamY), stemW) }
                val firstX = stems.first().first
                val lastX = stems.last().first
                val by = if (up) beamY else beamY - beamH
                drawRect(color, Offset(firstX - stemW / 2, by), Size(lastX - firstX + stemW, beamH))
                // seconda trave per i sedicesimi
                val y2 = if (up) by + beamGap else by - beamGap
                for (i in onsets.indices) {
                    if (durs[i] != 1) continue
                    val x = stems[i].first
                    val nextIs16 = i + 1 < onsets.size && durs[i + 1] == 1
                    val prevIs16 = i > 0 && durs[i - 1] == 1
                    when {
                        nextIs16 -> drawRect(color, Offset(x - stemW / 2, y2), Size(stems[i + 1].first - x + stemW, beamH))
                        prevIs16 -> Unit // già disegnata dal precedente
                        i + 1 < onsets.size -> drawRect(color, Offset(x - stemW / 2, y2), Size(0.9f * L, beamH))
                        else -> drawRect(color, Offset(x - 0.9f * L, y2), Size(0.9f * L + stemW / 2, beamH))
                    }
                }
            }
        }
    }
}

// ---------------- Editor a griglia ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrooveEditor(initial: Groove?, title: String, onDismiss: () -> Unit, onDone: (Groove?) -> Unit) {
    var g by remember { mutableStateOf(initial ?: Groove()) }
    val hScroll = rememberScrollState()
    val cell = 36.dp

    fun gapBefore(step: Int) = when {
        step == 0 -> 0.dp
        step % 16 == 0 -> 14.dp
        step % 4 == 0 -> 6.dp
        else -> 0.dp
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title.ifBlank { "Battuta" }, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Annulla") }
                    Button(onClick = { onDone(if (g.isEmpty()) null else g) }) { Text("Fatto") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = g.bars == 1, onClick = { g = g.withBars(1) }, label = { Text("1 battuta") })
                    FilterChip(selected = g.bars == 2, onClick = { g = g.withBars(2) }, label = { Text("2 battute") })
                    TextButton(onClick = { g = Groove(g.bars) }) { Text("Cancella tutto") }
                }
                // anteprima
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)
                ) { GrooveStaff(g, 1.1f) }

                Row(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // nomi strumenti
                    Column {
                        Spacer(Modifier.height(24.dp))
                        Drum.entries.forEach { d ->
                            Box(Modifier.height(cell).width(84.dp), contentAlignment = Alignment.CenterStart) {
                                Text(d.label, fontSize = 13.sp, color = Color.LightGray)
                            }
                        }
                    }
                    // griglia
                    Column(Modifier.horizontalScroll(hScroll)) {
                        Row(Modifier.height(24.dp)) {
                            for (s in 0 until g.steps) {
                                Spacer(Modifier.width(gapBefore(s)))
                                Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                                    val t = when (s % 4) { 0 -> "${(s % 16) / 4 + 1}"; 2 -> "e"; else -> "" }
                                    Text(t, color = if (s % 4 == 0) Amber else Color.Gray, fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Drum.entries.forEach { d ->
                            Row(Modifier.height(cell)) {
                                for (s in 0 until g.steps) {
                                    Spacer(Modifier.width(gapBefore(s)))
                                    val on = g.hit(d, s)
                                    Box(
                                        Modifier.size(cell).padding(2.dp)
                                            .background(
                                                when {
                                                    on -> Amber
                                                    s % 4 == 0 -> Color(0xFF3A3A3A)
                                                    else -> Color(0xFF262626)
                                                },
                                                RoundedCornerShape(4.dp),
                                            )
                                            .clickable { g = g.toggle(d, s) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (on) Text(if (d.xHead) "×" else "●", color = Color.Black, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "Ogni colonna è un sedicesimo: 1 e 2 e 3 e 4 e. Tocca le caselle per scrivere i colpi.",
                    color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
