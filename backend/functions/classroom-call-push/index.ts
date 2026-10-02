// ============================================================================
// classroom-call-push
//
// Called by the app right after a session is created (see
// ClassroomRepository.createSession / notifyIncomingCall). Sends a
// high-priority, data-only FCM push to every currently-INVITED student's
// device(s), which is what makes IncomingClassroomCallActivity ring — even
// with the app closed or the phone locked, the same way a normal phone call
// or a WhatsApp call works.
//
// The Firebase service account credential lives in Supabase Vault (as the
// secret named `fcm_service_account_json`), read here through the
// `get_fcm_service_account_json()` SECURITY DEFINER function — that function
// is only grantable to service_role, so only this admin-client call can ever
// read it. Vault is used instead of a Deno.env secret because this project's
// current tooling has no way to set Edge Function environment secrets
// directly; functionally it's the same "server-side only" guarantee.
//
// This never trusts the client's idea of who's invited: authorization and
// the student list are both re-derived here from the database, the same
// way classroom-jitsi-token re-derives role rather than trusting the caller.
// ============================================================================
import { create, Header, Payload } from "https://deno.land/x/djwt@v3.0.2/mod.ts";
import { AuthError, corsHeaders, createAdminClient, json, requireAuth } from "../_shared/auth.ts";

interface PushRequestBody {
  session_id: string;
}

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/** Exchanges the service account's signed JWT for a short-lived FCM access token. */
async function getFcmAccessToken(account: ServiceAccount): Promise<string> {
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

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as PushRequestBody;
    if (!body.session_id) return json({ error: "MISSING_SESSION_ID" }, 400);

    const admin = createAdminClient();

    const { data: rawServiceAccount, error: vaultError } = await admin.rpc("get_fcm_service_account_json");
    if (vaultError) {
      console.error("classroom-call-push: could not read FCM service account from vault", vaultError);
      return json({ error: "PUSH_NOT_CONFIGURED" }, 501);
    }
    if (!rawServiceAccount) {
      // Not configured yet — the caller (the app, right after creating the session) treats this
      // as non-fatal and just skips the call-style push.
      return json({ error: "PUSH_NOT_CONFIGURED" }, 501);
    }

    let account: ServiceAccount;
    try {
      account = JSON.parse(rawServiceAccount as string);
    } catch {
      console.error("classroom-call-push: stored FCM service account secret is not valid JSON");
      return json({ error: "PUSH_NOT_CONFIGURED" }, 501);
    }

    const { data: session, error: sessionError } = await admin
      .from("classroom_sessions")
      .select("id, teacher_id, title, status")
      .eq("id", body.session_id)
      .maybeSingle();
    if (sessionError || !session) return json({ error: "SESSION_NOT_FOUND" }, 404);

    const isTeacher = session.teacher_id === user.userId;
    let isStaff = false;
    if (!isTeacher) {
      const { data: profile } = await admin.from("profiles").select("role").eq("id", user.userId).maybeSingle();
      if (profile?.role === "OWNER") {
        isStaff = true;
      } else if (profile?.role === "ADMIN") {
        const { data: perm } = await admin
          .from("admin_permissions")
          .select("id")
          .eq("user_id", user.userId)
          .eq("permission", "classroom.manage")
          .maybeSingle();
        isStaff = !!perm;
      }
    }
    if (!isTeacher && !isStaff) return json({ error: "NOT_AUTHORIZED" }, 403);

    const { data: callerProfile } = await admin
      .from("profiles")
      .select("full_name")
      .eq("id", session.teacher_id)
      .maybeSingle();
    const callerName = callerProfile?.full_name ?? "";

    // Only ring people not already in the call — someone who already joined (or was already
    // invited and declined/left) doesn't need their phone ringing again.
    const { data: invited, error: invitedError } = await admin
      .from("classroom_participants")
      .select("user_id")
      .eq("session_id", body.session_id)
      .eq("role_in_session", "STUDENT")
      .eq("status", "INVITED");
    if (invitedError) throw invitedError;

    const studentIds = (invited ?? []).map((row) => row.user_id as string);
    if (studentIds.length === 0) return json({ sent: 0, students: 0 });

    const { data: tokenRows, error: tokenError } = await admin
      .from("device_push_tokens")
      .select("token")
      .in("user_id", studentIds);
    if (tokenError) throw tokenError;

    const tokens = (tokenRows ?? []).map((row) => row.token as string);
    if (tokens.length === 0) return json({ sent: 0, students: studentIds.length });

    const accessToken = await getFcmAccessToken(account);
    const staleTokens: string[] = [];
    let sent = 0;

    await Promise.all(
      tokens.map(async (token) => {
        const res = await fetch(
          `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${accessToken}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              message: {
                token,
                // Data-only on purpose: the app itself builds the ringing full-screen UI
                // (IncomingClassroomCallActivity) rather than a generic system notification,
                // exactly like a VoIP/calling app — a plain "notification" payload here would
                // just show a normal tray notification instead of ringing.
                data: {
                  type: "CLASSROOM_CALL",
                  session_id: body.session_id,
                  title: session.title ?? "",
                  caller_name: callerName,
                },
                android: {
                  priority: "high",
                },
              },
            }),
          },
        );
        if (res.ok) {
          sent += 1;
          return;
        }
        const errText = await res.text();
        // UNREGISTERED / NOT_FOUND means the token is dead (uninstalled, reset) — worth
        // cleaning up so future calls don't keep paying for a doomed request.
        if (errText.includes("UNREGISTERED") || errText.includes("NOT_FOUND")) {
          staleTokens.push(token);
        } else {
          console.error(`classroom-call-push: FCM send failed for a token: ${res.status} ${errText}`);
        }
      }),
    );

    if (staleTokens.length > 0) {
      await admin.from("device_push_tokens").delete().in("token", staleTokens);
    }

    return json({ sent, students: studentIds.length });
  } catch (e) {
    if (e instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("classroom-call-push error", e);
    return json({ error: "UNKNOWN" }, 500);
  }
});
