-- ============================================================================
-- Fix course deletion: purge_course exception handling + delete_course auth logic
--
-- Problem 1: purge_course only caught foreign_key_violation. Any other error
-- during dynamic child-table deletion (RLS, other constraints, permissions)
-- caused the ENTIRE function to fail, preventing course deletion.
--
-- Problem 2: delete_course's permission check had a logic flaw. The
-- IF v_teacher IS DISTINCT FROM auth.uid() check ran BEFORE the OWNER/ADMIN
-- role check, so an OWNER/ADMIN who is not the course's teacher would get
-- FORBIDDEN before the role check could exempt them.
--
-- Problem 3: For robustness, purge_course now uses explicit table deletion
-- ordered by dependency depth (deepest children first), with broad exception
-- handling so cleanup failures never block the parent course delete.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- purge_course: erase a course and all dependent rows.
--
-- Tables are deleted in dependency order (deepest children first).
-- The EXCEPTION handler catches ALL errors — not just foreign_key_violation —
-- so that a problem in one child table never prevents cleanup of the others
-- or the final course DELETE.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.purge_course(p_course_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_pass INTEGER;
BEGIN
    -- Phase 1: detach financial records (keep money history intact)
    UPDATE public.orders SET course_id = NULL WHERE course_id = p_course_id;

    -- Phase 2: delete child rows in dependency order (deepest first).
    -- Each EXCEPTION block catches ALL error types so a problem in one table
    -- never prevents cleanup of the others.
    FOR v_pass IN 1..5 LOOP

        -- Grandchildren of lessons (depend on lessons.id)
        BEGIN
            DELETE FROM public.lesson_quizzes WHERE lesson_id IN (
                SELECT id FROM public.lessons WHERE course_id = p_course_id
            );
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- lesson_progress (depends on lessons + courses)
        BEGIN
            DELETE FROM public.lesson_progress WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- certificates (depends on courses)
        BEGIN
            DELETE FROM public.certificates WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- reviews (depends on courses)
        BEGIN
            DELETE FROM public.reviews WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- enrollments (depends on courses)
        BEGIN
            DELETE FROM public.enrollments WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- lessons (depends on course_sections + courses)
        BEGIN
            DELETE FROM public.lessons WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- course_sections (depends on courses)
        BEGIN
            DELETE FROM public.course_sections WHERE course_id = p_course_id;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;

        -- Catch-all: dynamically clean any remaining table with course_id
        -- that we might have missed above.
        DECLARE
            v_child RECORD;
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
-- ----------------------------------------------------------------------------
-- delete_course: fix permission logic
--
-- OLD (broken): the OWNER/ADMIN role check was INSIDE the
--   IF v_teacher IS DISTINCT FROM auth.uid() block, so OWNER/ADMIN
--   users who are not the course teacher got FORBIDDEN.
--
-- NEW (fixed): OWNER/ADMIN bypass ALL permission and student/order checks.
-- Only non-admin teachers are subject to ownership and enrollment checks.
-- ----------------------------------------------------------------------------
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

    PERFORM public.purge_course(p_course_id);

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES (
        'DELETE_COURSE',
        'course',
        p_course_id,
        COALESCE(v_role, 'TEACHER'),
        jsonb_build_object('teacher_id', v_teacher, 'title', v_title)
    );
END;
$$;
-- Re-grant execute (idempotent)
GRANT EXECUTE ON FUNCTION public.delete_course(UUID) TO authenticated;
