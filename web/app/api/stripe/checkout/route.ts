// Cria a página de pagamento do Stripe (assinatura mensal de R$ 14,99)
// Docs: https://docs.stripe.com/billing/subscriptions/build-subscriptions?ui=stripe-hosted
import { admin, env, json, psychologistFromRequest, siteUrl, stripe } from "../../../../lib/server";

export const runtime = "nodejs";

export async function POST(req: Request) {
  try {
    const psy = await psychologistFromRequest(req);
    if (!psy) return json({ error: "Faça login como psicólogo(a)." }, 401);

    // Um cliente Stripe por psicólogo (reaproveitado nas renovações e no portal)
    let customerId = psy.stripe_customer_id;
    if (!customerId) {
      const customer = await stripe().customers.create({
        email: psy.email ?? undefined,
        name: psy.full_name ?? undefined,
        metadata: { supabase_user_id: psy.id },
      });
      customerId = customer.id;
      await admin().from("profiles").update({ stripe_customer_id: customerId }).eq("id", psy.id);
    }

    const base = siteUrl(req);
    const session = await stripe().checkout.sessions.create({
      mode: "subscription",
      customer: customerId,
      client_reference_id: psy.id,
      line_items: [{ price: env("STRIPE_PRICE_ID"), quantity: 1 }],
      subscription_data: { metadata: { supabase_user_id: psy.id } },
      locale: "pt-BR",
      allow_promotion_codes: true,
      success_url: `${base}/painel?pagamento=ok`,
      cancel_url: `${base}/painel?pagamento=cancelado`,
    });
    return json({ url: session.url });
  } catch (e) {
    console.error(e);
    return json({ error: e instanceof Error ? e.message : "Erro ao criar pagamento" }, 500);
  }
}
