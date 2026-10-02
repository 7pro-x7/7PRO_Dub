// ============================================================================
// classroom-jitsi-token
//
// يصدر JWT قصير العمر لسيرفر Jitsi اللي الحصة شغالة عليه — بس لو السيرفر ده متسجّل في
// لوحة "سيرفرات الاجتماعات" ومعاه JWT App ID + Secret (classroom_conference_servers +
// classroom_conference_server_secrets). السر محدش يقدر يقراه غير الفانكشن دي
// (service role). لو السيرفر مفيهوش JWT (زي المجتمعي) بيرجع 501 JWT_NOT_CONFIGURED
// والتطبيق/المتصفح بيدخلوا من غير توكن عادي.
//
// متغيرات JITSI_APP_ID / JITSI_APP_SECRET القديمة لسه شغالة كاحتياطي لو اتحطّت.
//
// الصلاحية بتتعاد هنا على السيرفر بنفس منطق classroom_join() — عمرنا ما بنصدّق
// العميل في دوره.
// ============================================================================
import { create, getNumericDate, Header, Payload } from "https://deno.land/x/djwt@v3.0.2/mod.ts";
import { AuthError, corsHeaders, createAdminClient, json, requireAuth } from "../_shared/auth.ts";

interface TokenRequestBody {
  session_id: string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as TokenRequestBody;
    if (!body.session_id) return json({ error: "MISSING_SESSION_ID" }, 400);

    const admin = createAdminClient();

    // Re-derive authorization and role server-side exactly the way classroom_join() does —
    // never trust anything the client says about its own role here.
    const { data: session, error: sessionError } = await admin
      .from("classroom_sessions")
      .select("id, teacher_id, room_name, status, jitsi_domain")
      .eq("id", body.session_id)
      .maybeSingle();
    if (sessionError || !session) return json({ error: "SESSION_NOT_FOUND" }, 404);
    if (session.status === "CANCELED") return json({ error: "SESSION_CANCELED" }, 409);
    if (session.status === "ENDED") return json({ error: "SESSION_ENDED" }, 409);

    // Which credentials? The ones of the server THIS session lives on — a session that started
    // before the owner switched servers keeps getting tokens for its own server.
    const creds = await credentialsFor(admin, String(session.jitsi_domain ?? ""));
    if (!creds) return json({ error: "JWT_NOT_CONFIGURED" }, 501);
    const { appId, appSecret } = creds;

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

    let isParticipant = false;
    if (!isTeacher && !isStaff) {
      const { data: participant } = await admin
        .from("classroom_participants")
        .select("status")
        .eq("session_id", body.session_id)
        .eq("user_id", user.userId)
        .maybeSingle();
      isParticipant = !!participant && participant.status !== "KICKED";
    }

    if (!isTeacher && !isStaff && !isParticipant) {
      return json({ error: "NOT_AUTHORIZED" }, 403);
    }

    const { data: profileRow } = await admin
      .from("profiles")
      .select("full_name, email, avatar_url")
      .eq("id", user.userId)
      .maybeSingle();

    const isModerator = isTeacher || isStaff;
    const now = getNumericDate(0);
    const payload: Payload = {
      iss: appId,
      aud: appId,
      sub: "*",
      room: String(session.room_name).toLowerCase(),
      exp: getNumericDate(60 * 60 * 3), // 3 hours — comfortably longer than any single session
      nbf: now,
      context: {
        user: {
          id: user.userId,
          name: profileRow?.full_name ?? profileRow?.email ?? "7PRO",
          email: profileRow?.email ?? undefined,
          avatar: profileRow?.avatar_url ?? undefined,
          moderator: isModerator,
        },
      },
    };

    const header: Header = { alg: "HS256", typ: "JWT" };
    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(appSecret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const jwt = await create(header, payload, key);

    return json({ token: jwt, is_moderator: isModerator });
  } catch (e) {
    if (e instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("classroom-jitsi-token error", e);
    return json({ error: "UNKNOWN" }, 500);
  }
});

async function credentialsFor(
  admin: ReturnType<typeof createAdminClient>,
  rawDomain: string,
): Promise<{ appId: string; appSecret: string } | null> {
  const domain = rawDomain.trim().toLowerCase().replace(/^https?:\/\//, "").split("/")[0];
  if (domain) {
    const { data: server } = await admin
      .from("classroom_conference_servers")
      .select("id, jwt_app_id")
      .eq("domain", domain)
      .maybeSingle();
    if (server?.jwt_app_id) {
      const { data: secret } = await admin
        .from("classroom_conference_server_secrets")
        .select("jwt_app_secret")
        .eq("server_id", server.id)
        .maybeSingle();
      if (secret?.jwt_app_secret) return { appId: server.jwt_app_id, appSecret: secret.jwt_app_secret };
    }
    // A registered server without JWT is an open server: no token, join as normal.
    if (server) return null;
  }
  const appId = Deno.env.get("JITSI_APP_ID");
  const appSecret = Deno.env.get("JITSI_APP_SECRET");
  return appId && appSecret ? { appId, appSecret } : null;
}
