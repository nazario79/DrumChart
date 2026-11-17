package com.nazario.drumchart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

/**
 * Tempo della griglia.
 * steps = caselle per battuta, group = caselle per movimento,
 * unit = valore di una casella: 16 = sedicesimo, 8 = ottavo, 3 = ottavo di terzina.
 */
enum class GridMeter(
    val key: String, val label: String, val top: Int, val bottom: Int,
    val steps: Int, val group: Int, val unit: Int, val slot: Float,
) {
    M44("4/4", "4/4", 4, 4, 16, 4, 16, 2.4f),
    M44T("4/4T", "4/4 terzine", 4, 4, 12, 3, 3, 3.0f),
    M34("3/4", "3/4", 3, 4, 12, 4, 16, 2.4f),
    M68("6/8", "6/8", 6, 8, 6, 3, 8, 3.6f),
    M128("12/8", "12/8", 12, 8, 12, 3, 8, 3.2f);

    companion object {
        fun of(key: String?) = entries.find { it.key == key } ?: M44
    }
}

/** Colpo: 'x' normale, 'A' accentato, 'g' ghost note. */
const val HIT_CHARS = "xAg"

/** Una o due battute: per ogni strumento una stringa con un carattere per casella ('.' = vuoto). */
data class Groove(
    val bars: Int = 1,
    val rows: Map<String, String> = emptyMap(),
    val meter: String = "4/4",
) {
    val m: GridMeter get() = GridMeter.of(meter)
    val steps: Int get() = bars * m.steps

    fun at(d: Drum, step: Int): Char = rows[d.key]?.getOrNull(step) ?: '.'
    fun hit(d: Drum, step: Int): Boolean = at(d, step) in HIT_CHARS

    /** Tocco sulla casella: vuoto → colpo → accento → ghost → vuoto. */
    fun cycle(d: Drum, step: Int): Groove {
        val c = (rows[d.key] ?: "").padEnd(steps, '.').take(steps).toCharArray()
        c[step] = when (c[step]) { '.' -> 'x'; 'x' -> 'A'; 'A' -> 'g'; else -> '.' }
        return copy(rows = rows + (d.key to String(c)))
    }

    fun withBars(n: Int): Groove = copy(
        bars = n,
        rows = rows.mapValues { it.value.padEnd(n * m.steps, '.').take(n * m.steps) },
    )

    /** Cambiando tempo la griglia si svuota (le caselle non corrispondono più). */
    fun withMeter(key: String): Groove = if (key == meter) this else Groove(bars, emptyMap(), key)

    fun isEmpty(): Boolean = rows.values.none { r -> r.any { it in HIT_CHARS } }

    fun toJson(): JSONObject {
        val r = JSONObject()
        rows.forEach { (k, v) -> if (v.any { it in HIT_CHARS }) r.put(k, v) }
        return JSONObject().put("bars", bars).put("meter", meter).put("rows", r)
    }
}

fun grooveFromJson(o: JSONObject): Groove {
    val r = o.optJSONObject("rows") ?: JSONObject()
    val map = mutableMapOf<String, String>()
    r.keys().forEach { k -> map[k] = r.optString(k, "") }
    return Groove(o.optInt("bars", 1).coerceIn(1, 2), map, o.optString("meter", "4/4").ifBlank { "4/4" })
}

// ---------------- Disegno sul pentagramma ----------------

private const val BAR_PAD = 1.6f   // margine dentro la battuta (in spazi del rigo)
private const val HEADER = 5.6f    // chiave + indicazione di tempo
private const val TOP = 6.0f       // margine sopra il rigo
private const val HEIGHT = 14.5f

private fun barWidth(m: GridMeter) = m.steps * m.slot + 2 * BAR_PAD

@Composable
fun GrooveStaff(g: Groove, scale: Float = 1f, color: Color = Color.White, modifier: Modifier = Modifier) {
    val ls = 7.5f * scale // spazio fra le linee, in dp
    val w = (HEADER + g.bars * barWidth(g.m) + 0.5f) * ls
    val h = HEIGHT * ls
    Canvas(modifier.size(w.dp, h.dp)) { drawGroove(g, ls.dp.toPx(), color) }
}

/** Valore di una nota che dura d caselle: (numero di travi/codette, puntata). */
private fun noteValue(unit: Int, d: Int): Pair<Int, Boolean> = when (unit) {
    16 -> when (d) { 1 -> 2 to false; 2 -> 1 to false; 3 -> 1 to true; else -> 0 to false }
    8 -> when (d) { 1 -> 1 to false; 2 -> 0 to false; else -> 0 to true }
    else -> when (d) { 1 -> 1 to false; else -> 0 to false } // terzine
}

private class N(val step: Int, val hits: List<Drum>, val beams: Int, val dotted: Boolean) {
    var stemX = 0f
    var anchor = 0f
    var far = 0f
}

private fun DrawScope.drawGroove(g: Groove, L: Float, color: Color) {
    val m = g.m
    val staffTop = TOP * L
    fun y(pos: Int) = staffTop + (8 - pos) * L / 2f
    val line = 0.12f * L
    val bw = barWidth(m)
    val totalW = (HEADER + g.bars * bw) * L

    // rigo
    for (i in 0..4) drawLine(color, Offset(0f, staffTop + i * L), Offset(totalW, staffTop + i * L), line)
    // chiave di percussioni
    drawRect(color, Offset(0.9f * L, y(6)), Size(0.4f * L, y(2) - y(6)))
    drawRect(color, Offset(1.7f * L, y(6)), Size(0.4f * L, y(2) - y(6)))
    // indicazione di tempo
    val num = Paint().apply {
        this.color = color.toArgb(); textSize = 2.3f * L; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    drawIntoCanvas {
        val cx = 4.0f * L
        it.nativeCanvas.drawText("${m.top}", cx, y(4) - 0.15f * L, num)
        it.nativeCanvas.drawText("${m.bottom}", cx, y(0) - 0.15f * L, num)
    }
    // stanghette
    for (b in 1..g.bars) {
        val x = (HEADER + b * bw) * L
        drawLine(color, Offset(x, y(8)), Offset(x, y(0)), if (b == g.bars) 0.3f * L else line * 1.5f)
    }

    val hw = 1.3f * L
    val stemLen = 3.4f * L
    val stemW = 0.13f * L
    val beamH = 0.45f * L
    val beamGap = 0.8f * L

    fun noteX(step: Int): Float {
        val bar = step / m.steps
        val inBar = step % m.steps
        return (HEADER + bar * bw + BAR_PAD + inBar * m.slot) * L + hw / 2
    }

    fun head(cx: Float, d: Drum, ch: Char) {
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
        if (d == Drum.HO) drawCircle(color, 0.38f * L, Offset(cx, y(13)), style = Stroke(0.14f * L))
        if (ch == 'g') {
            // ghost note: parentesi intorno alla testa
            val r = Size(0.9f * L, 1.5f * L)
            drawArc(color, 100f, 160f, false, Offset(cx - hw / 2 - 0.75f * L, cy - 0.75f * L), r, style = Stroke(0.14f * L))
            drawArc(color, -80f, 160f, false, Offset(cx + hw / 2 - 0.15f * L, cy - 0.75f * L), r, style = Stroke(0.14f * L))
        }
    }

    fun dot(cx: Float, d: Drum) {
        val p = if (d.pos % 2 == 0) d.pos + 1 else d.pos
        drawCircle(color, 0.2f * L, Offset(cx + hw * 0.95f, y(p)))
    }

    fun accent(cx: Float, yc: Float) {
        val w = 0.65f * L
        val h = 0.38f * L
        drawLine(color, Offset(cx - w, yc - h), Offset(cx + w, yc), 0.16f * L, StrokeCap.Round)
        drawLine(color, Offset(cx + w, yc), Offset(cx - w, yc + h), 0.16f * L, StrokeCap.Round)
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

    /** kind: 4 = semiminima, 8 = croma, 16 = semicroma. */
    fun rest(x: Float, kind: Int, dotted: Boolean) {
        val mid = staffTop + 2 * L
        if (kind == 4) {
            val p = Path().apply {
                moveTo(x - 0.2f * L, mid - 1.5f * L)
                lineTo(x + 0.55f * L, mid - 0.6f * L)
                lineTo(x - 0.15f * L, mid + 0.1f * L)
                lineTo(x + 0.5f * L, mid + 0.9f * L)
                quadraticBezierTo(x - 0.5f * L, mid + 0.6f * L, x + 0.1f * L, mid + 1.6f * L)
            }
            drawPath(p, color, style = Stroke(0.3f * L, cap = StrokeCap.Round))
            if (dotted) drawCircle(color, 0.2f * L, Offset(x + 1.0f * L, mid - 0.5f * L))
        } else {
            val n = if (kind == 16) 2 else 1
            val top = mid - 0.6f * L
            drawLine(color, Offset(x + 0.45f * L, top), Offset(x - 0.2f * L, top + (1.6f + n) * L), 0.18f * L)
            for (k in 0 until n) drawCircle(color, 0.25f * L, Offset(x - 0.15f * L, top + 0.15f * L + k * L))
            if (dotted) drawCircle(color, 0.18f * L, Offset(x + 0.9f * L, top + 0.4f * L))
        }
    }

    /** Pausa iniziale lunga r caselle (o tutto il movimento). */
    fun leadingRest(x: Float, r: Int) {
        val full = r >= m.group
        when (m.unit) {
            16 -> when (r) { 1 -> rest(x, 16, false); 2 -> rest(x, 8, false); 3 -> rest(x, 8, true); else -> rest(x, 4, false) }
            8 -> if (full) rest(x, 4, true) else if (r == 1) rest(x, 8, false) else rest(x, 4, false)
            else -> if (full) rest(x, 4, false) else if (r == 1) rest(x, 8, false) else rest(x, 4, false)
        }
    }

    val text3 = Paint().apply {
        this.color = color.toArgb(); textSize = 1.6f * L; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        isAntiAlias = true; textAlign = Paint.Align.CENTER
    }

    val groupsPerBar = m.steps / m.group
    for (bar in 0 until g.bars) for (grp in 0 until groupsPerBar) {
        val base = bar * m.steps + grp * m.group
        var tripletShown = false
        for (up in listOf(true, false)) {
            val drums = Drum.entries.filter { it.foot != up }
            val onsets = (0 until m.group).filter { s -> drums.any { g.hit(it, base + s) } }
            if (onsets.isEmpty()) {
                if (up) leadingRest(noteX(base), m.group)
                continue
            }
            if (up && onsets[0] > 0) leadingRest(noteX(base), onsets[0])

            val notes = onsets.mapIndexed { i, s ->
                val d = (onsets.getOrNull(i + 1) ?: m.group) - s
                val (beams, dotted) = noteValue(m.unit, d)
                N(base + s, drums.filter { g.hit(it, base + s) }, beams, dotted)
            }
            // teste, punti, gambi provvisori
            notes.forEach { n ->
                val cx = noteX(n.step)
                n.hits.forEach { head(cx, it, g.at(it, n.step)) }
                if (n.dotted) dot(cx, n.hits.first())
                n.stemX = if (up) cx + hw / 2 - stemW / 2 else cx - hw / 2 + stemW / 2
                n.anchor = if (up) y(n.hits.minOf { it.pos }) else y(n.hits.maxOf { it.pos })
                n.far = if (up) y(n.hits.maxOf { it.pos }) - stemLen else y(n.hits.minOf { it.pos }) + stemLen
            }
            // gruppi di note con travi consecutive
            val runs = mutableListOf<MutableList<N>>()
            notes.forEach { n ->
                if (n.beams == 0) runs += mutableListOf(n)
                else if (runs.isNotEmpty() && runs.last().last().beams > 0) runs.last() += n
                else runs += mutableListOf(n)
            }
            var groupEdge = Float.NaN
            runs.forEach { run ->
                if (run.size == 1) {
                    val n = run[0]
                    drawLine(color, Offset(n.stemX, n.anchor), Offset(n.stemX, n.far), stemW)
                    if (n.beams > 0) flags(n.stemX, n.far, up, n.beams)
                    groupEdge = if (groupEdge.isNaN()) n.far else if (up) minOf(groupEdge, n.far) else maxOf(groupEdge, n.far)
                } else {
                    val beamY = if (up) run.minOf { it.far } else run.maxOf { it.far }
                    run.forEach { n -> drawLine(color, Offset(n.stemX, n.anchor), Offset(n.stemX, beamY), stemW) }
                    val fx = run.first().stemX
                    val lx = run.last().stemX
                    val by = if (up) beamY else beamY - beamH
                    drawRect(color, Offset(fx - stemW / 2, by), Size(lx - fx + stemW, beamH))
                    val y2 = if (up) by + beamGap else by - beamGap
                    for (i in run.indices) {
                        if (run[i].beams < 2) continue
                        val x = run[i].stemX
                        val next16 = i + 1 < run.size && run[i + 1].beams >= 2
                        val prev16 = i > 0 && run[i - 1].beams >= 2
                        when {
                            next16 -> drawRect(color, Offset(x - stemW / 2, y2), Size(run[i + 1].stemX - x + stemW, beamH))
                            prev16 -> Unit
                            i + 1 < run.size -> drawRect(color, Offset(x - stemW / 2, y2), Size(0.9f * L, beamH))
                            else -> drawRect(color, Offset(x - 0.9f * L, y2), Size(0.9f * L + stemW / 2, beamH))
                        }
                    }
                    groupEdge = if (groupEdge.isNaN()) beamY else if (up) minOf(groupEdge, beamY) else maxOf(groupEdge, beamY)
                }
                // accenti
                run.forEach { n ->
                    if (n.hits.any { g.at(it, n.step) == 'A' }) {
                        val cx = noteX(n.step)
                        val edge = if (run.size > 1) (if (up) run.minOf { it.far } else run.maxOf { it.far }) else n.far
                        accent(cx, if (up) minOf(edge, y(10)) - 1.0f * L else maxOf(edge, y(-2)) + 1.0f * L)
                    }
                }
            }
            // "3" sopra le terzine
            if (m.unit == 3 && up && !tripletShown && !(onsets.size == 1 && onsets[0] == 0)) {
                val cx = (noteX(base) + noteX(base + m.group - 1)) / 2 + hw / 2
                val ty = minOf(groupEdge, y(10)) - 1.6f * L
                drawIntoCanvas { it.nativeCanvas.drawText("3", cx, ty, text3) }
                tripletShown = true
            }
        }
    }
}

// ---------------- Editor a griglia ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GrooveEditor(initial: Groove?, title: String, onDismiss: () -> Unit, onDone: (Groove?) -> Unit) {
    var g by remember { mutableStateOf(initial ?: Groove()) }
    var askMeter by remember { mutableStateOf<String?>(null) }
    val hScroll = rememberScrollState()
    val cell = 38.dp
    val m = g.m

    fun gapBefore(step: Int) = when {
        step == 0 -> 0.dp
        step % m.steps == 0 -> 14.dp
        step % m.group == 0 -> 6.dp
        else -> 0.dp
    }

    fun countLabel(s: Int): String {
        val inBar = s % m.steps
        val off = inBar % m.group
        return when {
            m.unit == 8 -> "${inBar + 1}"
            off == 0 -> "${inBar / m.group + 1}"
            m.unit == 16 && off == 2 -> "e"
            else -> ""
        }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GridMeter.entries.forEach { gm ->
                        FilterChip(
                            selected = m == gm,
                            onClick = {
                                if (gm != m) { if (g.isEmpty()) g = g.withMeter(gm.key) else askMeter = gm.key }
                            },
                            label = { Text(gm.label) },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = g.bars == 1, onClick = { g = g.withBars(1) }, label = { Text("1 battuta") })
                    FilterChip(selected = g.bars == 2, onClick = { g = g.withBars(2) }, label = { Text("2 battute") })
                    TextButton(onClick = { g = Groove(g.bars, emptyMap(), g.meter) }) { Text("Cancella tutto") }
                }
                askMeter?.let { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cambiando tempo la griglia si svuota.", color = Color(0xFFFFB74D), modifier = Modifier.weight(1f))
                        TextButton(onClick = { askMeter = null }) { Text("No") }
                        TextButton(onClick = { g = g.withMeter(k); askMeter = null }) { Text("Cambia") }
                    }
                }
                // anteprima
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)
                ) { GrooveStaff(g, 1.1f) }

                Row(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column {
                        Spacer(Modifier.height(24.dp))
                        Drum.entries.forEach { d ->
                            Box(Modifier.height(cell).width(84.dp), contentAlignment = Alignment.CenterStart) {
                                Text(d.label, fontSize = 13.sp, color = Color.LightGray)
                            }
                        }
                    }
                    Column(Modifier.horizontalScroll(hScroll)) {
                        Row(Modifier.height(24.dp)) {
                            for (s in 0 until g.steps) {
                                Spacer(Modifier.width(gapBefore(s)))
                                Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                                    Text(countLabel(s), color = if (s % m.group == 0) Amber else Color.Gray,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Drum.entries.forEach { d ->
                            Row(Modifier.height(cell)) {
                                for (s in 0 until g.steps) {
                                    Spacer(Modifier.width(gapBefore(s)))
                                    val ch = g.at(d, s)
                                    Box(
                                        Modifier.size(cell).padding(2.dp)
                                            .background(
                                                when (ch) {
                                                    'x' -> Amber
                                                    'A' -> Color(0xFFFF7043)
                                                    'g' -> Color(0xFF8D7A55)
                                                    else -> if (s % m.group == 0) Color(0xFF3A3A3A) else Color(0xFF262626)
                                                },
                                                RoundedCornerShape(4.dp),
                                            )
                                            .clickable { g = g.cycle(d, s) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val t = when (ch) {
                                            'x' -> if (d.xHead) "×" else "●"
                                            'A' -> ">"
                                            'g' -> "( )"
                                            else -> ""
                                        }
                                        if (t.isNotEmpty()) Text(t, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "Tocca una casella: 1 volta colpo, 2 volte accento (>), 3 volte ghost note ( ), 4 volte cancella.",
                    color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
