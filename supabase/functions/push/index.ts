// Edge Function: push
// Disparada por um Database Webhook (INSERT na tabela public.notifications).
// Envia a notificação para todos os celulares do destinatário via Firebase Cloud Messaging (API v1).
// Docs FCM v1: https://firebase.google.com/docs/cloud-messaging/send-message
//
// Secrets: GOOGLE_SERVICE_ACCOUNT_JSON (conta de serviço do projeto Firebase)
//          WEBHOOK_SECRET (o mesmo valor colocado no header "x-webhook-secret" do webhook)

import { createClient } from "npm:@supabase/supabase-js@2";
import { googleAccessToken, loadServiceAccount } from "../_shared/google.ts";
import { json } from "../_shared/cors.ts";

interface NotificationRow {
  id: string;
  recipient_id: string;
  patient_id: string | null;
  type: string;
  title: string;
  body: string | null;
  ref_id: string | null;
}

// Os IDs de canal precisam ser iguais aos criados no app (NotificationChannels.kt)
function channelFor(type: string): string {
  if (type.startsWith("emergency")) return "emergencias";
  if (type === "message") return "mensagens";
  return "avisos";
}

Deno.serve(async (req) => {
  const secret = Deno.env.get("WEBHOOK_SECRET");
  if (secret && req.headers.get("x-webhook-secret") !== secret) return json({ error: "forbidden" }, 403);

  const payload = await req.json();
  const n: NotificationRow = payload.record;
  if (!n?.recipient_id) return json({ skipped: true });

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: tokens } = await admin.from("device_tokens").select("token").eq("user_id", n.recipient_id);
  if (!tokens?.length) return json({ sent: 0 });

  const sa = loadServiceAccount();
  const access = await googleAccessToken(sa, "https://www.googleapis.com/auth/firebase.messaging");
  const isEmergency = n.type.startsWith("emergency");

  let sent = 0;
  for (const { token } of tokens) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          // Somente "data": o app monta a notificação (ícone, cor, canal e pop-up de emergência)
          data: {
            notification_id: n.id,
            type: n.type,
            title: n.title,
            body: n.body ?? "",
            patient_id: n.patient_id ?? "",
            ref_id: n.ref_id ?? "",
            channel: channelFor(n.type),
          },
          android: { priority: "HIGH", ttl: isEmergency ? "86400s" : "604800s" },
        },
      }),
    });
    if (res.ok) {
      sent++;
    } else {
      const err = await res.text();
      // Token inválido/desinstalado: remove do banco
      if (res.status === 404 || err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT")) {
        await admin.from("device_tokens").delete().eq("token", token);
      } else {
        console.error("FCM:", res.status, err);
      }
    }
  }
  return json({ sent });
});
