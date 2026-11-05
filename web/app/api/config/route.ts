// Entrega ao navegador as chaves PÚBLICAS do Supabase (URL e anon key).
// Assim elas são lidas na hora, sem precisar republicar o site quando mudarem.
export const dynamic = "force-dynamic";

export function GET() {
  return Response.json(
    { supabaseUrl: process.env.NEXT_PUBLIC_SUPABASE_URL ?? null, supabaseAnonKey: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? null },
    { headers: { "Cache-Control": "no-store" } },
  );
}
