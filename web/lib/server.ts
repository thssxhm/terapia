// Código que roda SOMENTE no servidor (rotas /api). Nunca importe em componentes "use client".
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import Stripe from "stripe";

export function env(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Variável de ambiente ${name} não configurada na Vercel`);
  return v;
}

let _stripe: Stripe | null = null;
export function stripe(): Stripe {
  if (!_stripe) _stripe = new Stripe(env("STRIPE_SECRET_KEY"));
  return _stripe;
}

/** Cliente com a service role (ignora RLS) — usar só para atualizar a assinatura. */
export function admin(): SupabaseClient {
  return createClient(env("NEXT_PUBLIC_SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false },
  });
}

/** Valida o token do Supabase enviado pelo navegador e devolve o perfil do psicólogo. */
export async function psychologistFromRequest(req: Request) {
  const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const db = admin();
  const { data } = await db.auth.getUser(token);
  if (!data.user) return null;
  const { data: profile } = await db.from("profiles").select("*").eq("id", data.user.id).single();
  if (!profile || profile.role !== "psychologist") return null;
  return profile as { id: string; email: string | null; full_name: string | null; stripe_customer_id: string | null };
}

export function siteUrl(req: Request): string {
  return process.env.NEXT_PUBLIC_SITE_URL ?? new URL(req.url).origin;
}

export const json = (body: unknown, status = 200) => Response.json(body, { status });
