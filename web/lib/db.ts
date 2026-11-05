// Leituras e gravações do painel desktop (mesmas tabelas e regras do app Android).
import { sb } from "./supabase";
import type {
  AppNotification, Attachment, ChangeRequest, Emergency, Evolution, Info, Message, MoodEntry, MoodShare,
  Patient, Profile, PsyNote, Session, Slot,
} from "./types";

type Res<T> = { data: T | null; error: { message: string } | null };
async function q<T>(p: PromiseLike<Res<T>>): Promise<T> {
  const { data, error } = await p;
  if (error) throw new Error(error.message);
  return data as T;
}

export async function uid(): Promise<string> {
  const { data } = await sb().auth.getUser();
  if (!data.user) throw new Error("Sessão expirada. Entre novamente.");
  return data.user.id;
}

// ---------- Perfil e acesso ----------
export const myProfile = async () => q<Profile | null>(sb().from("profiles").select("*").eq("id", await uid()).maybeSingle());
export const becomePsychologist = (name: string, crp: string) => q(sb().rpc("become_psychologist", { p_full_name: name, p_crp: crp }));
export const hasDesktopAccess = () => q<boolean>(sb().rpc("has_desktop_access"));
export const startDesktopTrial = () => q<string | null>(sb().rpc("start_desktop_trial"));
export const updateMyProfile = async (full_name: string, crp: string, phone: string | null) =>
  q(sb().from("profiles").update({ full_name, crp, phone }).eq("id", await uid()));

/** Chama as rotas /api/stripe/* enviando o token de login. */
export async function stripeRedirect(path: "checkout" | "portal") {
  const { data } = await sb().auth.getSession();
  const res = await fetch(`/api/stripe/${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${data.session?.access_token ?? ""}` },
  });
  const body = await res.json();
  if (!res.ok || !body.url) throw new Error(body.error ?? "Não foi possível abrir o pagamento.");
  window.location.href = body.url;
}

// ---------- Pacientes ----------
export const myPatients = async () =>
  q<Patient[]>(sb().from("patients").select("*, schedule_slots(*)").eq("psychologist_id", await uid()).order("full_name"));
export const patient = (id: string) => q<Patient>(sb().from("patients").select("*, schedule_slots(*)").eq("id", id).single());
export const updatePatient = (id: string, full_name: string, age: number | null, phone: string | null) =>
  q(sb().from("patients").update({ full_name, age, phone }).eq("id", id));

export interface InviteResult { patient?: Patient; play_store_url?: string; email_sent?: boolean; error?: string }
export async function invitePatient(body: Record<string, unknown>): Promise<InviteResult> {
  const { data, error } = await sb().functions.invoke("invite-patient", { body });
  if (error) throw new Error(error.message);
  return data as InviteResult;
}

export async function deletePatient(p: Patient) {
  const files = await attachments(p.id);
  if (files.length) await sb().storage.from("attachments").remove(files.map((f) => f.storage_path));
  await q(sb().from("patients").delete().eq("id", p.id));
}

// ---------- Horários e sessões ----------
export const addSlot = (s: Slot) => q(sb().from("schedule_slots").insert(s));
export const updateSlot = (s: Slot) =>
  q(sb().from("schedule_slots").update({ weekday: s.weekday, start_time: s.start_time, duration_minutes: s.duration_minutes }).eq("id", s.id!));
export const deleteSlot = (id: string) => q(sb().from("schedule_slots").delete().eq("id", id));

export const sessionsOf = (patientId: string) =>
  q<Session[]>(sb().from("sessions").select("*").eq("patient_id", patientId).order("starts_at"));
export const sessionsBetween = (from: Date, to: Date) =>
  q<Session[]>(sb().from("sessions").select("*, patients(full_name)").gte("starts_at", from.toISOString()).lt("starts_at", to.toISOString()).order("starts_at"));
export const session = (id: string) => q<Session>(sb().from("sessions").select("*, patients(full_name)").eq("id", id).single());
export const addSession = (patient_id: string, starts_at: string, duration_minutes: number) =>
  q(sb().from("sessions").insert({ patient_id, starts_at, duration_minutes }));
export const updateSession = (id: string, starts_at: string, duration_minutes: number, status: string) =>
  q(sb().from("sessions").update({ starts_at, duration_minutes, status }).eq("id", id));
export const setSessionStatus = (id: string, status: string) => q(sb().from("sessions").update({ status }).eq("id", id));
export const deleteSession = (id: string) => q(sb().from("sessions").delete().eq("id", id));

export const psyNotes = (patientId: string) =>
  q<PsyNote[]>(sb().from("psychologist_session_notes").select("*").eq("patient_id", patientId));
export const savePsyNote = (n: PsyNote) => q(sb().from("psychologist_session_notes").upsert(n, { onConflict: "session_id" }));

// ---------- Prontuário ----------
export const anamnesis = async (patientId: string) =>
  (await q<{ data: Record<string, string> } | null>(sb().from("anamnesis").select("data").eq("patient_id", patientId).maybeSingle()))?.data ?? {};
export const saveAnamnesis = (patient_id: string, data: Record<string, string>) =>
  q(sb().from("anamnesis").upsert({ patient_id, data, updated_at: new Date().toISOString() }, { onConflict: "patient_id" }));

export const evolutions = (patientId: string) =>
  q<Evolution[]>(sb().from("evolutions").select("*").eq("patient_id", patientId).order("entry_date", { ascending: false }));
export const saveEvolution = (e: Evolution) =>
  e.id ? q(sb().from("evolutions").update({ entry_date: e.entry_date, content: e.content }).eq("id", e.id))
       : q(sb().from("evolutions").insert({ patient_id: e.patient_id, entry_date: e.entry_date, content: e.content }));
export const deleteEvolution = (id: string) => q(sb().from("evolutions").delete().eq("id", id));

export const importantInfo = (patientId: string) =>
  q<Info[]>(sb().from("important_info").select("*").eq("patient_id", patientId).order("created_at", { ascending: false }));
export const saveInfo = (i: Info) => {
  const row = { category: i.category, title: i.title, details: i.details, ref_date: i.ref_date };
  return i.id ? q(sb().from("important_info").update(row).eq("id", i.id))
              : q(sb().from("important_info").insert({ ...row, patient_id: i.patient_id }));
};
export const deleteInfo = (id: string) => q(sb().from("important_info").delete().eq("id", id));

// ---------- Anexos ----------
export const attachments = (patientId: string) =>
  q<Attachment[]>(sb().from("attachments").select("*").eq("patient_id", patientId).order("created_at", { ascending: false }));

export async function uploadAttachment(patientId: string, file: File) {
  if (file.size > 20 * 1024 * 1024) throw new Error("Arquivo maior que 20 MB.");
  const safe = file.name.replace(/[^A-Za-z0-9._-]/g, "_");
  const path = `${await uid()}/${patientId}/${crypto.randomUUID()}-${safe}`;
  const { error } = await sb().storage.from("attachments").upload(path, file, { contentType: file.type || undefined });
  if (error) throw new Error(error.message);
  await q(sb().from("attachments").insert({
    patient_id: patientId, file_name: file.name, storage_path: path, mime_type: file.type || null, size_bytes: file.size,
  }));
}

export async function openAttachment(a: Attachment) {
  const { data, error } = await sb().storage.from("attachments").createSignedUrl(a.storage_path, 120);
  if (error) throw new Error(error.message);
  window.open(data.signedUrl, "_blank", "noopener");
}

export async function deleteAttachment(a: Attachment) {
  await sb().storage.from("attachments").remove([a.storage_path]);
  await q(sb().from("attachments").delete().eq("id", a.id));
}

// ---------- Pedidos, emergências, avisos ----------
export const requestsOf = (patientId: string) =>
  q<ChangeRequest[]>(sb().from("change_requests").select("*").eq("patient_id", patientId).order("created_at", { ascending: false }));
export const request = (id: string) => q<ChangeRequest | null>(sb().from("change_requests").select("*").eq("id", id).maybeSingle());
export const answerRequest = (id: string, accepted: boolean) =>
  q(sb().from("change_requests").update({ status: accepted ? "accepted" : "declined", resolved_at: new Date().toISOString() }).eq("id", id));

export const pendingEmergencies = () =>
  q<Emergency[]>(sb().from("emergencies").select("*, patients(full_name)").is("acknowledged_at", null).order("created_at"));
export const emergency = (id: string) =>
  q<Emergency | null>(sb().from("emergencies").select("*, patients(full_name)").eq("id", id).maybeSingle());
export const acknowledgeEmergency = (id: string) =>
  q(sb().from("emergencies").update({ acknowledged_at: new Date().toISOString() }).eq("id", id));

export const notifications = async () =>
  q<AppNotification[]>(sb().from("notifications").select("*").eq("recipient_id", await uid()).order("created_at", { ascending: false }).limit(200));
export const markNotificationRead = (id: string) => q(sb().from("notifications").update({ read_at: new Date().toISOString() }).eq("id", id));
export const markAllRead = async () =>
  q(sb().from("notifications").update({ read_at: new Date().toISOString() }).eq("recipient_id", await uid()).is("read_at", null));
export const deleteNotification = (id: string) => q(sb().from("notifications").delete().eq("id", id));

// ---------- Mensagens ----------
export const messages = (patientId: string) =>
  q<Message[]>(sb().from("messages").select("*").eq("patient_id", patientId).order("created_at").limit(500));
export const sendMessage = async (patient_id: string, body: string) =>
  q(sb().from("messages").insert({ patient_id, body, sender_id: await uid() }));
export const markMessagesRead = async (patientId: string) =>
  q(sb().from("messages").update({ read_at: new Date().toISOString() }).eq("patient_id", patientId).neq("sender_id", await uid()).is("read_at", null));

// ---------- Humor compartilhado ----------
export async function sharedMoods(patientId: string): Promise<{ shares: MoodShare[]; moods: MoodEntry[] }> {
  const shares = await q<MoodShare[]>(sb().from("mood_shares").select("*").eq("patient_id", patientId).order("week_start", { ascending: false }));
  if (!shares.length) return { shares, moods: [] };
  const moods = await q<MoodEntry[]>(sb().from("mood_entries").select("*").eq("patient_id", patientId)
    .gte("entry_date", shares[shares.length - 1].week_start).order("entry_date"));
  return { shares, moods };
}
