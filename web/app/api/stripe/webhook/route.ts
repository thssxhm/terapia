// Webhook do Stripe: é AQUI que o acesso ao desktop é liberado/bloqueado automaticamente.
// O Stripe chama esta URL quando o pagamento é confirmado, renovado, falha ou é cancelado.
// Docs: https://docs.stripe.com/billing/subscriptions/webhooks
import Stripe from "stripe";
import { admin, env, json, stripe } from "../../../../lib/server";

export const runtime = "nodejs";

/** A partir da API "basil" (2025-03-31) o fim do período fica nos itens da assinatura. */
function periodEnd(sub: Stripe.Subscription): string | null {
  const s = sub as Stripe.Subscription & { current_period_end?: number };
  const ts = sub.items?.data?.[0]?.current_period_end ?? s.current_period_end;
  return ts ? new Date(ts * 1000).toISOString() : null;
}

async function saveSubscription(sub: Stripe.Subscription) {
  const customerId = typeof sub.customer === "string" ? sub.customer : sub.customer.id;
  const userId = sub.metadata?.supabase_user_id;
  const update = { desktop_status: sub.status, desktop_period_end: periodEnd(sub), stripe_customer_id: customerId };
  const db = admin();
  // Procura pelo cliente Stripe; se não achar, usa o id do usuário guardado nos metadados
  const { data } = await db.from("profiles").update(update).eq("stripe_customer_id", customerId).select("id");
  if (!data?.length && userId) await db.from("profiles").update(update).eq("id", userId);
}

export async function POST(req: Request) {
  const body = await req.text();   // corpo bruto: necessário para validar a assinatura
  const signature = req.headers.get("stripe-signature") ?? "";
  let event: Stripe.Event;
  try {
    // Versão assíncrona + Web Crypto: funciona no Cloudflare Workers
    event = await stripe().webhooks.constructEventAsync(
      body, signature, env("STRIPE_WEBHOOK_SECRET"), undefined, Stripe.createSubtleCryptoProvider(),
    );
  } catch (e) {
    console.error("Assinatura do webhook inválida", e);
    return json({ error: "assinatura inválida" }, 400);
  }

  try {
    switch (event.type) {
      case "checkout.session.completed": {
        const s = event.data.object as Stripe.Checkout.Session;
        if (s.mode === "subscription" && s.subscription) {
          const sub = await stripe().subscriptions.retrieve(
            typeof s.subscription === "string" ? s.subscription : s.subscription.id,
          );
          if (!sub.metadata?.supabase_user_id && s.client_reference_id) sub.metadata = { ...sub.metadata, supabase_user_id: s.client_reference_id };
          await saveSubscription(sub);
        }
        break;
      }
      case "customer.subscription.created":
      case "customer.subscription.updated":
      case "customer.subscription.deleted":
      case "customer.subscription.paused":
      case "customer.subscription.resumed":
        await saveSubscription(event.data.object as Stripe.Subscription);
        break;
      case "invoice.paid":
      case "invoice.payment_failed": {
        const inv = event.data.object as Stripe.Invoice & { subscription?: string | null };
        const subId = inv.parent?.subscription_details?.subscription ?? inv.subscription;
        if (subId) await saveSubscription(await stripe().subscriptions.retrieve(typeof subId === "string" ? subId : subId.id));
        break;
      }
    }
  } catch (e) {
    console.error("Erro ao processar webhook", event.type, e);
    return json({ error: "erro interno" }, 500);   // o Stripe tenta de novo automaticamente
  }
  return json({ received: true });
}
