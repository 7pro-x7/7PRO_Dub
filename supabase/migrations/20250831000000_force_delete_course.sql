-- ============================================================================
-- Force-delete course: allows owner/admin to delete courses even with enrolled students
-- ============================================================================

CREATE OR REPLACE FUNCTION public.delete_course_force(p_course_id UUID)
RETURNS VOID AS $$
DECLARE
    v_role TEXT;
BEGIN
    -- Only owner/admin can force-delete
    SELECT role INTO v_role FROM public.profiles WHERE id = auth.uid();
    IF v_role NOT IN ('OWNER', 'ADMIN') THEN
        RAISE EXCEPTION 'Only owner or admin can force-delete courses';
    END IF;

    -- Delete enrollments first (cascade should handle this, but be explicit)
    DELETE FROM public.enrollments WHERE course_id = p_course_id;
    
    -- Delete orders related to this course
    DELETE FROM public.orders WHERE course_id = p_course_id;
    
    -- Delete course reviews
    DELETE FROM public.course_reviews WHERE course_id = p_course_id;
    
    -- Delete course sections and lessons
    DELETE FROM public.lessons WHERE section_id IN (
        SELECT id FROM public.course_sections WHERE course_id = p_course_id
    );
    DELETE FROM public.course_sections WHERE course_id = p_course_id;
    
    -- Delete the course itself
    DELETE FROM public.courses WHERE id = p_course_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
-- Grant execute to authenticated users (RLS will handle the role check)
GRANT EXECUTE ON FUNCTION public.delete_course_force(UUID) TO authenticated;
