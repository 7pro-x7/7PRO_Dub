import {
  AuthError,
  corsHeaders,
  createAdminClient,
  json,
  requireAuth,
} from "../_shared/auth.ts";

interface SubmissionBody {
  exercise_id: string;
  answers: Array<{
    question_id: string;
    answer_text?: string;
    selected_option?: string;
    uploaded_files?: string[];
  }>;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);

  try {
    const user = await requireAuth(req);
    const body = (await req.json()) as SubmissionBody;
    const admin = createAdminClient();

    if (!body.exercise_id) return json({ error: "MISSING_EXERCISE_ID" }, 400);
    if (!body.answers || body.answers.length === 0) {
      return json({ error: "MISSING_ANSWERS" }, 400);
    }

    // Check if exercise exists
    const { data: exercise, error: exerciseError } = await admin
      .from("exercises")
      .select("id, type, is_published")
      .eq("id", body.exercise_id)
      .maybeSingle();

    if (exerciseError || !exercise) {
      return json({ error: "EXERCISE_NOT_FOUND" }, 404);
    }

    if (!exercise.is_published) {
      return json({ error: "EXERCISE_NOT_PUBLISHED" }, 403);
    }

    // Check if student already submitted (optional: allow multiple submissions)
    const { data: existing } = await admin
      .from("student_results")
      .select("id")
      .eq("student_id", user.userId)
      .eq("exercise_id", body.exercise_id)
      .maybeSingle();

    // Store submission
    const submissionData = {
      student_id: user.userId,
      exercise_id: body.exercise_id,
      submitted_at: new Date().toISOString(),
      answers: body.answers,
      is_hidden: false,
    };

    let result;
    if (existing) {
      // Update existing submission
      const { data: updated, error: updateError } = await admin
        .from("student_results")
        .update(submissionData)
        .eq("id", existing.id)
        .select()
        .single();

      if (updateError) return json({ error: updateError.message }, 400);
      result = updated;
    } else {
      // Create new submission
      const { data: created, error: createError } = await admin
        .from("student_results")
        .insert(submissionData)
        .select()
        .single();

      if (createError) return json({ error: createError.message }, 400);
      result = created;
    }

    return json({
      ok: true,
      result_id: result.id,
      message: "Exercise submitted successfully",
    });
  } catch (err) {
    if (err instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("submit_exercise_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});
