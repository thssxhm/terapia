// Portal do cliente Stripe: trocar cartão, ver faturas e cancelar a assinatura
// Docs: https://docs.stripe.com/customer-management
import { json, psychologistFromRequest, siteUrl, stripe } from "../../../../lib/server";

export const runtime = "nodejs";

export async function POST(req: Request) {
  try {
    const psy = await psychologistFromRequest(req);
    if (!psy?.stripe_customer_id) return json({ error: "Nenhuma assinatura encontrada." }, 400);
    const portal = await stripe().billingPortal.sessions.create({
      customer: psy.stripe_customer_id,
      return_url: `${siteUrl(req)}/painel`,
      locale: "pt-BR",
    });
    return json({ url: portal.url });
  } catch (e) {
    console.error(e);
    return json({ error: e instanceof Error ? e.message : "Erro ao abrir o portal" }, 500);
  }
}
