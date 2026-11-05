// Formatação de datas em português (fuso do navegador)
const L = "pt-BR";
export const WEEKDAYS = ["", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"];
export const WEEKDAYS_SHORT = ["", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

export const fullDate = (iso: string) => {
  const s = new Date(iso).toLocaleString(L, { weekday: "long", day: "2-digit", month: "long", hour: "2-digit", minute: "2-digit" });
  return s.charAt(0).toUpperCase() + s.slice(1);
};
export const shortDateTime = (iso: string) => new Date(iso).toLocaleString(L, { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
export const time = (iso: string) => new Date(iso).toLocaleTimeString(L, { hour: "2-digit", minute: "2-digit" });
export const dateBR = (ymd: string) => { const [y, m, d] = ymd.slice(0, 10).split("-"); return `${d}/${m}/${y}`; };
export const dayMonth = (ymd: string) => dateBR(ymd).slice(0, 5);
export const hhmm = (t: string) => t.slice(0, 5);

/** Data local no formato yyyy-mm-dd */
export const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
export const addDays = (s: string, n: number) => { const d = new Date(s + "T12:00:00"); d.setDate(d.getDate() + n); return ymd(d); };
/** ISO do dia da semana: 1 = segunda … 7 = domingo */
export const isoWeekday = (d: Date) => ((d.getDay() + 6) % 7) + 1;

export const relative = (iso: string) => {
  const d = new Date(iso);
  const today = ymd(new Date());
  const day = ymd(d);
  const t = time(iso);
  if (day === today) return `Hoje ${t}`;
  if (day === addDays(today, -1)) return `Ontem ${t}`;
  return `${dateBR(day)} ${t}`;
};

export const statusLabel = (s: string) => ({ confirmed: "Confirmada", cancelled: "Cancelada", done: "Realizada" } as Record<string, string>)[s] ?? "Agendada";

export function errorMessage(e: unknown): string {
  const m = e instanceof Error ? e.message : typeof e === "object" && e && "message" in e ? String((e as { message: unknown }).message) : String(e);
  if (m.includes("Invalid login credentials")) return "E-mail ou senha incorretos.";
  if (m.includes("Token has expired") || m.includes("otp_expired")) return "Código expirado ou inválido. Peça um novo código.";
  if (m.includes("Email not confirmed")) return "Confirme seu e-mail pelo link que enviamos.";
  if (m.includes("Failed to fetch")) return "Sem conexão com a internet.";
  return m || "Ocorreu um erro. Tente novamente.";
}
