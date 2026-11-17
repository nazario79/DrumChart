package com.nazario.drumchart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetlistEditor(id: String, nav: Nav) {
    // Ogni modifica viene salvata subito: così tornando dalla modalità live non si perde nulla.
    val sl = Repo.setlist(id) ?: Setlist(id = id)
    var name by remember(id) { mutableStateOf(sl.name) }
    var picker by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    fun save(s: Setlist) = Repo.saveSetlist(s)
    fun setItems(items: List<SetItem>) = save(sl.copy(name = name, items = items))
    fun updItem(i: Int, f: (SetItem) -> SetItem) = setItems(sl.items.mapIndexed { j, it -> if (j == i) f(it) else it })

    fun close() {
        if (Repo.setlist(id) != null && name.isBlank() && sl.items.isEmpty()) Repo.deleteSetlist(id)
        else if (Repo.setlist(id) != null && name.isBlank()) save(sl.copy(name = "Scaletta"))
        nav.back()
    }

    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scaletta") },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Default.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { nav.go(Screen.Live(id, null)) }, enabled = sl.items.isNotEmpty()) {
                        Icon(Icons.Default.PlayArrow, "Live", tint = if (sl.items.isNotEmpty()) Amber else Color.DarkGray)
                    }
                    if (Repo.setlist(id) != null) {
                        IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; save(sl.copy(name = it)) },
                    label = { Text("Nome scaletta / serata") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            itemsIndexed(sl.items, key = { _, it -> it.id }) { i, item ->
                val song = Repo.song(item.songId)
                val next = sl.items.getOrNull(i + 1)?.let { Repo.song(it.songId) }
                val prevMedley = i > 0 && sl.items[i - 1].medley
                val linked = item.medley && next != null
                Row {
                    // barra colorata che unisce i brani del medley
                    Box(
                        Modifier.width(6.dp).heightIn(min = 72.dp)
                            .background(if (linked || prevMedley) Amber else Color.Transparent, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}.", color = Color.Gray, modifier = Modifier.width(32.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song?.title ?: "(brano eliminato)", style = MaterialTheme.typography.titleMedium)
                                        song?.bpm?.let { Text("$it BPM", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                                    }
                                    IconButton(onClick = { setItems(sl.items.moved(i, i - 1)) }, enabled = i > 0) {
                                        Icon(Icons.Default.KeyboardArrowUp, "Su")
                                    }
                                    IconButton(onClick = { setItems(sl.items.moved(i, i + 1)) }, enabled = i < sl.items.lastIndex) {
                                        Icon(Icons.Default.KeyboardArrowDown, "Giù")
                                    }
                                    IconButton(onClick = { setItems(sl.items.filterIndexed { j, _ -> j != i }) }) {
                                        Icon(Icons.Default.Delete, "Togli", tint = Color(0xFFE57373))
                                    }
                                }
                                if (next != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = !item.medley, onClick = { updItem(i) { it.copy(medley = false) } },
                                            label = { Text("Singolo") })
                                        FilterChip(selected = item.medley, onClick = { updItem(i) { it.copy(medley = true) } },
                                            label = { Text("Medley → aggancia il successivo") })
                                    }
                                }
                            }
                        }
                        if (linked) {
                            RaccordoEditor(item, song, next!!) { f -> updItem(i, f) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Aggiungi brani")
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (picker) {
        SongPicker(
            onDismiss = { picker = false },
            onPick = { ids ->
                picker = false
                setItems(sl.items + ids.map { SetItem(songId = it) })
            },
        )
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Eliminare la scaletta?") },
            text = { Text("I brani restano nell'archivio.") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; Repo.deleteSetlist(id); nav.back() }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Annulla") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RaccordoEditor(item: SetItem, song: Song?, next: Song, upd: ((SetItem) -> SetItem) -> Unit) {
    // Testo locale per non perdere il cursore mentre si scrive
    var counts by remember(item.id) { mutableStateOf(item.counts) }
    var note by remember(item.id) { mutableStateOf(item.transNote) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2210)),
        border = BorderStroke(1.dp, Amber),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RACCORDO → ${next.title}", color = Amber, fontWeight = FontWeight.Bold)
            val ch = bpmChange(song?.bpm, next.bpm)
            if (ch != null) Text(ch.text, color = ch.color, fontWeight = FontWeight.Bold)
            else Text("Imposta il BPM di entrambi i brani per vedere il cambio di tempo", color = Color.Gray,
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = counts,
                onValueChange = { counts = it; upd { s -> s.copy(counts = it) } },
                label = { Text("Conteggi prima dell'entrata") },
                placeholder = { Text("es. 4 click → strofa") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QUICK_COUNTS.forEach { c ->
                    AssistChip(onClick = { counts = c; upd { s -> s.copy(counts = c) } }, label = { Text(c) })
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; upd { s -> s.copy(transNote = it) } },
                label = { Text("Nota raccordo") },
                placeholder = { Text("es. stacco sul rullante, il basso tiene la nota") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SongPicker(onDismiss: () -> Unit, onPick: (List<String>) -> Unit) {
    val selected = remember { mutableStateListOf<String>() }
    var q by remember { mutableStateOf("") }
    val songs = Repo.data.songs
        .filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) }
        .sortedBy { it.title.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi brani") },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text("Cerca") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (Repo.data.songs.isEmpty()) Text("Nessun brano in archivio: scrivine prima qualcuno.",
                    color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(songs, key = { it.id }) { s ->
                        val sel = s.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable { if (sel) selected.remove(s.id) else selected.add(s.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = sel, onCheckedChange = { if (sel) selected.remove(s.id) else selected.add(s.id) })
                            Column {
                                Text(s.title)
                                if (s.artist.isNotBlank()) Text(s.artist, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Aggiungi (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
