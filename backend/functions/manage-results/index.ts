import {
  AuthError,
  corsHeaders,
  createAdminClient,
  json,
  requireAuth,
} from "../_shared/auth.ts";
import {
  canManageExercises,
  canManagePlacementTests,
  getUserRole,
  hideStudentResults,
  UserRole,
} from "../_shared/exercises.ts";

interface ManageResultsBody {
  action: "hide_results" | "show_results" | "get_results" | "delete_result";
  exercise_id?: string;
  student_id?: string;
  result_id?: string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as ManageResultsBody;
    const admin = createAdminClient();
    const userRole = await getUserRole(admin, user.userId);

    // ==================== HIDE RESULTS ====================
    if (body.action === "hide_results") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      if (body.student_id) {
        // Hide results for specific student
        const { error } = await admin
          .from("student_results")
          .update({ is_hidden: true })
          .eq("exercise_id", body.exercise_id)
          .eq("student_id", body.student_id);

        if (error) return json({ error: error.message }, 400);
      } else {
        // Hide all results for this exercise
        const { error } = await admin
          .from("student_results")
          .update({ is_hidden: true })
          .eq("exercise_id", body.exercise_id);

        if (error) return json({ error: error.message }, 400);
      }

      return json({ ok: true, message: "Results hidden" });
    }

    // ==================== SHOW RESULTS ====================
    if (body.action === "show_results") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      if (body.student_id) {
        const { error } = await admin
          .from("student_results")
          .update({ is_hidden: false })
          .eq("exercise_id", body.exercise_id)
          .eq("student_id", body.student_id);

        if (error) return json({ error: error.message }, 400);
      } else {
        const { error } = await admin
          .from("student_results")
          .update({ is_hidden: false })
          .eq("exercise_id", body.exercise_id);

        if (error) return json({ error: error.message }, 400);
      }

      return json({ ok: true, message: "Results shown" });
    }

    // ==================== GET RESULTS ====================
    if (body.action === "get_results") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      const { data: results, error: resultsError } = await admin
        .from("student_results")
        .select("*, profiles:student_id(full_name, email)")
        .eq("exercise_id", body.exercise_id)
        .order("submitted_at", { ascending: false });

      if (resultsError) return json({ error: resultsError.message }, 400);

      return json({ ok: true, results });
    }

    // ==================== DELETE RESULT ====================
    if (body.action === "delete_result") {
      if (!body.result_id) return json({ error: "MISSING_RESULT_ID" }, 400);

      const { data: result, error: getError } = await admin
        .from("student_results")
        .select("exercise_id")
        .eq("id", body.result_id)
        .maybeSingle();

      if (getError || !result) return json({ error: "RESULT_NOT_FOUND" }, 404);

      if (!await canManageExercises(admin, user.userId, result.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      const { error: deleteError } = await admin
        .from("student_results")
        .delete()
        .eq("id", body.result_id);

      if (deleteError) return json({ error: deleteError.message }, 400);

      return json({ ok: true, message: "Result deleted" });
    }

    return json({ error: "UNKNOWN_ACTION" }, 400);
  } catch (err) {
    if (err instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("manage_results_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
