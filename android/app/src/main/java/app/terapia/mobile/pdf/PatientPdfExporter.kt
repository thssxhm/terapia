package app.terapia.mobile.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import app.terapia.mobile.data.ANAMNESIS_FIELDS
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Mood
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.statusLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Gera um PDF com TODAS as informações do paciente (dados, horários, anamnese, evolução,
 * informações importantes, sessões com anotações, humor compartilhado e lista de anexos).
 * Usa a API nativa android.graphics.pdf.PdfDocument — não precisa de biblioteca extra.
 */
object PatientPdfExporter {
    private const val W = 595   // A4 em pontos
    private const val H = 842
    private const val M = 48f

    suspend fun export(context: Context, patientId: String): Uri = withContext(Dispatchers.IO) {
        val psy = Repo.myProfile()
        val p = Repo.patient(patientId)
        val anam = Repo.anamnesis(patientId)
        val evol = Repo.evolutions(patientId)
        val info = Repo.importantInfo(patientId)
        val sessions = Repo.sessionsOf(patientId)
        val notes = Repo.psyNotes(patientId).associateBy { it.sessionId }
        val files = Repo.attachments(patientId)
        val shares = Repo.moodShares(patientId)
        val moods = if (shares.isEmpty()) emptyList() else
            Repo.moods(patientId, LocalDate.parse(shares.last().weekStart), LocalDate.now())

        val w = Writer()
        w.title("Prontuário — ${p.fullName}")
        w.small("Psicólogo(a): ${psy?.fullName.orEmpty()}  •  CRP ${psy?.crp.orEmpty()}")
        w.small("Gerado em ${Dates.date(LocalDate.now())} pelo app Terapia. Documento sigiloso (Resolução CFP nº 001/2009).")
        w.gap()

        w.h("Dados do paciente")
        w.p("Nome: ${p.fullName}")
        p.age?.let { w.p("Idade: $it anos") }
        p.phone?.let { w.p("Telefone: $it") }
        w.p("E-mail: ${p.email}")
        p.slots.orEmpty().forEach {
            w.p("Horário fixo: ${Dates.weekdayName(it.weekday)} às ${Dates.hhmm(it.startTime)} (${it.durationMinutes} min)")
        }

        w.h("Anamnese")
        val filled = ANAMNESIS_FIELDS.filter { !anam.data[it.first].isNullOrBlank() }
        if (filled.isEmpty()) w.p("Não preenchida.")
        filled.forEach { (k, label) -> w.label(label); w.p(anam.data[k].orEmpty()) }

        w.h("Informações importantes")
        if (info.isEmpty()) w.p("Nenhuma.")
        info.forEach {
            val cat = when (it.category) { "nome" -> "Nome"; "data" -> "Data"; else -> "Outro" }
            val date = it.refDate?.let { d -> " (${Dates.date(d)})" }.orEmpty()
            w.p("• [$cat] ${it.title}$date${it.details?.let { d -> " — $d" }.orEmpty()}")
        }

        w.h("Evolução")
        if (evol.isEmpty()) w.p("Nenhum registro.")
        evol.sortedBy { it.entryDate }.forEach { w.label(Dates.date(it.entryDate)); w.p(it.content) }

        w.h("Sessões e anotações")
        val past = sessions.filter { Dates.instant(it.startsAt).isBefore(java.time.Instant.now()) || notes.containsKey(it.id) }
        if (past.isEmpty()) w.p("Nenhuma sessão realizada.")
        past.forEach { s ->
            w.label("${Dates.full(s.startsAt)} — ${statusLabel(s.status)}")
            notes[s.id]?.preNote?.takeIf { it.isNotBlank() }?.let { w.p("Pré-sessão: $it") }
            notes[s.id]?.postNote?.takeIf { it.isNotBlank() }?.let { w.p("Pós-sessão: $it") }
        }

        w.h("Humor (semanas enviadas pelo paciente)")
        if (moods.isEmpty()) w.p("Nenhuma semana compartilhada.")
        moods.forEach { m ->
            val mood = Mood.of(m.mood)
            w.p("${Dates.date(m.entryDate)}: ${mood.label}${m.note?.let { " — $it" }.orEmpty()}")
        }

        w.h("Anexos")
        if (files.isEmpty()) w.p("Nenhum.")
        files.forEach { w.p("• ${it.fileName}") }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = p.fullName.replace(Regex("[^A-Za-z0-9]+"), "_")
        val file = File(dir, "Prontuario_${safeName}_${LocalDate.now()}.pdf")
        file.outputStream().use { w.finish(it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Salvar ou enviar PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Escreve texto com quebra de linha e de página automaticamente. */
    private class Writer {
        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f
        val width = (W - 2 * M).toInt()

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(40, 36, 52) }
        val small = TextPaint(body).apply { textSize = 8.5f; color = Color.GRAY }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val h = TextPaint(body).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(124, 92, 196) }
        val title = TextPaint(h).apply { textSize = 20f }

        init { newPage() }

        private fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
            page.canvas.drawText("Terapia • página $pageNo", M, H - 24f, small)
        }

        private fun draw(text: String, paint: TextPaint, after: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            var line = 0
            while (line < layout.lineCount) {
                // Desenha linha a linha para permitir quebra de página no meio do parágrafo
                val lh = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                if (y + lh > H - M) newPage()
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line)
                page.canvas.drawText(text.substring(start, end).trimEnd(), M, y - layout.getLineAscent(line), paint)
                y += lh
                line++
            }
            y += after
        }

        fun title(t: String) = draw(t, title, 6f)
        fun h(t: String) { y += 10f; draw(t, h, 6f) }
        fun label(t: String) = draw(t, bold, 2f)
        fun p(t: String) = draw(t.trimEnd(), body, 6f)
        fun small(t: String) = draw(t, small, 2f)
        fun gap() { y += 8f }

        fun finish(out: java.io.OutputStream) {
            doc.finishPage(page)
            doc.writeTo(out)
            doc.close()
        }
    }
}
