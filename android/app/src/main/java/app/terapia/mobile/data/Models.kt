package app.terapia.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val role: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val crp: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val timezone: String = "America/Sao_Paulo",
    @SerialName("patient_limit") val patientLimit: Int = 0,
    @SerialName("trial_until") val trialUntil: String? = null,
)

@Serializable
data class Patient(
    val id: String = "",
    @SerialName("psychologist_id") val psychologistId: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String,
    val age: Int? = null,
    val phone: String? = null,
    val email: String,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = false,
    @SerialName("reminder_minutes") val reminderMinutes: Int = 30,
    @SerialName("schedule_slots") val slots: List<Slot>? = null,
)

@Serializable
data class Slot(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String? = null,
    val weekday: Int,                 // 1 = segunda … 7 = domingo
    @SerialName("start_time") val startTime: String,   // "14:00:00"
    @SerialName("duration_minutes") val durationMinutes: Int = 50,
    val active: Boolean = true,
)

@Serializable
data class PatientName(@SerialName("full_name") val fullName: String)

@Serializable
data class Session(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("slot_id") val slotId: String? = null,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int = 50,
    val status: String = "scheduled",
    val patients: PatientName? = null,
)

@Serializable
data class NewSession(
    @SerialName("patient_id") val patientId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int = 50,
)

@Serializable
data class PsyNote(
    @SerialName("session_id") val sessionId: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("pre_note") val preNote: String? = null,
    @SerialName("post_note") val postNote: String? = null,
)

@Serializable
data class PatientNote(
    @SerialName("session_id") val sessionId: String,
    @SerialName("patient_id") val patientId: String,
    val title: String? = null,
    @SerialName("pre_note") val preNote: String? = null,
    @SerialName("post_note") val postNote: String? = null,
)

@Serializable
data class Anamnesis(
    @SerialName("patient_id") val patientId: String,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
data class Evolution(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("entry_date") val entryDate: String,
    val content: String,
)

@Serializable
data class ImportantInfo(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    val category: String = "outro",   // nome | data | outro
    val title: String,
    val details: String? = null,
    @SerialName("ref_date") val refDate: String? = null,
)

@Serializable
data class Attachment(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChangeRequest(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("session_id") val sessionId: String? = null,
    val kind: String,                 // confirm | cancel | reschedule
    val message: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Emergency(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    val level: String,                // yellow | red
    val message: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    val patients: PatientName? = null,
)

@Serializable
data class ChatMessage(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
data class MoodEntry(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("entry_date") val entryDate: String,   // yyyy-MM-dd
    val mood: Int,                                      // 5 muito feliz … 1 muito triste
    val note: String? = null,
)

@Serializable
data class MoodShare(
    val id: String? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("week_start") val weekStart: String,
)

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("patient_id") val patientId: String? = null,
    val type: String,
    val title: String,
    val body: String? = null,
    @SerialName("ref_id") val refId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DeviceToken(val token: String, @SerialName("user_id") val userId: String)

@Serializable
data class InviteRequest(
    @SerialName("resend_patient_id") val resendPatientId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val age: Int? = null,
    val phone: String? = null,
    val email: String? = null,
    val slots: List<Slot>? = null,
)

@Serializable
data class InviteResponse(
    val patient: Patient? = null,
    @SerialName("play_store_url") val playStoreUrl: String? = null,
    @SerialName("email_sent") val emailSent: Boolean = false,
    val error: String? = null,
)

@Serializable
data class VerifyPurchaseRequest(
    @SerialName("product_id") val productId: String,
    @SerialName("purchase_token") val purchaseToken: String,
)

/** Os 5 níveis de humor (valor salvo no banco = posição). */
enum class Mood(val value: Int, val emoji: String, val label: String) {
    VERY_HAPPY(5, "😄", "Muito feliz"),
    HAPPY(4, "🙂", "Feliz"),
    NEUTRAL(3, "😐", "Neutro"),
    SAD(2, "🙁", "Triste"),
    VERY_SAD(1, "😢", "Muito triste");

    companion object { fun of(v: Int) = entries.firstOrNull { it.value == v } ?: NEUTRAL }
}

/** Campos da anamnese (chave salva no banco → rótulo na tela). */
val ANAMNESIS_FIELDS = listOf(
    "queixa" to "Queixa principal",
    "historico_queixa" to "Histórico da queixa",
    "historico_familiar" to "Histórico familiar",
    "saude" to "Saúde física e medicações",
    "tratamentos" to "Tratamentos psicológicos/psiquiátricos anteriores",
    "social" to "Contexto social, familiar e profissional",
    "habitos" to "Hábitos (sono, alimentação, atividade física, substâncias)",
    "expectativas" to "Expectativas com a terapia",
    "observacoes" to "Observações",
)
