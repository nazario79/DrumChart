package com.nazario.drumchart

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Nav, tab: MutableIntState) {
    val ctx = LocalContext.current
    val data = Repo.data
    var menu by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    var query by remember { mutableStateOf("") }
    var manageBands by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching { Repo.exportTo(ctx, uri) }
                .onSuccess { Toast.makeText(ctx, "Backup salvato", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Errore: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val autoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            Repo.enableAutoBackup(ctx, uri)
            Toast.makeText(ctx, "Backup automatico attivo", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { Repo.readBackup(ctx, uri) }
                .onSuccess { pendingImport = it }
                .onFailure { Toast.makeText(ctx, "Errore: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DrumChart", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { nav.go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Impostazioni") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Esporta backup (Drive o file)") },
                            onClick = {
                                menu = false
                                exportLauncher.launch("DrumChart-backup-${LocalDate.now()}.json")
                            },
                        )
                        val auto = Repo.autoBackupName
                        if (auto == null) {
                            DropdownMenuItem(
                                text = { Text("Backup automatico su file (Drive)…") },
                                onClick = { menu = false; autoLauncher.launch("DrumChart-auto.json") },
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Disattiva backup automatico")
                                        Text(
                                            "Attivo su: $auto" + (Repo.autoBackupError?.let { "\n⚠ $it" } ?: ""),
                                            color = if (Repo.autoBackupError != null) Color(0xFFFF8A65) else Color.Gray,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                },
                                onClick = { menu = false; Repo.disableAutoBackup(ctx) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Importa backup") },
                            onClick = {
                                menu = false
                                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (tab.intValue == 0) nav.go(Screen.EditSong(newId()))
                else nav.go(Screen.EditSet(newId()))
            }) { Icon(Icons.Default.Add, "Nuovo") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.intValue) {
                Tab(selected = tab.intValue == 0, onClick = { tab.intValue = 0 },
                    text = { Text("Brani (${data.songs.size})") })
                Tab(selected = tab.intValue == 1, onClick = { tab.intValue = 1 },
                    text = { Text("Scalette (${data.setlists.size})") })
            }
            val filter = Repo.bandFilter.let { f -> if (f.isNotEmpty() && f != "-" && Repo.band(f) == null) "" else f }
            Spacer(Modifier.height(6.dp))
            BandFilterRow(filter, { Repo.setBandFilter(it) }, { manageBands = true })
            val bandsSorted = data.bands.sortedBy { it.name.lowercase() }
            if (tab.intValue == 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Cerca brano o artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
                val base = data.songs
                    .filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
                    .sortedBy { it.title.lowercase() }
                // gruppi (titolo band, brani): con "Tutte" i brani sono raggruppati per band
                val groups: List<Pair<String?, List<Song>>> = when (filter) {
                    "" -> if (bandsSorted.isEmpty()) listOf(null to base)
                    else bandsSorted.map { b -> b.name to base.filter { b.id in it.bandIds } }
                        .plus("Senza band" to base.filter { s -> s.bandIds.none { Repo.band(it) != null } })
                        .filter { it.second.isNotEmpty() }
                    "-" -> listOf(null to base.filter { s -> s.bandIds.none { Repo.band(it) != null } })
                    else -> listOf(null to base.filter { filter in it.bandIds })
                }
                if (data.songs.isEmpty()) Empty("Nessun brano. Tocca + per scriverne uno.")
                else if (groups.all { it.second.isEmpty() }) Empty("Nessun brano in questo gruppo.")
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    groups.forEach { (header, list) ->
                        if (header != null) item(key = "h-$header") { GroupHeader(header, list.size) }
                        items(list, key = { "${header ?: ""}/${it.id}" }) { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.go(Screen.EditSong(s.id)) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.title.ifBlank { "(senza titolo)" }, style = MaterialTheme.typography.titleMedium)
                                    val sub = listOfNotNull(
                                        s.artist.ifBlank { null },
                                        s.bpm?.let { "$it BPM" },
                                        "${s.sections.size} sezioni",
                                    ).joinToString(" · ")
                                    Text(sub, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { nav.go(Screen.Live(null, s.id)) }) {
                                    Icon(Icons.Default.PlayArrow, "Live", tint = Amber)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                val all = data.setlists
                val groups: List<Pair<String?, List<Setlist>>> = when (filter) {
                    "" -> if (bandsSorted.isEmpty()) listOf(null to all)
                    else bandsSorted.map { b -> b.name to all.filter { it.bandId == b.id } }
                        .plus("Senza band" to all.filter { Repo.band(it.bandId) == null })
                        .filter { it.second.isNotEmpty() }
                    "-" -> listOf(null to all.filter { Repo.band(it.bandId) == null })
                    else -> listOf(null to all.filter { it.bandId == filter })
                }
                if (data.setlists.isEmpty()) Empty("Nessuna scaletta. Tocca + per crearne una.")
                else if (groups.all { it.second.isEmpty() }) Empty("Nessuna scaletta in questo gruppo.")
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    groups.forEach { (header, list) ->
                        if (header != null) item(key = "h-$header") { GroupHeader(header, list.size) }
                        items(list, key = { "${header ?: ""}/${it.id}" }) { sl ->
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.go(Screen.EditSet(sl.id)) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sl.name.ifBlank { "(senza nome)" }, style = MaterialTheme.typography.titleMedium)
                                    val medleys = medleyChains(sl.items).size
                                    Text(
                                        "${sl.items.size} brani" + if (medleys > 0) " · $medleys medley" else "",
                                        color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                IconButton(onClick = { nav.go(Screen.Live(sl.id, null)) }, enabled = sl.items.isNotEmpty()) {
                                    Icon(Icons.Default.PlayArrow, "Live", tint = if (sl.items.isNotEmpty()) Amber else Color.DarkGray)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (manageBands) ManageBandsDialog { manageBands = false }

    pendingImport?.let { imp ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importa backup") },
            text = {
                Text(
                    "Il backup contiene ${imp.songs.size} brani e ${imp.setlists.size} scalette.\n\n" +
                        "Unisci: aggiunge e aggiorna, senza cancellare quello che hai già.\n" +
                        "Sostituisci: questo dispositivo diventa identico al backup."
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        Repo.importData(imp, replace = true); pendingImport = null
                        Toast.makeText(ctx, "Dati sostituiti", Toast.LENGTH_SHORT).show()
                    }) { Text("Sostituisci") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        Repo.importData(imp, replace = false); pendingImport = null
                        Toast.makeText(ctx, "Backup unito", Toast.LENGTH_SHORT).show()
                    }) { Text("Unisci") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = Color.Gray, modifier = Modifier.padding(24.dp))
}

@Composable
private fun GroupHeader(name: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.uppercase(), color = Amber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("$count", color = Color.Gray)
    }
}
