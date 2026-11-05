// Cliente Supabase do navegador (usa a chave pública "anon"; a segurança vem do RLS do banco)
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

let config: { url: string; key: string } | null = null;
let client: SupabaseClient | null = null;

/** Carrega URL e chave pública (do build, se existirem, ou da rota /api/config). */
export async function loadSupabaseConfig(): Promise<boolean> {
  if (config) return true;
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (url && key) { config = { url, key }; return true; }
  try {
    const r = await fetch("/api/config", { cache: "no-store" });
    const j = await r.json();
    if (j.supabaseUrl && j.supabaseAnonKey) { config = { url: j.supabaseUrl, key: j.supabaseAnonKey }; return true; }
  } catch { /* sem conexão */ }
  return false;
}

export function sb(): SupabaseClient {
  if (!client) {
    if (!config) throw new Error("Supabase não configurado (variáveis NEXT_PUBLIC_SUPABASE_* na Cloudflare).");
    client = createClient(config.url, config.key, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } });
  }
  return client;
}
