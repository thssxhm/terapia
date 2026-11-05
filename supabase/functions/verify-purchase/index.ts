// Edge Function: verify-purchase
// O app envia o token da assinatura comprada na Google Play; aqui validamos no servidor
// (nunca confie só no app) e atualizamos o limite de pacientes do psicólogo.
// Docs: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get
//
// IDs dos produtos (crie iguais no Play Console > Monetizar > Assinaturas):
//   plano_10, plano_20, plano_30, plano_40, plano_50  → número = pacientes liberados
//
// Secrets: GOOGLE_SERVICE_ACCOUNT_JSON (conta de serviço com acesso ao Play Console)
//          ANDROID_PACKAGE_NAME (ex.: app.terapia.mobile)

import { createClient } from "npm:@supabase/supabase-js@2";
import { googleAccessToken, loadServiceAccount } from "../_shared/google.ts";
import { corsHeaders, json } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const url = Deno.env.get("SUPABASE_URL")!;
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const asUser = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await asUser.auth.getUser();
  if (!user) return json({ error: "Não autenticado" }, 401);

  const { product_id, purchase_token } = await req.json();
  const patients = Number(/^plano_(\d+)$/.exec(product_id ?? "")?.[1] ?? 0);
  if (!patients || !purchase_token) return json({ error: "Produto inválido" }, 400);

  const pkg = Deno.env.get("ANDROID_PACKAGE_NAME")!;
  const access = await googleAccessToken(loadServiceAccount(), "https://www.googleapis.com/auth/androidpublisher");
  const res = await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/subscriptionsv2/tokens/${purchase_token}`,
    { headers: { Authorization: `Bearer ${access}` } },
  );
  if (!res.ok) return json({ error: `Play: ${await res.text()}` }, 400);
  const sub = await res.json();

  const state: string = sub.subscriptionState;
  const expires = sub.lineItems?.[0]?.expiryTime ?? null;
  const active = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"].includes(state);

  await admin.from("subscriptions").upsert({
    user_id: user.id,
    product_id,
    purchase_token,
    patients,
    expires_at: expires,
    state,
    updated_at: new Date().toISOString(),
  }, { onConflict: "purchase_token" });

  // Limite = soma das assinaturas ativas do usuário
  const { data: subs } = await admin.from("subscriptions").select("patients, state, expires_at").eq("user_id", user.id);
  const limit = (subs ?? [])
    .filter((s) => ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"].includes(s.state) &&
      (!s.expires_at || new Date(s.expires_at) > new Date()))
    .reduce((acc, s) => acc + s.patients, 0);
  await admin.from("profiles").update({ patient_limit: limit }).eq("id", user.id);

  return json({ active, patient_limit: limit });
});
