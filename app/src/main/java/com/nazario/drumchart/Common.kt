package com.nazario.drumchart

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val QUICK_SECTIONS = listOf("Intro", "Strofa", "Pre", "Rit", "Special", "Solo", "Bridge", "Stop", "Finale")
val QUICK_ATTACCHI = listOf("Conta 4", "Conta 2", "Parte la base", "Parte la chitarra", "Parte la tastiera", "Parte la batteria")
val QUICK_COUNTS = listOf("4 click → strofa", "8 click → strofa", "4 click → entra batteria", "8 click → entra batteria", "Attacco diretto")

fun defaultBars(name: String): Int = when (name.lowercase()) {
    "pre" -> 4
    "stop" -> 1
    "finale" -> 4
    else -> 8
}

/** Colore del blocco in base al nome della sezione. */
fun sectionColor(name: String): Color {
    val n = name.lowercase().trim()
    return when {
        n.startsWith("intro") -> Color(0xFF455A64)
        n.startsWith("strof") || n.startsWith("verse") -> Color(0xFF1565C0)
        n.startsWith("pre") -> Color(0xFF00838F)
        n.startsWith("rit") || n.startsWith("chorus") || n.startsWith("ritornello") -> Color(0xFFD84315)
        n.startsWith("special") || n.startsWith("bridge") || n.startsWith("ponte") -> Color(0xFF6A1B9A)
        n.startsWith("solo") -> Color(0xFF2E7D32)
        n.startsWith("stop") || n.startsWith("break") || n.startsWith("stacco") -> Color(0xFFF9A825)
        n.startsWith("final") || n.startsWith("coda") || n.startsWith("outro") -> Color(0xFF8E0000)
        else -> Color(0xFF424242)
    }
}

fun sectionTextColor(name: String): Color =
    if (sectionColor(name) == Color(0xFFF9A825)) Color.Black else Color.White

data class BpmChange(val text: String, val color: Color)

/** "124 → 98 ▼ rallenta" ecc. Null se manca uno dei due BPM. */
fun bpmChange(from: Int?, to: Int?): BpmChange? {
    if (from == null || to == null) return null
    return when {
        to < from -> BpmChange("$from → $to  ▼ rallenta", Color(0xFF64B5F6))
        to > from -> BpmChange("$from → $to  ▲ accelera", Color(0xFFFF8A65))
        else -> BpmChange("$from → $to  = stesso tempo", Color(0xFFBDBDBD))
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, prefix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > min) onChange(value - 1) }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "$prefix$value",
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onChange(value + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Più")
        }
    }
}
