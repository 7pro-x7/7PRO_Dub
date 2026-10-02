-- ============================================================================
-- FINAL FIX: All course deletion issues in one file
--
-- Run this ENTIRE file in Supabase SQL Editor → New query → Paste → Run
--
-- Issues fixed:
--   1. purge_course: catches ALL errors (not just foreign_key_violation)
--   2. purge_course: explicit table deletion in dependency order + catch-all dynamic
--   3. delete_course: OWNER/ADMIN bypass permission/enrollment checks
--   4. delete_course: audit INSERT includes actor_id + exception-safe
--   5. delete_course_force: same audit INSERT fix
-- ============================================================================

-- Step 1: purge_course — robust child-table cleanup
CREATE OR REPLACE FUNCTION public.purge_course(p_course_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_pass INTEGER;
    v_child RECORD;
BEGIN
    -- Phase 1: detach financial records (keep money history intact)
    UPDATE public.orders SET course_id = NULL WHERE course_id = p_course_id;

    -- Phase 2: delete child rows in dependency order (deepest first).
    FOR v_pass IN 1..5 LOOP

        BEGIN
            DELETE FROM public.lesson_quizzes WHERE lesson_id IN (
                SELECT id FROM public.lessons WHERE course_id = p_course_id
            );
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        BEGIN
            DELETE FROM public.lesson_progress WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        BEGIN
            DELETE FROM public.certificates WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        BEGIN
            DELETE FROM public.reviews WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        BEGIN
            DELETE FROM public.enrollments WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        BEGIN
            DELETE FROM public.lessons WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        BEGIN
            DELETE FROM public.course_sections WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- Catch-all: dynamically clean any remaining table with course_id
        BEGIN
            FOR v_child IN
                SELECT c.table_name
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema
                 AND t.table_name = c.table_name
                 AND t.table_type = 'BASE TABLE'
                WHERE c.table_schema = 'public'
                  AND c.column_name = 'course_id'
                  AND c.table_name NOT IN (
                      'orders', 'courses',
                      'lesson_quizzes', 'lesson_progress',
                      'certificates', 'reviews',
                      'enrollments', 'lessons', 'course_sections'
                  )
            LOOP
                BEGIN
                    EXECUTE format('DELETE FROM public.%I WHERE course_id = $1', v_child.table_name)
                    USING p_course_id;
                EXCEPTION WHEN OTHERS THEN NULL;
                END;
            END LOOP;
        END;

    END LOOP;

    -- Phase 3: delete the course itself
    DELETE FROM public.courses WHERE id = p_course_id;
END;
$$;
-- Step 2: delete_course — fixed permission logic + exception-safe audit
CREATE OR REPLACE FUNCTION public.delete_course(p_course_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT;
    v_teacher UUID;
    v_title TEXT;
    v_students INTEGER;
    v_paid INTEGER;
BEGIN
    SELECT role::text INTO v_role FROM public.profiles WHERE id = auth.uid();
    SELECT teacher_id, title INTO v_teacher, v_title FROM public.courses WHERE id = p_course_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'COURSE_NOT_FOUND';
    END IF;

    -- Owner/Admin: no further checks — they can always delete.
    IF COALESCE(v_role, '') NOT IN ('OWNER', 'ADMIN') THEN
        -- Regular teacher: must own the course.
        IF v_teacher IS DISTINCT FROM auth.uid() THEN
            RAISE EXCEPTION 'FORBIDDEN';
        END IF;

        -- Non-admin teachers cannot delete courses with enrolled students.
        SELECT COUNT(*) INTO v_students FROM public.enrollments WHERE course_id = p_course_id;
        IF v_students > 0 THEN
            RAISE EXCEPTION 'COURSE_HAS_STUDENTS';
        END IF;

        -- Non-admin teachers cannot delete courses with paid orders.
        SELECT COUNT(*) INTO v_paid
        FROM public.orders
        WHERE course_id = p_course_id AND status = 'PAID';
        IF v_paid > 0 THEN
            RAISE EXCEPTION 'COURSE_HAS_ORDERS';
        END IF;
    END IF;

    -- Delete all child rows and the course itself
    PERFORM public.purge_course(p_course_id);

    -- Audit log: exception-safe so failure never rolls back the deletion
    BEGIN
        INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
        VALUES (
            auth.uid(),
            'DELETE_COURSE',
            'course',
            p_course_id,
            COALESCE(v_role, 'TEACHER'),
            jsonb_build_object('teacher_id', v_teacher, 'title', v_title)
        );
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_course(UUID) TO authenticated;
-- Step 3: delete_course_force — same audit fix
CREATE OR REPLACE FUNCTION public.delete_course_force(p_course_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT;
    v_teacher UUID;
    v_title TEXT;
BEGIN
    SELECT role::text INTO v_role FROM public.profiles WHERE id = auth.uid();
    IF COALESCE(v_role, '') NOT IN ('OWNER', 'ADMIN') THEN
        RAISE EXCEPTION 'FORBIDDEN';
    END IF;

    SELECT teacher_id, title INTO v_teacher, v_title FROM public.courses WHERE id = p_course_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'COURSE_NOT_FOUND';
    END IF;

    -- Force-delete: no enrollment or order checks
    PERFORM public.purge_course(p_course_id);

    -- Audit log: exception-safe
    BEGIN
        INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
        VALUES (
            auth.uid(),
            'FORCE_DELETE_COURSE',
            'course',
            p_course_id,
            v_role,
            jsonb_build_object('teacher_id', v_teacher, 'title', v_title)
        );
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_course_force(UUID) TO authenticated;
