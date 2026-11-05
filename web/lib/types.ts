export interface Profile {
  id: string; role: string | null; full_name: string | null; crp: string | null; email: string | null; phone: string | null;
  patient_limit: number; trial_until: string | null;
  desktop_trial_until: string | null; desktop_status: string | null; desktop_period_end: string | null; stripe_customer_id: string | null;
}
export interface Slot { id?: string; patient_id?: string; weekday: number; start_time: string; duration_minutes: number; active?: boolean }
export interface Patient {
  id: string; psychologist_id: string; user_id: string | null; full_name: string; age: number | null; phone: string | null; email: string;
  reminder_enabled: boolean; reminder_minutes: number; schedule_slots?: Slot[];
}
export interface Session { id: string; patient_id: string; slot_id: string | null; starts_at: string; duration_minutes: number; status: string; patients?: { full_name: string } | null }
export interface PsyNote { session_id: string; patient_id: string; pre_note: string | null; post_note: string | null }
export interface Evolution { id?: string; patient_id: string; entry_date: string; content: string }
export interface Info { id?: string; patient_id: string; category: string; title: string; details: string | null; ref_date: string | null }
export interface Attachment { id: string; patient_id: string; file_name: string; storage_path: string; mime_type: string | null; size_bytes: number | null; created_at: string }
export interface ChangeRequest { id: string; patient_id: string; session_id: string | null; kind: string; message: string | null; status: string; created_at: string }
export interface Emergency { id: string; patient_id: string; level: string; message: string; created_at: string; acknowledged_at: string | null; patients?: { full_name: string } | null }
export interface Message { id: string; patient_id: string; sender_id: string; body: string; created_at: string; read_at: string | null }
export interface MoodEntry { patient_id: string; entry_date: string; mood: number; note: string | null }
export interface MoodShare { patient_id: string; week_start: string }
export interface AppNotification { id: string; patient_id: string | null; type: string; title: string; body: string | null; ref_id: string | null; read_at: string | null; created_at: string }

export const ANAMNESIS_FIELDS: [string, string][] = [
  ["queixa", "Queixa principal"],
  ["historico_queixa", "Histórico da queixa"],
  ["historico_familiar", "Histórico familiar"],
  ["saude", "Saúde física e medicações"],
  ["tratamentos", "Tratamentos psicológicos/psiquiátricos anteriores"],
  ["social", "Contexto social, familiar e profissional"],
  ["habitos", "Hábitos (sono, alimentação, atividade física, substâncias)"],
  ["expectativas", "Expectativas com a terapia"],
  ["observacoes", "Observações"],
];

export const MOODS: Record<number, [string, string]> = {
  5: ["😄", "Muito feliz"], 4: ["🙂", "Feliz"], 3: ["😐", "Neutro"], 2: ["🙁", "Triste"], 1: ["😢", "Muito triste"],
};
