-- ============================================================================
-- manage_teacher: Owner/Admin can suspend, reactivate, or delete teachers
-- ============================================================================

DROP FUNCTION IF EXISTS public.manage_teacher(UUID, TEXT, TEXT);
CREATE OR REPLACE FUNCTION public.manage_teacher(
    p_teacher UUID,
    p_action TEXT,
    p_note TEXT DEFAULT ''
)
RETURNS VOID AS $$
DECLARE
    v_caller_role TEXT;
    v_teacher_status TEXT;
BEGIN
    -- Check caller is owner or admin
    SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
    IF v_caller_role NOT IN ('OWNER', 'ADMIN') THEN
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

    -- Log the action
    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES (
        'TEACHER_' || p_action,
        'teacher',
        p_teacher,
        v_caller_role,
        jsonb_build_object('note', p_note, 'previous_status', v_teacher_status)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.manage_teacher(UUID, TEXT, TEXT) TO authenticated;
