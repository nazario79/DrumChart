package com.nazario.drumchart

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Archivio locale: un file JSON nella memoria interna dell'app. */
object Repo {
    var data by mutableStateOf(AppData())
        private set

    /** Dimensione del testo in modalità live (1.0 = normale). */
    var liveScale by mutableFloatStateOf(1f)
        private set

    /** Tema chiaro in modalità live (per le serate all'aperto). */
    var liveLight by mutableStateOf(false)
        private set

    /** Backup automatico: nome del file collegato (null = disattivo) ed eventuale errore. */
    var autoBackupName by mutableStateOf<String?>(null)
        private set
    var autoBackupError by mutableStateOf<String?>(null)
        private set

    /** Sezione copiata (appunti interni dell'app), conservata anche chiudendo l'app. */
    var copiedSection by mutableStateOf<Section?>(null)
        private set
    var copiedFrom by mutableStateOf("")
        private set

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences
    private lateinit var appCtx: Context
    private var autoUri: Uri? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "drumchart.json")
        prefs = ctx.getSharedPreferences("drumchart", Context.MODE_PRIVATE)
        appCtx = ctx.applicationContext
        liveScale = prefs.getFloat("liveScale", 1f)
        liveLight = prefs.getBoolean("liveLight", false)
        autoUri = prefs.getString("autoUri", null)?.let { Uri.parse(it) }
        autoBackupName = if (autoUri != null) prefs.getString("autoName", "backup") else null
        copiedSection = prefs.getString("clipSection", null)?.let { runCatching { sectionFromJson(JSONObject(it)) }.getOrNull() }
        copiedFrom = prefs.getString("clipFrom", "") ?: ""
        if (file.exists()) {
            runCatching { data = appDataFromJson(JSONObject(file.readText())) }
        }
    }

    private fun save() {
        val text = data.toJson().toString(2)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        autoUri?.let { writeAuto(it, text) }
    }

    private fun writeAuto(uri: Uri, text: String) {
        io.execute {
            val r = runCatching {
                appCtx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("file non raggiungibile")
            }
            main.post { autoBackupError = r.exceptionOrNull()?.let { it.message ?: "errore di scrittura" } }
        }
    }

    fun copySection(sec: Section, songTitle: String) {
        copiedSection = sec
        copiedFrom = songTitle
        prefs.edit().putString("clipSection", sec.toJson().toString()).putString("clipFrom", songTitle).apply()
    }

    fun toggleLiveLight() {
        liveLight = !liveLight
        prefs.edit().putBoolean("liveLight", liveLight).apply()
    }

    /** Collega un file (anche su Google Drive) che verrà aggiornato a ogni modifica. */
    fun enableAutoBackup(ctx: Context, uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "backup"
        autoUri = uri
        autoBackupName = name
        prefs.edit().putString("autoUri", uri.toString()).putString("autoName", name).apply()
        writeAuto(uri, data.toJson().toString(2))
    }

    fun disableAutoBackup(ctx: Context) {
        autoUri?.let { u ->
            runCatching {
                ctx.contentResolver.releasePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        autoUri = null
        autoBackupName = null
        autoBackupError = null
        prefs.edit().remove("autoUri").remove("autoName").apply()
    }

    private fun update(f: (AppData) -> AppData) {
        data = f(data)
        save()
    }

    fun changeLiveScale(v: Float) {
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
