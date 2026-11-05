package app.terapia.mobile.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Todas as leituras e gravações no Supabase ficam aqui.
 * A segurança (quem pode ver o quê) é garantida pelo banco (RLS), não pelo app.
 */
object Repo {

    val uid: String get() = supabase.auth.currentUserOrNull()?.id ?: error("Sessão expirada. Entre novamente.")

    // ---------------- Perfil ----------------
    suspend fun myProfile(): Profile? =
        supabase.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull()

    suspend fun profile(id: String): Profile? =
        supabase.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull()

    suspend fun becomePsychologist(name: String, crp: String) {
        supabase.postgrest.rpc("become_psychologist", buildJsonObject {
            put("p_full_name", name); put("p_crp", crp)
        })
    }

    suspend fun updateMyProfile(name: String, crp: String?, phone: String?) {
        supabase.from("profiles").update({
            set("full_name", name)
            if (crp != null) set("crp", crp)
            set("phone", phone)
        }) { filter { eq("id", uid) } }
    }

    suspend fun deleteMyAccount() {
        supabase.postgrest.rpc("delete_my_account")
        supabase.auth.signOut()
    }

    suspend fun saveDeviceToken(token: String) {
        val id = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.from("device_tokens").upsert(DeviceToken(token, id)) { onConflict = "token" }
    }

    suspend fun removeDeviceToken(token: String) {
        supabase.from("device_tokens").delete { filter { eq("token", token) } }
    }

    // ---------------- Pacientes (psicólogo) ----------------
    suspend fun myPatients(): List<Patient> =
        supabase.from("patients").select(Columns.raw("*, schedule_slots(*)")) {
            filter { eq("psychologist_id", uid) }
            order("full_name", Order.ASCENDING)
        }.decodeList()

    suspend fun patient(id: String): Patient =
        supabase.from("patients").select(Columns.raw("*, schedule_slots(*)")) { filter { eq("id", id) } }.decodeSingle()

    suspend fun invitePatient(req: InviteRequest): InviteResponse {
        val response = supabase.functions.invoke("invite-patient", body = req)
        return json.decodeFromString(InviteResponse.serializer(), response.bodyAsText())
    }

    suspend fun updatePatient(id: String, name: String, age: Int?, phone: String?) {
        supabase.from("patients").update({
            set("full_name", name); set("age", age); set("phone", phone)
        }) { filter { eq("id", id) } }
    }

    suspend fun setReminder(patientId: String, enabled: Boolean, minutes: Int) {
        supabase.from("patients").update({
            set("reminder_enabled", enabled); set("reminder_minutes", minutes)
        }) { filter { eq("id", patientId) } }
    }

    suspend fun deletePatient(p: Patient) {
        // Remove os arquivos do Storage antes de apagar os registros
        val paths = attachments(p.id).map { it.storagePath }
        if (paths.isNotEmpty()) runCatching { supabase.storage.from("attachments").delete(paths) }
        supabase.from("patients").delete { filter { eq("id", p.id) } }
    }

    // ---------------- Horários fixos ----------------
    suspend fun addSlot(slot: Slot) { supabase.from("schedule_slots").insert(slot) }

    suspend fun updateSlot(slot: Slot) {
        supabase.from("schedule_slots").update({
            set("weekday", slot.weekday); set("start_time", slot.startTime)
            set("duration_minutes", slot.durationMinutes)
        }) { filter { eq("id", slot.id!!) } }
    }

    suspend fun deleteSlot(id: String) { supabase.from("schedule_slots").delete { filter { eq("id", id) } } }

    // ---------------- Sessões ----------------
    suspend fun sessionsOf(patientId: String): List<Session> =
        supabase.from("sessions").select {
            filter { eq("patient_id", patientId) }
            order("starts_at", Order.ASCENDING)
        }.decodeList()

    suspend fun sessionsBetween(from: Instant, to: Instant): List<Session> =
        supabase.from("sessions").select(Columns.raw("*, patients(full_name)")) {
            filter { gte("starts_at", from.toString()); lt("starts_at", to.toString()) }
            order("starts_at", Order.ASCENDING)
        }.decodeList()

    suspend fun session(id: String): Session =
        supabase.from("sessions").select { filter { eq("id", id) } }.decodeSingle()

    suspend fun addSession(s: NewSession) { supabase.from("sessions").insert(s) }

    suspend fun updateSession(id: String, startsAt: Instant, duration: Int, status: String) {
        supabase.from("sessions").update({
            set("starts_at", startsAt.toString()); set("duration_minutes", duration); set("status", status)
        }) { filter { eq("id", id) } }
    }

    suspend fun setSessionStatus(id: String, status: String) {
        supabase.from("sessions").update({ set("status", status) }) { filter { eq("id", id) } }
    }

    suspend fun deleteSession(id: String) { supabase.from("sessions").delete { filter { eq("id", id) } } }

    // ---------------- Anotações de sessão ----------------
    suspend fun psyNotes(patientId: String): List<PsyNote> =
        supabase.from("psychologist_session_notes").select { filter { eq("patient_id", patientId) } }.decodeList()

    suspend fun savePsyNote(n: PsyNote) {
        supabase.from("psychologist_session_notes").upsert(n) { onConflict = "session_id" }
    }

    suspend fun patientNotes(patientId: String): List<PatientNote> =
        supabase.from("patient_session_notes").select { filter { eq("patient_id", patientId) } }.decodeList()

    suspend fun savePatientNote(n: PatientNote) {
        supabase.from("patient_session_notes").upsert(n) { onConflict = "session_id" }
    }

    // ---------------- Prontuário ----------------
    suspend fun anamnesis(patientId: String): Anamnesis =
        supabase.from("anamnesis").select { filter { eq("patient_id", patientId) } }.decodeSingleOrNull()
            ?: Anamnesis(patientId)

    suspend fun saveAnamnesis(a: Anamnesis) { supabase.from("anamnesis").upsert(a) { onConflict = "patient_id" } }

    suspend fun evolutions(patientId: String): List<Evolution> =
        supabase.from("evolutions").select {
            filter { eq("patient_id", patientId) }
            order("entry_date", Order.DESCENDING)
        }.decodeList()

    suspend fun saveEvolution(e: Evolution) {
        if (e.id == null) supabase.from("evolutions").insert(e)
        else supabase.from("evolutions").update({
            set("entry_date", e.entryDate); set("content", e.content)
        }) { filter { eq("id", e.id) } }
    }

    suspend fun deleteEvolution(id: String) { supabase.from("evolutions").delete { filter { eq("id", id) } } }

    suspend fun importantInfo(patientId: String): List<ImportantInfo> =
        supabase.from("important_info").select {
            filter { eq("patient_id", patientId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun saveInfo(i: ImportantInfo) {
        if (i.id == null) supabase.from("important_info").insert(i)
        else supabase.from("important_info").update({
            set("category", i.category); set("title", i.title); set("details", i.details); set("ref_date", i.refDate)
        }) { filter { eq("id", i.id) } }
    }

    suspend fun deleteInfo(id: String) { supabase.from("important_info").delete { filter { eq("id", id) } } }

    // ---------------- Anexos ----------------
    suspend fun attachments(patientId: String): List<Attachment> =
        supabase.from("attachments").select {
            filter { eq("patient_id", patientId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun uploadAttachment(patientId: String, fileName: String, mime: String?, bytes: ByteArray) {
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = "$uid/$patientId/${UUID.randomUUID()}-$safe"
        supabase.storage.from("attachments").upload(path, bytes) { upsert = false }
        supabase.from("attachments").insert(
            Attachment(patientId = patientId, fileName = fileName, storagePath = path, mimeType = mime, sizeBytes = bytes.size.toLong())
        )
    }

    suspend fun downloadAttachment(a: Attachment): ByteArray =
        supabase.storage.from("attachments").downloadAuthenticated(a.storagePath)

    suspend fun deleteAttachment(a: Attachment) {
        runCatching { supabase.storage.from("attachments").delete(a.storagePath) }
        supabase.from("attachments").delete { filter { eq("id", a.id!!) } }
    }

    // ---------------- Pedidos e emergências ----------------
    suspend fun sendRequest(r: ChangeRequest) { supabase.from("change_requests").insert(r) }

    suspend fun requestsOf(patientId: String): List<ChangeRequest> =
        supabase.from("change_requests").select {
            filter { eq("patient_id", patientId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun request(id: String): ChangeRequest? =
        supabase.from("change_requests").select { filter { eq("id", id) } }.decodeSingleOrNull()

    suspend fun answerRequest(id: String, accepted: Boolean) {
        supabase.from("change_requests").update({
            set("status", if (accepted) "accepted" else "declined")
            set("resolved_at", Instant.now().toString())
        }) { filter { eq("id", id) } }
    }

    suspend fun sendEmergency(e: Emergency) { supabase.from("emergencies").insert(e) }

    suspend fun pendingEmergencies(): List<Emergency> =
        supabase.from("emergencies").select(Columns.raw("*, patients(full_name)")) {
            filter { exact("acknowledged_at", null) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun emergency(id: String): Emergency? =
        supabase.from("emergencies").select(Columns.raw("*, patients(full_name)")) { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun acknowledgeEmergency(id: String) {
        supabase.from("emergencies").update({ set("acknowledged_at", Instant.now().toString()) }) {
            filter { eq("id", id) }
        }
    }

    // ---------------- Notificações ----------------
    suspend fun notifications(): List<AppNotification> =
        supabase.from("notifications").select {
            filter { eq("recipient_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(200)
        }.decodeList()

    suspend fun markNotificationRead(id: String) {
        supabase.from("notifications").update({ set("read_at", Instant.now().toString()) }) { filter { eq("id", id) } }
    }

    suspend fun markAllNotificationsRead() {
        supabase.from("notifications").update({ set("read_at", Instant.now().toString()) }) {
            filter { eq("recipient_id", uid); exact("read_at", null) }
        }
    }

    suspend fun deleteNotification(id: String) {
        supabase.from("notifications").delete { filter { eq("id", id) } }
    }

    // ---------------- Mensagens ----------------
    suspend fun messages(patientId: String): List<ChatMessage> =
        supabase.from("messages").select {
            filter { eq("patient_id", patientId) }
            order("created_at", Order.ASCENDING)
            limit(500)
        }.decodeList()

    suspend fun sendMessage(patientId: String, body: String) {
        supabase.from("messages").insert(ChatMessage(patientId = patientId, senderId = uid, body = body))
    }

    suspend fun markMessagesRead(patientId: String) {
        supabase.from("messages").update({ set("read_at", Instant.now().toString()) }) {
            filter { eq("patient_id", patientId); neq("sender_id", uid); exact("read_at", null) }
        }
    }

    // ---------------- Paciente ----------------
    /** Registro de paciente do usuário logado (o vínculo mais recente). */
    suspend fun myPatientRecord(): Patient? =
        supabase.from("patients").select {
            filter { eq("user_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<Patient>().firstOrNull()

    suspend fun moods(patientId: String, from: LocalDate, to: LocalDate): List<MoodEntry> =
        supabase.from("mood_entries").select {
            filter {
                eq("patient_id", patientId)
                gte("entry_date", from.toString()); lte("entry_date", to.toString())
            }
            order("entry_date", Order.ASCENDING)
        }.decodeList()

    suspend fun saveMood(m: MoodEntry) {
        supabase.from("mood_entries").upsert(m.copy(id = null)) { onConflict = "patient_id,entry_date" }
    }

    suspend fun moodShares(patientId: String): List<MoodShare> =
        supabase.from("mood_shares").select {
            filter { eq("patient_id", patientId) }
            order("week_start", Order.DESCENDING)
        }.decodeList()

    suspend fun shareMoodWeek(patientId: String, weekStart: LocalDate) {
        supabase.from("mood_shares").insert(MoodShare(patientId = patientId, weekStart = weekStart.toString()))
    }
}
