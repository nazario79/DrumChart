package com.nazario.drumchart

import android.app.Activity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

private data class LiveEntry(
    val song: Song,
    val item: SetItem?,
    val next: Song?,
    val linkedToNext: Boolean,
    val inMedley: Boolean,
    val medleyTitle: String = "",
    val medleySongs: List<String> = emptyList(),
    val medleyPos: Int = -1,
)

private data class LivePal(
    val bg: Color, val fg: Color, val muted: Color, val faint: Color, val chip: Color,
    val panel: Color, val accent: Color, val staffBg: Color, val staffFg: Color,
    val slower: Color, val faster: Color,
)

private val DarkPal = LivePal(
    Color.Black, Color.White, Color.Gray, Color.DarkGray, Color(0xFF222222),
    Color(0xFF1E1808), Amber, Color(0xFF111111), Color.White,
    Color(0xFF64B5F6), Color(0xFFFF8A65),
)
private val LightPal = LivePal(
    Color.White, Color.Black, Color(0xFF505050), Color(0xFF9E9E9E), Color(0xFFE6E6E6),
    Color(0xFFFFF3D6), Color(0xFFB26A00), Color.White, Color.Black,
    Color(0xFF1565C0), Color(0xFFD84315),
)

/** Una pagina della modalità live: un brano singolo oppure un medley intero. */
private sealed interface LPage {
    data class Single(val index: Int, val e: LiveEntry) : LPage
    data class Medley(val title: String, val first: Int, val items: List<LiveEntry>) : LPage
}

private fun buildPages(entries: List<LiveEntry>): List<LPage> {
    val out = mutableListOf<LPage>()
    var i = 0
    while (i < entries.size) {
        val e = entries[i]
        if (e.inMedley && e.medleyPos == 0) {
            val n = e.medleySongs.size.coerceAtLeast(1)
            out += LPage.Medley(e.medleyTitle, i, entries.subList(i, (i + n).coerceAtMost(entries.size)).toList())
            i += n
        } else {
            out += LPage.Single(i, e)
            i++
        }
    }
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(setlistId: String?, songId: String?, nav: Nav) {
    val setlist = Repo.setlist(setlistId)
    val entries = remember(setlistId, songId, Repo.data) {
        if (setlist != null) {
            val pairs = setlist.items.mapNotNull { it -> Repo.song(it.songId)?.let { s -> it to s } }
            val chains = medleyChains(pairs.map { it.first })
            pairs.mapIndexed { i, (item, song) ->
                val next = pairs.getOrNull(i + 1)?.second
                val linked = chains.linksNext(i)
                val chain = chains.find { i in it }
                if (chain != null) {
                    LiveEntry(
                        song, item, next, linked, true,
                        medleyTitle = pairs[chain.first()].first.medleyTitle.ifBlank { "Medley" },
                        medleySongs = chain.map { pairs[it].second.title },
                        medleyPos = chain.indexOf(i),
                    )
                } else {
                    LiveEntry(song, item, next, linked, false)
                }
            }
        } else {
            listOfNotNull(Repo.song(songId)?.let { LiveEntry(it, null, null, false, false) })
        }
    }

    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    val pal = if (Repo.liveLight) LightPal else DarkPal

    // Modalità live: schermo sempre acceso e barre di sistema nascoste
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val ctrl = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.Center) {
            TextButton(onClick = { nav.back() }) { Text("Nessun brano. Torna indietro") }
        }
        return
    }

    val pages = remember(entries) { buildPages(entries) }
    val listStates = remember(pages) { pages.map { LazyListState() } }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }

    /** Avanti/indietro: dentro un medley scorre al brano successivo, poi passa alla pagina dopo. */
    fun step(delta: Int) {
        val p = pager.currentPage
        val page = pages.getOrNull(p) ?: return
        if (page is LPage.Medley) {
            val target = listStates[p].firstVisibleItemIndex + delta
            if (target in page.items.indices) {
                scope.launch { listStates[p].animateScrollToItem(target) }
                return
            }
        }
        val np = (p + delta).coerceIn(0, pages.lastIndex)
        if (np == p) return
        scope.launch {
            val nextPage = pages[np]
            if (nextPage is LPage.Medley) listStates[np].scrollToItem(if (delta > 0) 0 else nextPage.items.lastIndex)
            pager.animateScrollToPage(np)
        }
    }

    // Pedale Bluetooth / tastiera
    DisposableEffect(pages) {
        val main = activity as? MainActivity
        main?.keyListener = { code ->
            when (code) {
                in MainActivity.NEXT_KEYS -> { step(1); true }
                in MainActivity.PREV_KEYS -> { step(-1); true }
                else -> false
            }
        }
        onDispose { main?.keyListener = null }
    }

    val scale = Repo.liveScale
    val total = entries.size
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { p ->
            when (val page = pages[p]) {
                is LPage.Single -> SinglePage(page.e, page.index, total, setlist?.name, scale, pal)
                is LPage.Medley -> MedleyPage(page, listStates[p], total, setlist?.name, scale, pal)
            }
        }
        // Comandi piccoli in alto a destra
        Row(
            Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entries.size > 1) {
                TextButton(onClick = { jump = true }) { Text("☰", color = pal.muted, fontSize = 22.sp) }
            }
            TextButton(onClick = { Repo.toggleLiveLight() }) {
                Text(if (Repo.liveLight) "☾" else "☀", color = pal.muted, fontSize = 20.sp)
            }
            TextButton(onClick = { Repo.changeLiveScale(scale - 0.1f) }) { Text("A−", color = pal.muted, fontSize = 18.sp) }
            TextButton(onClick = { Repo.changeLiveScale(scale + 0.1f) }) { Text("A+", color = pal.muted, fontSize = 18.sp) }
            IconButton(onClick = { nav.back() }) { Icon(Icons.Default.Close, "Esci", tint = pal.muted) }
        }
    }

    if (jump) {
        // brano attuale (indice in entries)
        val curPage = pages[pager.currentPage]
        val curEntry = when (curPage) {
            is LPage.Single -> curPage.index
            is LPage.Medley -> curPage.first + listStates[pager.currentPage].firstVisibleItemIndex
                .coerceIn(0, curPage.items.lastIndex)
        }
        AlertDialog(
            onDismissRequest = { jump = false },
            title = { Text(setlist?.name?.ifBlank { null } ?: "Scaletta") },
            text = {
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    itemsIndexed(entries) { i, e ->
                        if (e.inMedley && e.medleyPos == 0) {
                            Text(e.medleyTitle.uppercase(), color = Amber, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                        val cur = i == curEntry
                        Text(
                            "${i + 1}. ${e.song.title}",
                            fontSize = 19.sp,
                            fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                            color = if (cur) Color.Black else Color.Unspecified,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (cur) Amber else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable {
                                    jump = false
                                    val pIdx = pages.indexOfFirst { pg ->
                                        when (pg) {
                                            is LPage.Single -> pg.index == i
                                            is LPage.Medley -> i in pg.first until pg.first + pg.items.size
                                        }
                                    }.coerceAtLeast(0)
                                    scope.launch {
                                        val pg = pages[pIdx]
                                        if (pg is LPage.Medley) listStates[pIdx].scrollToItem(i - pg.first)
                                        pager.scrollToPage(pIdx)
                                    }
                                }
                                .padding(start = if (e.inMedley) 18.dp else 6.dp, top = 9.dp, bottom = 9.dp, end = 6.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { jump = false }) { Text("Chiudi") } },
        )
    }
}

@Composable
private fun SinglePage(e: LiveEntry, index: Int, total: Int, setName: String?, scale: Float, pal: LivePal) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val pos = if (total > 1) "${index + 1}/$total" else ""
        Text(
            listOfNotNull(pos.ifBlank { null }, setName?.ifBlank { null }).joinToString("  ·  "),
            color = pal.muted, fontSize = 15.sp,
        )
        SongHeader(e.song, scale, pal, big = true, endPad = 260.dp)
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState())) {
            SongStructure(e.song, scale, pal)
        }
        NextInfo(e, total, scale, pal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedleyPage(page: LPage.Medley, ls: LazyListState, total: Int, setName: String?, scale: Float, pal: LivePal) {
    fun s(v: Float): TextUnit = (v * scale).sp
    val scope = rememberCoroutineScope()
    val cur by remember(ls) { derivedStateOf { ls.firstVisibleItemIndex.coerceIn(0, page.items.lastIndex) } }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val from = page.first + 1
        val to = page.first + page.items.size
        Text(
            listOfNotNull("Brani $from–$to di $total", setName?.ifBlank { null }).joinToString("  ·  "),
            color = pal.muted, fontSize = 15.sp,
        )
        Text(
            page.title.uppercase(), color = pal.accent, fontSize = s(30f), fontWeight = FontWeight.Black,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 260.dp),
        )
        // elenco dei brani del medley: tocca per andare al brano
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        ) {
            page.items.forEachIndexed { k, e ->
                val isCur = k == cur
                Text(
                    "${k + 1}. ${e.song.title}",
                    color = if (isCur) Color.Black else if (k < cur) pal.faint else pal.fg,
                    fontSize = s(16f),
                    fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (isCur) Amber else pal.chip, RoundedCornerShape(4.dp))
                        .clickable { scope.launch { ls.animateScrollToItem(k) } }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (k < page.items.lastIndex) Text("›", color = pal.muted, fontSize = s(16f))
            }
        }
        HorizontalDivider(color = pal.faint.copy(alpha = 0.5f))

        // brani uno sotto l'altro, scorrimento verticale
        LazyColumn(state = ls, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(page.items) { k, e ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                    Box(
                        Modifier.width(5.dp).heightIn(min = 60.dp)
                            .background(if (k == cur) Amber else pal.faint.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${page.first + k + 1}/$total", color = pal.muted, fontSize = 14.sp)
                        SongHeader(e.song, scale, pal, big = false, endPad = 0.dp)
                        Spacer(Modifier.height(8.dp))
                        SongStructure(e.song, scale, pal)
                        NextInfo(e, total, scale, pal)
                    }
                }
            }
            // spazio finale: anche l'ultimo brano può arrivare in cima allo schermo
            item { Spacer(Modifier.fillParentMaxHeight(0.7f)) }
        }
    }
}

@Composable
private fun SongHeader(song: Song, scale: Float, pal: LivePal, big: Boolean, endPad: Dp) {
    fun s(v: Float): TextUnit = (v * scale).sp
    Text(
        song.title, color = pal.fg, fontSize = s(if (big) 40f else 32f), fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = s(if (big) 44f else 36f),
        modifier = Modifier.padding(end = endPad),
    )
    if (song.artist.isNotBlank()) Text(song.artist, color = pal.muted, fontSize = s(18f))
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        song.bpm?.let {
            Text("♩ $it", color = pal.accent, fontSize = s(if (big) 30f else 26f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.widthIn(min = 20.dp).padding(horizontal = 10.dp))
        }
        if (song.attacco.isNotBlank()) {
            Text(song.attacco, color = pal.fg, fontSize = s(if (big) 24f else 22f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongStructure(song: Song, scale: Float, pal: LivePal) {
    fun s(v: Float): TextUnit = (v * scale).sp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        song.sections.forEachIndexed { i, sec ->
            SectionBlock(sec, song.bpm, scale, pal)
            if (i < song.sections.lastIndex) {
                Text("›", color = pal.faint, fontSize = s(34f), modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
    if (song.note.isNotBlank()) {
        Text(song.note, color = pal.fg, fontSize = s(20f), modifier = Modifier.padding(top = 14.dp))
    }
}

/** Raccordo verso il brano successivo del medley, oppure "Prossimo: ...". */
@Composable
private fun NextInfo(e: LiveEntry, total: Int, scale: Float, pal: LivePal) {
    fun s(v: Float): TextUnit = (v * scale).sp
    val next = e.next
    if (e.linkedToNext && next != null) {
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp)
                .border(BorderStroke(2.dp, pal.accent), RoundedCornerShape(10.dp))
                .background(pal.panel, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("RACCORDO → ${next.title}", color = pal.accent, fontSize = s(24f), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                bpmChange(e.song.bpm, next.bpm)?.let {
                    val from = e.song.bpm ?: 0
                    val to = next.bpm ?: 0
                    val c = when {
                        to < from -> pal.slower
                        to > from -> pal.faster
                        else -> pal.muted
                    }
                    Text(it.text, color = c, fontSize = s(24f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.padding(horizontal = 12.dp))
                }
                val counts = e.item?.counts.orEmpty()
                if (counts.isNotBlank()) Text(counts, color = pal.fg, fontSize = s(24f), fontWeight = FontWeight.Bold)
            }
            val tn = e.item?.transNote.orEmpty()
            if (tn.isNotBlank()) Text(tn, color = pal.fg, fontSize = s(19f))
        }
    } else if (next != null) {
        Text(
            "Prossimo: ${next.title}" + (next.bpm?.let { "  ·  ♩ $it" } ?: ""),
            color = pal.muted, fontSize = s(18f), modifier = Modifier.padding(top = 10.dp),
        )
    } else if (total > 1) {
        Text("Fine scaletta", color = pal.faint, fontSize = s(16f), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun SectionBlock(sec: Section, songBpm: Int?, scale: Float, pal: LivePal) {
    fun s(v: Float): TextUnit = (v * scale).sp
    val bg = sectionColor(sec.name)
    val fg = sectionTextColor(sec.name)
    Column(
        Modifier.background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .widthIn(min = (70 * scale).dp, max = if (sec.groove != null) Dp.Unspecified else (320 * scale).dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(sec.name.ifBlank { "—" }.uppercase(), color = fg, fontSize = s(22f), fontWeight = FontWeight.Bold)
            Text("  ${sec.bars}", color = fg, fontSize = s(28f), fontWeight = FontWeight.Black)
            if (sec.repeat > 1) Text(" ×${sec.repeat}", color = fg, fontSize = s(24f), fontWeight = FontWeight.Bold)
        }
        // cambio di BPM o di tempo solo per questa sezione
        val extra = listOfNotNull(
            sec.bpm?.takeIf { it != songBpm }?.let { b ->
                "♩ $b" + when {
                    songBpm == null -> ""
                    b < songBpm -> " ▼"
                    else -> " ▲"
                }
            },
            sec.meter.ifBlank { null },
        )
        if (extra.isNotEmpty()) {
            Text(
                extra.joinToString("   "), color = Color.Black, fontSize = s(18f), fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 2.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
            )
        }
        if (sec.note.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(sec.note, color = fg.copy(alpha = 0.9f), fontSize = s(16f))
        }
        sec.groove?.let { gr ->
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.background(pal.staffBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp)
            ) { GrooveStaff(gr, 0.9f * scale, pal.staffFg) }
        }
    }
}
