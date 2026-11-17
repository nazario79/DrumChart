package com.nazario.drumchart

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Righe della scaletta con i medley raggruppati: (testo, tipo) dove tipo = 0 brano, 1 titolo medley, 2 brano nel medley. */
private fun setlistLines(sl: Setlist): List<Pair<String, Int>> {
    val chains = medleyChains(sl.items)
    val out = mutableListOf<Pair<String, Int>>()
    sl.items.forEachIndexed { i, item ->
        val song = Repo.song(item.songId) ?: return@forEachIndexed
        val chain = chains.find { i in it }
        if (chain != null && chain.first() == i) {
            out += ("MEDLEY " + item.medleyTitle.ifBlank { "" }).trim().uppercase() to 1
        }
        val bpm = song.bpm?.let { "  ($it BPM)" } ?: ""
        out += "${i + 1}. ${song.title}$bpm" to (if (chain != null) 2 else 0)
    }
    return out
}

fun shareSetlistText(ctx: Context, sl: Setlist) {
    val sb = StringBuilder()
    sb.append("🥁 SCALETTA: ").append(sl.name.ifBlank { "Scaletta" }).append('\n').append('\n')
    setlistLines(sl).forEach { (t, kind) ->
        when (kind) {
            1 -> sb.append("🎵 ").append(t).append('\n')
            2 -> sb.append("     ").append(t).append('\n')
            else -> sb.append(t).append('\n')
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Scaletta ${sl.name}")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(send, "Invia scaletta"))
}

fun shareSetlistPdf(ctx: Context, sl: Setlist) {
    runCatching {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val margin = 48f
        val title = Paint().apply { textSize = 26f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val normal = Paint().apply { textSize = 17f; isAntiAlias = true }
        val medley = Paint().apply {
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = 0xFFB26A00.toInt()
        }
        val bar = Paint().apply { color = 0xFFFFB300.toInt(); strokeWidth = 4f }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var c = page.canvas
        var y = margin + 20f
        c.drawText(sl.name.ifBlank { "Scaletta" }, margin, y, title)
        y += 36f

        setlistLines(sl).forEach { (t, kind) ->
            if (y > h - margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
                c = page.canvas
                y = margin + 10f
            }
            when (kind) {
                1 -> { y += 6f; c.drawText(t, margin, y, medley) }
                2 -> {
                    c.drawLine(margin + 4f, y - 16f, margin + 4f, y + 8f, bar)
                    c.drawText(t, margin + 20f, y, normal)
                }
                else -> c.drawText(t, margin, y, normal)
            }
            y += 26f
        }
        doc.finishPage(page)

        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val safe = sl.name.ifBlank { "Scaletta" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Scaletta" }
        val file = File(dir, "$safe.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Scaletta ${sl.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Invia scaletta (PDF)"))
    }.onFailure {
        Toast.makeText(ctx, "Errore PDF: ${it.message}", Toast.LENGTH_LONG).show()
    }
}
