package com.nazario.drumchart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongEditor(id: String, nav: Nav) {
    val existing = remember(id) { Repo.song(id) }
    var song by remember(id) { mutableStateOf(existing ?: Song(id = id)) }
    var bpmText by remember(id) { mutableStateOf(existing?.bpm?.toString() ?: "") }
    var askDelete by remember { mutableStateOf(false) }
    var grooveFor by remember { mutableStateOf<String?>(null) } // id della sezione

    fun current() = song.copy(bpm = bpmText.trim().toIntOrNull()?.takeIf { it in 20..400 })

    fun isEmpty(s: Song) = s.title.isBlank() && s.sections.isEmpty() && s.attacco.isBlank() && s.note.isBlank()

    fun saveIfNeeded() {
        val s = current()
        if (isEmpty(s) && existing == null) return
        Repo.saveSong(s.copy(title = s.title.trim().ifBlank { "Senza titolo" }))
    }

    fun close() { saveIfNeeded(); nav.back() }

    fun updSection(i: Int, f: (Section) -> Section) {
        song = song.copy(sections = song.sections.mapIndexed { j, s -> if (j == i) f(s) else s })
    }

    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nuovo brano" else "Modifica brano") },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Default.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = {
                        saveIfNeeded()
                        if (Repo.song(id) != null) nav.go(Screen.Live(null, id))
                    }) { Icon(Icons.Default.PlayArrow, "Prova in live", tint = Amber) }
                    if (existing != null) {
                        IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                    }
                    IconButton(onClick = { close() }) { Icon(Icons.Default.Check, "Salva") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = song.title, onValueChange = { song = song.copy(title = it) },
                    label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = song.artist, onValueChange = { song = song.copy(artist = it) },
                        label = { Text("Artista") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = bpmText, onValueChange = { t -> bpmText = t.filter { it.isDigit() }.take(3) },
                        label = { Text("BPM") }, singleLine = true, modifier = Modifier.width(110.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = song.attacco, onValueChange = { song = song.copy(attacco = it) },
                    label = { Text("Attacco") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_ATTACCHI.forEach { a ->
                        AssistChip(onClick = { song = song.copy(attacco = a) }, label = { Text(a) })
                    }
                }
            }
            item {
                Text("Nelle scalette di solito è", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !song.medley, onClick = { song = song.copy(medley = false) },
                        label = { Text("Singolo") })
                    FilterChip(selected = song.medley, onClick = { song = song.copy(medley = true) },
                        label = { Text("Medley (si aggancia al successivo)") })
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Struttura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            itemsIndexed(song.sections, key = { _, s -> s.id }) { i, sec ->
                Card {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(14.dp, 40.dp)
                                    .background(sectionColor(sec.name), RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = sec.name, onValueChange = { v -> updSection(i) { it.copy(name = v) } },
                                label = { Text("Sezione") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { song = song.copy(sections = song.sections.moved(i, i - 1)) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "Su")
                            }
                            IconButton(onClick = { song = song.copy(sections = song.sections.moved(i, i + 1)) },
                                enabled = i < song.sections.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, "Giù")
                            }
                            IconButton(onClick = { song = song.copy(sections = song.sections.filterIndexed { j, _ -> j != i }) }) {
                                Icon(Icons.Default.Delete, "Rimuovi", tint = Color(0xFFE57373))
                            }
                        }
                        FlowRow(verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Battute", color = Color.Gray)
                                Stepper(sec.bars, { v -> updSection(i) { it.copy(bars = v) } })
                            }
                            Spacer(Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Ripeti", color = Color.Gray)
                                Stepper(sec.repeat, { v -> updSection(i) { it.copy(repeat = v) } }, prefix = "×")
                            }
                        }
                        OutlinedTextField(
                            value = sec.note, onValueChange = { v -> updSection(i) { it.copy(note = v) } },
                            placeholder = { Text("Appunto (es. solo hi-hat, fill tom, stop sul 4)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            SectionBpmField(sec) { v -> updSection(i) { it.copy(bpm = v) } }
                            QUICK_METERS.forEach { m ->
                                FilterChip(
                                    selected = sec.meter == m,
                                    onClick = { updSection(i) { it.copy(meter = if (it.meter == m) "" else m) } },
                                    label = { Text(m) },
                                )
                            }
                        }
                        TextButton(onClick = { grooveFor = sec.id }) {
                            Text(if (sec.groove == null) "♪ Scrivi battuta sul pentagramma" else "♪ Modifica battuta")
                        }
                        sec.groove?.let { gr ->
                            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                GrooveStaff(gr, 0.8f)
                            }
                        }
                    }
                }
            }
            item {
                Text("Aggiungi sezione", color = Color.Gray)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (QUICK_SECTIONS + "Altro").forEach { name ->
                        AssistChip(
                            onClick = {
                                val n = if (name == "Altro") "" else name
                                song = song.copy(sections = song.sections + Section(name = n, bars = defaultBars(n)))
                            },
                            label = { Text("+ $name") },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = song.note, onValueChange = { song = song.copy(note = it) },
                    label = { Text("Note generali del brano") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    grooveFor?.let { sid ->
        val idx = song.sections.indexOfFirst { it.id == sid }
        val sec = song.sections.getOrNull(idx)
        if (sec != null) {
            GrooveEditor(
                initial = sec.groove,
                title = sec.name,
                onDismiss = { grooveFor = null },
                onDone = { gr -> updSection(idx) { it.copy(groove = gr) }; grooveFor = null },
            )
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Eliminare il brano?") },
            text = { Text("\"${song.title}\" verrà tolto anche dalle scalette.") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; Repo.deleteSong(id); nav.back() }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun SectionBpmField(sec: Section, onChange: (Int?) -> Unit) {
    var t by remember(sec.id) { mutableStateOf(sec.bpm?.toString() ?: "") }
    OutlinedTextField(
        value = t,
        onValueChange = { v ->
            t = v.filter { it.isDigit() }.take(3)
            onChange(t.toIntOrNull()?.takeIf { it in 20..400 })
        },
        label = { Text("BPM sezione") },
        placeholder = { Text("= brano") },
        singleLine = true,
        modifier = Modifier.width(130.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
