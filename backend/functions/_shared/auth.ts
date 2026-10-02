import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export class AuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AuthError";
  }
}

export interface AuthUser {
  userId: string;
  email?: string;
}

/** Verifies the caller's Supabase session and returns the authenticated user. */
export async function requireAuth(req: Request): Promise<AuthUser> {
  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader) throw new AuthError("Missing token");
  const client = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) throw new AuthError("Unauthorized");
  return { userId: data.user.id, email: data.user.email ?? undefined };
}

export function createUserClient(req: Request): SupabaseClient {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
}

export function createAdminClient(): SupabaseClient {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
}

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const ISO2 = /^[A-Z]{2}$/;

/**
 * Resolves the buyer's country from trusted server-side signals only.
 * Order: edge geo headers -> IP geolocation -> platform default.
 * The client's own claim is never used for pricing.
 */
export async function resolveCountry(req: Request, fallback: string): Promise<string> {
  const headerCandidates = [
    req.headers.get("cf-ipcountry"),
    req.headers.get("x-vercel-ip-country"),
    req.headers.get("x-geo-country"),
  ];
  for (const c of headerCandidates) {
    const up = (c ?? "").toUpperCase();
    if (ISO2.test(up) && up !== "XX" && up !== "T1") return up;
  }

  const ip = (req.headers.get("x-forwarded-for") ?? "").split(",")[0]?.trim();
  if (ip && !ip.startsWith("10.") && !ip.startsWith("192.168.") && ip !== "127.0.0.1") {
    try {
      const res = await fetch(`https://ipapi.co/${encodeURIComponent(ip)}/country/`, {
        signal: AbortSignal.timeout(2500),
      });
      if (res.ok) {
        const up = (await res.text()).trim().toUpperCase();
        if (ISO2.test(up)) return up;
      }
    } catch (_e) {
      // geolocation is best-effort; fall through to the platform default
    }
  }
  return fallback.toUpperCase();
}
