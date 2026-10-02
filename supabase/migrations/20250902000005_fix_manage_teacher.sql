-- ============================================================================
-- Fix manage_teacher: audit_logs INSERT missing actor_id
--
-- ROOT CAUSE: Same pattern as the delete_course bug. The audit INSERT
-- did NOT include actor_id (which is NOT NULL), causing a constraint
-- violation that ROLLED BACK the entire transaction — undoing the
-- suspend/reactivate/delete action that already succeeded.
--
-- FIX: Add actor_id = auth.uid() and wrap the INSERT in EXCEPTION
-- handling so audit failure never rolls back the teacher action.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.manage_teacher(p_teacher uuid, p_action text, p_note text DEFAULT ''::text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_role TEXT;
    v_teacher_status TEXT;
BEGIN
    -- Check caller is owner or admin
    SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
    IF COALESCE(v_caller_role, '') NOT IN ('OWNER', 'ADMIN') THEN
        RAISE EXCEPTION 'Only owner or admin can manage teachers';
    END IF;

    -- Get current teacher status
    SELECT status INTO v_teacher_status FROM public.teacher_profiles WHERE id = p_teacher;
    IF v_teacher_status IS NULL THEN
        RAISE EXCEPTION 'Teacher not found';
    END IF;

    IF p_action = 'SUSPEND' THEN
        UPDATE public.teacher_profiles SET status = 'SUSPENDED', updated_at = now() WHERE id = p_teacher;
    ELSIF p_action = 'REACTIVATE' THEN
        UPDATE public.teacher_profiles SET status = 'APPROVED', updated_at = now() WHERE id = p_teacher;
    ELSIF p_action = 'DELETE' THEN
        UPDATE public.courses SET status = 'ARCHIVED', updated_at = now() WHERE teacher_id = p_teacher;
        DELETE FROM public.teacher_profiles WHERE id = p_teacher;
        UPDATE public.profiles SET role = 'STUDENT', updated_at = now() WHERE id = p_teacher;
    ELSE
        RAISE EXCEPTION 'Unknown action: %', p_action;
    END IF;

    -- Log the action: wrap in EXCEPTION so audit failure never rolls back the action
    BEGIN
        INSERT INTO public.audit_logs (actor_id, action, target_type, target_id, actor_role, metadata)
        VALUES (
            auth.uid(),
            'TEACHER_' || p_action,
            'teacher',
            p_teacher,
            v_caller_role,
            jsonb_build_object('note', p_note, 'previous_status', v_teacher_status)
        );
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;
END;
$$;
GRANT EXECUTE ON FUNCTION public.manage_teacher(uuid, text, text) TO authenticated;
