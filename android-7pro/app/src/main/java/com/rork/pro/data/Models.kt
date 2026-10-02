package com.rork.pro.data

import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.i18n.Lang
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Pick the right translation based on the current app language. Falls back to source. */
fun lang(source: String?, ar: String?, en: String?): String {
    val text = if (AppLanguage.current == Lang.AR) ar else en
    return text?.takeIf { it.isNotBlank() } ?: source.orEmpty()
}

@Serializable
data class Profile(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "STUDENT",
    val status: String = "ACTIVE",
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("current_level") val currentLevel: String? = null,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_guest") val isGuest: Boolean = false,
) {
    val isOwner: Boolean get() = role == "OWNER"
    val isStaff: Boolean get() = role == "OWNER" || role == "ADMIN"
    val isTeacher: Boolean get() = role == "TEACHER"
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "Learner"
}

@Serializable
data class AdminPermission(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val permission: String,
)

@Serializable
data class Category(
    val id: String,
    val slug: String,
    val name: String,
    val icon: String? = null,
    @SerialName("name_ar") val nameAr: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    val displayName: String get() = lang(name, nameAr, nameEn)
}

@Serializable
data class Banner(
    val id: String,
    val placement: String = "HOME_HERO",
    val title: String,
    val subtitle: String? = null,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("subtitle_ar") val subtitleAr: String? = null,
    @SerialName("subtitle_en") val subtitleEn: String? = null,
    @SerialName("cta_label") val ctaLabel: String? = null,
    @SerialName("cta_label_ar") val ctaLabelAr: String? = null,
    @SerialName("cta_label_en") val ctaLabelEn: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("cta_target") val ctaTarget: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
    val displaySubtitle: String get() = lang(subtitle, subtitleAr, subtitleEn)
    val displayCtaLabel: String get() = lang(ctaLabel, ctaLabelAr, ctaLabelEn)
}

/**
 * An owner-configured shortcut tile shown on the Home screen. [target] uses the same convention
 * as [Banner.ctaTarget] (see [com.rork.pro.ui.screens.home.resolveHomeShortcutTarget]), with a
 * few extra keys ("classroom", "tutor", "profile", "orders", "certificates", "support") on top of
 * "test" and "course:<id>". [icon] is a single emoji, matching how [Category.icon] already works.
 */
@Serializable
data class HomeShortcut(
    val id: String,
    val title: String,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    val icon: String = "⭐",
    val target: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
}

@Serializable
data class TeacherRef(
    val id: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class Course(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("category_id") val categoryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("subtitle_ar") val subtitleAr: String? = null,
    @SerialName("subtitle_en") val subtitleEn: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val level: String? = null,
    @SerialName("base_price") val basePrice: Double = 0.0,
    @SerialName("base_currency") val baseCurrency: String = "USD",
    @SerialName("is_free") val isFree: Boolean = false,
    /** Optional monthly-subscription price (same currency as [basePrice]); null = not offered. */
    @SerialName("monthly_price") val monthlyPrice: Double? = null,
    /** Sold only as a monthly subscription: no one-time purchase is offered or accepted. */
    @SerialName("monthly_only") val monthlyOnly: Boolean = false,
    val status: String = "DRAFT",
    @SerialName("rejection_note") val rejectionNote: String? = null,
    @SerialName("certificate_enabled") val certificateEnabled: Boolean = true,
    @SerialName("certificate_min_percent") val certificateMinPercent: Int = 80,
    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("enrollments_count") val enrollmentsCount: Int = 0,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val teacher: TeacherRef? = null,
) {
    /** Translated title for the current language. */
    val displayTitle: String get() = lang(title, titleAr, titleEn)
    /** Translated subtitle for the current language. */
    val displaySubtitle: String get() = lang(subtitle, subtitleAr, subtitleEn)
    /** Translated description for the current language. */
    val displayDescription: String get() = lang(description, descriptionAr, descriptionEn)

    /**
     * Whether the course actually costs anything.
     *
     * A course carries both a free flag and a price, and the two can disagree: one created in
     * the studio sits at zero long before the flag is ever switched on. Nothing without a price
     * is a paid course, so every screen asks this instead of reading the raw flag.
     */
    val isFreeCourse: Boolean get() = isFree || basePrice <= 0.0

    /** Whether a paid course is also sold as a monthly subscription. */
    val hasMonthly: Boolean get() = !isFreeCourse && (monthlyPrice ?: 0.0) > 0.0

    /** Monthly subscription is the only way to get this course. */
    val isMonthlyOnly: Boolean get() = monthlyOnly && hasMonthly
}

@Serializable
data class CourseSection(
    val id: String,
    @SerialName("course_id") val courseId: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("subtitle_ar") val subtitleAr: String? = null,
    @SerialName("subtitle_en") val subtitleEn: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
    val displaySubtitle: String get() = lang(subtitle, subtitleAr, subtitleEn)
}

@Serializable
data class Lesson(
    val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("section_id") val sectionId: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    @SerialName("content_ar") val contentAr: String? = null,
    @SerialName("content_en") val contentEn: String? = null,
    val kind: String = "VIDEO",
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("document_url") val documentUrl: String? = null,
    val content: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_preview") val isPreview: Boolean = false,
    /**
     * Reading-lesson content as ordered blocks (heading, paragraph, image, list). Null for
     * lessons written before blocks existed; those keep showing [content] as plain text.
     */
    val blocks: List<LessonBlock>? = null,
    /** Whether students may download this lesson as a PDF / plain-text file. */
    @SerialName("allow_pdf") val allowPdf: Boolean = true,
    @SerialName("allow_txt") val allowTxt: Boolean = true,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
    val displayDescription: String get() = lang(description, descriptionAr, descriptionEn)
    val displayContent: String get() = lang(content, contentAr, contentEn)

    /** A reading lesson built from blocks (as opposed to a legacy plain-text one). */
    val hasBlocks: Boolean get() = !blocks.isNullOrEmpty()
}

/** One block of a reading lesson. [type] is one of [LessonBlock.TYPES]. */
@Serializable
data class LessonBlock(
    val type: String = PARAGRAPH,
    val text: String = "",
    val url: String? = null,
    val caption: String? = null,
    val items: List<String> = emptyList(),
) {
    companion object {
        const val HEADING = "HEADING"
        const val PARAGRAPH = "PARAGRAPH"
        const val IMAGE = "IMAGE"
        const val LIST = "LIST"
        val TYPES = listOf(HEADING, PARAGRAPH, IMAGE, LIST)
    }

    /** The block as plain text — used for the TXT download and the lesson's search/translation text. */
    fun plainText(): String = when (type) {
        IMAGE -> caption.orEmpty()
        LIST -> items.filter { it.isNotBlank() }.joinToString("\n") { "• $it" }
        else -> text
    }.trim()
}

/** A whole lesson as plain text: the title, then every block in order. */
fun List<LessonBlock>.toPlainText(title: String): String = buildString {
    append(title.trim())
    this@toPlainText.map { it.plainText() }.filter { it.isNotBlank() }.forEach {
        append("\n\n")
        append(it)
    }
}

@Serializable
data class LessonQuiz(
    val id: String,
    @SerialName("lesson_id") val lessonId: String,
    val question: String,
    val options: List<String> = emptyList(),
    val points: Int = 1,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class LessonProgress(
    val id: String? = null,
    @SerialName("lesson_id") val lessonId: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("seconds_watched") val secondsWatched: Int = 0,
    @SerialName("is_completed") val isCompleted: Boolean = false,
)

@Serializable
data class Enrollment(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("course_id") val courseId: String,
    val status: String = "ACTIVE",
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
    @SerialName("last_lesson_id") val lastLessonId: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    /** FREE, ONE_TIME or MONTHLY — how this access was obtained. */
    @SerialName("access_type") val accessType: String = "ONE_TIME",
    /** End of a monthly subscription (ISO timestamp); null for free / one-time access. */
    @SerialName("expires_at") val expiresAt: String? = null,
    val course: Course? = null,
) {
    val isMonthly: Boolean get() = accessType == "MONTHLY"

    /** Opened by hand by the owner or a teacher rather than bought. */
    val isGranted: Boolean get() = accessType == "GRANTED"

    /** True once a monthly subscription's paid month has passed (the server closes it shortly after). */
    fun isExpired(now: java.time.Instant = java.time.Instant.now()): Boolean {
        val end = expiresAt ?: return false
        return runCatching { !java.time.OffsetDateTime.parse(end).toInstant().isAfter(now) }.getOrDefault(false)
    }

    /** Grants access right now: ACTIVE on the server and not past its end date. */
    val hasAccess: Boolean get() = status == "ACTIVE" && !isExpired()

    /** Locked because the course became paid, or the monthly subscription ended. */
    val needsPayment: Boolean get() = status == "PAYMENT_REQUIRED" || status == "EXPIRED" || (status == "ACTIVE" && isExpired())

    /** The calendar day a monthly subscription ends, e.g. "2026-10-29". */
    val expiresDay: String? get() = expiresAt?.take(10)

    /**
     * True from the day a monthly subscription ends (Cairo date, same as the server) — never
     * before. While the paid month is still running the renew button stays hidden, and the
     * server refuses an early renewal anyway.
     */
    fun isRenewalDue(now: java.time.Instant = java.time.Instant.now()): Boolean {
        if (!isMonthly) return false
        val end = expiresAt ?: return false
        return runCatching {
            val cairo = java.time.ZoneId.of("Africa/Cairo")
            val endDay = java.time.OffsetDateTime.parse(end).toInstant().atZone(cairo).toLocalDate()
            !now.atZone(cairo).toLocalDate().isBefore(endDay)
        }.getOrDefault(false)
    }
}

@Serializable
data class TeacherProfile(
    val id: String,
    val headline: String? = null,
    val bio: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val subjects: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    @SerialName("experience_years") val experienceYears: Int = 0,
    val status: String = "APPROVED",
    @SerialName("accepting_new_students") val acceptingNewStudents: Boolean = false,
    @SerialName("can_manage_tests") val canManageTests: Boolean = false,
    /** Owner-granted permission: can this teacher file their exercises under one of their own courses. */
    @SerialName("can_manage_course_exercises") val canManageCourseExercises: Boolean = false,
    @SerialName("commission_rate") val commissionRate: Double? = null,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("students_count") val studentsCount: Int = 0,
    val badge: String? = null,
    val profile: TeacherRef? = null,
)

@Serializable
data class Order(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("item_type") val itemType: String,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("country_code") val countryCode: String,
    val currency: String,
    @SerialName("list_price") val listPrice: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("commission_rate") val commissionRate: Double = 0.0,
    @SerialName("teacher_amount") val teacherAmount: Double = 0.0,
    @SerialName("platform_amount") val platformAmount: Double = 0.0,
    @SerialName("refunded_amount") val refundedAmount: Double = 0.0,
    val status: String = "PENDING",
    val provider: String? = null,
    @SerialName("checkout_url") val checkoutUrl: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
)

@Serializable
data class LedgerEntry(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    val kind: String,
    val amount: Double,
    val currency: String,
    val status: String,
    @SerialName("available_at") val availableAt: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    /** Set on earnings that came from a checkout order (course/live). Legacy subscription
     *  earnings have no order, which is how the server tells them apart from course money. */
    @SerialName("order_id") val orderId: String? = null,
)

@Serializable
data class Payout(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    val amount: Double,
    val currency: String,
    val method: String? = null,
    val destination: String? = null,
    val reference: String? = null,
    val note: String? = null,
    val status: String = "PENDING",
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    val teacher: TeacherRef? = null,
)

@Serializable
data class Certificate(
    val id: String,
    val serial: String,
    @SerialName("course_title") val courseTitle: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("score_percent") val scorePercent: Double? = null,
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("verify_code") val verifyCode: String,
    val revoked: Boolean = false,
)

@Serializable
data class Review(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    val rating: Int,
    val body: String? = null,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val author: TeacherRef? = null,
)

@Serializable
data class NotificationPreference(
    val kind: String,
    val enabled: Boolean = true,
)

@Serializable
data class AppNotification(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String? = null,
    /** Free-form payload the server attaches, used to open the right screen when tapped. */
    val data: JsonObject? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    private fun value(key: String): String? =
        data?.get(key)?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }

    /**
     * Where tapping this notification should land.
     *
     * Falls back to the notification list, so an unknown payload can never open a dead end.
     */
    val deepLink: String
        get() = value("route")?.removePrefix("/")?.takeIf(::isKnownRoute)
            ?: value("classroom_session_id")?.let { "classroom/lobby/$it" }
            ?: value("course_id")?.let { "course/$it" }
            ?: value("attempt_id")?.let { "test-result/$it" }
            ?: value("subscription_id")?.let { "booking/pay/$it" }
            ?: value("request_id")?.let { "admin/approvals" }
            ?: "notifications"

    private fun isKnownRoute(route: String): Boolean = route == "notifications" ||
        route == "orders" ||
        route == "certificates" ||
        route == "support" ||
        route.startsWith("support/") ||
        route == "notification-settings" ||
        route.startsWith("course/") ||
        route.startsWith("player/") ||
        route.startsWith("test-result/") ||
        route.startsWith("booking/pay/") ||
        route.startsWith("classroom/lobby/") ||
        route.startsWith("admin/") ||
        route.startsWith("teacher/")
}

@Serializable
data class SupportTicket(
    val id: String,
    @SerialName("user_id") val userId: String,
    val subject: String,
    val category: String? = null,
    val status: String = "OPEN",
    val priority: String = "NORMAL",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TicketMessage(
    val id: Long,
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PlacementTest(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    /** `PLACEMENT` for academy level tests, `EXERCISE` for a teacher's own exercise. */
    val kind: String = "PLACEMENT",
    val scope: String = "PUBLIC",
    @SerialName("target_level") val targetLevel: String? = null,
    @SerialName("is_adaptive") val isAdaptive: Boolean = true,
    @SerialName("question_count") val questionCount: Int = 20,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int = 1200,
    @SerialName("passing_score") val passingScore: Double = 50.0,
    /** Manual drag-and-drop position: shared across all placement tests, per-teacher for exercises. */
    @SerialName("sort_order") val sortOrder: Int = 0,
    val status: String = "DRAFT",
    @SerialName("owner_id") val ownerId: String? = null,
    /** The level this exercise is filed under, or null when it hasn't been filed yet. */
    @SerialName("section_id") val sectionId: String? = null,
    /** Teacher-controlled lock. A locked exercise is visible to students but can't be started. */
    @SerialName("is_locked") val isLocked: Boolean = false,
    /**
     * The course this exercise belongs to, if the teacher/owner/admin attached one. Additional to
     * (not a replacement for) [sectionId]: when set, the exercise also appears inside that
     * course's own detail page, and only students enrolled in it may read or start it.
     */
    @SerialName("course_id") val courseId: String? = null,
    /** Author of a teacher exercise, joined for the student and admin lists. */
    val owner: TeacherRef? = null,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
    val displayDescription: String get() = lang(description, descriptionAr, descriptionEn)
}

@Serializable
data class TestQuestionView(
    val id: String,
    val kind: String,
    val skill: String,
    val difficulty: Int,
    val prompt: String,
    val options: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val points: Double = 1.0,
)

@Serializable
data class NextQuestion(
    val done: Boolean = false,
    val reason: String? = null,
    val question: TestQuestionView? = null,
    val index: Int = 0,
    val total: Int = 0,
    /** Server-authoritative countdown, so the on-screen timer can never drift from the real limit. */
    @SerialName("seconds_left") val secondsLeft: Int? = null,
)

@Serializable
data class StartAttempt(
    @SerialName("attempt_id") val attemptId: String,
    val resumed: Boolean = false,
    @SerialName("question_count") val questionCount: Int = 0,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int = 0,
    @SerialName("seconds_left") val secondsLeft: Int? = null,
    val answered: Int = 0,
    val title: String? = null,
)

@Serializable
data class AnswerResult(
    val correct: Boolean = false,
    val earned: Double = 0.0,
    val explanation: String? = null,
    /** False when the question carries no model answer, so it is recorded but not scored. */
    val graded: Boolean = true,
)

@Serializable
data class TestResult(
    @SerialName("attempt_id") val attemptId: String,
    val percent: Double = 0.0,
    val level: String? = null,
    val skills: Map<String, Double> = emptyMap(),
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    @SerialName("better_than_percent") val betterThanPercent: Double? = null,
    val passed: Boolean = false,
    val status: String = "SUBMITTED",
    val answered: Int = 0,
)

/** One stored question of a test or exercise bank, as the editor lists it. */
@Serializable
data class TestQuestionRow(
    val id: String,
    @SerialName("test_id") val testId: String,
    val kind: String = "SINGLE",
    val skill: String = "GRAMMAR",
    val prompt: String,
    val options: List<String> = emptyList(),
    @SerialName("correct_indexes") val correctIndexes: List<Int> = emptyList(),
    @SerialName("correct_text") val correctText: List<String>? = null,
    @SerialName("media_image_url") val imageUrl: String? = null,
    @SerialName("media_audio_url") val audioUrl: String? = null,
    @SerialName("media_video_url") val videoUrl: String? = null,
    val explanation: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    /** The teacher's per-question lock. An inactive question is skipped when a student is
     *  served questions, but stays in the bank — the same "hide without deleting" idea as the
     *  exercise-level lock, just at question granularity. */
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class RecentPlacementResult(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    val level: String? = null,
    val percent: Double = 0.0,
    @SerialName("submitted_at") val submittedAt: String? = null,
)

/** One person currently using the app — a row of `admin_online_users()` (last 90 seconds). */
@Serializable
data class OnlineUser(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    val role: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class TestAttemptRow(
    val id: String,
    @SerialName("test_id") val testId: String,
    /** Who sat the attempt. Needed to show a teacher which student an attempt belongs to. */
    @SerialName("user_id") val userId: String? = null,
    val status: String,
    val percent: Double = 0.0,
    val level: String? = null,
    @SerialName("skill_breakdown") val skillBreakdown: Map<String, Double> = emptyMap(),
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    @SerialName("submitted_at") val submittedAt: String? = null,
)

/** One stored answer — what the learner picked or typed, and whether it counted. */
@Serializable
data class TestAnswerRow(
    val id: String,
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("selected_indexes") val selectedIndexes: List<Int> = emptyList(),
    @SerialName("text_answer") val textAnswer: String? = null,
    @SerialName("is_correct") val isCorrect: Boolean = false,
    val earned: Double = 0.0,
    @SerialName("answered_at") val answeredAt: String? = null,
)

/** A stored answer paired with the question it belongs to, ready to render in a review list. */
data class AnswerReview(
    val answer: TestAnswerRow,
    val question: TestQuestionRow,
) {
    /** What the learner chose, in words rather than indexes. */
    val givenText: String
        get() = when {
            !answer.textAnswer.isNullOrBlank() -> answer.textAnswer
            answer.selectedIndexes.isNotEmpty() ->
                answer.selectedIndexes.mapNotNull { question.options.getOrNull(it) }.joinToString(" / ")
            else -> ""
        }

    /** The answer that would have counted. Empty when the question type has no single one. */
    val expectedText: String
        get() = when {
            !question.correctText.isNullOrEmpty() -> question.correctText.joinToString(" / ")
            question.correctIndexes.isNotEmpty() ->
                question.correctIndexes.mapNotNull { question.options.getOrNull(it) }.joinToString(" / ")
            else -> ""
        }
}

/** A student's attempt on a teacher's exercise, with the student attached. */
/** A named level a teacher files their exercises under — the exercise twin of a course section. */
@Serializable
data class ExerciseSection(
    val id: String,
    @SerialName("owner_id") val ownerId: String? = null,
    val title: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** One of the owner's published exercises, offered to teachers to copy into their own account. */
@Serializable
data class OwnerExerciseOffer(
    val id: String,
    val title: String,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    val description: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    @SerialName("questions_total") val questionsTotal: Int = 0,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int = 0,
    @SerialName("already_copied") val alreadyCopied: Boolean = false,
    /** The owner's level this exercise is filed under; null when it is not filed in any level. */
    @SerialName("section_id") val sectionId: String? = null,
    @SerialName("section_title") val sectionTitle: String? = null,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
    val displayDescription: String get() = lang(description, descriptionAr, descriptionEn)
}

/** A level with the exercises filed under it. A null [section] holds the unfiled ones. */
data class ExerciseGroup(val section: ExerciseSection?, val exercises: List<PlacementTest>)

/**
 * Groups exercises under their levels, in the teacher's level order, with unfiled exercises
 * last. An empty level is kept so the teacher can still see and fill it; the student-facing
 * caller drops those itself.
 */
fun groupExercises(sections: List<ExerciseSection>, exercises: List<PlacementTest>): List<ExerciseGroup> {
    val grouped = sections.sortedBy { it.sortOrder }.map { section ->
        ExerciseGroup(section, exercises.filter { it.sectionId == section.id }.sortedBy { it.sortOrder })
    }
    val loose = exercises.filter { it.sectionId == null }.sortedBy { it.sortOrder }
    return if (loose.isEmpty()) grouped else grouped + ExerciseGroup(null, loose)
}

data class StudentAttempt(
    val attempt: TestAttemptRow,
    val student: Profile?,
)

@Serializable
data class PriceQuote(
    @SerialName("item_type") val itemType: String,
    /** "MONTH" for a monthly course subscription, otherwise absent. */
    @SerialName("billing_period") val billingPeriod: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("country_code") val countryCode: String = "",
    val currency: String = "",
    @SerialName("base_price") val basePrice: Double = 0.0,
    @SerialName("list_price") val listPrice: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    val coupon: CouponResult? = null,
    // Only present on AI Tutor plan quotes.
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("plan_name_ar") val planNameAr: String? = null,
    @SerialName("period_days") val periodDays: Int? = null,
    @SerialName("replies") val replies: Int? = null,
)

/**
 * A paid AI Tutor plan, priced entirely by the owner: [price] is the normal price, [salePrice] the
 * price after a discount (shown next to the crossed-out [price]) until [saleEndsAt], if set.
 * Buying one gives [replies] tutor replies, valid for [periodDays] days from the purchase (unused ones expire).
 */
@Serializable
data class AiTutorPlan(
    val id: String,
    val name: String,
    @SerialName("name_ar") val nameAr: String = "",
    val description: String = "",
    @SerialName("description_ar") val descriptionAr: String = "",
    val price: Double = 0.0,
    @SerialName("sale_price") val salePrice: Double? = null,
    @SerialName("sale_ends_at") val saleEndsAt: String? = null,
    val currency: String = "EGP",
    @SerialName("period_days") val periodDays: Int = 30,
    val replies: Int = 100,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    /** True while the discounted price applies (a sale price is set and its end date, if any, has not passed). */
    fun saleActive(now: java.time.Instant = java.time.Instant.now()): Boolean {
        if (salePrice == null || salePrice >= price) return false
        val end = saleEndsAt ?: return true
        return runCatching { java.time.OffsetDateTime.parse(end).toInstant().isAfter(now) }.getOrDefault(true)
    }

    /** What a buyer pays before any coupon. */
    fun effectivePrice(now: java.time.Instant = java.time.Instant.now()): Double = if (saleActive(now)) salePrice!! else price

    /** Whole-number percentage saved by the sale, for the badge. */
    fun salePercent(now: java.time.Instant = java.time.Instant.now()): Int =
        if (saleActive(now) && price > 0) (((price - salePrice!!) / price) * 100).toInt().coerceIn(1, 99) else 0
}

@Serializable
data class CouponResult(
    val valid: Boolean = false,
    val discount: Double = 0.0,
    val code: String? = null,
    val reason: String? = null,
)

/**
 * One payment option, exactly as it exists on the platform's own Paymob account.
 *
 * [kind] is the family the app draws an icon and a translated name for; [label] is whatever the
 * integration is called in the Paymob dashboard and is shown as-is for anything unrecognised.
 */
@Serializable
data class PaymentMethod(
    val id: String,
    val kind: String = "OTHER",
    val label: String = "",
    /**
     * For a WALLET option, the operator the gateway account bills through this integration —
     * VODAFONE / ORANGE / ETISALAT / WE — as named by the merchant's own Paymob dashboard.
     *
     * Null is the common case and means one unified wallet rail that serves every operator; the
     * app then shows a single wallet option and colours it from the number as it is typed.
     */
    @SerialName("wallet_brand") val walletBrand: String? = null,
)

@Serializable
data class PaymentMethods(
    val ok: Boolean = false,
    val methods: List<PaymentMethod> = emptyList(),
    /**
     * Whether the owner currently has the gateway switched on. False means the wallets on the
     * sheet are paid by manual transfer instead of being charged.
     */
    @SerialName("paymob_enabled") val paymobEnabled: Boolean = true,
)

/**
 * What the server did with a checkout request.
 *
 * A mobile-wallet payment answers with [walletPending] instead of a page: the request is already
 * on its way to the payer's phone and only the signed webhook can mark it paid.
 */
@Serializable
data class CheckoutSession(
    val ok: Boolean = false,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("checkout_url") val checkoutUrl: String? = null,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    val currency: String? = null,
    val free: Boolean = false,
    @SerialName("wallet_pending") val walletPending: Boolean = false,
    @SerialName("wallet_phone") val walletPhone: String? = null,
    /** The transfer proof was filed and is now waiting for an owner or admin to approve it. */
    @SerialName("manual_pending") val manualPending: Boolean = false,
    @SerialName("request_id") val requestId: String? = null,
    val error: String? = null,
)

@Serializable
data class TeacherBalance(
    val pending: Double = 0.0,
    val available: Double = 0.0,
    val paid: Double = 0.0,
    val gross: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("minimum_payout") val minimumPayout: Double = 1000.0,
)

@Serializable
data class CountryPricing(
    val id: String? = null,
    @SerialName("country_code") val countryCode: String,
    val currency: String,
    @SerialName("fx_multiplier") val fxMultiplier: Double = 1.0,
    @SerialName("round_to") val roundTo: Double = 1.0,
    @SerialName("discount_percent") val discountPercent: Double = 0.0,
    @SerialName("region_group") val regionGroup: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class Coupon(
    val id: String? = null,
    val code: String,
    val kind: String = "PERCENT",
    val amount: Double = 0.0,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("used_count") val usedCount: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    /** NONE = courses only, ALSO = courses and AI Tutor plans, ONLY = AI Tutor plans only. */
    @SerialName("ai_tutor_scope") val aiTutorScope: String = "NONE",
    /** Courses this coupon works on; empty = every course. Enforced server-side by evaluate_coupon. */
    @SerialName("course_ids") val courseIds: List<String> = emptyList(),
)

@Serializable
data class AdmobPlacement(
    val id: String? = null,
    val screen: String,
    val section: String? = null,
    val format: String = "BANNER",
    @SerialName("ad_unit_id") val adUnitId: String,
    val frequency: Int = 1,
    val spacing: Int = 4,
    @SerialName("max_impressions_per_session") val maxImpressions: Int = 10,
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("free_content_only") val freeContentOnly: Boolean = true,
    @SerialName("display_interval_seconds") val displayIntervalSeconds: Int = 90,
)

@Serializable
data class AppSetting(
    val key: String,
    val value: JsonElement,
    val description: String? = null,
)

@Serializable
data class AuditLog(
    val id: Long,
    val action: String,
    @SerialName("target_type") val targetType: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("actor_role") val actorRole: String? = null,
    val metadata: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val actor: TeacherRef? = null,
)

@Serializable
data class RefundRow(
    val id: String,
    @SerialName("order_id") val orderId: String,
    val kind: String,
    val amount: Double,
    val currency: String,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Recommendations(
    val courses: List<Course> = emptyList(),
)

// ── Teacher Groups ───────────────────────────────────────────────────────

@Serializable
data class TeacherGroup(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    val name: String,
    val level: String? = null,
    @SerialName("approval_status") val approvalStatus: String = "APPROVED",
    // Booking settings, owned by the owner/admin and written only through set_group_booking.
    @SerialName("monthly_price") val monthlyPrice: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("is_open") val isOpen: Boolean = true,
    val capacity: Int = 0,
    val schedule: String? = null,
    // Settable by the owning teacher OR owner/admin staff, only through set_group_photo.
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isInactive: Boolean get() = approvalStatus == "INACTIVE"
    val isPending: Boolean get() = approvalStatus == "PENDING"
    val isApproved: Boolean get() = approvalStatus == "APPROVED"
    val isRejected: Boolean get() = approvalStatus == "REJECTED"
}

// ── Subscriptions ─────────────────────────────────────────────────────────

@Serializable
data class Subscription(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("parent_name") val parentName: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("monthly_amount") val monthlyAmount: Double = 0.0,
    /** What the billing period actually is — WEEKLY or MONTHLY today, QUARTERLY/YEARLY
     *  reserved. monthlyAmount is the amount charged per THIS cycle, not necessarily a
     *  calendar month, so anything that turns it into a monthly figure (active-earnings
     *  totals, group breakdowns) must normalize by this field — see
     *  Subscription.monthlyEquivalent and the subscription_active_earnings RPC it mirrors. */
    @SerialName("billing_cycle") val billingCycle: String = "MONTHLY",
    val currency: String = "EGP",
    @SerialName("next_renewal_date") val nextRenewalDate: String,
    val status: String = "ACTIVE",
    @SerialName("approval_status") val approvalStatus: String = "APPROVED",
    val level: String? = null,
    @SerialName("parent_phone") val parentPhone: String? = null,
    val notes: String? = null,
    /** The learner's actual registered account, looked up by email/name at add-time —
     *  replaces free-typed level entry in the add-student form. Null for older rows
     *  created before accounts were linked. */
    @SerialName("student_user_id") val studentUserId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Filled in after loading (see [PaymentSources.attach]); never stored in the table. */
    @kotlinx.serialization.Transient val paymentSource: String? = null,
) {
    val isPending: Boolean get() = approvalStatus == "PENDING"
    val isApproved: Boolean get() = approvalStatus == "APPROVED"
    val isRejected: Boolean get() = approvalStatus == "REJECTED"

    /** monthlyAmount converted to what it's actually worth per calendar month — mirrors the
     *  CASE in the subscription_active_earnings RPC exactly, so client-side aggregates
     *  (group breakdowns) can never drift from what that RPC reports as the total. */
    val monthlyEquivalent: Double get() = when (billingCycle) {
        "WEEKLY" -> monthlyAmount * 52.0 / 12.0
        "QUARTERLY" -> monthlyAmount / 3.0
        "YEARLY" -> monthlyAmount / 12.0
        else -> monthlyAmount
    }
}

@Serializable
data class SubscriptionRenewal(
    val id: String,
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("renewed_by") val renewedBy: String? = null,
    @SerialName("renewed_at") val renewedAt: String? = null,
    @SerialName("previous_renewal_date") val previousRenewalDate: String,
    @SerialName("new_renewal_date") val newRenewalDate: String,
    val amount: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SubscriptionStats(
    @SerialName("total_subscriptions") val totalSubscriptions: Int = 0,
    @SerialName("due_this_week") val dueThisWeek: Int = 0,
    val overdue: Int = 0,
    @SerialName("total_monthly_value") val totalMonthlyValue: Double = 0.0,
)

// ── Subscription Earnings ─────────────────────────────────────────────────

@Serializable
data class TeacherSubscriptionRate(
    @SerialName("teacher_id") val teacherId: String,
    val percentage: Double = 0.0,
    @SerialName("set_by") val setBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * What fraction of this earning's own billing cycle ([periodStart]..[periodEnd], typically a
 * ~month-long span) falls inside [windowStart]..[windowEnd].
 *
 * A monthly subscription earns continuously across the whole month it covers, not all at once
 * on the day it renewed. Reporting "this week" by checking whether the renewal date happens to
 * fall inside the current week — the previous approach — either dumped the entire month's
 * amount onto whichever single week that was, or showed zero every other week even though the
 * subscription was actively running. Weighting by day-overlap instead means a month priced at
 * 1000 and queried for a 7-day week reports about 1000/30*7 ≈ 233, and a late-arriving report
 * for a partial month reports its true partial share too — this is what every caller below
 * multiplies [monthlyAmount]/[teacherEarning]/[ownerEarning] by.
 */
internal fun SubscriptionEarning.overlapFraction(windowStart: String, windowEnd: String): Double {
    val start = runCatching { java.time.LocalDate.parse(periodStart) }.getOrNull() ?: return 0.0
    val end = runCatching { java.time.LocalDate.parse(periodEnd) }.getOrNull() ?: return 0.0
    val winStart = runCatching { java.time.LocalDate.parse(windowStart) }.getOrNull() ?: return 0.0
    val winEnd = runCatching { java.time.LocalDate.parse(windowEnd) }.getOrNull() ?: return 0.0
    if (end.isBefore(start) || winEnd.isBefore(winStart)) return 0.0

    val overlapStart = if (start.isAfter(winStart)) start else winStart
    val overlapEnd = if (end.isBefore(winEnd)) end else winEnd
    if (overlapEnd.isBefore(overlapStart)) return 0.0

    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
    if (totalDays <= 0) return 0.0
    val overlapDays = java.time.temporal.ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1
    return overlapDays.toDouble() / totalDays.toDouble()
}

/** [monthlyAmount], [teacherEarning] and [ownerEarning], each scaled to only the slice that
 *  actually falls inside [windowStart]..[windowEnd] — see [overlapFraction]. */
internal fun SubscriptionEarning.prorated(windowStart: String, windowEnd: String): ProratedSubscriptionEarning {
    val fraction = overlapFraction(windowStart, windowEnd)
    return ProratedSubscriptionEarning(
        total = monthlyAmount * fraction,
        teacherShare = teacherEarning * fraction,
        ownerShare = ownerEarning * fraction,
    )
}

internal data class ProratedSubscriptionEarning(val total: Double, val teacherShare: Double, val ownerShare: Double)

@Serializable
data class SubscriptionEarning(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("renewal_id") val renewalId: String? = null,
    @SerialName("group_name") val groupName: String,
    @SerialName("monthly_amount") val monthlyAmount: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("teacher_percentage") val teacherPercentage: Double = 0.0,
    @SerialName("teacher_earning") val teacherEarning: Double = 0.0,
    @SerialName("owner_earning") val ownerEarning: Double = 0.0,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * What the subscriptions that exist bill per month, and how that splits.
 *
 * Deliberately not derived from the `subscription_earnings` ledger: that ledger answers "how
 * much accrued during a window", which is a different question from "what are we earning from
 * the subscriptions we have". A live 400/month subscription reads as 400 here whether it
 * started today or six months ago. See the subscription_active_earnings RPC.
 */
@Serializable
data class ActiveSubscriptionEarnings(
    val total: Double = 0.0,
    @SerialName("teacher_share") val teacherShare: Double = 0.0,
    @SerialName("owner_share") val ownerShare: Double = 0.0,
    @SerialName("subscription_count") val subscriptionCount: Int = 0,
)

/**
 * One group's contribution to [ActiveSubscriptionEarnings] — computed client-side by summing
 * the `monthly_amount` of subscriptions that currently exist (not paused, approved), grouped by
 * `group_name`, with the teacher/owner split applied per subscription using that subscription's
 * own teacher's rate (not one ratio spread across the whole group) — see
 * AdminRepository.activeSubscriptionGroups / TeacherRepository.myActiveSubscriptionGroups.
 */
data class ActiveSubscriptionGroup(
    val groupName: String,
    val monthlyTotal: Double = 0.0,
    val teacherShare: Double = 0.0,
    val ownerShare: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class SubscriptionEarningsGroup(
    @SerialName("group_name") val groupName: String,
    val total: Double = 0.0,
    @SerialName("teacher_share") val teacherShare: Double = 0.0,
    @SerialName("owner_share") val ownerShare: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class SubscriptionEarningsSummary(
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("total_earned") val totalEarned: Double = 0.0,
    @SerialName("teacher_share") val teacherShare: Double = 0.0,
    @SerialName("owner_share") val ownerShare: Double = 0.0,
    @SerialName("subscription_count") val subscriptionCount: Int = 0,
    val groups: List<SubscriptionEarningsGroup> = emptyList(),
)

/**
 * Earnings from one-time course purchases — deliberately its own type, never merged into
 * [SubscriptionEarningsSummary], so a course sale and a subscription renewal are never added
 * together into one ambiguous number on screen. Unlike a subscription's earning (a monthly
 * charge that has to be prorated across the days it covers — see
 * [SubscriptionEarning.overlapFraction]), a course purchase is a single point-in-time event: its
 * full amount belongs entirely to the day it was paid, so no proration applies here.
 */
data class CourseEarningsSummary(
    val periodStart: String,
    val periodEnd: String,
    val totalEarned: Double = 0.0,
    val teacherShare: Double = 0.0,
    val ownerShare: Double = 0.0,
    val orderCount: Int = 0,
)

/** Decode-only shape for aggregating course-sale rows out of `orders` — see
 *  TeacherRepository.myCourseEarningsSummary / AdminRepository.allCourseEarningsSummary. */
@Serializable
internal data class CourseOrderRow(
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("teacher_amount") val teacherAmount: Double = 0.0,
    @SerialName("platform_amount") val platformAmount: Double = 0.0,
    @SerialName("refunded_amount") val refundedAmount: Double = 0.0,
)

/**
 * A course order's contribution to earnings, net of anything refunded on it.
 *
 * PARTIALLY_REFUNDED orders are deliberately included in every earnings query (a half-refunded
 * sale still earned half), but their `total_amount` is the *original* charge — summing it
 * reports money that was handed back as if it were still income, and inflates the teacher and
 * platform shares with it. The refund is taken off the total and the two shares are scaled by
 * the same surviving fraction, because `orders` records one refunded amount rather than a
 * per-party split.
 */
internal fun CourseOrderRow.net(): Triple<Double, Double, Double> {
    val refunded = refundedAmount.coerceIn(0.0, totalAmount)
    val netTotal = totalAmount - refunded
    val keptFraction = if (totalAmount > 0.0) netTotal / totalAmount else 0.0
    return Triple(netTotal, teacherAmount * keptFraction, platformAmount * keptFraction)
}

/**
 * The single definition of the window every earnings headline on the owner console and in the
 * teacher studio is measured over, so two screens can never disagree about what "this month"
 * means. A rolling 30 days ending today — and the weekly figure shown beside it is always this
 * same number divided by four (see EarningsMatrixCard), never a separately-queried calendar
 * week, which would not add up to the monthly figure next to it.
 *
 * Note this is intentionally different from the calendar-period filters ("week" = since Monday,
 * "month" = since the 1st) used by the subscriptions dashboard and the earnings report screen,
 * whose labels say "this week"/"this month" and mean exactly that.
 */
object EarningsWindow {
    const val DAYS: Long = 30

    fun from(): String = java.time.LocalDate.now().minusDays(DAYS).toString()
    fun to(): String = java.time.LocalDate.now().toString()
}

@Serializable
data class SubscriptionEarningsReport(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    val year: Int = 0,
    val month: Int = 0,
    @SerialName("total_earned") val totalEarned: Double = 0.0,
    @SerialName("owner_share") val ownerShare: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("subscription_count") val subscriptionCount: Int = 0,
    @SerialName("generated_at") val generatedAt: String? = null,
    val data: List<SubscriptionEarningsGroup> = emptyList(),
)

// ── Approval System ──────────────────────────────────────────────────────

@Serializable
data class ApprovalRequest(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("action_type") val actionType: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("request_data") val requestData: JsonObject? = null,
    val status: String = "PENDING",
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("teacher_avatar") val teacherAvatar: String? = null,
    /** How the subscription was / will be paid — see [PaymentSource]. Null for other request kinds. */
    @SerialName("payment_source") val paymentSource: String? = null,
    @SerialName("payment_brand") val paymentBrand: String? = null,
    @SerialName("payment_amount") val paymentAmount: Double? = null,
    @SerialName("payment_currency") val paymentCurrency: String? = null,
    /** The transfer screenshot the student attached (owner/admin only) and when it was sent / decided. */
    @SerialName("payment_proof_path") val paymentProofPath: String? = null,
    @SerialName("payment_sender_phone") val paymentSenderPhone: String? = null,
    @SerialName("payment_submitted_at") val paymentSubmittedAt: String? = null,
    @SerialName("payment_reviewed_at") val paymentReviewedAt: String? = null,
)

@Serializable
data class PendingRequestCount(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("teacher_avatar") val teacherAvatar: String? = null,
    @SerialName("pending_count") val pendingCount: Long = 0,
)


/**
 * A wallet number the platform receives manual transfers on, published by the owner.
 *
 * The app never hardcodes a number: a brand whose row is disabled, or whose number the owner has
 * not filled in yet, simply cannot be paid to.
 */
@Serializable
data class ManualPaymentAccount(
    val id: String? = null,
    val brand: String,
    val phone: String = "",
    @SerialName("holder_name") val holderName: String? = null,
    val instructions: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    /** True only when this wallet can actually be transferred to right now. */
    val isUsable: Boolean get() = isEnabled && phone.isNotBlank()
}

/**
 * A transfer a learner says they made, waiting for the owner or an admin to confirm it.
 *
 * Nothing here grants access on its own — approval runs through the same server path a verified
 * gateway webhook does.
 */
@Serializable
data class ManualPaymentRequest(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("course_id") val courseId: String? = null,
    val brand: String,
    @SerialName("account_phone") val accountPhone: String? = null,
    @SerialName("sender_phone") val senderPhone: String,
    @SerialName("proof_path") val proofPath: String,
    val amount: Double = 0.0,
    val currency: String = "EGP",
    val note: String? = null,
    val status: String = "PENDING",
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val student: Profile? = null,
    val course: Course? = null,
)

// ── Group booking ─────────────────────────────────────────────────────────
//
// The student-facing half of the teacher-subscription system: who can be booked, which
// of their groups is open, and what a seat costs. Every price and availability flag here
// is the owner's, read straight from the server — the app never decides either.

/** One teacher offered on the booking screen, with the cheapest open group they have. */
@Serializable
data class BookingTeacher(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val headline: String? = null,
    @SerialName("groups_count") val groupsCount: Int = 0,
    @SerialName("min_price") val minPrice: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("students_count") val studentsCount: Int = 0,
    val rating: Double = 0.0,
    /** Owner/admin decision — draws the premium frame and pill on the booking card. */
    @SerialName("is_premium") val isPremium: Boolean = false,
    /** Custom premium label. Null means the app's own default wording is used. */
    val badge: String? = null,
    /** The owner's arrangement. Already applied server-side; kept for debugging. */
    @SerialName("sort_order") val sortOrder: Int = 1000,
)

/** One open group of a chosen teacher. [seatsLeft] is null when the group is uncapped. */
@Serializable
data class BookingGroup(
    @SerialName("group_id") val groupId: String,
    @SerialName("teacher_id") val teacherId: String,
    val name: String,
    val level: String? = null,
    val schedule: String? = null,
    val price: Double = 0.0,
    val currency: String = "EGP",
    val capacity: Int = 0,
    val members: Int = 0,
    @SerialName("seats_left") val seatsLeft: Int? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
) {
    val isFull: Boolean get() = seatsLeft != null && seatsLeft <= 0
}

/** The server's "nearest group" pick: the emptiest open group at the student's own level. */
@Serializable
data class NearestBooking(
    @SerialName("group_id") val groupId: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String,
    val name: String,
    val level: String? = null,
    val price: Double = 0.0,
    val currency: String = "EGP",
)

/** What comes back from filing a booking: the seat is held, the payment is still owed. */
@Serializable
data class BookingRequest(
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("group_name") val groupName: String = "",
    val amount: Double = 0.0,
    val currency: String = "EGP",
)

/** One child created by a booking: its own seat and its own price. */
@Serializable
data class BookingItem(
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("student_name") val studentName: String = "",
    val amount: Double = 0.0,
)

/** The result of booking one or several children in a group in one go. */
@Serializable
data class BookingBatch(
    val items: List<BookingItem> = emptyList(),
    val count: Int = 0,
    val total: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("group_name") val groupName: String = "",
) {
    val subscriptionIds: List<String> get() = items.map { it.subscriptionId }
}

/** One child that is waiting on a payment right now (a new seat, or a renewal). */
@Serializable
data class PaymentItem(
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("student_name") val studentName: String = "",
    val kind: String = "NEW",
    val amount: Double = 0.0,
    val currency: String = "EGP",
)

/** What is owed across a list of the account's subscriptions, decided by the server. */
@Serializable
data class BookingPaymentState(
    val items: List<PaymentItem> = emptyList(),
    val count: Int = 0,
    val total: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("group_name") val groupName: String = "",
    @SerialName("is_renewal") val isRenewal: Boolean = false,
) {
    val needsPayment: Boolean get() = count > 0
}

/** The price and the seats left of one group, for the details step. */
@Serializable
data class BookingGroupInfo(
    @SerialName("group_id") val groupId: String,
    @SerialName("teacher_id") val teacherId: String,
    val name: String = "",
    val price: Double = 0.0,
    val currency: String = "EGP",
    val capacity: Int = 0,
    val members: Int = 0,
    @SerialName("seats_left") val seatsLeft: Int? = null,
)

/**
 * The single source of truth behind the home banner.
 *
 * There is deliberately no local "subscribed" flag anywhere in the app: this row is
 * recomputed by the server from the subscription, its open approval request and the
 * transfer attached to it, so the banner can never disagree with the backend.
 */
@Serializable
data class SubscriptionState(
    val state: String = "NONE",
    @SerialName("subscription_id") val subscriptionId: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("group_name") val groupName: String = "",
    @SerialName("student_name") val studentName: String = "",
    val amount: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("next_renewal_date") val nextRenewalDate: String? = null,
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("approval_request_id") val approvalRequestId: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    /** Every child of the same teacher + group that is in this same state — paid for together. */
    @SerialName("subscription_ids") val subscriptionIds: List<String> = emptyList(),
    /** How many children this account has booked in total. */
    @SerialName("students_count") val studentsCount: Int = 0,
) {
    /** The subscriptions this banner acts on: all of them, or the single legacy id. */
    val actionIds: List<String> get() = subscriptionIds.ifEmpty { listOfNotNull(subscriptionId) }
    val isRenewal: Boolean get() = state.startsWith("RENEWAL")

    /** True while the student still owes money on an open request. */
    val needsPayment: Boolean
        get() = state == "AWAITING_PAYMENT" || state == "RENEWAL_AWAITING_PAYMENT"
}

/** A wallet transfer for a group seat or its renewal, waiting on the owner. */
@Serializable
data class SubscriptionPaymentRequest(
    val id: String,
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("approval_request_id") val approvalRequestId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("group_name") val groupName: String,
    val kind: String = "NEW",
    val brand: String,
    @SerialName("account_phone") val accountPhone: String? = null,
    @SerialName("sender_phone") val senderPhone: String,
    @SerialName("proof_path") val proofPath: String,
    val amount: Double = 0.0,
    val currency: String = "EGP",
    val note: String? = null,
    val status: String = "PENDING",
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val student: Profile? = null,
    val teacher: Profile? = null,
) {
    val isRenewal: Boolean get() = kind == "RENEWAL"
}

/** A group as the owner sees it on the booking-and-prices screen — closed and unpriced ones included. */
@Serializable
data class AdminBookingGroup(
    @SerialName("group_id") val groupId: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String,
    val name: String,
    val level: String? = null,
    val schedule: String? = null,
    @SerialName("monthly_price") val monthlyPrice: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("is_open") val isOpen: Boolean = true,
    val capacity: Int = 0,
    val members: Int = 0,
    /** The teacher's own booking availability — one switch shared with the rest of the platform. */
    val available: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
)

/**
 * One teacher as the owner arranges them for the booking screen.
 *
 * [visible] is what a student would actually see right now (an open group + accepting new
 * students); the roster deliberately lists hidden teachers too, so an order can be prepared
 * before a teacher is opened for booking instead of only after.
 */
@Serializable
data class AdminShowcaseTeacher(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val headline: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    val badge: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 1000,
    @SerialName("groups_count") val groupsCount: Int = 0,
    val rating: Double = 0.0,
    val visible: Boolean = false,
)
