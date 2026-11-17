package com.nazario.drumchart

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/** Archivio locale: un file JSON nella memoria interna dell'app. */
object Repo {
    var data by mutableStateOf(AppData())
        private set

    /** Dimensione del testo in modalità live (1.0 = normale). */
    var liveScale by mutableFloatStateOf(1f)
        private set

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "drumchart.json")
        prefs = ctx.getSharedPreferences("drumchart", Context.MODE_PRIVATE)
        liveScale = prefs.getFloat("liveScale", 1f)
        if (file.exists()) {
            runCatching { data = appDataFromJson(JSONObject(file.readText())) }
        }
    }

    private fun save() {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(data.toJson().toString(2))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun update(f: (AppData) -> AppData) {
        data = f(data)
        save()
    }

    fun setLiveScale(v: Float) {
        liveScale = v.coerceIn(0.6f, 2.2f)
        prefs.edit().putFloat("liveScale", liveScale).apply()
    }

    fun song(id: String?): Song? = data.songs.find { it.id == id }
    fun setlist(id: String?): Setlist? = data.setlists.find { it.id == id }

    fun saveSong(s: Song) = update { d ->
        if (d.songs.any { it.id == s.id }) d.copy(songs = d.songs.map { if (it.id == s.id) s else it })
        else d.copy(songs = d.songs + s)
    }

    fun deleteSong(id: String) = update { d ->
        d.copy(
            songs = d.songs.filter { it.id != id },
            setlists = d.setlists.map { sl -> sl.copy(items = sl.items.filter { it.songId != id }) },
        )
    }

    fun saveSetlist(s: Setlist) = update { d ->
        if (d.setlists.any { it.id == s.id }) d.copy(setlists = d.setlists.map { if (it.id == s.id) s else it })
        else d.copy(setlists = d.setlists + s)
    }

    fun deleteSetlist(id: String) = update { d -> d.copy(setlists = d.setlists.filter { it.id != id }) }

    // ---------------- Backup ----------------

    fun exportTo(ctx: Context, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(data.toJson().toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Impossibile scrivere il file")
    }

    fun readBackup(ctx: Context, uri: Uri): AppData {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Impossibile leggere il file")
        val o = JSONObject(text)
        require(o.has("songs")) { "Il file non è un backup di DrumChart" }
        return appDataFromJson(o)
    }

    /** replace = true: sostituisce tutto. false: unisce (a parità di id vince il backup). */
    fun importData(imported: AppData, replace: Boolean) = update { d ->
        if (replace) imported
        else {
            val songs = LinkedHashMap<String, Song>()
            d.songs.forEach { songs[it.id] = it }
            imported.songs.forEach { songs[it.id] = it }
            val sets = LinkedHashMap<String, Setlist>()
            d.setlists.forEach { sets[it.id] = it }
            imported.setlists.forEach { sets[it.id] = it }
            AppData(songs.values.toList(), sets.values.toList())
        }
    }
}
