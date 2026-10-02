import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { AuthUser } from "./auth.ts";

export enum ExerciseType {
  PLACEMENT_TEST = "PLACEMENT_TEST",
  TEACHER_EXERCISE = "TEACHER_EXERCISE",
}

export enum UserRole {
  STUDENT = "STUDENT",
  TEACHER = "TEACHER",
  OWNER = "OWNER",
  ADMIN = "ADMIN",
}

export interface Exercise {
  id: string;
  type: ExerciseType;
  title: string;
  description?: string;
  teacher_id?: string;
  created_at: string;
  updated_at: string;
  is_published: boolean;
  language: "AR" | "EN";
  duration_minutes?: number;
}

export interface ExerciseQuestion {
  id: string;
  exercise_id: string;
  question_text: string;
  question_type: "MULTIPLE_CHOICE" | "SHORT_ANSWER" | "ESSAY";
  media_url?: string;
  media_type?: "image" | "video" | "audio";
  media_aspect_ratio?: number; // width / height (e.g., 1.78 for 16:9, 0.56 for 9:16)
  options?: string[];
  correct_answer?: string;
  order_index: number;
}

export interface StudentResult {
  id: string;
  student_id: string;
  exercise_id: string;
  submitted_at: string;
  score?: number;
  is_hidden: boolean;
}

/** Get user role from database */
export async function getUserRole(
  client: SupabaseClient,
  userId: string,
): Promise<UserRole> {
  const { data: profile } = await client
    .from("profiles")
    .select("role")
    .eq("id", userId)
    .maybeSingle();

  return (profile?.role as UserRole) || UserRole.STUDENT;
}

/** Check if user can manage exercises */
export async function canManageExercises(
  client: SupabaseClient,
  userId: string,
  exerciseId?: string,
): Promise<boolean> {
  const role = await getUserRole(client, userId);

  // Owner/Admin can manage all exercises
  if (role === UserRole.OWNER || role === UserRole.ADMIN) return true;

  // Teacher can only manage their own exercises
  if (role === UserRole.TEACHER && exerciseId) {
    const { data: exercise } = await client
      .from("exercises")
      .select("teacher_id")
      .eq("id", exerciseId)
      .maybeSingle();

    return exercise?.teacher_id === userId;
  }

  return false;
}

/** Check if user can manage placement tests */
export async function canManagePlacementTests(
  client: SupabaseClient,
  userId: string,
): Promise<boolean> {
  const role = await getUserRole(client, userId);
  return role === UserRole.OWNER || role === UserRole.ADMIN;
}

/** Get published exercises for a teacher that student can see */
export async function getTeacherExercises(
  client: SupabaseClient,
  teacherId: string,
  studentId?: string,
): Promise<Exercise[]> {
  let query = client
    .from("exercises")
    .select("*")
    .eq("type", ExerciseType.TEACHER_EXERCISE)
    .eq("teacher_id", teacherId)
    .eq("is_published", true);

  const { data } = await query;
  return (data as Exercise[]) || [];
}

/** Hide results from UI (data remains in database) */
export async function hideStudentResults(
  client: SupabaseClient,
  studentId: string,
  exerciseId?: string,
): Promise<void> {
  let query = client
    .from("student_results")
    .update({ is_hidden: true })
    .eq("student_id", studentId);

  if (exerciseId) {
    query = query.eq("exercise_id", exerciseId);
  }

  await query;
}

/** Get visible results only */
export async function getVisibleResults(
  client: SupabaseClient,
  studentId: string,
  exerciseId?: string,
): Promise<StudentResult[]> {
  let query = client
    .from("student_results")
    .select("*")
    .eq("student_id", studentId)
    .eq("is_hidden", false);

  if (exerciseId) {
    query = query.eq("exercise_id", exerciseId);
  }

  const { data } = await query;
  return (data as StudentResult[]) || [];
}
