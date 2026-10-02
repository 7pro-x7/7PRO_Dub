import {
  AuthError,
  corsHeaders,
  createAdminClient,
  json,
  requireAuth,
} from "../_shared/auth.ts";
import {
  ExerciseType,
  getTeacherExercises,
  getVisibleResults,
} from "../_shared/exercises.ts";

interface StudentExercisesBody {
  action: "list_teachers" | "get_teacher_exercises" | "get_results";
  teacher_id?: string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as StudentExercisesBody;
    const admin = createAdminClient();

    // ==================== LIST TEACHERS ====================
    if (body.action === "list_teachers") {
      const { data: teachers, error: teachersError } = await admin
        .from("profiles")
        .select("id, full_name, avatar_url")
        .eq("role", "TEACHER");

      if (teachersError) return json({ error: teachersError.message }, 400);

      return json({ ok: true, teachers });
    }

    // ==================== GET TEACHER EXERCISES ====================
    if (body.action === "get_teacher_exercises") {
      if (!body.teacher_id) return json({ error: "MISSING_TEACHER_ID" }, 400);

      const exercises = await getTeacherExercises(admin, body.teacher_id);

      const { data: placementTests, error: placementError } = await admin
        .from("exercises")
        .select("*, exercise_questions(*)")
        .eq("type", ExerciseType.PLACEMENT_TEST)
        .eq("is_published", true);

      if (placementError) return json({ error: placementError.message }, 400);

      const allExercises = [
        ...exercises.map((e) => ({ ...e, source: "teacher" })),
        ...((placementTests as any[]) || []).map((e) => ({ ...e, source: "placement" })),
      ];

      return json({ ok: true, exercises: allExercises });
    }

    // ==================== GET RESULTS ====================
    if (body.action === "get_results") {
      const results = await getVisibleResults(admin, user.userId);

      return json({ ok: true, results });
    }

    return json({ error: "UNKNOWN_ACTION" }, 400);
  } catch (err) {
    if (err instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("student_exercises_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
