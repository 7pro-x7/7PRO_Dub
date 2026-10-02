// ============================================================================
// _shared/fcm.ts
//
// Factored out of classroom-call-push, which already solved sending FCM push
// from an Edge Function using the Vault-stored Firebase service account. This
// module makes that reusable for anything else that needs to push a real,
// user-visible, audible notification (not just the silent data-only ring
// classroom-call-push sends) — starting with renewal reminders.
// ============================================================================
import { create, Header, Payload } from "https://deno.land/x/djwt@v3.0.2/mod.ts";
import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/** Reads the Firebase service account from Vault. Returns null if not configured yet. */
export async function readFcmServiceAccount(admin: SupabaseClient): Promise<ServiceAccount | null> {
  const { data: raw, error } = await admin.rpc("get_fcm_service_account_json");
  if (error || !raw) return null;
  try {
    return JSON.parse(raw as string) as ServiceAccount;
  } catch {
    console.error("fcm: stored service account secret is not valid JSON");
    return null;
  }
}

/** Exchanges the service account's signed JWT for a short-lived FCM access token. */
export async function getFcmAccessToken(account: ServiceAccount): Promise<string> {
  const pem = account.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const keyBytes = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const header: Header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const payload: Payload = {
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const assertion = await create(header, payload, cryptoKey);

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`FCM token exchange failed: ${res.status} ${await res.text()}`);
  const data = (await res.json()) as { access_token: string };
  return data.access_token;
}

export interface PushTarget {
  token: string;
  locale: string;
}

/**
 * Sends a real, visible, audible push (title + body, default sound) to a set of tokens —
 * unlike classroom-call-push's data-only ring, this shows in the system tray with sound even
 * when the app is fully closed, which is what a renewal/payment reminder needs. `localize`
 * picks the title/body for each token's own locale, so one call can address a mixed set of
 * Arabic- and English-language devices correctly.
 *
 * Returns how many sends succeeded and cleans up any token FCM reports as dead.
 */
export async function sendLocalizedPush(
  admin: SupabaseClient,
  account: ServiceAccount,
  targets: PushTarget[],
  localize: (locale: string) => { title: string; body: string },
  data: Record<string, string>,
): Promise<{ sent: number }> {
  if (targets.length === 0) return { sent: 0 };
  const accessToken = await getFcmAccessToken(account);
  const staleTokens: string[] = [];
  let sent = 0;

  await Promise.all(
    targets.map(async ({ token, locale }) => {
      const { title, body } = localize(locale);
      const res = await fetch(
        `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            message: {
              token,
              notification: { title, body },
              data,
              android: { priority: "high", notification: { sound: "default" } },
              apns: { payload: { aps: { sound: "default" } } },
            },
          }),
        },
      );
      if (res.ok) {
        sent += 1;
        return;
      }
      const errText = await res.text();
      if (errText.includes("UNREGISTERED") || errText.includes("NOT_FOUND")) {
        staleTokens.push(token);
      } else {
        console.error(`fcm: send failed for a token: ${res.status} ${errText}`);
      }
    }),
  );

  if (staleTokens.length > 0) {
    await admin.from("device_push_tokens").delete().in("token", staleTokens);
  }

  return { sent };
}
