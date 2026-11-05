package app.terapia.mobile.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Utilidades de data/hora em português do Brasil. */
object Dates {
    val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM", ptBR)
    private val full = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'às' HH:mm", ptBR)
    private val short = DateTimeFormatter.ofPattern("EEE dd/MM 'às' HH:mm", ptBR)
    private val time = DateTimeFormatter.ofPattern("HH:mm", ptBR)
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBR)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)

    fun instant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()
    fun local(iso: String): ZonedDateTime = instant(iso).atZone(zone)

    fun full(iso: String): String = full.format(local(iso)).replaceFirstChar { it.uppercase() }
    fun short(iso: String): String = short.format(local(iso)).replaceFirstChar { it.uppercase() }
    fun time(iso: String): String = time.format(local(iso))
    fun dayMonth(d: LocalDate): String = dayMonth.format(d)
    fun date(d: LocalDate): String = date.format(d)
    fun date(iso: String): String = date.format(LocalDate.parse(iso.take(10)))
    fun monthYear(d: LocalDate): String = monthYear.format(d).replaceFirstChar { it.uppercase() }
    fun dateTime(iso: String): String = "${date.format(local(iso))} ${time(iso)}"

    /** 1 = segunda … 7 = domingo */
    fun weekdayName(day: Int): String =
        DayOfWeek.of(day).getDisplayName(TextStyle.FULL, ptBR).replaceFirstChar { it.uppercase() }

    fun weekdayShort(day: Int): String =
        DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, ptBR).replaceFirstChar { it.uppercase() }.removeSuffix(".")

    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** "14:00:00" → "14:00" */
    fun hhmm(t: String) = t.take(5)

    fun relative(iso: String): String {
        val then = local(iso)
        val today = LocalDate.now(zone)
        return when (then.toLocalDate()) {
            today -> "Hoje ${time(iso)}"
            today.minusDays(1) -> "Ontem ${time(iso)}"
            else -> dateTime(iso)
        }
    }
}

fun statusLabel(status: String) = when (status) {
    "confirmed" -> "Confirmada"
    "cancelled" -> "Cancelada"
    "done" -> "Realizada"
    else -> "Agendada"
}

/** Converte exceções em mensagens amigáveis. */
fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        "Limite do plano" in msg -> msg.substringAfter("Limite do plano").let { "Limite do plano$it" }.substringBefore("\n")
        "Invalid login credentials" in msg -> "E-mail ou senha incorretos."
        "Token has expired" in msg || "otp_expired" in msg -> "Código expirado ou inválido. Peça um novo código."
        "Email not confirmed" in msg -> "Confirme seu e-mail pelo link que enviamos antes de entrar."
        "User already registered" in msg -> "Este e-mail já tem cadastro. Use \"Entrar\"."
        "Password should be" in msg -> "A senha precisa ter pelo menos 6 caracteres."
        e is java.net.UnknownHostException || "Unable to resolve host" in msg -> "Sem conexão com a internet."
        "já está cadastrada como paciente" in msg -> "Esta conta já está cadastrada como paciente."
        else -> msg.lineSequence().firstOrNull()?.take(200)?.ifBlank { null } ?: "Ocorreu um erro. Tente novamente."
    }
}
