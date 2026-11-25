package com.nazario.drumchart

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Filtro per band nella schermata principale ("" tutte, "-" senza band, altrimenti id). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandFilterRow(value: String, onChange: (String) -> Unit, onManage: () -> Unit) {
    val bands = Repo.data.bands.sortedBy { it.name.lowercase() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = value == "", onClick = { onChange("") }, label = { Text("Tutte") })
        bands.forEach { b ->
            FilterChip(selected = value == b.id, onClick = { onChange(b.id) }, label = { Text(b.name) })
        }
        FilterChip(selected = value == "-", onClick = { onChange("-") }, label = { Text("Senza band") })
        TextButton(onClick = onManage) { Text(if (bands.isEmpty()) "+ Crea band" else "Gestisci band") }
    }
}

/** Scelta delle band di un brano (anche più di una). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BandMultiPicker(selected: List<String>, onChange: (List<String>) -> Unit) {
    var newBand by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Repo.data.bands.sortedBy { it.name.lowercase() }.forEach { b ->
            val on = b.id in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - b.id else selected + b.id) },
                label = { Text(b.name) },
            )
        }
        AssistChip(onClick = { newBand = true }, label = { Text("+ Nuova band") })
    }
    if (newBand) NameDialog("Nuova band", "") { name ->
        newBand = false
        if (name != null && name.isNotBlank()) {
            val b = Repo.addBand(name)
            if (b.id !in selected) onChange(selected + b.id)
        }
    }
}

/** Scelta della band di una scaletta (una sola, facoltativa). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BandSinglePicker(selected: String?, onChange: (String?) -> Unit) {
    var newBand by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onChange(null) }, label = { Text("Nessuna") })
        Repo.data.bands.sortedBy { it.name.lowercase() }.forEach { b ->
            FilterChip(selected = selected == b.id, onClick = { onChange(b.id) }, label = { Text(b.name) })
        }
        AssistChip(onClick = { newBand = true }, label = { Text("+ Nuova band") })
    }
    if (newBand) NameDialog("Nuova band", "") { name ->
        newBand = false
        if (name != null && name.isNotBlank()) onChange(Repo.addBand(name).id)
    }
}

/** Finestra per creare, rinominare ed eliminare le band. */
@Composable
fun ManageBandsDialog(onDismiss: () -> Unit) {
    var rename by remember { mutableStateOf<Band?>(null) }
    var delete by remember { mutableStateOf<Band?>(null) }
    var create by remember { mutableStateOf(false) }
    val data = Repo.data
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Band") },
        text = {
            Column {
                if (data.bands.isEmpty()) Text("Nessuna band. Creane una per raggruppare brani e scalette.", color = Color.Gray)
                data.bands.sortedBy { it.name.lowercase() }.forEach { b ->
                    val n = data.songs.count { b.id in it.bandIds }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name)
                            Text("$n brani", color = Color.Gray)
                        }
                        IconButton(onClick = { rename = b }) { Icon(Icons.Default.Edit, "Rinomina") }
                        IconButton(onClick = { delete = b }) { Icon(Icons.Default.Delete, "Elimina", tint = Color(0xFFE57373)) }
                    }
                }
                TextButton(onClick = { create = true }) { Text("+ Nuova band") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
    if (create) NameDialog("Nuova band", "") { name ->
        create = false
        if (name != null && name.isNotBlank()) Repo.addBand(name)
    }
    rename?.let { b ->
        NameDialog("Rinomina band", b.name) { name ->
            rename = null
            if (name != null && name.isNotBlank()) Repo.renameBand(b.id, name)
        }
    }
    delete?.let { b ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("Eliminare \"${b.name}\"?") },
            text = { Text("I brani e le scalette non vengono cancellati: perdono solo l'associazione a questa band.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteBand(b.id)
                    if (Repo.bandFilter == b.id) Repo.chooseBandFilter("")
                    delete = null
                }) { Text("Elimina", color = Color(0xFFE57373)) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Annulla") } },
        )
    }
}

/** Dialogo con un campo di testo. onDone(null) = annullato. */
@Composable
fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(value = t, onValueChange = { t = it }, singleLine = true,
                placeholder = { Text("Nome della band") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onDone(t) }, enabled = t.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Annulla") } },
    )
}
