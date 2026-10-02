import {
  AuthError,
  corsHeaders,
  createAdminClient,
  createUserClient,
  json,
  requireAuth,
} from "../_shared/auth.ts";
import {
  canManageExercises,
  canManagePlacementTests,
  ExerciseType,
  UserRole,
  getUserRole,
} from "../_shared/exercises.ts";

interface ExerciseBody {
  action: "create" | "update" | "delete" | "publish" | "unpublish" | "list";
  exercise_id?: string;
  type?: ExerciseType;
  title?: string;
  description?: string;
  language?: "AR" | "EN";
  duration_minutes?: number;
  questions?: Array<{
    question_text: string;
    question_type: "MULTIPLE_CHOICE" | "SHORT_ANSWER" | "ESSAY";
    media_url?: string;
    media_type?: "image" | "video" | "audio";
    options?: string[];
    correct_answer?: string;
    order_index: number;
  }>;
  filter_type?: ExerciseType;
  filter_teacher_id?: string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as ExerciseBody;
    const client = createUserClient(req);
    const admin = createAdminClient();
    const userRole = await getUserRole(admin, user.userId);

    // ==================== CREATE ====================
    if (body.action === "create") {
      if (body.type === ExerciseType.PLACEMENT_TEST) {
        if (!await canManagePlacementTests(admin, user.userId)) {
          return json({ error: "UNAUTHORIZED" }, 403);
        }
      } else if (body.type === ExerciseType.TEACHER_EXERCISE) {
        if (userRole !== UserRole.TEACHER && userRole !== UserRole.OWNER && userRole !== UserRole.ADMIN) {
          return json({ error: "ONLY_TEACHERS_CAN_CREATE" }, 403);
        }
      }

      const { data: exercise, error: exerciseError } = await admin
        .from("exercises")
        .insert({
          type: body.type,
          title: body.title,
          description: body.description,
          teacher_id: body.type === ExerciseType.TEACHER_EXERCISE ? user.userId : null,
          is_published: false,
          language: body.language || "AR",
          duration_minutes: body.duration_minutes,
        })
        .select()
        .single();

      if (exerciseError) return json({ error: exerciseError.message }, 400);

      if (body.questions && body.questions.length > 0) {
        const { error: questionsError } = await admin
          .from("exercise_questions")
          .insert(
            body.questions.map((q) => ({
              exercise_id: exercise.id,
              question_text: q.question_text,
              question_type: q.question_type,
              media_url: q.media_url,
              media_type: q.media_type,
              options: q.options,
              correct_answer: q.correct_answer,
              order_index: q.order_index,
            }))
          );

        if (questionsError) {
          await admin.from("exercises").delete().eq("id", exercise.id);
          return json({ error: questionsError.message }, 400);
        }
      }

      return json({ ok: true, exercise });
    }

    // ==================== UPDATE ====================
    if (body.action === "update") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      const { error: updateError } = await admin
        .from("exercises")
        .update({
          title: body.title,
          description: body.description,
          language: body.language,
          duration_minutes: body.duration_minutes,
          updated_at: new Date().toISOString(),
        })
        .eq("id", body.exercise_id);

      if (updateError) return json({ error: updateError.message }, 400);

      if (body.questions && body.questions.length > 0) {
        await admin.from("exercise_questions").delete().eq("exercise_id", body.exercise_id);

        const { error: questionsError } = await admin
          .from("exercise_questions")
          .insert(
            body.questions.map((q) => ({
              exercise_id: body.exercise_id,
              question_text: q.question_text,
              question_type: q.question_type,
              media_url: q.media_url,
              media_type: q.media_type,
              options: q.options,
              correct_answer: q.correct_answer,
              order_index: q.order_index,
            }))
          );

        if (questionsError) return json({ error: questionsError.message }, 400);
      }

      return json({ ok: true, message: "Exercise updated" });
    }

    // ==================== DELETE ====================
    if (body.action === "delete") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      const { error: deleteError } = await admin
        .from("exercises")
        .delete()
        .eq("id", body.exercise_id);

      if (deleteError) return json({ error: deleteError.message }, 400);

      return json({ ok: true, message: "Exercise deleted" });
    }

    // ==================== PUBLISH ====================
    if (body.action === "publish") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      const { error: publishError } = await admin
        .from("exercises")
        .update({ is_published: true, updated_at: new Date().toISOString() })
        .eq("id", body.exercise_id);

      if (publishError) return json({ error: publishError.message }, 400);

      return json({ ok: true, message: "Exercise published" });
    }

    // ==================== UNPUBLISH ====================
    if (body.action === "unpublish") {
      if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);

      if (!await canManageExercises(admin, user.userId, body.exercise_id)) {
        return json({ error: "UNAUTHORIZED" }, 403);
      }

      const { error: unpublishError } = await admin
        .from("exercises")
        .update({ is_published: false, updated_at: new Date().toISOString() })
        .eq("id", body.exercise_id);

      if (unpublishError) return json({ error: unpublishError.message }, 400);

      return json({ ok: true, message: "Exercise unpublished" });
    }

    // ==================== LIST ====================
    if (body.action === "list") {
      let query = admin.from("exercises").select("*, exercise_questions(*)");

      if (body.filter_type) {
        query = query.eq("type", body.filter_type);
      }

      if (body.filter_teacher_id) {
        query = query.eq("teacher_id", body.filter_teacher_id);
      }

      // Teachers can only see their own exercises or published placement tests
      if (userRole === UserRole.TEACHER) {
        query = query.or(
          `teacher_id.eq.${user.userId},and(type.eq.${ExerciseType.PLACEMENT_TEST},is_published.eq.true)`
        );
      }

      const { data: exercises, error: listError } = await query;

      if (listError) return json({ error: listError.message }, 400);

      return json({ ok: true, exercises });
    }

    return json({ error: "UNKNOWN_ACTION" }, 400);
  } catch (err) {
    if (err instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("exercises_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
