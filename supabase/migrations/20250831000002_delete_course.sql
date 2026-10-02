-- ============================================================================
-- delete_course: Delete a course with checks for enrolled students/paid orders
-- Drop and recreate with VOID return type to match client expectation
-- ============================================================================

DROP FUNCTION IF EXISTS public.delete_course(UUID);
CREATE OR REPLACE FUNCTION public.delete_course(p_course_id UUID)
RETURNS VOID AS $$
DECLARE
    v_caller_role TEXT;
    v_course_teacher_id UUID;
    v_student_count INTEGER;
    v_order_count INTEGER;
BEGIN
    -- Check caller is owner, admin, or the course teacher
    SELECT role INTO v_caller_role FROM public.profiles WHERE id = auth.uid();
    SELECT teacher_id INTO v_course_teacher_id FROM public.courses WHERE id = p_course_id;
    
    IF v_caller_role NOT IN ('OWNER', 'ADMIN') AND v_course_teacher_id != auth.uid() THEN
        RAISE EXCEPTION 'You do not have permission to delete this course';
    END IF;

    -- Only non-admin teachers are blocked by enrolled students
    IF v_caller_role NOT IN ('OWNER', 'ADMIN') THEN
        SELECT COUNT(*) INTO v_student_count FROM public.enrollments WHERE course_id = p_course_id;
        IF v_student_count > 0 THEN
            RAISE EXCEPTION 'COURSE_HAS_STUDENTS';
        END IF;

        SELECT COUNT(*) INTO v_order_count FROM public.orders WHERE course_id = p_course_id AND status = 'PAID';
        IF v_order_count > 0 THEN
            RAISE EXCEPTION 'COURSE_HAS_ORDERS';
        END IF;
    END IF;

    -- Clean up related data before deleting the course
    DELETE FROM public.enrollments WHERE course_id = p_course_id;
    DELETE FROM public.orders WHERE course_id = p_course_id;
    DELETE FROM public.course_reviews WHERE course_id = p_course_id;
    DELETE FROM public.lessons WHERE section_id IN (
        SELECT id FROM public.course_sections WHERE course_id = p_course_id
    );
    DELETE FROM public.course_sections WHERE course_id = p_course_id;
    DELETE FROM public.courses WHERE id = p_course_id;

    -- Log the action
    INSERT INTO public.audit_logs (action, target_type, target_id, actor_role, metadata)
    VALUES (
        'DELETE_COURSE',
        'course',
        p_course_id,
        v_caller_role,
        jsonb_build_object('teacher_id', v_course_teacher_id)
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
GRANT EXECUTE ON FUNCTION public.delete_course(UUID) TO authenticated;
