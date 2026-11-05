// Cliente Supabase do navegador (usa a chave pública "anon"; a segurança vem do RLS do banco)
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

export const supabaseConfigured = Boolean(url && key);

let client: SupabaseClient | null = null;
export function sb(): SupabaseClient {
  if (!client) {
    if (!url || !key) throw new Error("Supabase não configurado (variáveis NEXT_PUBLIC_SUPABASE_* na Vercel).");
    client = createClient(url, key, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } });
  }
  return client;
}
