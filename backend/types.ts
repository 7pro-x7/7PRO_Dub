/* eslint-disable */
// AUTO-GENERATED — DO NOT EDIT
// Run migrations to regenerate.

export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
  public: {
    Tables: {
      abuse_reports: {
        Row: {
          created_at: string
          id: string
          reason: string
          reporter_id: string
          status: string
          target_id: string
          target_type: string
        }
        Insert: {
          created_at?: string
          id?: string
          reason: string
          reporter_id: string
          status?: string
          target_id: string
          target_type: string
        }
        Update: {
          created_at?: string
          id?: string
          reason?: string
          reporter_id?: string
          status?: string
          target_id?: string
          target_type?: string
        }
        Relationships: [
          {
            foreignKeyName: "abuse_reports_reporter_id_fkey"
            columns: ["reporter_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      admin_permissions: {
        Row: {
          created_at: string
          granted_by: string | null
          id: string
          permission: string
          user_id: string
        }
        Insert: {
          created_at?: string
          granted_by?: string | null
          id?: string
          permission: string
          user_id: string
        }
        Update: {
          created_at?: string
          granted_by?: string | null
          id?: string
          permission?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "admin_permissions_granted_by_fkey"
            columns: ["granted_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "admin_permissions_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      admob_placements: {
        Row: {
          ad_unit_id: string
          created_at: string
          display_interval_seconds: number
          format: string
          free_content_only: boolean
          frequency: number
          id: string
          is_enabled: boolean
          max_impressions_per_session: number
          screen: string
          section: string | null
          sort_order: number
          spacing: number
          updated_at: string
        }
        Insert: {
          ad_unit_id: string
          created_at?: string
          display_interval_seconds?: number
          format?: string
          free_content_only?: boolean
          frequency?: number
          id?: string
          is_enabled?: boolean
          max_impressions_per_session?: number
          screen: string
          section?: string | null
          sort_order?: number
          spacing?: number
          updated_at?: string
        }
        Update: {
          ad_unit_id?: string
          created_at?: string
          display_interval_seconds?: number
          format?: string
          free_content_only?: boolean
          frequency?: number
          id?: string
          is_enabled?: boolean
          max_impressions_per_session?: number
          screen?: string
          section?: string | null
          sort_order?: number
          spacing?: number
          updated_at?: string
        }
        Relationships: []
      }
      app_settings: {
        Row: {
          description: string | null
          key: string
          updated_at: string
          updated_by: string | null
          value: Json
        }
        Insert: {
          description?: string | null
          key: string
          updated_at?: string
          updated_by?: string | null
          value: Json
        }
        Update: {
          description?: string | null
          key?: string
          updated_at?: string
          updated_by?: string | null
          value?: Json
        }
        Relationships: [
          {
            foreignKeyName: "app_settings_updated_by_fkey"
            columns: ["updated_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      attendance: {
        Row: {
          created_at: string
          id: string
          live_group_id: string | null
          live_session_id: string | null
          minutes: number
          note: string | null
          present: boolean
          session_date: string
          student_user_id: string | null
          teacher_id: string
          teacher_student_id: string | null
        }
        Insert: {
          created_at?: string
          id?: string
          live_group_id?: string | null
          live_session_id?: string | null
          minutes?: number
          note?: string | null
          present?: boolean
          session_date?: string
          student_user_id?: string | null
          teacher_id: string
          teacher_student_id?: string | null
        }
        Update: {
          created_at?: string
          id?: string
          live_group_id?: string | null
          live_session_id?: string | null
          minutes?: number
          note?: string | null
          present?: boolean
          session_date?: string
          student_user_id?: string | null
          teacher_id?: string
          teacher_student_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "attendance_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "attendance_live_session_id_fkey"
            columns: ["live_session_id"]
            isOneToOne: false
            referencedRelation: "live_sessions"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "attendance_student_user_id_fkey"
            columns: ["student_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "attendance_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "attendance_teacher_student_id_fkey"
            columns: ["teacher_student_id"]
            isOneToOne: false
            referencedRelation: "teacher_students"
            referencedColumns: ["id"]
          },
        ]
      }
      audit_logs: {
        Row: {
          action: string
          actor_id: string | null
          actor_role: Database["public"]["Enums"]["app_role"] | null
          created_at: string
          id: number
          metadata: Json
          target_id: string | null
          target_type: string | null
        }
        Insert: {
          action: string
          actor_id?: string | null
          actor_role?: Database["public"]["Enums"]["app_role"] | null
          created_at?: string
          id?: number
          metadata?: Json
          target_id?: string | null
          target_type?: string | null
        }
        Update: {
          action?: string
          actor_id?: string | null
          actor_role?: Database["public"]["Enums"]["app_role"] | null
          created_at?: string
          id?: number
          metadata?: Json
          target_id?: string | null
          target_type?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "audit_logs_actor_id_fkey"
            columns: ["actor_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      categories: {
        Row: {
          created_at: string
          icon: string | null
          id: string
          is_active: boolean
          name: string
          slug: string
          sort_order: number
        }
        Insert: {
          created_at?: string
          icon?: string | null
          id?: string
          is_active?: boolean
          name: string
          slug: string
          sort_order?: number
        }
        Update: {
          created_at?: string
          icon?: string | null
          id?: string
          is_active?: boolean
          name?: string
          slug?: string
          sort_order?: number
        }
        Relationships: []
      }
      certificates: {
        Row: {
          course_id: string
          course_title: string
          id: string
          issued_at: string
          revoked: boolean
          score_percent: number | null
          serial: string
          student_name: string
          teacher_id: string | null
          teacher_name: string | null
          user_id: string
          verify_code: string
        }
        Insert: {
          course_id: string
          course_title: string
          id?: string
          issued_at?: string
          revoked?: boolean
          score_percent?: number | null
          serial: string
          student_name: string
          teacher_id?: string | null
          teacher_name?: string | null
          user_id: string
          verify_code: string
        }
        Update: {
          course_id?: string
          course_title?: string
          id?: string
          issued_at?: string
          revoked?: boolean
          score_percent?: number | null
          serial?: string
          student_name?: string
          teacher_id?: string | null
          teacher_name?: string | null
          user_id?: string
          verify_code?: string
        }
        Relationships: [
          {
            foreignKeyName: "certificates_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "certificates_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "certificates_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      cms_banners: {
        Row: {
          created_at: string
          cta_label: string | null
          cta_target: string | null
          ends_at: string | null
          id: string
          image_url: string | null
          is_active: boolean
          placement: string
          sort_order: number
          starts_at: string | null
          subtitle: string | null
          title: string
          updated_at: string
        }
        Insert: {
          created_at?: string
          cta_label?: string | null
          cta_target?: string | null
          ends_at?: string | null
          id?: string
          image_url?: string | null
          is_active?: boolean
          placement?: string
          sort_order?: number
          starts_at?: string | null
          subtitle?: string | null
          title: string
          updated_at?: string
        }
        Update: {
          created_at?: string
          cta_label?: string | null
          cta_target?: string | null
          ends_at?: string | null
          id?: string
          image_url?: string | null
          is_active?: boolean
          placement?: string
          sort_order?: number
          starts_at?: string | null
          subtitle?: string | null
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      conversations: {
        Row: {
          created_at: string
          id: string
          last_message_at: string
          student_blocked: boolean
          student_id: string
          teacher_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          last_message_at?: string
          student_blocked?: boolean
          student_id: string
          teacher_id: string
        }
        Update: {
          created_at?: string
          id?: string
          last_message_at?: string
          student_blocked?: boolean
          student_id?: string
          teacher_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "conversations_student_id_fkey"
            columns: ["student_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "conversations_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      country_pricing: {
        Row: {
          country_code: string
          created_at: string
          currency: string
          discount_percent: number
          fx_multiplier: number
          id: string
          is_active: boolean
          region_group: string | null
          round_to: number
          updated_at: string
        }
        Insert: {
          country_code: string
          created_at?: string
          currency: string
          discount_percent?: number
          fx_multiplier?: number
          id?: string
          is_active?: boolean
          region_group?: string | null
          round_to?: number
          updated_at?: string
        }
        Update: {
          country_code?: string
          created_at?: string
          currency?: string
          discount_percent?: number
          fx_multiplier?: number
          id?: string
          is_active?: boolean
          region_group?: string | null
          round_to?: number
          updated_at?: string
        }
        Relationships: []
      }
      coupon_redemptions: {
        Row: {
          coupon_id: string
          created_at: string
          currency: string
          discount_amount: number
          id: string
          order_id: string | null
          user_id: string
        }
        Insert: {
          coupon_id: string
          created_at?: string
          currency: string
          discount_amount: number
          id?: string
          order_id?: string | null
          user_id: string
        }
        Update: {
          coupon_id?: string
          created_at?: string
          currency?: string
          discount_amount?: number
          id?: string
          order_id?: string | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "coupon_redemptions_coupon_id_fkey"
            columns: ["coupon_id"]
            isOneToOne: false
            referencedRelation: "coupons"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "coupon_redemptions_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      coupons: {
        Row: {
          amount: number
          code: string
          country_codes: string[]
          course_ids: string[]
          created_at: string
          created_by: string | null
          currency: string | null
          expires_at: string | null
          id: string
          is_active: boolean
          kind: string
          live_service_ids: string[]
          max_uses: number | null
          max_uses_per_user: number
          min_purchase: number
          starts_at: string | null
          teacher_ids: string[]
          updated_at: string
          used_count: number
        }
        Insert: {
          amount: number
          code: string
          country_codes?: string[]
          course_ids?: string[]
          created_at?: string
          created_by?: string | null
          currency?: string | null
          expires_at?: string | null
          id?: string
          is_active?: boolean
          kind?: string
          live_service_ids?: string[]
          max_uses?: number | null
          max_uses_per_user?: number
          min_purchase?: number
          starts_at?: string | null
          teacher_ids?: string[]
          updated_at?: string
          used_count?: number
        }
        Update: {
          amount?: number
          code?: string
          country_codes?: string[]
          course_ids?: string[]
          created_at?: string
          created_by?: string | null
          currency?: string | null
          expires_at?: string | null
          id?: string
          is_active?: boolean
          kind?: string
          live_service_ids?: string[]
          max_uses?: number | null
          max_uses_per_user?: number
          min_purchase?: number
          starts_at?: string | null
          teacher_ids?: string[]
          updated_at?: string
          used_count?: number
        }
        Relationships: [
          {
            foreignKeyName: "coupons_created_by_fkey"
            columns: ["created_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      course_sections: {
        Row: {
          course_id: string
          created_at: string
          id: string
          sort_order: number
          subtitle: string | null
          title: string
          updated_at: string
        }
        Insert: {
          course_id: string
          created_at?: string
          id?: string
          sort_order?: number
          subtitle?: string | null
          title: string
          updated_at?: string
        }
        Update: {
          course_id?: string
          created_at?: string
          id?: string
          sort_order?: number
          subtitle?: string | null
          title?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "course_sections_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
        ]
      }
      courses: {
        Row: {
          ads_enabled: boolean
          base_currency: string
          base_price: number
          category_id: string | null
          certificate_enabled: boolean
          certificate_min_percent: number
          commission_rate: number | null
          created_at: string
          description: string | null
          enrollments_count: number
          gallery: Json
          id: string
          is_featured: boolean
          is_free: boolean
          language: string | null
          level: string | null
          published_at: string | null
          rating_avg: number
          rating_count: number
          rejection_note: string | null
          reviewed_at: string | null
          reviewed_by: string | null
          status: Database["public"]["Enums"]["content_status"]
          subtitle: string | null
          teacher_id: string
          thumbnail_url: string | null
          title: string
          updated_at: string
          views_count: number
        }
        Insert: {
          ads_enabled?: boolean
          base_currency?: string
          base_price?: number
          category_id?: string | null
          certificate_enabled?: boolean
          certificate_min_percent?: number
          commission_rate?: number | null
          created_at?: string
          description?: string | null
          enrollments_count?: number
          gallery?: Json
          id?: string
          is_featured?: boolean
          is_free?: boolean
          language?: string | null
          level?: string | null
          published_at?: string | null
          rating_avg?: number
          rating_count?: number
          rejection_note?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          status?: Database["public"]["Enums"]["content_status"]
          subtitle?: string | null
          teacher_id: string
          thumbnail_url?: string | null
          title: string
          updated_at?: string
          views_count?: number
        }
        Update: {
          ads_enabled?: boolean
          base_currency?: string
          base_price?: number
          category_id?: string | null
          certificate_enabled?: boolean
          certificate_min_percent?: number
          commission_rate?: number | null
          created_at?: string
          description?: string | null
          enrollments_count?: number
          gallery?: Json
          id?: string
          is_featured?: boolean
          is_free?: boolean
          language?: string | null
          level?: string | null
          published_at?: string | null
          rating_avg?: number
          rating_count?: number
          rejection_note?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          status?: Database["public"]["Enums"]["content_status"]
          subtitle?: string | null
          teacher_id?: string
          thumbnail_url?: string | null
          title?: string
          updated_at?: string
          views_count?: number
        }
        Relationships: [
          {
            foreignKeyName: "courses_category_id_fkey"
            columns: ["category_id"]
            isOneToOne: false
            referencedRelation: "categories"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "courses_reviewed_by_fkey"
            columns: ["reviewed_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "courses_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      enrollments: {
        Row: {
          completed_at: string | null
          course_id: string
          created_at: string
          id: string
          last_lesson_id: string | null
          order_id: string | null
          progress_percent: number
          status: string
          updated_at: string
          user_id: string
        }
        Insert: {
          completed_at?: string | null
          course_id: string
          created_at?: string
          id?: string
          last_lesson_id?: string | null
          order_id?: string | null
          progress_percent?: number
          status?: string
          updated_at?: string
          user_id: string
        }
        Update: {
          completed_at?: string | null
          course_id?: string
          created_at?: string
          id?: string
          last_lesson_id?: string | null
          order_id?: string | null
          progress_percent?: number
          status?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "enrollments_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "enrollments_last_lesson_id_fkey"
            columns: ["last_lesson_id"]
            isOneToOne: false
            referencedRelation: "lessons"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "enrollments_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "enrollments_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      exercise_questions: {
        Row: {
          correct_answer: string | null
          created_at: string | null
          exercise_id: string
          id: string
          media_type: string | null
          media_url: string | null
          options: Json | null
          order_index: number
          question_text: string
          question_type: string
        }
        Insert: {
          correct_answer?: string | null
          created_at?: string | null
          exercise_id: string
          id?: string
          media_type?: string | null
          media_url?: string | null
          options?: Json | null
          order_index: number
          question_text: string
          question_type: string
        }
        Update: {
          correct_answer?: string | null
          created_at?: string | null
          exercise_id?: string
          id?: string
          media_type?: string | null
          media_url?: string | null
          options?: Json | null
          order_index?: number
          question_text?: string
          question_type?: string
        }
        Relationships: [
          {
            foreignKeyName: "exercise_questions_exercise_id_fkey"
            columns: ["exercise_id"]
            isOneToOne: false
            referencedRelation: "exercises"
            referencedColumns: ["id"]
          },
        ]
      }
      exercises: {
        Row: {
          created_at: string | null
          description: string | null
          duration_minutes: number | null
          id: string
          is_published: boolean | null
          language: string | null
          teacher_id: string | null
          title: string
          type: string
          updated_at: string | null
        }
        Insert: {
          created_at?: string | null
          description?: string | null
          duration_minutes?: number | null
          id?: string
          is_published?: boolean | null
          language?: string | null
          teacher_id?: string | null
          title: string
          type: string
          updated_at?: string | null
        }
        Update: {
          created_at?: string | null
          description?: string | null
          duration_minutes?: number | null
          id?: string
          is_published?: boolean | null
          language?: string | null
          teacher_id?: string | null
          title?: string
          type?: string
          updated_at?: string | null
        }
        Relationships: []
      }
      lesson_progress: {
        Row: {
          completed_at: string | null
          course_id: string
          id: string
          is_completed: boolean
          lesson_id: string
          seconds_watched: number
          updated_at: string
          user_id: string
        }
        Insert: {
          completed_at?: string | null
          course_id: string
          id?: string
          is_completed?: boolean
          lesson_id: string
          seconds_watched?: number
          updated_at?: string
          user_id: string
        }
        Update: {
          completed_at?: string | null
          course_id?: string
          id?: string
          is_completed?: boolean
          lesson_id?: string
          seconds_watched?: number
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "lesson_progress_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "lesson_progress_lesson_id_fkey"
            columns: ["lesson_id"]
            isOneToOne: false
            referencedRelation: "lessons"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "lesson_progress_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      lesson_quizzes: {
        Row: {
          correct_indexes: number[]
          id: string
          lesson_id: string
          options: Json
          points: number
          question: string
          sort_order: number
        }
        Insert: {
          correct_indexes?: number[]
          id?: string
          lesson_id: string
          options?: Json
          points?: number
          question: string
          sort_order?: number
        }
        Update: {
          correct_indexes?: number[]
          id?: string
          lesson_id?: string
          options?: Json
          points?: number
          question?: string
          sort_order?: number
        }
        Relationships: [
          {
            foreignKeyName: "lesson_quizzes_lesson_id_fkey"
            columns: ["lesson_id"]
            isOneToOne: false
            referencedRelation: "lessons"
            referencedColumns: ["id"]
          },
        ]
      }
      lessons: {
        Row: {
          content: string | null
          course_id: string
          created_at: string
          description: string | null
          document_url: string | null
          duration_seconds: number
          id: string
          is_preview: boolean
          kind: Database["public"]["Enums"]["lesson_kind"]
          section_id: string | null
          sort_order: number
          title: string
          updated_at: string
          video_url: string | null
        }
        Insert: {
          content?: string | null
          course_id: string
          created_at?: string
          description?: string | null
          document_url?: string | null
          duration_seconds?: number
          id?: string
          is_preview?: boolean
          kind?: Database["public"]["Enums"]["lesson_kind"]
          section_id?: string | null
          sort_order?: number
          title: string
          updated_at?: string
          video_url?: string | null
        }
        Update: {
          content?: string | null
          course_id?: string
          created_at?: string
          description?: string | null
          document_url?: string | null
          duration_seconds?: number
          id?: string
          is_preview?: boolean
          kind?: Database["public"]["Enums"]["lesson_kind"]
          section_id?: string | null
          sort_order?: number
          title?: string
          updated_at?: string
          video_url?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "lessons_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "lessons_section_id_fkey"
            columns: ["section_id"]
            isOneToOne: false
            referencedRelation: "course_sections"
            referencedColumns: ["id"]
          },
        ]
      }
      live_groups: {
        Row: {
          capacity: number
          created_at: string
          id: string
          is_active: boolean
          level: string | null
          live_service_id: string
          name: string
          schedule_note: string | null
          seats_taken: number
          starts_on: string | null
          teacher_id: string
          updated_at: string
        }
        Insert: {
          capacity?: number
          created_at?: string
          id?: string
          is_active?: boolean
          level?: string | null
          live_service_id: string
          name: string
          schedule_note?: string | null
          seats_taken?: number
          starts_on?: string | null
          teacher_id: string
          updated_at?: string
        }
        Update: {
          capacity?: number
          created_at?: string
          id?: string
          is_active?: boolean
          level?: string | null
          live_service_id?: string
          name?: string
          schedule_note?: string | null
          seats_taken?: number
          starts_on?: string | null
          teacher_id?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "live_groups_live_service_id_fkey"
            columns: ["live_service_id"]
            isOneToOne: false
            referencedRelation: "live_services"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "live_groups_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      live_plans: {
        Row: {
          base_price: number
          id: string
          is_active: boolean
          is_best_value: boolean
          label: string
          live_service_id: string
          period_count: number
          period_kind: string
          sort_order: number
        }
        Insert: {
          base_price: number
          id?: string
          is_active?: boolean
          is_best_value?: boolean
          label: string
          live_service_id: string
          period_count?: number
          period_kind?: string
          sort_order?: number
        }
        Update: {
          base_price?: number
          id?: string
          is_active?: boolean
          is_best_value?: boolean
          label?: string
          live_service_id?: string
          period_count?: number
          period_kind?: string
          sort_order?: number
        }
        Relationships: [
          {
            foreignKeyName: "live_plans_live_service_id_fkey"
            columns: ["live_service_id"]
            isOneToOne: false
            referencedRelation: "live_services"
            referencedColumns: ["id"]
          },
        ]
      }
      live_services: {
        Row: {
          base_currency: string
          base_price: number
          commission_rate: number | null
          cover_url: string | null
          created_at: string
          description: string | null
          duration_minutes: number
          id: string
          level: string | null
          meeting_instructions: string | null
          meeting_provider: string | null
          meeting_url: string | null
          rating_avg: number
          rating_count: number
          rejection_note: string | null
          reviewed_at: string | null
          reviewed_by: string | null
          schedule_note: string | null
          start_time: string | null
          status: Database["public"]["Enums"]["content_status"]
          teacher_id: string
          timezone: string | null
          title: string
          updated_at: string
          weekdays: number[]
        }
        Insert: {
          base_currency?: string
          base_price?: number
          commission_rate?: number | null
          cover_url?: string | null
          created_at?: string
          description?: string | null
          duration_minutes?: number
          id?: string
          level?: string | null
          meeting_instructions?: string | null
          meeting_provider?: string | null
          meeting_url?: string | null
          rating_avg?: number
          rating_count?: number
          rejection_note?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          schedule_note?: string | null
          start_time?: string | null
          status?: Database["public"]["Enums"]["content_status"]
          teacher_id: string
          timezone?: string | null
          title: string
          updated_at?: string
          weekdays?: number[]
        }
        Update: {
          base_currency?: string
          base_price?: number
          commission_rate?: number | null
          cover_url?: string | null
          created_at?: string
          description?: string | null
          duration_minutes?: number
          id?: string
          level?: string | null
          meeting_instructions?: string | null
          meeting_provider?: string | null
          meeting_url?: string | null
          rating_avg?: number
          rating_count?: number
          rejection_note?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          schedule_note?: string | null
          start_time?: string | null
          status?: Database["public"]["Enums"]["content_status"]
          teacher_id?: string
          timezone?: string | null
          title?: string
          updated_at?: string
          weekdays?: number[]
        }
        Relationships: [
          {
            foreignKeyName: "live_services_reviewed_by_fkey"
            columns: ["reviewed_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "live_services_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      live_sessions: {
        Row: {
          created_at: string
          duration_minutes: number
          id: string
          live_group_id: string
          scheduled_at: string
          status: string
          teacher_id: string
          title: string | null
        }
        Insert: {
          created_at?: string
          duration_minutes?: number
          id?: string
          live_group_id: string
          scheduled_at: string
          status?: string
          teacher_id: string
          title?: string | null
        }
        Update: {
          created_at?: string
          duration_minutes?: number
          id?: string
          live_group_id?: string
          scheduled_at?: string
          status?: string
          teacher_id?: string
          title?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "live_sessions_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "live_sessions_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      live_waitlist: {
        Row: {
          created_at: string
          id: string
          live_group_id: string
          offered_at: string | null
          status: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          live_group_id: string
          offered_at?: string | null
          status?: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          live_group_id?: string
          offered_at?: string | null
          status?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "live_waitlist_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "live_waitlist_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      messages: {
        Row: {
          body: string
          conversation_id: string
          created_at: string
          id: number
          read_at: string | null
          reported: boolean
          sender_id: string
        }
        Insert: {
          body: string
          conversation_id: string
          created_at?: string
          id?: number
          read_at?: string | null
          reported?: boolean
          sender_id: string
        }
        Update: {
          body?: string
          conversation_id?: string
          created_at?: string
          id?: number
          read_at?: string | null
          reported?: boolean
          sender_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "messages_conversation_id_fkey"
            columns: ["conversation_id"]
            isOneToOne: false
            referencedRelation: "conversations"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "messages_sender_id_fkey"
            columns: ["sender_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      notifications: {
        Row: {
          body: string | null
          created_at: string
          data: Json
          id: number
          kind: string
          read_at: string | null
          title: string
          user_id: string
        }
        Insert: {
          body?: string | null
          created_at?: string
          data?: Json
          id?: number
          kind: string
          read_at?: string | null
          title: string
          user_id: string
        }
        Update: {
          body?: string | null
          created_at?: string
          data?: Json
          id?: number
          kind?: string
          read_at?: string | null
          title?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "notifications_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      orders: {
        Row: {
          base_price: number
          checkout_url: string | null
          commission_rate: number
          country_code: string
          coupon_id: string | null
          course_id: string | null
          created_at: string
          currency: string
          discount_amount: number
          failure_reason: string | null
          id: string
          idempotency_key: string | null
          item_type: string
          list_price: number
          live_group_id: string | null
          live_plan_id: string | null
          live_service_id: string | null
          paid_at: string | null
          platform_amount: number
          pricing_rule: Json
          provider: string | null
          provider_ref: string | null
          refunded_amount: number
          status: Database["public"]["Enums"]["order_status"]
          teacher_amount: number
          teacher_id: string | null
          total_amount: number
          updated_at: string
          user_id: string
        }
        Insert: {
          base_price: number
          checkout_url?: string | null
          commission_rate: number
          country_code: string
          coupon_id?: string | null
          course_id?: string | null
          created_at?: string
          currency: string
          discount_amount?: number
          failure_reason?: string | null
          id?: string
          idempotency_key?: string | null
          item_type: string
          list_price: number
          live_group_id?: string | null
          live_plan_id?: string | null
          live_service_id?: string | null
          paid_at?: string | null
          platform_amount?: number
          pricing_rule?: Json
          provider?: string | null
          provider_ref?: string | null
          refunded_amount?: number
          status?: Database["public"]["Enums"]["order_status"]
          teacher_amount?: number
          teacher_id?: string | null
          total_amount: number
          updated_at?: string
          user_id: string
        }
        Update: {
          base_price?: number
          checkout_url?: string | null
          commission_rate?: number
          country_code?: string
          coupon_id?: string | null
          course_id?: string | null
          created_at?: string
          currency?: string
          discount_amount?: number
          failure_reason?: string | null
          id?: string
          idempotency_key?: string | null
          item_type?: string
          list_price?: number
          live_group_id?: string | null
          live_plan_id?: string | null
          live_service_id?: string | null
          paid_at?: string | null
          platform_amount?: number
          pricing_rule?: Json
          provider?: string | null
          provider_ref?: string | null
          refunded_amount?: number
          status?: Database["public"]["Enums"]["order_status"]
          teacher_amount?: number
          teacher_id?: string | null
          total_amount?: number
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "orders_coupon_id_fkey"
            columns: ["coupon_id"]
            isOneToOne: false
            referencedRelation: "coupons"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "orders_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "orders_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "orders_live_plan_id_fkey"
            columns: ["live_plan_id"]
            isOneToOne: false
            referencedRelation: "live_plans"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "orders_live_service_id_fkey"
            columns: ["live_service_id"]
            isOneToOne: false
            referencedRelation: "live_services"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "orders_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "orders_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      payment_events: {
        Row: {
          created_at: string
          event_type: string
          id: string
          order_id: string | null
          payload: Json
          processed_at: string | null
          provider: string
          provider_event_id: string
        }
        Insert: {
          created_at?: string
          event_type: string
          id?: string
          order_id?: string | null
          payload?: Json
          processed_at?: string | null
          provider: string
          provider_event_id: string
        }
        Update: {
          created_at?: string
          event_type?: string
          id?: string
          order_id?: string | null
          payload?: Json
          processed_at?: string | null
          provider?: string
          provider_event_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "payment_events_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
        ]
      }
      payouts: {
        Row: {
          amount: number
          currency: string
          destination: string | null
          id: string
          method: string | null
          note: string | null
          paid_at: string | null
          reference: string | null
          requested_at: string
          reviewed_at: string | null
          reviewed_by: string | null
          status: Database["public"]["Enums"]["payout_status"]
          teacher_id: string
        }
        Insert: {
          amount: number
          currency: string
          destination?: string | null
          id?: string
          method?: string | null
          note?: string | null
          paid_at?: string | null
          reference?: string | null
          requested_at?: string
          reviewed_at?: string | null
          reviewed_by?: string | null
          status?: Database["public"]["Enums"]["payout_status"]
          teacher_id: string
        }
        Update: {
          amount?: number
          currency?: string
          destination?: string | null
          id?: string
          method?: string | null
          note?: string | null
          paid_at?: string | null
          reference?: string | null
          requested_at?: string
          reviewed_at?: string | null
          reviewed_by?: string | null
          status?: Database["public"]["Enums"]["payout_status"]
          teacher_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "payouts_reviewed_by_fkey"
            columns: ["reviewed_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "payouts_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      placement_tests: {
        Row: {
          attempt_limit: number
          cover_url: string | null
          created_at: string
          description: string | null
          id: string
          is_adaptive: boolean
          kind: string
          level_thresholds: Json
          levels: string[]
          live_group_id: string | null
          owner_id: string | null
          parent_id: string | null
          passing_score: number
          question_count: number
          randomize: boolean
          scope: string
          status: Database["public"]["Enums"]["test_status"]
          target_level: string | null
          time_limit_seconds: number
          title: string
          updated_at: string
          version: number
        }
        Insert: {
          attempt_limit?: number
          cover_url?: string | null
          created_at?: string
          description?: string | null
          id?: string
          is_adaptive?: boolean
          kind?: string
          level_thresholds?: Json
          levels?: string[]
          live_group_id?: string | null
          owner_id?: string | null
          parent_id?: string | null
          passing_score?: number
          question_count?: number
          randomize?: boolean
          scope?: string
          status?: Database["public"]["Enums"]["test_status"]
          target_level?: string | null
          time_limit_seconds?: number
          title: string
          updated_at?: string
          version?: number
        }
        Update: {
          attempt_limit?: number
          cover_url?: string | null
          created_at?: string
          description?: string | null
          id?: string
          is_adaptive?: boolean
          kind?: string
          level_thresholds?: Json
          levels?: string[]
          live_group_id?: string | null
          owner_id?: string | null
          parent_id?: string | null
          passing_score?: number
          question_count?: number
          randomize?: boolean
          scope?: string
          status?: Database["public"]["Enums"]["test_status"]
          target_level?: string | null
          time_limit_seconds?: number
          title?: string
          updated_at?: string
          version?: number
        }
        Relationships: [
          {
            foreignKeyName: "placement_tests_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "placement_tests_owner_id_fkey"
            columns: ["owner_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "placement_tests_parent_id_fkey"
            columns: ["parent_id"]
            isOneToOne: false
            referencedRelation: "placement_tests"
            referencedColumns: ["id"]
          },
        ]
      }
      price_overrides: {
        Row: {
          country_code: string | null
          created_at: string
          currency: string
          id: string
          is_active: boolean
          item_id: string
          item_type: string
          price: number
        }
        Insert: {
          country_code?: string | null
          created_at?: string
          currency: string
          id?: string
          is_active?: boolean
          item_id: string
          item_type: string
          price: number
        }
        Update: {
          country_code?: string | null
          created_at?: string
          currency?: string
          id?: string
          is_active?: boolean
          item_id?: string
          item_type?: string
          price?: number
        }
        Relationships: []
      }
      profiles: {
        Row: {
          avatar_url: string | null
          country_code: string | null
          created_at: string
          current_level: string | null
          email: string | null
          email_verified: boolean
          full_name: string | null
          id: string
          last_active_on: string | null
          locale: string | null
          phone: string | null
          phone_verified: boolean
          role: Database["public"]["Enums"]["app_role"]
          status: Database["public"]["Enums"]["account_status"]
          streak_days: number
          updated_at: string
        }
        Insert: {
          avatar_url?: string | null
          country_code?: string | null
          created_at?: string
          current_level?: string | null
          email?: string | null
          email_verified?: boolean
          full_name?: string | null
          id: string
          last_active_on?: string | null
          locale?: string | null
          phone?: string | null
          phone_verified?: boolean
          role?: Database["public"]["Enums"]["app_role"]
          status?: Database["public"]["Enums"]["account_status"]
          streak_days?: number
          updated_at?: string
        }
        Update: {
          avatar_url?: string | null
          country_code?: string | null
          created_at?: string
          current_level?: string | null
          email?: string | null
          email_verified?: boolean
          full_name?: string | null
          id?: string
          last_active_on?: string | null
          locale?: string | null
          phone?: string | null
          phone_verified?: boolean
          role?: Database["public"]["Enums"]["app_role"]
          status?: Database["public"]["Enums"]["account_status"]
          streak_days?: number
          updated_at?: string
        }
        Relationships: []
      }
      quiz_attempts: {
        Row: {
          answers: Json
          course_id: string
          created_at: string
          id: string
          lesson_id: string
          max_score: number
          passed: boolean
          score: number
          user_id: string
        }
        Insert: {
          answers?: Json
          course_id: string
          created_at?: string
          id?: string
          lesson_id: string
          max_score?: number
          passed?: boolean
          score?: number
          user_id: string
        }
        Update: {
          answers?: Json
          course_id?: string
          created_at?: string
          id?: string
          lesson_id?: string
          max_score?: number
          passed?: boolean
          score?: number
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "quiz_attempts_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "quiz_attempts_lesson_id_fkey"
            columns: ["lesson_id"]
            isOneToOne: false
            referencedRelation: "lessons"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "quiz_attempts_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      refunds: {
        Row: {
          amount: number
          created_at: string
          created_by: string | null
          currency: string
          id: string
          kind: string
          order_id: string
          reason: string | null
          status: string
        }
        Insert: {
          amount: number
          created_at?: string
          created_by?: string | null
          currency: string
          id?: string
          kind?: string
          order_id: string
          reason?: string | null
          status?: string
        }
        Update: {
          amount?: number
          created_at?: string
          created_by?: string | null
          currency?: string
          id?: string
          kind?: string
          order_id?: string
          reason?: string | null
          status?: string
        }
        Relationships: [
          {
            foreignKeyName: "refunds_created_by_fkey"
            columns: ["created_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "refunds_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
        ]
      }
      reviews: {
        Row: {
          body: string | null
          course_id: string | null
          created_at: string
          hidden_by: string | null
          hidden_reason: string | null
          id: string
          is_hidden: boolean
          live_service_id: string | null
          order_id: string | null
          rating: number
          target_type: string
          teacher_id: string | null
          updated_at: string
          user_id: string
        }
        Insert: {
          body?: string | null
          course_id?: string | null
          created_at?: string
          hidden_by?: string | null
          hidden_reason?: string | null
          id?: string
          is_hidden?: boolean
          live_service_id?: string | null
          order_id?: string | null
          rating: number
          target_type: string
          teacher_id?: string | null
          updated_at?: string
          user_id: string
        }
        Update: {
          body?: string | null
          course_id?: string | null
          created_at?: string
          hidden_by?: string | null
          hidden_reason?: string | null
          id?: string
          is_hidden?: boolean
          live_service_id?: string | null
          order_id?: string | null
          rating?: number
          target_type?: string
          teacher_id?: string | null
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "reviews_course_id_fkey"
            columns: ["course_id"]
            isOneToOne: false
            referencedRelation: "courses"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "reviews_hidden_by_fkey"
            columns: ["hidden_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "reviews_live_service_id_fkey"
            columns: ["live_service_id"]
            isOneToOne: false
            referencedRelation: "live_services"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "reviews_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "reviews_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "reviews_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      student_payments: {
        Row: {
          amount: number
          created_at: string
          currency: string
          id: string
          method: string | null
          note: string | null
          order_id: string | null
          paid_on: string
          teacher_id: string
          teacher_student_id: string
        }
        Insert: {
          amount: number
          created_at?: string
          currency: string
          id?: string
          method?: string | null
          note?: string | null
          order_id?: string | null
          paid_on?: string
          teacher_id: string
          teacher_student_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          currency?: string
          id?: string
          method?: string | null
          note?: string | null
          order_id?: string | null
          paid_on?: string
          teacher_id?: string
          teacher_student_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "student_payments_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "student_payments_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "student_payments_teacher_student_id_fkey"
            columns: ["teacher_student_id"]
            isOneToOne: false
            referencedRelation: "teacher_students"
            referencedColumns: ["id"]
          },
        ]
      }
      student_results: {
        Row: {
          answers: Json
          created_at: string | null
          exercise_id: string
          id: string
          is_hidden: boolean | null
          score: number | null
          student_id: string
          submitted_at: string | null
          updated_at: string | null
        }
        Insert: {
          answers?: Json
          created_at?: string | null
          exercise_id: string
          id?: string
          is_hidden?: boolean | null
          score?: number | null
          student_id: string
          submitted_at?: string | null
          updated_at?: string | null
        }
        Update: {
          answers?: Json
          created_at?: string | null
          exercise_id?: string
          id?: string
          is_hidden?: boolean | null
          score?: number | null
          student_id?: string
          submitted_at?: string | null
          updated_at?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "student_results_exercise_id_fkey"
            columns: ["exercise_id"]
            isOneToOne: false
            referencedRelation: "exercises"
            referencedColumns: ["id"]
          },
        ]
      }
      subscriptions: {
        Row: {
          auto_renew: boolean
          cancelled_at: string | null
          created_at: string
          ends_at: string | null
          id: string
          live_group_id: string | null
          live_plan_id: string | null
          live_service_id: string
          order_id: string | null
          renews_at: string | null
          started_at: string | null
          status: Database["public"]["Enums"]["subscription_status"]
          teacher_id: string
          updated_at: string
          user_id: string
        }
        Insert: {
          auto_renew?: boolean
          cancelled_at?: string | null
          created_at?: string
          ends_at?: string | null
          id?: string
          live_group_id?: string | null
          live_plan_id?: string | null
          live_service_id: string
          order_id?: string | null
          renews_at?: string | null
          started_at?: string | null
          status?: Database["public"]["Enums"]["subscription_status"]
          teacher_id: string
          updated_at?: string
          user_id: string
        }
        Update: {
          auto_renew?: boolean
          cancelled_at?: string | null
          created_at?: string
          ends_at?: string | null
          id?: string
          live_group_id?: string | null
          live_plan_id?: string | null
          live_service_id?: string
          order_id?: string | null
          renews_at?: string | null
          started_at?: string | null
          status?: Database["public"]["Enums"]["subscription_status"]
          teacher_id?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "subscriptions_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "subscriptions_live_plan_id_fkey"
            columns: ["live_plan_id"]
            isOneToOne: false
            referencedRelation: "live_plans"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "subscriptions_live_service_id_fkey"
            columns: ["live_service_id"]
            isOneToOne: false
            referencedRelation: "live_services"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "subscriptions_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "subscriptions_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "subscriptions_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      support_tickets: {
        Row: {
          assigned_to: string | null
          category: string | null
          created_at: string
          id: string
          priority: string
          status: string
          subject: string
          updated_at: string
          user_id: string
        }
        Insert: {
          assigned_to?: string | null
          category?: string | null
          created_at?: string
          id?: string
          priority?: string
          status?: string
          subject: string
          updated_at?: string
          user_id: string
        }
        Update: {
          assigned_to?: string | null
          category?: string | null
          created_at?: string
          id?: string
          priority?: string
          status?: string
          subject?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "support_tickets_assigned_to_fkey"
            columns: ["assigned_to"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "support_tickets_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      teacher_applications: {
        Row: {
          bio: string | null
          created_at: string
          experience_years: number | null
          headline: string | null
          id: string
          languages: string[]
          review_note: string | null
          reviewed_at: string | null
          reviewed_by: string | null
          sample_url: string | null
          status: Database["public"]["Enums"]["application_status"]
          subjects: string[]
          updated_at: string
          user_id: string
        }
        Insert: {
          bio?: string | null
          created_at?: string
          experience_years?: number | null
          headline?: string | null
          id?: string
          languages?: string[]
          review_note?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          sample_url?: string | null
          status?: Database["public"]["Enums"]["application_status"]
          subjects?: string[]
          updated_at?: string
          user_id: string
        }
        Update: {
          bio?: string | null
          created_at?: string
          experience_years?: number | null
          headline?: string | null
          id?: string
          languages?: string[]
          review_note?: string | null
          reviewed_at?: string | null
          reviewed_by?: string | null
          sample_url?: string | null
          status?: Database["public"]["Enums"]["application_status"]
          subjects?: string[]
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "teacher_applications_reviewed_by_fkey"
            columns: ["reviewed_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "teacher_applications_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      teacher_ledger: {
        Row: {
          amount: number
          available_at: string | null
          created_at: string
          currency: string
          id: string
          kind: string
          note: string | null
          order_id: string | null
          payout_id: string | null
          status: Database["public"]["Enums"]["ledger_status"]
          teacher_id: string
        }
        Insert: {
          amount: number
          available_at?: string | null
          created_at?: string
          currency: string
          id?: string
          kind: string
          note?: string | null
          order_id?: string | null
          payout_id?: string | null
          status?: Database["public"]["Enums"]["ledger_status"]
          teacher_id: string
        }
        Update: {
          amount?: number
          available_at?: string | null
          created_at?: string
          currency?: string
          id?: string
          kind?: string
          note?: string | null
          order_id?: string | null
          payout_id?: string | null
          status?: Database["public"]["Enums"]["ledger_status"]
          teacher_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "teacher_ledger_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "teacher_ledger_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      teacher_profiles: {
        Row: {
          accepting_new_students: boolean
          badge: string | null
          bio: string | null
          can_manage_tests: boolean
          commission_rate: number | null
          cover_url: string | null
          created_at: string
          experience_years: number
          headline: string | null
          id: string
          languages: string[]
          live_enabled: boolean
          photo_url: string | null
          rating_avg: number
          rating_count: number
          status: Database["public"]["Enums"]["application_status"]
          students_count: number
          subjects: string[]
          updated_at: string
        }
        Insert: {
          accepting_new_students?: boolean
          badge?: string | null
          bio?: string | null
          can_manage_tests?: boolean
          commission_rate?: number | null
          cover_url?: string | null
          created_at?: string
          experience_years?: number
          headline?: string | null
          id: string
          languages?: string[]
          live_enabled?: boolean
          photo_url?: string | null
          rating_avg?: number
          rating_count?: number
          status?: Database["public"]["Enums"]["application_status"]
          students_count?: number
          subjects?: string[]
          updated_at?: string
        }
        Update: {
          accepting_new_students?: boolean
          badge?: string | null
          bio?: string | null
          can_manage_tests?: boolean
          commission_rate?: number | null
          cover_url?: string | null
          created_at?: string
          experience_years?: number
          headline?: string | null
          id?: string
          languages?: string[]
          live_enabled?: boolean
          photo_url?: string | null
          rating_avg?: number
          rating_count?: number
          status?: Database["public"]["Enums"]["application_status"]
          students_count?: number
          subjects?: string[]
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "teacher_profiles_id_fkey"
            columns: ["id"]
            isOneToOne: true
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      teacher_students: {
        Row: {
          amount: number | null
          created_at: string
          currency: string | null
          email: string | null
          full_name: string
          id: string
          live_group_id: string | null
          next_payment_date: string | null
          notes: string | null
          phone: string | null
          source: string
          status: Database["public"]["Enums"]["student_record_status"]
          student_user_id: string | null
          subscription_end: string | null
          subscription_start: string | null
          teacher_id: string
          updated_at: string
        }
        Insert: {
          amount?: number | null
          created_at?: string
          currency?: string | null
          email?: string | null
          full_name: string
          id?: string
          live_group_id?: string | null
          next_payment_date?: string | null
          notes?: string | null
          phone?: string | null
          source?: string
          status?: Database["public"]["Enums"]["student_record_status"]
          student_user_id?: string | null
          subscription_end?: string | null
          subscription_start?: string | null
          teacher_id: string
          updated_at?: string
        }
        Update: {
          amount?: number | null
          created_at?: string
          currency?: string | null
          email?: string | null
          full_name?: string
          id?: string
          live_group_id?: string | null
          next_payment_date?: string | null
          notes?: string | null
          phone?: string | null
          source?: string
          status?: Database["public"]["Enums"]["student_record_status"]
          student_user_id?: string | null
          subscription_end?: string | null
          subscription_start?: string | null
          teacher_id?: string
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "teacher_students_live_group_id_fkey"
            columns: ["live_group_id"]
            isOneToOne: false
            referencedRelation: "live_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "teacher_students_student_user_id_fkey"
            columns: ["student_user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "teacher_students_teacher_id_fkey"
            columns: ["teacher_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      test_answers: {
        Row: {
          answered_at: string
          attempt_id: string
          earned: number
          id: string
          is_correct: boolean
          match_answer: Json | null
          order_answer: number[] | null
          question_id: string
          selected_indexes: number[]
          text_answer: string | null
        }
        Insert: {
          answered_at?: string
          attempt_id: string
          earned?: number
          id?: string
          is_correct?: boolean
          match_answer?: Json | null
          order_answer?: number[] | null
          question_id: string
          selected_indexes?: number[]
          text_answer?: string | null
        }
        Update: {
          answered_at?: string
          attempt_id?: string
          earned?: number
          id?: string
          is_correct?: boolean
          match_answer?: Json | null
          order_answer?: number[] | null
          question_id?: string
          selected_indexes?: number[]
          text_answer?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "test_answers_attempt_id_fkey"
            columns: ["attempt_id"]
            isOneToOne: false
            referencedRelation: "test_attempts"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "test_answers_question_id_fkey"
            columns: ["question_id"]
            isOneToOne: false
            referencedRelation: "test_questions"
            referencedColumns: ["id"]
          },
        ]
      }
      test_attempts: {
        Row: {
          answered_count: number
          asked_question_ids: string[]
          correct_count: number
          current_difficulty: number
          id: string
          level: string | null
          max_score: number
          percent: number
          raw_score: number
          skill_breakdown: Json
          started_at: string
          status: string
          strengths: string[]
          submitted_at: string | null
          test_id: string
          test_version: number
          user_id: string
          weaknesses: string[]
        }
        Insert: {
          answered_count?: number
          asked_question_ids?: string[]
          correct_count?: number
          current_difficulty?: number
          id?: string
          level?: string | null
          max_score?: number
          percent?: number
          raw_score?: number
          skill_breakdown?: Json
          started_at?: string
          status?: string
          strengths?: string[]
          submitted_at?: string | null
          test_id: string
          test_version?: number
          user_id: string
          weaknesses?: string[]
        }
        Update: {
          answered_count?: number
          asked_question_ids?: string[]
          correct_count?: number
          current_difficulty?: number
          id?: string
          level?: string | null
          max_score?: number
          percent?: number
          raw_score?: number
          skill_breakdown?: Json
          started_at?: string
          status?: string
          strengths?: string[]
          submitted_at?: string | null
          test_id?: string
          test_version?: number
          user_id?: string
          weaknesses?: string[]
        }
        Relationships: [
          {
            foreignKeyName: "test_attempts_test_id_fkey"
            columns: ["test_id"]
            isOneToOne: false
            referencedRelation: "placement_tests"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "test_attempts_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      test_questions: {
        Row: {
          correct_indexes: number[]
          correct_text: string[] | null
          difficulty: number
          explanation: string | null
          id: string
          is_active: boolean
          kind: string
          match_pairs: Json | null
          media_audio_url: string | null
          media_image_url: string | null
          media_video_url: string | null
          options: Json
          order_answer: number[] | null
          points: number
          prompt: string
          skill: string
          sort_order: number
          test_id: string
          weight: number
        }
        Insert: {
          correct_indexes?: number[]
          correct_text?: string[] | null
          difficulty?: number
          explanation?: string | null
          id?: string
          is_active?: boolean
          kind?: string
          match_pairs?: Json | null
          media_audio_url?: string | null
          media_image_url?: string | null
          media_video_url?: string | null
          options?: Json
          order_answer?: number[] | null
          points?: number
          prompt: string
          skill?: string
          sort_order?: number
          test_id: string
          weight?: number
        }
        Update: {
          correct_indexes?: number[]
          correct_text?: string[] | null
          difficulty?: number
          explanation?: string | null
          id?: string
          is_active?: boolean
          kind?: string
          match_pairs?: Json | null
          media_audio_url?: string | null
          media_image_url?: string | null
          media_video_url?: string | null
          options?: Json
          order_answer?: number[] | null
          points?: number
          prompt?: string
          skill?: string
          sort_order?: number
          test_id?: string
          weight?: number
        }
        Relationships: [
          {
            foreignKeyName: "test_questions_test_id_fkey"
            columns: ["test_id"]
            isOneToOne: false
            referencedRelation: "placement_tests"
            referencedColumns: ["id"]
          },
        ]
      }
      ticket_messages: {
        Row: {
          body: string
          created_at: string
          id: number
          is_staff: boolean
          sender_id: string
          ticket_id: string
        }
        Insert: {
          body: string
          created_at?: string
          id?: number
          is_staff?: boolean
          sender_id: string
          ticket_id: string
        }
        Update: {
          body?: string
          created_at?: string
          id?: number
          is_staff?: boolean
          sender_id?: string
          ticket_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "ticket_messages_sender_id_fkey"
            columns: ["sender_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "ticket_messages_ticket_id_fkey"
            columns: ["ticket_id"]
            isOneToOne: false
            referencedRelation: "support_tickets"
            referencedColumns: ["id"]
          },
        ]
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      ad_config: {
        Args: { p_course_id?: string; p_screen: string }
        Returns: Json
      }
      app_role_of: {
        Args: { uid: string }
        Returns: Database["public"]["Enums"]["app_role"]
      }
      apply_to_teach: {
        Args: {
          p_bio: string
          p_headline: string
          p_languages: string[]
          p_sample_url: string
          p_subjects: string[]
          p_years: number
        }
        Returns: {
          bio: string | null
          created_at: string
          experience_years: number | null
          headline: string | null
          id: string
          languages: string[]
          review_note: string | null
          reviewed_at: string | null
          reviewed_by: string | null
          sample_url: string | null
          status: Database["public"]["Enums"]["application_status"]
          subjects: string[]
          updated_at: string
          user_id: string
        }
        SetofOptions: {
          from: "*"
          to: "teacher_applications"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      broadcast_notification: {
        Args: {
          p_audience: string
          p_body: string
          p_data?: Json
          p_kind: string
          p_title: string
        }
        Returns: Json
      }
      can_manage_test: { Args: { p_test_id: string }; Returns: boolean }
      confirm_order_payment: {
        Args: {
          p_event_id: string
          p_order_id: string
          p_payload?: Json
          p_provider: string
          p_provider_ref: string
        }
        Returns: Json
      }
      create_order: {
        Args: {
          p_country: string
          p_coupon_code: string
          p_course_id: string
          p_idempotency_key: string
          p_item_type: string
          p_live_group_id: string
          p_live_plan_id: string
          p_user: string
        }
        Returns: {
          base_price: number
          checkout_url: string | null
          commission_rate: number
          country_code: string
          coupon_id: string | null
          course_id: string | null
          created_at: string
          currency: string
          discount_amount: number
          failure_reason: string | null
          id: string
          idempotency_key: string | null
          item_type: string
          list_price: number
          live_group_id: string | null
          live_plan_id: string | null
          live_service_id: string | null
          paid_at: string | null
          platform_amount: number
          pricing_rule: Json
          provider: string | null
          provider_ref: string | null
          refunded_amount: number
          status: Database["public"]["Enums"]["order_status"]
          teacher_amount: number
          teacher_id: string | null
          total_amount: number
          updated_at: string
          user_id: string
        }
        SetofOptions: {
          from: "*"
          to: "orders"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      create_teacher: {
        Args: { p_commission?: number; p_headline?: string; p_user: string }
        Returns: Json
      }
      delete_course: { Args: { p_course_id: string }; Returns: undefined }
      delete_course_force: { Args: { p_course_id: string }; Returns: undefined }
      ensure_my_profile: {
        Args: never
        Returns: {
          avatar_url: string | null
          country_code: string | null
          created_at: string
          current_level: string | null
          email: string | null
          email_verified: boolean
          full_name: string | null
          id: string
          last_active_on: string | null
          locale: string | null
          phone: string | null
          phone_verified: boolean
          role: Database["public"]["Enums"]["app_role"]
          status: Database["public"]["Enums"]["account_status"]
          streak_days: number
          updated_at: string
        }
        SetofOptions: {
          from: "*"
          to: "profiles"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      evaluate_coupon: {
        Args: {
          p_amount: number
          p_code: string
          p_country: string
          p_currency: string
          p_item_id: string
          p_item_type: string
          p_teacher: string
          p_user: string
        }
        Returns: Json
      }
      expire_subscriptions: { Args: never; Returns: number }
      fail_order_payment: {
        Args: {
          p_event_id: string
          p_order_id: string
          p_payload?: Json
          p_provider: string
          p_reason: string
          p_status: string
        }
        Returns: Json
      }
      finish_test_attempt: { Args: { p_attempt_id: string }; Returns: Json }
      get_live_access: { Args: { p_live_service_id: string }; Returns: Json }
      has_course_access: {
        Args: { p_course: string; p_user: string }
        Returns: boolean
      }
      has_permission: { Args: { perm: string }; Returns: boolean }
      is_active_account: { Args: never; Returns: boolean }
      is_approved_teacher: { Args: { uid: string }; Returns: boolean }
      is_bookable_teacher: { Args: { uid: string }; Returns: boolean }
      is_owner: { Args: never; Returns: boolean }
      is_staff: { Args: never; Returns: boolean }
      join_waitlist: { Args: { p_live_group_id: string }; Returns: Json }
      manage_teacher: {
        Args: { p_action: string; p_note?: string; p_teacher: string }
        Returns: undefined
      }
      mark_lesson_progress: {
        Args: { p_completed: boolean; p_lesson_id: string; p_seconds: number }
        Returns: Json
      }
      my_role: { Args: never; Returns: Database["public"]["Enums"]["app_role"] }
      next_test_question: { Args: { p_attempt_id: string }; Returns: Json }
      notify_staff: {
        Args: { p_body: string; p_data?: Json; p_kind: string; p_title: string }
        Returns: undefined
      }
      offer_waitlist_seats: {
        Args: { p_live_group_id: string }
        Returns: number
      }
      owner_analytics: {
        Args: {
          p_country: string
          p_from: string
          p_teacher: string
          p_to: string
        }
        Returns: Json
      }
      plan_interval: {
        Args: { p_count: number; p_kind: string }
        Returns: string
      }
      process_refund: {
        Args: {
          p_amount: number
          p_kind: string
          p_order_id: string
          p_reason: string
        }
        Returns: Json
      }
      push_notification: {
        Args: {
          p_body: string
          p_data?: Json
          p_kind: string
          p_title: string
          p_user: string
        }
        Returns: undefined
      }
      quote_checkout: {
        Args: {
          p_country: string
          p_coupon_code: string
          p_course_id: string
          p_item_type: string
          p_live_group_id: string
          p_live_plan_id: string
          p_user: string
        }
        Returns: Json
      }
      recommendations_for_level: { Args: { p_level: string }; Returns: Json }
      release_matured_earnings: { Args: never; Returns: number }
      request_payout: {
        Args: {
          p_amount: number
          p_destination: string
          p_method: string
          p_note: string
        }
        Returns: {
          amount: number
          currency: string
          destination: string | null
          id: string
          method: string | null
          note: string | null
          paid_at: string | null
          reference: string | null
          requested_at: string
          reviewed_at: string | null
          reviewed_by: string | null
          status: Database["public"]["Enums"]["payout_status"]
          teacher_id: string
        }
        SetofOptions: {
          from: "*"
          to: "payouts"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      resolve_commission_rate: {
        Args: { p_item_id: string; p_item_type: string; p_teacher: string }
        Returns: number
      }
      resolve_price: {
        Args: { p_country: string; p_item_id: string; p_item_type: string }
        Returns: Json
      }
      review_content: {
        Args: {
          p_decision: string
          p_id: string
          p_kind: string
          p_note: string
        }
        Returns: Json
      }
      review_payout: {
        Args: {
          p_decision: string
          p_note: string
          p_payout_id: string
          p_reference: string
        }
        Returns: {
          amount: number
          currency: string
          destination: string | null
          id: string
          method: string | null
          note: string | null
          paid_at: string | null
          reference: string | null
          requested_at: string
          reviewed_at: string | null
          reviewed_by: string | null
          status: Database["public"]["Enums"]["payout_status"]
          teacher_id: string
        }
        SetofOptions: {
          from: "*"
          to: "payouts"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      review_teacher_application: {
        Args: { p_app_id: string; p_decision: string; p_note: string }
        Returns: Json
      }
      set_admin_permissions: {
        Args: { p_permissions: string[]; p_user: string }
        Returns: Json
      }
      set_course_status: {
        Args: { p_course_id: string; p_note?: string; p_status: string }
        Returns: {
          ads_enabled: boolean
          base_currency: string
          base_price: number
          category_id: string | null
          certificate_enabled: boolean
          certificate_min_percent: number
          commission_rate: number | null
          created_at: string
          description: string | null
          enrollments_count: number
          gallery: Json
          id: string
          is_featured: boolean
          is_free: boolean
          language: string | null
          level: string | null
          published_at: string | null
          rating_avg: number
          rating_count: number
          rejection_note: string | null
          reviewed_at: string | null
          reviewed_by: string | null
          status: Database["public"]["Enums"]["content_status"]
          subtitle: string | null
          teacher_id: string
          thumbnail_url: string | null
          title: string
          updated_at: string
          views_count: number
        }
        SetofOptions: {
          from: "*"
          to: "courses"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      set_teacher_flags: {
        Args: {
          p_accepting: boolean
          p_can_manage_tests: boolean
          p_commission: number
          p_live_enabled: boolean
          p_teacher: string
        }
        Returns: {
          accepting_new_students: boolean
          badge: string | null
          bio: string | null
          can_manage_tests: boolean
          commission_rate: number | null
          cover_url: string | null
          created_at: string
          experience_years: number
          headline: string | null
          id: string
          languages: string[]
          live_enabled: boolean
          photo_url: string | null
          rating_avg: number
          rating_count: number
          status: Database["public"]["Enums"]["application_status"]
          students_count: number
          subjects: string[]
          updated_at: string
        }
        SetofOptions: {
          from: "*"
          to: "teacher_profiles"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      set_user_role: {
        Args: {
          p_reason?: string
          p_role: Database["public"]["Enums"]["app_role"]
          p_user: string
        }
        Returns: Json
      }
      set_user_status: {
        Args: {
          p_reason: string
          p_status: Database["public"]["Enums"]["account_status"]
          p_user: string
        }
        Returns: Json
      }
      setting_num: {
        Args: { p_default: number; p_key: string }
        Returns: number
      }
      setting_text: {
        Args: { p_default: string; p_key: string }
        Returns: string
      }
      start_test_attempt: { Args: { p_test_id: string }; Returns: Json }
      submit_lesson_quiz: {
        Args: { p_answers: Json; p_lesson_id: string }
        Returns: Json
      }
      submit_test_answer: {
        Args: {
          p_attempt_id: string
          p_match: Json
          p_order: number[]
          p_question_id: string
          p_selected: number[]
          p_text: string
        }
        Returns: Json
      }
      teacher_analytics: {
        Args: { p_from: string; p_teacher: string; p_to: string }
        Returns: Json
      }
      teacher_balance: { Args: { p_teacher: string }; Returns: Json }
      test_attempt_result: { Args: { p_attempt_id: string }; Returns: Json }
      track_course_view: { Args: { p_course_id: string }; Returns: undefined }
      transfer_student: {
        Args: {
          p_note: string
          p_teacher_student_id: string
          p_to_teacher: string
        }
        Returns: Json
      }
      upsert_setting: { Args: { p_key: string; p_value: Json }; Returns: Json }
      write_audit: {
        Args: {
          p_action: string
          p_metadata?: Json
          p_target_id: string
          p_target_type: string
        }
        Returns: undefined
      }
    }
    Enums: {
      account_status: "ACTIVE" | "SUSPENDED" | "PENDING" | "DELETED"
      app_role: "STUDENT" | "TEACHER" | "ADMIN" | "OWNER"
      application_status: "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED"
      content_status:
        | "DRAFT"
        | "PENDING_REVIEW"
        | "APPROVED"
        | "PUBLISHED"
        | "REJECTED"
        | "SUSPENDED"
        | "ARCHIVED"
      ledger_status: "PENDING" | "AVAILABLE" | "PAID" | "REVERSED"
      lesson_kind: "VIDEO" | "DOCUMENT" | "TEXT" | "QUIZ"
      order_status:
        | "PENDING"
        | "PAID"
        | "FAILED"
        | "CANCELLED"
        | "EXPIRED"
        | "REFUNDED"
        | "PARTIALLY_REFUNDED"
        | "CHARGEBACK"
      payout_status: "PENDING" | "APPROVED" | "REJECTED" | "PAID" | "CANCELLED"
      student_record_status:
        | "ACTIVE"
        | "DUE_SOON"
        | "OVERDUE"
        | "EXPIRED"
        | "SUSPENDED"
      subscription_status:
        | "PENDING"
        | "ACTIVE"
        | "EXPIRING"
        | "EXPIRED"
        | "CANCELLED"
        | "SUSPENDED"
      test_status: "DRAFT" | "PUBLISHED" | "UNPUBLISHED" | "ARCHIVED"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {
      account_status: ["ACTIVE", "SUSPENDED", "PENDING", "DELETED"],
      app_role: ["STUDENT", "TEACHER", "ADMIN", "OWNER"],
      application_status: ["PENDING", "APPROVED", "REJECTED", "SUSPENDED"],
      content_status: [
        "DRAFT",
        "PENDING_REVIEW",
        "APPROVED",
        "PUBLISHED",
        "REJECTED",
        "SUSPENDED",
        "ARCHIVED",
      ],
      ledger_status: ["PENDING", "AVAILABLE", "PAID", "REVERSED"],
      lesson_kind: ["VIDEO", "DOCUMENT", "TEXT", "QUIZ"],
      order_status: [
        "PENDING",
        "PAID",
        "FAILED",
        "CANCELLED",
        "EXPIRED",
        "REFUNDED",
        "PARTIALLY_REFUNDED",
        "CHARGEBACK",
      ],
      payout_status: ["PENDING", "APPROVED", "REJECTED", "PAID", "CANCELLED"],
      student_record_status: [
        "ACTIVE",
        "DUE_SOON",
        "OVERDUE",
        "EXPIRED",
        "SUSPENDED",
      ],
      subscription_status: [
        "PENDING",
        "ACTIVE",
        "EXPIRING",
        "EXPIRED",
        "CANCELLED",
        "SUSPENDED",
      ],
      test_status: ["DRAFT", "PUBLISHED", "UNPUBLISHED", "ARCHIVED"],
    },
  },
} as const
