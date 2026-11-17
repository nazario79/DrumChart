package com.nazario.drumchart

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Una sezione del brano: Intro, Strofa, Rit... con battute, ripetizioni e appunto. */
data class Section(
    val id: String = newId(),
    val name: String,
    val bars: Int = 8,
    val repeat: Int = 1,
    val note: String = "",
    val groove: Groove? = null,
    /** BPM diverso solo per questa sezione (null = come il brano). */
    val bpm: Int? = null,
    /** Tempo o feel della sezione: "3/4", "6/8", "Half-time"... vuoto = normale. */
    val meter: String = "",
)

data class Song(
    val id: String = newId(),
    val title: String = "",
    val artist: String = "",
    val bpm: Int? = null,
    val attacco: String = "",
    val note: String = "",
    val medley: Boolean = false,
    val sections: List<Section> = emptyList(),
)

const val ROLE_SINGLE = 0
const val ROLE_IN = 1     // nel medley: si aggancia al brano successivo
const val ROLE_END = 2    // chiude il medley

/** Un brano dentro una scaletta. */
data class SetItem(
    val id: String = newId(),
    val songId: String,
    val role: Int = ROLE_SINGLE,
    val counts: String = "",
    val transNote: String = "",
    /** Titolo del medley: usato sul primo brano di una catena di brani agganciati. */
    val medleyTitle: String = "",
)

data class Setlist(
    val id: String = newId(),
    val name: String = "",
    val items: List<SetItem> = emptyList(),
)

data class AppData(
    val songs: List<Song> = emptyList(),
    val setlists: List<Setlist> = emptyList(),
)

// ---------------- JSON ----------------

private fun JSONObject.str(key: String) = if (has(key) && !isNull(key)) optString(key, "") else ""

fun Section.toJson(): JSONObject {
    val o = JSONObject()
        .put("id", id).put("name", name).put("bars", bars).put("repeat", repeat).put("note", note)
    if (groove != null && !groove.isEmpty()) o.put("groove", groove.toJson())
    if (bpm != null) o.put("bpm", bpm)
    if (meter.isNotBlank()) o.put("meter", meter)
    return o
}

fun sectionFromJson(o: JSONObject) = Section(
    id = o.str("id").ifBlank { newId() },
    name = o.str("name"),
    bars = o.optInt("bars", 8),
    repeat = o.optInt("repeat", 1).coerceAtLeast(1),
    note = o.str("note"),
    groove = o.optJSONObject("groove")?.let { grooveFromJson(it) },
    bpm = if (o.has("bpm") && !o.isNull("bpm")) o.optInt("bpm") else null,
    meter = o.str("meter"),
)

fun Song.toJson(): JSONObject {
    val o = JSONObject()
        .put("id", id).put("title", title).put("artist", artist)
        .put("attacco", attacco).put("note", note).put("medley", medley)
    if (bpm != null) o.put("bpm", bpm)
    o.put("sections", JSONArray().apply { sections.forEach { put(it.toJson()) } })
    return o
}

fun songFromJson(o: JSONObject): Song {
    val arr = o.optJSONArray("sections") ?: JSONArray()
    return Song(
        id = o.str("id").ifBlank { newId() },
        title = o.str("title"),
        artist = o.str("artist"),
        bpm = if (o.has("bpm") && !o.isNull("bpm")) o.optInt("bpm") else null,
        attacco = o.str("attacco"),
        note = o.str("note"),
        medley = o.optBoolean("medley", false),
        sections = (0 until arr.length()).map { sectionFromJson(arr.getJSONObject(it)) },
    )
}

fun SetItem.toJson() = JSONObject()
    .put("id", id).put("songId", songId).put("role", role)
    .put("counts", counts).put("transNote", transNote).put("medleyTitle", medleyTitle)

fun setItemFromJson(o: JSONObject) = SetItem(
    id = o.str("id").ifBlank { newId() },
    songId = o.str("songId"),
    role = o.optInt("role", ROLE_SINGLE).coerceIn(0, 2),
    counts = o.str("counts"),
    transNote = o.str("transNote"),
    medleyTitle = o.str("medleyTitle"),
)

fun Setlist.toJson() = JSONObject()
    .put("id", id).put("name", name)
    .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })

fun setlistFromJson(o: JSONObject): Setlist {
    val arr = o.optJSONArray("items") ?: JSONArray()
    return Setlist(
        id = o.str("id").ifBlank { newId() },
        name = o.str("name"),
        items = (0 until arr.length()).map { i ->
            val io = arr.getJSONObject(i)
            val it = setItemFromJson(io)
            if (io.has("role")) it
            else {
                // vecchio formato: medley = aggancia il successivo
                val m = io.optBoolean("medley", false)
                val prevM = i > 0 && arr.getJSONObject(i - 1).optBoolean("medley", false)
                it.copy(role = if (m) ROLE_IN else if (prevM) ROLE_END else ROLE_SINGLE)
            }
        },
    )
}

fun AppData.toJson() = JSONObject()
    .put("app", "DrumChart")
    .put("version", 1)
    .put("songs", JSONArray().apply { songs.forEach { put(it.toJson()) } })
    .put("setlists", JSONArray().apply { setlists.forEach { put(it.toJson()) } })

fun appDataFromJson(o: JSONObject): AppData {
    val s = o.optJSONArray("songs") ?: JSONArray()
    val l = o.optJSONArray("setlists") ?: JSONArray()
    return AppData(
        songs = (0 until s.length()).map { songFromJson(s.getJSONObject(it)) },
        setlists = (0 until l.length()).map { setlistFromJson(l.getJSONObject(it)) },
    )
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    val m = toMutableList()
    val x = m.removeAt(from)
    m.add(to, x)
    return m
}

/**
 * Catene di medley in una scaletta: indici dei brani dello stesso medley (almeno 2).
 * Un medley è una serie di brani "Nel medley" chiusa da un brano "Chiude il medley"
 * (o dall'ultimo brano "Nel medley" se dopo c'è un singolo).
 */
fun medleyChains(items: List<SetItem>): List<List<Int>> {
    val chains = mutableListOf<List<Int>>()
    var i = 0
    while (i < items.size) {
        if (items[i].role != ROLE_SINGLE) {
            var j = i
            while (j + 1 < items.size && items[j].role == ROLE_IN && items[j + 1].role != ROLE_SINGLE) j++
            if (j > i) chains += (i..j).toList()
            i = j + 1
        } else i++
    }
    return chains
}

/** true se il brano i è agganciato al successivo nello stesso medley. */
fun List<List<Int>>.linksNext(i: Int): Boolean = any { i in it && (i + 1) in it }
