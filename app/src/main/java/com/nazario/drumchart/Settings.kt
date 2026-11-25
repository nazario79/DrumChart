package com.nazario.drumchart

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

// ---------------- Modello delle impostazioni ----------------

val DEFAULT_NEXT = setOf(
    KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_NEXT,
)
val DEFAULT_PREV = setOf(
    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
)

/** Colori disponibili per il titolo del brano in live. */
val TITLE_COLORS = listOf(
    "Azzurro" to 0xFF40C4FF,
    "Giallo" to 0xFFFFEB3B,
    "Verde" to 0xFF69F0AE,
    "Arancio" to 0xFFFF9100,
    "Rosso" to 0xFFFF5252,
    "Magenta" to 0xFFFF4081,
    "Viola" to 0xFFB388FF,
    "Bianco" to 0xFFFFFFFF,
)

data class AppSettings(
    val pedalEnabled: Boolean = true,
    val keysNext: Set<Int> = DEFAULT_NEXT,
    val keysPrev: Set<Int> = DEFAULT_PREV,
    val keysBlink: Set<Int> = emptySet(),
    val swipe: Boolean = true,
    val edgeTap: Boolean = false,
    val twoFingerBlink: Boolean = true,
    val titleColor: Long = 0xFF40C4FF,
    val titleBanner: Boolean = false,
    val blinkFast: Boolean = false,
) {
    val titleColorC: Color get() = Color(titleColor.toInt())

    fun toJson(): JSONObject = JSONObject()
        .put("pedalEnabled", pedalEnabled)
        .put("keysNext", JSONArray(keysNext.toList()))
        .put("keysPrev", JSONArray(keysPrev.toList()))
        .put("keysBlink", JSONArray(keysBlink.toList()))
        .put("swipe", swipe).put("edgeTap", edgeTap).put("twoFingerBlink", twoFingerBlink)
        .put("titleColor", titleColor).put("titleBanner", titleBanner).put("blinkFast", blinkFast)
}

private fun JSONObject.intSet(k: String, def: Set<Int>): Set<Int> {
    val a = optJSONArray(k) ?: return def
    return (0 until a.length()).map { a.getInt(it) }.toSet()
}

fun settingsFromJson(o: JSONObject): AppSettings {
    val d = AppSettings()
    return AppSettings(
        pedalEnabled = o.optBoolean("pedalEnabled", d.pedalEnabled),
        keysNext = o.intSet("keysNext", d.keysNext),
        keysPrev = o.intSet("keysPrev", d.keysPrev),
        keysBlink = o.intSet("keysBlink", d.keysBlink),
        swipe = o.optBoolean("swipe", d.swipe),
        edgeTap = o.optBoolean("edgeTap", d.edgeTap),
        twoFingerBlink = o.optBoolean("twoFingerBlink", d.twoFingerBlink),
        titleColor = o.optLong("titleColor", d.titleColor),
        titleBanner = o.optBoolean("titleBanner", d.titleBanner),
        blinkFast = o.optBoolean("blinkFast", d.blinkFast),
    )
}

/** Nome leggibile di un tasto. */
fun keyName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_PAGE_DOWN -> "Pagina giù"
    KeyEvent.KEYCODE_PAGE_UP -> "Pagina su"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "Freccia →"
    KeyEvent.KEYCODE_DPAD_LEFT -> "Freccia ←"
    KeyEvent.KEYCODE_DPAD_UP -> "Freccia ↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "Freccia ↓"
    KeyEvent.KEYCODE_SPACE -> "Spazio"
    KeyEvent.KEYCODE_ENTER -> "Invio"
    KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
    KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume −"
    KeyEvent.KEYCODE_MEDIA_NEXT -> "Brano succ."
    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Brano prec."
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play/Pausa"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}

private enum class PedalAction(val label: String, val hint: String) {
    NEXT("Avanti", "Brano successivo (dentro un medley: brano successivo del medley)"),
    PREV("Indietro", "Brano precedente"),
    BLINK("Lampeggio titolo", "Accende / spegne il lampeggio del titolo del brano corrente"),
}

// ---------------- Schermata ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: Nav) {
    val st = Repo.settings
    var learn by remember { mutableStateOf<PedalAction?>(null) }
    var lastKey by remember { mutableStateOf<Int?>(null) }
    val activity = LocalContext.current as? MainActivity

    BackHandler { nav.back() }

    fun keysOf(a: PedalAction) = when (a) {
        PedalAction.NEXT -> st.keysNext
        PedalAction.PREV -> st.keysPrev
        PedalAction.BLINK -> st.keysBlink
    }

    /** Assegna un tasto a un'azione, togliendolo dalle altre. */
    fun assign(a: PedalAction, code: Int) = Repo.updateSettings { s ->
        val n = s.keysNext - code
        val p = s.keysPrev - code
        val b = s.keysBlink - code
        when (a) {
            PedalAction.NEXT -> s.copy(keysNext = n + code, keysPrev = p, keysBlink = b)
            PedalAction.PREV -> s.copy(keysNext = n, keysPrev = p + code, keysBlink = b)
            PedalAction.BLINK -> s.copy(keysNext = n, keysPrev = p, keysBlink = b + code)
        }
    }

    fun clear(a: PedalAction) = Repo.updateSettings { s ->
        when (a) {
            PedalAction.NEXT -> s.copy(keysNext = emptySet())
            PedalAction.PREV -> s.copy(keysPrev = emptySet())
            PedalAction.BLINK -> s.copy(keysBlink = emptySet())
        }
    }

    // Prova del pedale: mostra il tasto premuto
    DisposableEffect(learn) {
        activity?.learnListener = { code ->
            val a = learn
            if (a != null) { assign(a, code); learn = null }
            lastKey = code
        }
        onDispose { activity?.learnListener = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.Default.ArrowBack, "Indietro") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Pedale ----------
            item { Header("Pedale Bluetooth") }
            item {
                SwitchRow("Usa il pedale in modalità live", st.pedalEnabled) { v -> Repo.updateSettings { it.copy(pedalEnabled = v) } }
                Text(
                    "Collega il pedale dalle impostazioni Bluetooth di Android. Poi premi un pedale qui sotto: " +
                        "vedrai quale tasto invia e potrai assegnarlo all'azione che vuoi.",
                    color = Color.Gray, fontSize = 13.sp,
                )
                Text(
                    "Ultimo tasto ricevuto: " + (lastKey?.let { keyName(it) } ?: "nessuno"),
                    color = Amber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
                )
            }
            PedalAction.entries.forEach { a ->
                item(key = a.name) {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(a.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(a.hint, color = Color.Gray, fontSize = 13.sp)
                            val keys = keysOf(a)
                            Text(
                                if (keys.isEmpty()) "Nessun tasto assegnato" else keys.joinToString("  ·  ") { keyName(it) },
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = { learn = a }, label = { Text("Assegna premendo il pedale") })
                                if (a != PedalAction.BLINK) {
                                    AssistChip(onClick = {
                                        Repo.updateSettings {
                                            if (a == PedalAction.NEXT) it.copy(keysNext = DEFAULT_NEXT - it.keysBlink - it.keysPrev)
                                            else it.copy(keysPrev = DEFAULT_PREV - it.keysBlink - it.keysNext)
                                        }
                                    }, label = { Text("Predefiniti") })
                                }
                                AssistChip(onClick = { clear(a) }, label = { Text("Rimuovi tutti") })
                            }
                        }
                    }
                }
            }

            // ---------- Gesti ----------
            item { HorizontalDivider(); Header("Gesti sullo schermo (modalità live)") }
            item {
                SwitchRow("Scorri con il dito per cambiare brano", st.swipe) { v -> Repo.updateSettings { it.copy(swipe = v) } }
                SwitchRow("Tocco sui bordi: sinistra indietro, destra avanti", st.edgeTap) { v -> Repo.updateSettings { it.copy(edgeTap = v) } }
                SwitchRow("Tocco con due dita: lampeggio del titolo", st.twoFingerBlink) { v -> Repo.updateSettings { it.copy(twoFingerBlink = v) } }
            }

            // ---------- Titolo ----------
            item { HorizontalDivider(); Header("Titolo del brano (modalità live)") }
            item {
                Text("Colore", color = Color.Gray)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)) {
                    TITLE_COLORS.forEach { (name, c) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { Repo.updateSettings { it.copy(titleColor = c) } }) {
                            Box(
                                Modifier.size(42.dp).background(Color(c.toInt()), CircleShape)
                                    .border(if (st.titleColor == c) 3.dp else 1.dp, if (st.titleColor == c) Color.White else Color.DarkGray, CircleShape)
                            )
                            Text(name, fontSize = 12.sp, color = if (st.titleColor == c) Color.White else Color.Gray)
                        }
                    }
                }
                Text("Stile", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !st.titleBanner, onClick = { Repo.updateSettings { it.copy(titleBanner = false) } },
                        label = { Text("Testo colorato") })
                    FilterChip(selected = st.titleBanner, onClick = { Repo.updateSettings { it.copy(titleBanner = true) } },
                        label = { Text("Fascia colorata") })
                }
                Text("Velocità del lampeggio", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !st.blinkFast, onClick = { Repo.updateSettings { it.copy(blinkFast = false) } },
                        label = { Text("Lento") })
                    FilterChip(selected = st.blinkFast, onClick = { Repo.updateSettings { it.copy(blinkFast = true) } },
                        label = { Text("Veloce") })
                }
                Text("Anteprima", color = Color.Gray, modifier = Modifier.padding(top = 10.dp))
                Box(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    LiveTitle("Titolo del brano", st, big = false, scale = 0.8f, inverted = false)
                }
            }

            // ---------- Live ----------
            item { HorizontalDivider(); Header("Modalità live") }
            item {
                SwitchRow("Tema chiaro (per le serate all'aperto)", Repo.liveLight) { Repo.toggleLiveLight() }
                Text("Dimensione del testo: ${(Repo.liveScale * 100).toInt()}%", color = Color.Gray)
                Slider(
                    value = Repo.liveScale,
                    onValueChange = { Repo.changeLiveScale(it) },
                    valueRange = 0.6f..2.2f,
                )
                Text("Lo schermo resta sempre acceso in modalità live.", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    learn?.let { a ->
        AlertDialog(
            onDismissRequest = { learn = null },
            title = { Text("Assegna: ${a.label}") },
            text = { Text("Premi adesso il pedale (o il tasto) da usare per \"${a.label}\".") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { learn = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Header(t: String) {
    Text(t, color = Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
