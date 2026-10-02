import { corsHeaders, createAdminClient, json } from "../_shared/auth.ts";
import { PushTarget, readFcmServiceAccount, sendLocalizedPush } from "../_shared/fcm.ts";

/**
 * Scheduled housekeeping: matures teacher earnings, expires subscriptions and
 * sends renewal / payment reminders — now as real, localized, audible push
 * notifications (not just a silent database row), and only for notification
 * kinds the owner has not switched off app-wide (see notification_kind_enabled())
 * and that the individual recipient has not opted out of in their own
 * Notification Settings screen (see optedOut() below).
 *
 * Protected by a shared secret, checked two ways: MAINTENANCE_SECRET as a
 * Deno env var if one is set, falling back to the value stored under the
 * `maintenance.secret` app_setting — this lets a scheduler (pg_cron, or any
 * external cron) authenticate against a secret that lives in the same
 * database this project already fully controls, with no extra deploy step.
 */
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const admin = createAdminClient();

  const provided = req.headers.get("x-maintenance-secret") ?? new URL(req.url).searchParams.get("secret");
  const envSecret = Deno.env.get("MAINTENANCE_SECRET");
  const { data: settingRow } = await admin.from("app_settings").select("value").eq("key", "maintenance.secret").maybeSingle();
  const dbSecret = typeof settingRow?.value === "string" ? (settingRow.value as string) : null;
  // Accept either — whichever the deployment actually has configured. This means the DB-stored
  // secret (which a pg_cron job inside this project can always read without any separate deploy
  // step) keeps working even on a deployment that also happens to have an old/different
  // MAINTENANCE_SECRET env var already set from before.
  const authorized = (!!envSecret && provided === envSecret) || (!!dbSecret && provided === dbSecret);
  if (!authorized) return json({ error: "UNAUTHORIZED" }, 401);

  try {
    const { data: released } = await admin.rpc("release_matured_earnings");
    const { data: expired } = await admin.rpc("expire_subscriptions");

    // Virtual Classroom sweeps — unrelated to reminders, unchanged.
    const { data: classroomExpired } = await admin.rpc("classroom_expire_overdue_sessions");
    const { data: classroomCleanup } = await admin.rpc("classroom_cleanup_old_logs", { p_retention_days: 90 });
    const { data: placementAttemptsRemoved } = await admin.rpc("maintenance_cleanup_placement_attempts");

    const account = await readFcmServiceAccount(admin);

    /** Every currently-registered token for a user, each with its own locale. */
    async function tokensFor(userIds: string[]): Promise<Map<string, PushTarget[]>> {
      const byUser = new Map<string, PushTarget[]>();
      if (userIds.length === 0) return byUser;
      const { data } = await admin
        .from("device_push_tokens")
        .select("user_id, token, locale")
        .in("user_id", userIds);
      for (const row of data ?? []) {
        const list = byUser.get(row.user_id as string) ?? [];
        list.push({ token: row.token as string, locale: (row.locale as string) ?? "ar" });
        byUser.set(row.user_id as string, list);
      }
      return byUser;
    }

    /**
     * Users who switched a kind off in their own Notification Settings screen. This is
     * separate from notification_kind_enabled() above, which is the owner's app-wide
     * switch — this is the per-recipient one (notification_kind_preferences table), and
     * until now nothing here checked it: a person who opted out of RENEWAL_REMINDER or
     * PAYMENT_REMINDER still got the in-app row and the push anyway.
     */
    async function optedOut(userIds: string[], kind: string): Promise<Set<string>> {
      if (userIds.length === 0) return new Set();
      const { data } = await admin
        .from("notification_kind_preferences")
        .select("user_id")
        .in("user_id", userIds)
        .eq("kind", kind)
        .eq("enabled", false);
      return new Set((data ?? []).map((row) => row.user_id as string));
    }

    // ---------------------------------------------------------------- renewal reminders
    let reminders = 0;
    const { data: renewalEnabled } = await admin.rpc("notification_kind_enabled", { p_kind: "RENEWAL_REMINDER" });
    if (renewalEnabled !== false) {
      const soon = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString();
      const { data: expiring } = await admin
        .from("subscriptions")
        .select("id, user_id, ends_at, live_service_id")
        .in("status", ["ACTIVE", "EXPIRING"])
        .lte("ends_at", soon)
        .gte("ends_at", new Date().toISOString());

      const dueUsers = (expiring ?? []).map((s) => s.user_id as string);
      const tokenMap = await tokensFor(dueUsers);
      const renewalOptOut = await optedOut(dueUsers, "RENEWAL_REMINDER");

      for (const sub of expiring ?? []) {
        if (renewalOptOut.has(sub.user_id as string)) continue;

        const { data: already } = await admin
          .from("notifications")
          .select("id")
          .eq("user_id", sub.user_id)
          .eq("kind", "RENEWAL_REMINDER")
          .contains("data", { subscription_id: sub.id })
          .maybeSingle();
        if (already) continue;

        const dateStr = new Date(sub.ends_at as string).toISOString().slice(0, 10);
        const titleAr = "اشتراكك يستحق التجديد قريبًا";
        const bodyAr = `جدد اشتراكك قبل ${dateStr} حتى لا يفقد مكانك.`;
        const titleEn = "Your subscription is ending soon";
        const bodyEn = `Renew before ${dateStr} to keep your seat.`;

        await admin.from("notifications").insert({
          user_id: sub.user_id,
          kind: "RENEWAL_REMINDER",
          title: titleAr,
          body: bodyAr,
          data: { subscription_id: sub.id, live_service_id: sub.live_service_id },
        });
        reminders++;

        if (account) {
          const targets = tokenMap.get(sub.user_id as string) ?? [];
          await sendLocalizedPush(
            admin,
            account,
            targets,
            (locale) => (locale.startsWith("en") ? { title: titleEn, body: bodyEn } : { title: titleAr, body: bodyAr }),
            { type: "RENEWAL_REMINDER", subscription_id: String(sub.id) },
          );
        }
      }
    }

    // ---------------------------------------------------------------- teacher payment alerts
    let teacherAlerts = 0;
    const { data: paymentEnabled } = await admin.rpc("notification_kind_enabled", { p_kind: "PAYMENT_REMINDER" });
    if (paymentEnabled !== false) {
      const today = new Date().toISOString().slice(0, 10);
      const { data: due } = await admin
        .from("teacher_students")
        .select("id, teacher_id, full_name, next_payment_date, status")
        .in("status", ["DUE_SOON", "OVERDUE"])
        .lte("next_payment_date", today);

      const teacherIds = (due ?? []).map((d) => d.teacher_id as string);
      const tokenMap = await tokensFor(teacherIds);
      const paymentOptOut = await optedOut(teacherIds, "PAYMENT_REMINDER");

      for (const record of due ?? []) {
        if (paymentOptOut.has(record.teacher_id as string)) continue;

        const overdue = record.status === "OVERDUE";
        const titleAr = overdue ? "دفعة متأخرة" : "دفعة مستحقة قريبًا";
        const bodyAr = `${record.full_name} — الاستحقاق ${record.next_payment_date}.`;
        const titleEn = overdue ? "Payment overdue" : "Payment due";
        const bodyEn = `${record.full_name} — due ${record.next_payment_date}.`;

        await admin.from("notifications").insert({
          user_id: record.teacher_id,
          kind: "PAYMENT_REMINDER",
          title: titleAr,
          body: bodyAr,
          data: { teacher_student_id: record.id },
        });
        teacherAlerts++;

        if (account) {
          const targets = tokenMap.get(record.teacher_id as string) ?? [];
          await sendLocalizedPush(
            admin,
            account,
            targets,
            (locale) => (locale.startsWith("en") ? { title: titleEn, body: bodyEn } : { title: titleAr, body: bodyAr }),
            { type: "PAYMENT_REMINDER", teacher_student_id: String(record.id) },
          );
        }
      }
    }

    return json({ ok: true, released, expired, reminders, teacherAlerts, classroomExpired, classroomCleanup, placementAttemptsRemoved, push_configured: !!account });
  } catch (err) {
    console.error("maintenance_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
