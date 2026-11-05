// Edge Function: invite-patient
// Chamado pelo app do psicólogo ao cadastrar um paciente (ou para reenviar o convite).
// 1) cria (ou reaproveita) a conta do paciente pelo e-mail
// 2) grava o paciente e os horários fixos (o banco gera as sessões)
// 3) gera um código de acesso de 6 dígitos e envia por e-mail com o link da Play Store
//
// Secrets necessários (Dashboard > Edge Functions > Secrets):
//   RESEND_API_KEY   – chave da https://resend.com (envio de e-mail)
//   EMAIL_FROM       – ex.: "Terapia <convite@seudominio.com.br>"
//   PLAY_STORE_URL   – ex.: https://play.google.com/store/apps/details?id=app.terapia.mobile
// SUPABASE_URL, SUPABASE_ANON_KEY e SUPABASE_SERVICE_ROLE_KEY já existem por padrão.
//
// Erros de validação voltam com HTTP 200 e o campo "error" para o app mostrar a mensagem ao usuário.

import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, json } from "../_shared/cors.ts";

interface Slot { weekday: number; start_time: string; duration_minutes?: number }
interface Body {
  resend_patient_id?: string;
  full_name?: string;
  age?: number | null;
  phone?: string | null;
  email?: string;
  slots?: Slot[];
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const url = Deno.env.get("SUPABASE_URL")!;
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const asUser = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });

  const { data: { user } } = await asUser.auth.getUser();
  if (!user) return json({ error: "Não autenticado" });

  const { data: psy } = await admin.from("profiles").select("*").eq("id", user.id).single();
  if (psy?.role !== "psychologist") return json({ error: "Apenas psicólogos podem cadastrar pacientes" });

  const body: Body = await req.json();
  const playUrl = Deno.env.get("PLAY_STORE_URL") ?? "";

  let patient: Record<string, unknown>;

  if (body.resend_patient_id) {
    // Reenviar convite de um paciente já cadastrado
    const { data, error } = await asUser.from("patients").select("*").eq("id", body.resend_patient_id).single();
    if (error || !data) return json({ error: "Paciente não encontrado" });
    patient = data;
  } else {
    const email = (body.email ?? "").trim().toLowerCase();
    if (!email || !body.full_name) return json({ error: "Nome e e-mail são obrigatórios" });

    // 1) Conta do paciente
    let userId: string | null = null;
    const { data: existing } = await admin.from("profiles").select("id, role").eq("email", email).maybeSingle();
    if (existing) {
      if (existing.role === "psychologist") {
        return json({ error: "Este e-mail pertence a uma conta de psicólogo" });
      }
      userId = existing.id;
    } else {
      const { data: created, error } = await admin.auth.admin.createUser({
        email,
        email_confirm: true,
        user_metadata: { full_name: body.full_name },
      });
      if (error) return json({ error: `Erro ao criar conta: ${error.message}` });
      userId = created.user.id;
    }
    await admin.from("profiles").update({ role: "patient", full_name: body.full_name, phone: body.phone })
      .eq("id", userId).is("role", null);

    // 2) Paciente (o gatilho do banco confere o limite do plano)
    const { data: inserted, error: insErr } = await asUser.from("patients").insert({
      psychologist_id: user.id,
      user_id: userId,
      full_name: body.full_name,
      age: body.age ?? null,
      phone: body.phone ?? null,
      email,
    }).select().single();
    if (insErr) {
      const msg = insErr.code === "23505" ? "Você já cadastrou um paciente com este e-mail" : insErr.message;
      return json({ error: msg });
    }
    patient = inserted;

    if (body.slots?.length) {
      const { error: slotErr } = await asUser.from("schedule_slots").insert(
        body.slots.map((s) => ({
          patient_id: inserted.id,
          weekday: s.weekday,
          start_time: s.start_time,
          duration_minutes: s.duration_minutes ?? 50,
        })),
      );
      if (slotErr) return json({ error: `Paciente criado, mas houve erro nos horários: ${slotErr.message}` });
    }
  }

  // 3) Código de acesso (OTP de 6 dígitos) + e-mail
  const { data: link, error: linkErr } = await admin.auth.admin.generateLink({
    type: "magiclink",
    email: patient.email as string,
  });
  if (linkErr) return json({ error: `Erro ao gerar código: ${linkErr.message}` });
  const code = link.properties.email_otp;

  const emailSent = await sendInviteEmail({
    to: patient.email as string,
    patientName: patient.full_name as string,
    psyName: psy.full_name ?? "seu(sua) psicólogo(a)",
    crp: psy.crp ?? "",
    code,
    playUrl,
  });

  return json({ patient, play_store_url: playUrl, email_sent: emailSent });
});

async function sendInviteEmail(p: {
  to: string; patientName: string; psyName: string; crp: string; code: string; playUrl: string;
}): Promise<boolean> {
  const key = Deno.env.get("RESEND_API_KEY");
  if (!key) {
    console.warn("RESEND_API_KEY ausente: e-mail não enviado");
    return false;
  }
  const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]!));
  const html = `
  <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#2d2640">
    <h2 style="color:#7c5cc4">Bem-vindo(a) ao Terapia 💬</h2>
    <p>Olá, ${esc(p.patientName)}!</p>
    <p><b>${esc(p.psyName)}</b>${p.crp ? ` (CRP ${esc(p.crp)})` : ""} liberou seu acesso ao aplicativo <b>Terapia</b>.</p>
    <ol>
      <li>Instale o app: <a href="${p.playUrl}">${p.playUrl}</a></li>
      <li>Abra o app, toque em <b>Sou paciente</b> e informe este e-mail.</li>
      <li>Digite o código abaixo:</li>
    </ol>
    <p style="font-size:32px;letter-spacing:8px;font-weight:bold;background:#efe9fb;padding:16px;text-align:center;border-radius:12px">${p.code}</p>
    <p>Depois de entrar você poderá criar uma senha, entrar com o Google ou ativar a biometria do celular.</p>
    <p style="font-size:12px;color:#777">O código expira em 1 hora. Se expirar, peça um novo código na tela de entrada do app.</p>
  </div>`;

  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from: Deno.env.get("EMAIL_FROM") ?? "Terapia <onboarding@resend.dev>",
      to: [p.to],
      subject: `${p.psyName} liberou seu acesso ao Terapia`,
      html,
    }),
  });
  if (!res.ok) console.error("Resend:", await res.text());
  return res.ok;
}
