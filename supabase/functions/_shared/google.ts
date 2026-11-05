// Gera um access token OAuth2 do Google a partir de uma conta de serviço
// (usado para enviar push pelo Firebase Cloud Messaging e validar compras na Play Store).
// Docs: https://developers.google.com/identity/protocols/oauth2/service-account#httprest

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

export function loadServiceAccount(): ServiceAccount {
  const raw = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
  if (!raw) throw new Error("Secret GOOGLE_SERVICE_ACCOUNT_JSON não configurado");
  return JSON.parse(raw);
}

const b64url = (data: ArrayBuffer | string) => {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

const cache = new Map<string, { token: string; exp: number }>();

export async function googleAccessToken(sa: ServiceAccount, scope: string): Promise<string> {
  const hit = cache.get(scope);
  const now = Math.floor(Date.now() / 1000);
  if (hit && hit.exp - 60 > now) return hit.token;

  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const jwt = `${header}.${claims}.${b64url(sig)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`Falha ao obter token Google: ${await res.text()}`);
  const json = await res.json();
  cache.set(scope, { token: json.access_token, exp: now + json.expires_in });
  return json.access_token;
}
