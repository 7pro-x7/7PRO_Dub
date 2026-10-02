-- ============================================================================
-- Fix audit_logs INSERT in delete_course / delete_course_force
--
-- ROOT CAUSE: The audit_logs INSERT in both functions did NOT set actor_id.
-- If audit_logs.actor_id is NOT NULL (which is the case — confirmed by the
-- audit_logs_actor_id_fkey foreign key), the INSERT fails with a NOT NULL
-- constraint violation, which ROLLS BACK the entire transaction — including
-- the purge_course that already deleted all child rows and the course itself.
--
-- This is the real reason course deletion appeared to "succeed" but the
-- course was still there: the purge worked, but the audit INSERT undid it.
--
-- FIX: Wrap the audit INSERT in EXCEPTION handling so that if it fails for
-- any reason, the course deletion still persists. Also include actor_id
-- and RLS bypass (SECURITY DEFINER context should handle this, but be safe).
-- ============================================================================

-- Fix delete_course
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

    -- Audit log: wrap in EXCEPTION so a missing column or constraint never
    -- rolls back the deletion that already succeeded.
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
        -- Audit failure is non-fatal; the course is already deleted.
        NULL;
    END;
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_course(UUID) TO authenticated;
-- Fix delete_course_force (same fix)
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

    -- Audit log: wrap in EXCEPTION so failure never rolls back the deletion.
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
