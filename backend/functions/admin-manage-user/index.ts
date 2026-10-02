import { AuthError, corsHeaders, createAdminClient, json, requireAuth } from "../_shared/auth.ts";

/**
 * Owner-only account recovery: set a new password or change the sign-in email of any account.
 *
 * Passwords are stored as one-way hashes by Supabase Auth, so an existing password can never be
 * read back — only replaced. The new password is sent over TLS, handed straight to the Auth
 * admin API, and is never logged or written to the audit trail.
 *
 * Body: { action: "set_password", user_id, new_password }
 *       { action: "set_email",    user_id, new_email }
 */
interface Body {
  action?: "set_password" | "set_email";
  user_id?: string;
  new_password?: string;
  new_email?: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD = 8;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const caller = await requireAuth(req);
    const body = (await req.json()) as Body;
    const admin = createAdminClient();

    // Owner only — deliberately NOT delegated to admins via a permission flag: resetting someone's
    // password is the same as being able to sign in as them.
    const { data: me } = await admin.from("profiles").select("role").eq("id", caller.userId).maybeSingle();
    if (me?.role !== "OWNER") return json({ error: "FORBIDDEN" }, 403);

    const targetId = body.user_id?.trim();
    if (!targetId) return json({ error: "MISSING_USER_ID" }, 400);

    const { data: target } = await admin
      .from("profiles").select("id, role, email").eq("id", targetId).maybeSingle();
    if (!target) return json({ error: "USER_NOT_FOUND" }, 404);

    // Your own credentials go through the normal account settings flow, not this back door.
    if (target.id === caller.userId) return json({ error: "USE_ACCOUNT_SETTINGS_FOR_YOURSELF" }, 400);

    if (body.action === "set_password") {
      const pw = body.new_password ?? "";
      if (pw.length < MIN_PASSWORD) return json({ error: "PASSWORD_TOO_SHORT" }, 400);
      const { error } = await admin.auth.admin.updateUserById(targetId, { password: pw });
      if (error) return json({ error: error.message }, 400);
      await audit(admin, caller.userId, "USER_PASSWORD_RESET", targetId, {});
      return json({ ok: true });
    }

    if (body.action === "set_email") {
      const email = (body.new_email ?? "").trim().toLowerCase();
      if (!EMAIL_RE.test(email)) return json({ error: "INVALID_EMAIL" }, 400);
      // email_confirm: the owner is vouching for the address, so no confirmation mail is required.
      const { error } = await admin.auth.admin.updateUserById(targetId, { email, email_confirm: true });
      if (error) return json({ error: error.message }, 400);
      // The app reads the address from profiles, so keep the two in step.
      await admin.from("profiles").update({ email }).eq("id", targetId);
      await audit(admin, caller.userId, "USER_EMAIL_CHANGED", targetId, { old_email: target.email, new_email: email });
      return json({ ok: true });
    }

    return json({ error: "UNKNOWN_ACTION" }, 400);
  } catch (e) {
    if (e instanceof AuthError) return json({ error: e.message }, 401);
    return json({ error: (e as Error).message ?? "SERVER_ERROR" }, 500);
  }
});

async function audit(
  admin: ReturnType<typeof createAdminClient>,
  actorId: string,
  action: string,
  targetId: string,
  metadata: Record<string, unknown>,
) {
  // Best effort: a failed log line must not undo a recovery the owner already completed.
  await admin.from("audit_logs").insert({
    actor_id: actorId,
    actor_role: "OWNER",
    action,
    target_type: "user",
    target_id: targetId,
    metadata,
  });
}
