-- ============================================================================
-- Owner / Admin full control
--
-- The console promised the owner and admins could deactivate or delete any
-- teacher and delete any course at any time, but two things stood in the way:
--
--   1. delete_course / delete_course_force deleted from "course_reviews", a
--      table that does not exist (reviews is the real one), so every deletion
--      failed outright. They also missed certificates, lesson progress and quiz
--      attempts, which hold the course down with foreign keys.
--   2. manage_teacher refused any account without a teaching-profile row
--      ("Teacher not found"), and had no guard for the owner account.
--
-- Deleting a course now clears every child row whatever the state of the
-- course, while paid orders are DETACHED rather than destroyed so the money
-- history, teacher ledger and refunds stay intact.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Internal helper: erase a course and everything hanging off it.
--
-- Child tables are discovered from the catalogue, so a table added later is
-- cleared too without another migration. Passes repeat because children point
-- at each other (lesson progress -> lessons); a foreign key that is not free
-- yet is simply retried on the next pass.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.purge_course(p_course_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_child RECORD;
    v_pass INTEGER;
BEGIN
    -- Money is never destroyed: the order keeps its amount, payout share and
    -- refunds, it just stops pointing at a course that no longer exists.
    UPDATE public.orders SET course_id = NULL WHERE course_id = p_course_id;

    FOR v_pass IN 1..5 LOOP
        FOR v_child IN
            SELECT c.table_name
            FROM information_schema.columns c
            JOIN information_schema.tables t
              ON t.table_schema = c.table_schema
             AND t.table_name = c.table_name
             AND t.table_type = 'BASE TABLE'
            WHERE c.table_schema = 'public'
              AND c.column_name = 'course_id'
              AND c.table_name NOT IN ('orders', 'courses')
        LOOP
            BEGIN
                EXECUTE format('DELETE FROM public.%I WHERE course_id = $1', v_child.table_name)
                USING p_course_id;
            EXCEPTION
                WHEN foreign_key_violation THEN
                    NULL; -- still held by another child; freed on a later pass
            END;
        END LOOP;
    END LOOP;

    DELETE FROM public.courses WHERE id = p_course_id;
END;
$$;
-- Internal only: reachable through the audited functions below, never directly.
REVOKE ALL ON FUNCTION public.purge_course(UUID) FROM PUBLIC;
-- ----------------------------------------------------------------------------
-- delete_course: owner/admin may always delete; a teacher only their own, and
-- only while nobody has enrolled or paid.
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.delete_course(UUID);
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

    IF COALESCE(v_role, '') NOT IN ('OWNER', 'ADMIN') THEN
        IF v_teacher IS DISTINCT FROM auth.uid() THEN
            RAISE EXCEPTION 'FORBIDDEN';
        END IF;

        SELECT COUNT(*) INTO v_students FROM public.enrollments WHERE course_id = p_course_id;
        IF v_students > 0 THEN
            RAISE EXCEPTION 'COURSE_HAS_STUDENTS';
        END IF;

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
GRANT EXECUTE ON FUNCTION public.delete_course(UUID) TO authenticated;
-- ----------------------------------------------------------------------------
-- delete_course_force: owner/admin only, no conditions at all.
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.delete_course_force(UUID);
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

    PERFORM public.purge_course(p_course_id);

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES (
        'FORCE_DELETE_COURSE',
        'course',
        p_course_id,
        v_role,
        jsonb_build_object('teacher_id', v_teacher, 'title', v_title)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_course_force(UUID) TO authenticated;
-- ----------------------------------------------------------------------------
-- manage_teacher: deactivate, reactivate or delete any teaching account.
--
-- Works from whatever state the account is in, and no longer fails when the
-- teaching-profile row is already gone. Only the platform owner is protected.
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.manage_teacher(UUID, TEXT, TEXT);
CREATE OR REPLACE FUNCTION public.manage_teacher(
    p_teacher UUID,
    p_action TEXT,
    p_note TEXT DEFAULT ''
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT;
    v_target_role TEXT;
    v_status TEXT;
    v_action TEXT;
BEGIN
    SELECT role::text INTO v_role FROM public.profiles WHERE id = auth.uid();
    IF COALESCE(v_role, '') NOT IN ('OWNER', 'ADMIN') THEN
        RAISE EXCEPTION 'FORBIDDEN';
    END IF;

    SELECT role::text INTO v_target_role FROM public.profiles WHERE id = p_teacher;
    IF v_target_role IS NULL THEN
        RAISE EXCEPTION 'TARGET_NOT_TEACHER';
    END IF;
    IF v_target_role = 'OWNER' THEN
        RAISE EXCEPTION 'CANNOT_MODIFY_OWNER';
    END IF;

    v_action := upper(COALESCE(p_action, ''));
    SELECT status::text INTO v_status FROM public.teacher_profiles WHERE id = p_teacher;

    IF v_action IN ('SUSPEND', 'DEACTIVATE') THEN
        -- A deactivated teacher stops taking on anyone new, whatever state the
        -- account was in before.
        UPDATE public.teacher_profiles
        SET status = 'SUSPENDED', accepting_new_students = FALSE, updated_at = now()
        WHERE id = p_teacher;

    ELSIF v_action IN ('REACTIVATE', 'ACTIVATE') THEN
        UPDATE public.teacher_profiles
        SET status = 'APPROVED', updated_at = now()
        WHERE id = p_teacher;

    ELSIF v_action = 'DELETE' THEN
        -- Courses are archived rather than erased so enrolled students keep
        -- what they paid for; the owner can still delete them one by one.
        UPDATE public.courses
        SET status = 'ARCHIVED', updated_at = now()
        WHERE teacher_id = p_teacher;

        DELETE FROM public.teacher_profiles WHERE id = p_teacher;

        UPDATE public.profiles
        SET role = 'STUDENT', updated_at = now()
        WHERE id = p_teacher AND role::text <> 'OWNER';

    ELSE
        RAISE EXCEPTION 'UNKNOWN_ACTION';
    END IF;

    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES (
        'TEACHER_' || v_action,
        'teacher',
        p_teacher,
        v_role,
        jsonb_build_object('note', p_note, 'previous_status', v_status)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.manage_teacher(UUID, TEXT, TEXT) TO authenticated;
