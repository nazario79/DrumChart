package com.nazario.drumchart

import android.app.Activity
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
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(setlistId: String?, songId: String?, nav: Nav) {
    val setlist = Repo.setlist(setlistId)
    val entries = remember(setlistId, songId, Repo.data) {
        if (setlist != null) {
            val pairs = setlist.items.mapNotNull { it -> Repo.song(it.songId)?.let { s -> it to s } }
            pairs.mapIndexed { i, (item, song) ->
                val next = pairs.getOrNull(i + 1)?.second
                val linked = item.medley && next != null
                val prevLinked = i > 0 && pairs[i - 1].first.medley
                LiveEntry(song, item, next, linked, linked || prevLinked)
            }
        } else {
            listOfNotNull(Repo.song(songId)?.let { LiveEntry(it, null, null, false, false) })
        }
    }

    val view = LocalView.current
    val activity = LocalContext.current as? Activity

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
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            TextButton(onClick = { nav.back() }) { Text("Nessun brano. Torna indietro") }
        }
        return
    }

    val pager = rememberPagerState { entries.size }
    val scope = rememberCoroutineScope()

    // Pedale Bluetooth / tastiera: avanti e indietro tra i brani
    DisposableEffect(entries.size) {
        val main = activity as? MainActivity
        main?.keyListener = { code ->
            when (code) {
                in MainActivity.NEXT_KEYS -> {
                    scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(entries.lastIndex)) }; true
                }
                in MainActivity.PREV_KEYS -> {
                    scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true
                }
                else -> false
            }
        }
        onDispose { main?.keyListener = null }
    }

    val scale = Repo.liveScale
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            LivePage(entries[page], page, entries.size, setlist?.name, scale)
        }
        // Comandi piccoli in alto a destra: dimensione testo e uscita
        Row(
            Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { Repo.setLiveScale(scale - 0.1f) }) { Text("A−", color = Color.Gray, fontSize = 18.sp) }
            TextButton(onClick = { Repo.setLiveScale(scale + 0.1f) }) { Text("A+", color = Color.Gray, fontSize = 18.sp) }
            IconButton(onClick = { nav.back() }) { Icon(Icons.Default.Close, "Esci", tint = Color.Gray) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LivePage(e: LiveEntry, index: Int, total: Int, setName: String?, scale: Float) {
    fun s(v: Float): TextUnit = (v * scale).sp
    val song = e.song

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Riga info: posizione nella scaletta e medley
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pos = if (total > 1) "${index + 1}/$total" else ""
            Text(
                listOfNotNull(pos.ifBlank { null }, setName?.ifBlank { null }).joinToString("  ·  "),
                color = Color.Gray, fontSize = 15.sp,
            )
            if (e.inMedley) {
                Spacer(Modifier.widthIn(min = 10.dp))
                Text(
                    "MEDLEY", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).background(Amber, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }

        // Titolo
        Text(
            song.title, color = Color.White, fontSize = s(40f), fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = s(44f),
            modifier = Modifier.padding(end = 150.dp),
        )
        if (song.artist.isNotBlank()) Text(song.artist, color = Color.Gray, fontSize = s(18f))

        // BPM e attacco
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            song.bpm?.let {
                Text("♩ $it", color = Amber, fontSize = s(30f), fontWeight = FontWeight.Bold)
                Spacer(Modifier.widthIn(min = 20.dp).padding(horizontal = 10.dp))
            }
            if (song.attacco.isNotBlank()) {
                Text(song.attacco, color = Color.White, fontSize = s(24f), fontWeight = FontWeight.SemiBold)
            }
        }

        // Struttura
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState())) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                song.sections.forEachIndexed { i, sec ->
                    SectionBlock(sec, scale)
                    if (i < song.sections.lastIndex) {
                        Text("›", color = Color.DarkGray, fontSize = s(34f),
                            modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            if (song.note.isNotBlank()) {
                Text(song.note, color = Color(0xFFE0E0E0), fontSize = s(20f), modifier = Modifier.padding(top = 14.dp))
            }
        }

        // Raccordo medley o prossimo brano
        if (e.linkedToNext && e.next != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .border(BorderStroke(2.dp, Amber), RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1808), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("MEDLEY → ${e.next.title}", color = Amber, fontSize = s(24f), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    bpmChange(song.bpm, e.next.bpm)?.let {
                        Text(it.text, color = it.color, fontSize = s(24f), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.padding(horizontal = 12.dp))
                    }
                    val counts = e.item?.counts.orEmpty()
                    if (counts.isNotBlank()) Text(counts, color = Color.White, fontSize = s(24f), fontWeight = FontWeight.Bold)
                }
                val tn = e.item?.transNote.orEmpty()
                if (tn.isNotBlank()) Text(tn, color = Color(0xFFE0E0E0), fontSize = s(19f))
            }
        } else if (e.next != null) {
            Text(
                "Prossimo: ${e.next.title}" + (e.next.bpm?.let { "  ·  ♩ $it" } ?: ""),
                color = Color.Gray, fontSize = s(18f), modifier = Modifier.padding(top = 8.dp),
            )
        } else if (total > 1) {
            Text("Fine scaletta", color = Color.DarkGray, fontSize = s(16f), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SectionBlock(sec: Section, scale: Float) {
    fun s(v: Float): TextUnit = (v * scale).sp
    val bg = sectionColor(sec.name)
    val fg = sectionTextColor(sec.name)
    Column(
        Modifier.background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .widthIn(min = (70 * scale).dp, max = (320 * scale).dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(sec.name.ifBlank { "—" }.uppercase(), color = fg, fontSize = s(22f), fontWeight = FontWeight.Bold)
            Text("  ${sec.bars}", color = fg, fontSize = s(28f), fontWeight = FontWeight.Black)
            if (sec.repeat > 1) Text(" ×${sec.repeat}", color = fg, fontSize = s(24f), fontWeight = FontWeight.Bold)
        }
        if (sec.note.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(sec.note, color = fg.copy(alpha = 0.9f), fontSize = s(16f))
        }
    }
}
