package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Owner / Admin console. Every mutation is a permission-checked server function. */
object AdminRepository {

    val ALL_PERMISSIONS: List<Pair<String, Tr>> = listOf(
        "users.manage" to StrAdmin.permUsersManage,
        "teachers.manage" to StrAdmin.permTeachersManage,
        "courses.manage" to StrAdmin.permCoursesManage,
        "course_content.manage" to com.rork.pro.ui.i18n.StrContent.permCourseContent,
        "notifications.manage" to StrAdmin.permNotificationsManage,
        "pricing.manage" to StrAdmin.permPricingManage,
        "coupons.manage" to StrAdmin.permCouponsManage,
        "courses.grant" to StrAdmin.permCoursesGrant,
        "students.manage" to StrAdmin.permStudentsManage,
        "ai_tutor.manage" to StrAdmin.permAiTutorManage,
        "finance.read" to StrAdmin.permFinanceRead,
        "finance.manage" to StrAdmin.permFinanceManage,
        "payouts.manage" to StrAdmin.permPayoutsManage,
        "reviews.manage" to StrAdmin.permReviewsManage,
        "support.manage" to StrAdmin.permSupportManage,
        "tests.manage" to StrAdmin.permTestsManage,
        "classroom.manage" to StrAdmin.permClassroomManage,
        "meeting_servers.manage" to com.rork.pro.ui.i18n.StrMeetingServers.perm,
        "cms.manage" to StrAdmin.permCmsManage,
        "ads.manage" to StrAdmin.permAdsManage,
        "settings.manage" to StrAdmin.permSettingsManage,
        "analytics.read" to StrAdmin.permAnalyticsRead,
        "audit.read" to StrAdmin.permAuditRead,
    )

    suspend fun analytics(country: String? = null, teacherId: String? = null): JsonObject =
        Backend.rpcRaw(
            "owner_analytics",
            buildJsonObject {
                put("p_from", null as String?)
                put("p_to", null as String?)
                put("p_country", country)
                put("p_teacher", teacherId)
            },
        ) as JsonObject

    // ---------- teachers ----------

    /**
     * Owner-only lifecycle actions on a teaching account.
     *
     * Both branches touch several tables at once (profile role, teaching profile, catalogue),
     * so the whole change is made server-side in one audited step.
     *
     * @param action `REACTIVATE` to lift a suspension, `DELETE` to remove the teaching account.
     */
    suspend fun manageTeacher(teacherId: String, action: String, note: String = "") {
        Backend.rpcVoid(
            "manage_teacher",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_action", action)
                val trimmedNote = note.trim()
                if (trimmedNote.isNotBlank()) put("p_note", trimmedNote)
            },
        )
    }

    /**
     * Turns a registered member into a teacher.
     *
     * Teachers cannot sign themselves up any more, so this owner/admin action is the only way a
     * teaching account is ever created. The commission the owner types here is the share the
     * platform keeps and is stored on the teaching profile, never on the client.
     */
    suspend fun createTeacher(userId: String, headline: String, commission: Double?) {
        Backend.rpcVoid(
            "create_teacher",
            buildJsonObject {
                put("p_user", userId)
                put("p_headline", headline.trim().takeIf { it.isNotBlank() })
                put("p_commission", commission)
            },
        )
    }

    suspend fun teachers(): List<TeacherProfile> =
        Backend.client.from("teacher_profiles")
            .select(Columns.raw("*, profile:profiles!teacher_profiles_id_fkey(id, full_name, avatar_url)")) {
                order("created_at", SortOrder.DESCENDING)
            }
            .decodeList()

    /** One teaching account by id (owner drill-down header). */
    suspend fun teacher(teacherId: String): TeacherProfile? =
        Backend.client.from("teacher_profiles")
            .select(Columns.raw("*, profile:profiles!teacher_profiles_id_fkey(id, full_name, avatar_url)")) {
                filter { eq("id", teacherId) }
                limit(1)
            }
            .decodeList<TeacherProfile>()
            .firstOrNull()

    suspend fun setTeacherFlags(
        teacherId: String,
        accepting: Boolean?,
        liveEnabled: Boolean?,
        canManageTests: Boolean?,
        commission: Double?,
        canManageCourseExercises: Boolean? = null,
    ) {
        Backend.rpcVoid(
            "set_teacher_flags",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_accepting", accepting)
                put("p_live_enabled", liveEnabled)
                put("p_can_manage_tests", canManageTests)
                put("p_commission", commission)
                put("p_can_manage_course_exercises", canManageCourseExercises)
            },
        )
    }

    // ---------- content review ----------
    suspend fun coursesForReview(status: String?): List<Course> =
        Backend.client.from("courses")
            .select(Columns.raw("*, teacher:profiles!courses_teacher_id_fkey(id, full_name, avatar_url)")) {
                filter { status?.let { eq("status", it) } }
                order("created_at", SortOrder.DESCENDING)
                limit(100)
            }
            .decodeList()

    suspend fun reviewContent(kind: String, id: String, decision: String, note: String) {
        Backend.rpcVoid(
            "review_content",
            buildJsonObject {
                put("p_kind", kind)
                put("p_id", id)
                put("p_decision", decision)
                put("p_note", note)
            },
        )
    }

    /**
     * Removes a course outright, whatever its state.
     *
     * Owner/Admin only — the server re-checks the role. Lessons, enrolments, progress and
     * reviews go with it, while paid orders stay in the financial records and simply stop
     * pointing at the course.
     */
    suspend fun deleteCourse(courseId: String) {
        Backend.rpcVoid("delete_course_force", buildJsonObject { put("p_course_id", courseId) })
    }

    suspend fun setCourseFeatured(courseId: String, featured: Boolean) {
        Backend.client.from("courses")
            .update(buildJsonObject { put("is_featured", featured) }) { filter { eq("id", courseId) } }
    }

    suspend fun setCourseCommission(courseId: String, rate: Double?) {
        Backend.client.from("courses")
            .update(buildJsonObject { put("commission_rate", rate) }) { filter { eq("id", courseId) } }
    }

    // ---------- pricing ----------
    suspend fun countryPricing(): List<CountryPricing> =
        Backend.client.from("country_pricing").select { order("country_code", SortOrder.ASCENDING) }.decodeList()

    suspend fun saveCountryPricing(row: CountryPricing) {
        val code = row.countryCode.uppercase()
        val payload = buildJsonObject {
            put("country_code", code)
            put("currency", row.currency.uppercase())
            put("fx_multiplier", row.fxMultiplier)
            put("round_to", row.roundTo)
            put("discount_percent", row.discountPercent)
            put("region_group", row.regionGroup)
            put("is_active", row.isActive)
        }
        val existing = Backend.client.from("country_pricing")
            .select { filter { eq("country_code", code) } }
            .decodeList<CountryPricing>()
        if (existing.isEmpty()) {
            Backend.client.from("country_pricing").insert(payload)
        } else {
            Backend.client.from("country_pricing").update(payload) { filter { eq("country_code", code) } }
        }
    }

    suspend fun deleteCountryPricing(code: String) {
        Backend.client.from("country_pricing").delete { filter { eq("country_code", code) } }
    }

    // ---------- coupons ----------
    suspend fun coupons(): List<Coupon> =
        Backend.client.from("coupons").select { order("created_at", SortOrder.DESCENDING) }.decodeList()

    /** Every course, for linking a coupon to one. */
    suspend fun couponCourses(): List<Course> = CourseStudioRepository.courses(CourseScope.ALL)

    suspend fun saveCoupon(
        code: String, kind: String, amount: Double, maxUses: Int?, expiresAt: String?,
        aiTutorScope: String = "NONE", courseIds: List<String> = emptyList(),
    ) {
        Backend.client.from("coupons").insert(
            buildJsonObject {
                put("code", code.uppercase())
                put("kind", kind)
                put("amount", amount)
                put("max_uses", maxUses)
                put("expires_at", expiresAt)
                // A coupon tied to courses can never apply to the AI Tutor.
                put("ai_tutor_scope", if (courseIds.isNotEmpty()) "NONE" else aiTutorScope)
                put("course_ids", buildJsonArray { courseIds.forEach { add(JsonPrimitive(it)) } })
                put("created_by", Backend.currentUserId)
            },
        )
    }

    /** Rewrites an existing coupon. Redemptions already made keep pointing at it. */
    suspend fun updateCoupon(
        id: String, code: String, kind: String, amount: Double, maxUses: Int?,
        aiTutorScope: String? = null, courseIds: List<String>? = null,
    ) {
        Backend.client.from("coupons").update(
            buildJsonObject {
                put("code", code.uppercase().trim())
                put("kind", kind)
                put("amount", amount)
                put("max_uses", maxUses)
                if (courseIds != null) {
                    put("course_ids", buildJsonArray { courseIds.forEach { add(JsonPrimitive(it)) } })
                    if (courseIds.isNotEmpty()) put("ai_tutor_scope", "NONE") else aiTutorScope?.let { put("ai_tutor_scope", it) }
                } else {
                    aiTutorScope?.let { put("ai_tutor_scope", it) }
                }
            },
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteCoupon(id: String) {
        Backend.client.from("coupons").delete { filter { eq("id", id) } }
    }

    suspend fun setCouponActive(id: String, active: Boolean) {
        Backend.client.from("coupons")
            .update(buildJsonObject { put("is_active", active) }) { filter { eq("id", id) } }
    }

    // ---------- finance ----------
    suspend fun orders(status: String? = null, limit: Long = 100): List<Order> =
        Backend.client.from("orders")
            .select {
                filter { status?.let { eq("status", it) } }
                order("created_at", SortOrder.DESCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun refund(orderId: String, amount: Double, kind: String, reason: String) {
        Backend.rpcVoid(
            "process_refund",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_amount", amount)
                put("p_kind", kind)
                put("p_reason", reason)
            },
        )
    }

    suspend fun refunds(): List<RefundRow> =
        Backend.client.from("refunds").select { order("created_at", SortOrder.DESCENDING); limit(100) }.decodeList()

    suspend fun payouts(status: String? = null, teacherId: String? = null): List<Payout> =
        Backend.client.from("payouts")
            .select(Columns.raw("*, teacher:profiles!payouts_teacher_id_fkey(id, full_name, avatar_url)")) {
                filter {
                    status?.let { eq("status", it) }
                    teacherId?.let { eq("teacher_id", it) }
                }
                order("requested_at", SortOrder.DESCENDING)
                limit(100)
            }
            .decodeList()

    suspend fun requestPayout(amount: Double, payoutMethod: String, destination: String, note: String) {
        Backend.rpcVoid(
            "request_payout",
            buildJsonObject {
                put("p_amount", amount)
                put("p_payout_method", payoutMethod)
                put("p_destination", destination)
                put("p_note", note)
            },
        )
    }

    suspend fun reviewPayout(payoutId: String, decision: String, reference: String, note: String) {
        Backend.rpcVoid(
            "review_payout",
            buildJsonObject {
                put("p_payout_id", payoutId)
                put("p_decision", decision)
                put("p_reference", reference)
                put("p_note", note)
            },
        )
    }

    // ---------- users & admins ----------
    /**
     * Every account search in the admin console goes through here — the promote-to-teacher
     * picker, the users list. Matches name OR email.
     *
     * Without a search term this is the newest 100 accounts (a page to browse, not a directory).
     * With one, it used to filter that same fixed page of 100 in memory — so an older account,
     * however exact the query, was invisible if it wasn't among the newest 100 signups. A search
     * now queries the whole table by name and by email and merges the two, so it finds an
     * account regardless of when it signed up.
     */
    suspend fun users(role: String? = null, search: String? = null): List<Profile> {
        val q = search?.trim()?.takeIf { it.isNotBlank() }
        if (q == null) {
            return Backend.client.from("profiles")
                .select {
                    filter {
                        role?.let { eq("role", it) }
                        eq("is_guest", false)
                    }
                    order("created_at", SortOrder.DESCENDING)
                    limit(100)
                }
                .decodeList()
        }
        // Merged client-side rather than a single OR-filter call: this project doesn't use the
        // postgrest OR-filter helper anywhere else, so two plain queries is the safer bet over
        // a filter shape nothing here has exercised.
        val byName = Backend.client.from("profiles")
            .select {
                filter {
                    role?.let { eq("role", it) }
                    eq("is_guest", false)
                    ilike("full_name", "%$q%")
                }
                limit(100)
            }
            .decodeList<Profile>()
        val byEmail = Backend.client.from("profiles")
            .select {
                filter {
                    role?.let { eq("role", it) }
                    eq("is_guest", false)
                    ilike("email", "%$q%")
                }
                limit(100)
            }
            .decodeList<Profile>()
        return (byName + byEmail)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun permissionsOf(userId: String): Set<String> =
        Backend.client.from("admin_permissions")
            .select { filter { eq("user_id", userId) } }
            .decodeList<AdminPermission>()
            .map { it.permission }
            .toSet()

    suspend fun setPermissions(userId: String, permissions: Set<String>) {
        Backend.rpcVoid(
            "set_admin_permissions",
            buildJsonObject {
                put("p_user", userId)
                put("p_permissions", JsonArray(permissions.map { JsonPrimitive(it) }))
            },
        )
    }

    /**
     * Appoints an admin, or takes the role away again (owner only).
     *
     * Permissions travel with the role: dropping someone out of `ADMIN` clears every permission
     * they held, so revoking access is a single action rather than a checklist.
     */
    suspend fun setUserRole(userId: String, role: String, reason: String = "") {
        Backend.rpcVoid(
            "set_user_role",
            buildJsonObject {
                put("p_user", userId)
                put("p_role", role)
                put("p_reason", reason.trim().takeIf { it.isNotBlank() })
            },
        )
    }

    suspend fun setUserStatus(userId: String, status: String, reason: String) {
        Backend.rpcVoid(
            "set_user_status",
            buildJsonObject {
                put("p_user", userId)
                put("p_status", status)
                put("p_reason", reason)
            },
        )
    }

    /**
     * Owner-only account recovery through the `admin-manage-user` edge function. An existing
     * password can never be read (Supabase stores only a hash) — it can only be replaced.
     * The function re-checks that the caller is the OWNER; the app gating is just for UX.
     */
    suspend fun setUserPassword(userId: String, newPassword: String) =
        manageAccount(buildJsonObject {
            put("action", "set_password"); put("user_id", userId); put("new_password", newPassword)
        })

    suspend fun setUserEmail(userId: String, newEmail: String) =
        manageAccount(buildJsonObject {
            put("action", "set_email"); put("user_id", userId); put("new_email", newEmail)
        })

    private suspend fun manageAccount(body: JsonObject) {
        val res = Backend.invokeFunction("admin-manage-user", body) as? JsonObject
        val err = (res?.get("error") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (err != null) throw IllegalStateException(err)
    }

    /**
     * Sends one important announcement to a whole audience.
     *
     * @return how many people it reached, so the sender sees the real result.
     */
    suspend fun broadcast(audience: String, title: String, body: String): Int {
        val result = Backend.rpcRaw(
            "broadcast_notification",
            buildJsonObject {
                put("p_audience", audience)
                put("p_kind", "ANNOUNCEMENT")
                put("p_title", title.trim())
                put("p_body", body.trim())
                put("p_data", buildJsonObject { })
            },
        )
        return runCatching {
            (result as JsonObject)["sent"]?.toString()?.trim('"')?.toIntOrNull() ?: 0
        }.getOrDefault(0)
    }

    // ---------- CMS / settings / ads ----------
    suspend fun banners(): List<Banner> =
        Backend.client.from("cms_banners").select { order("sort_order", SortOrder.ASCENDING) }.decodeList()

    suspend fun saveBanner(id: String?, title: String, subtitle: String, imageUrl: String, ctaLabel: String, ctaTarget: String, active: Boolean) {
        val payload = buildJsonObject {
            put("placement", "HOME_HERO")
            put("title", title)
            put("subtitle", subtitle.ifBlank { null })
            put("image_url", imageUrl.ifBlank { null })
            put("cta_label", ctaLabel.ifBlank { null })
            put("cta_target", ctaTarget.ifBlank { null })
            put("is_active", active)
        }
        val bannerId = if (id == null) {
            val inserted = Backend.client.from("cms_banners").insert(payload) { select() }.decodeSingle<JsonObject>()
            inserted["id"]?.toString()?.trim('"')
        } else {
            Backend.client.from("cms_banners").update(payload) { filter { eq("id", id) } }
            id
        }
        if (bannerId != null) Translator.translate("cms_banners", bannerId)
    }

    suspend fun deleteBanner(id: String) {
        Backend.client.from("cms_banners").delete { filter { eq("id", id) } }
    }

    suspend fun shortcuts(): List<HomeShortcut> =
        Backend.client.from("home_shortcuts").select { order("sort_order", SortOrder.ASCENDING) }.decodeList()

    suspend fun saveShortcut(id: String?, title: String, icon: String, target: String, active: Boolean, sortOrder: Int) {
        val payload = buildJsonObject {
            put("title", title)
            put("icon", icon.ifBlank { "⭐" })
            put("target", target)
            put("is_active", active)
            put("sort_order", sortOrder)
        }
        val shortcutId = if (id == null) {
            val inserted = Backend.client.from("home_shortcuts").insert(payload) { select() }.decodeSingle<JsonObject>()
            inserted["id"]?.toString()?.trim('"')
        } else {
            Backend.client.from("home_shortcuts").update(payload) { filter { eq("id", id) } }
            id
        }
        if (shortcutId != null) Translator.translate("home_shortcuts", shortcutId)
    }

    suspend fun deleteShortcut(id: String) {
        Backend.client.from("home_shortcuts").delete { filter { eq("id", id) } }
    }

    suspend fun categories(): List<Category> =
        Backend.client.from("categories").select { order("sort_order", SortOrder.ASCENDING) }.decodeList()

    suspend fun saveCategory(name: String, slug: String, sortOrder: Int, icon: String? = null) {
        val normalized = slug.lowercase().trim().replace(' ', '-')
        val payload = buildJsonObject {
            put("name", name)
            put("slug", normalized)
            put("sort_order", sortOrder)
            if (!icon.isNullOrBlank()) put("icon", icon)
        }
        val existing = Backend.client.from("categories")
            .select { filter { eq("slug", normalized) } }
            .decodeList<Category>()
        if (existing.isEmpty()) {
            val inserted = Backend.client.from("categories").insert(payload) { select() }.decodeSingle<JsonObject>()
            val catId = inserted["id"]?.toString()?.trim('"')
            if (catId != null) Translator.translate("categories", catId)
        } else {
            Backend.client.from("categories").update(payload) { filter { eq("slug", normalized) } }
            val catId = existing.firstOrNull()?.id
            if (catId != null) Translator.translate("categories", catId)
        }
    }

    /** Publishing switch: only active categories (and their courses) are offered to students. */
    suspend fun setCategoryActive(id: String, isActive: Boolean) {
        Backend.client.from("categories").update(
            buildJsonObject { put("is_active", isActive) },
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteCategory(id: String) {
        Backend.client.from("categories").delete { filter { eq("id", id) } }
    }

    suspend fun settings(): List<AppSetting> =
        Backend.client.from("app_settings").select { order("key", SortOrder.ASCENDING) }.decodeList()

    suspend fun saveSetting(key: String, rawValue: String) {
        val parsed = runCatching { Backend.json.parseToJsonElement(rawValue) }
            .getOrElse { JsonPrimitive(rawValue) }
        Backend.rpcVoid(
            "upsert_setting",
            buildJsonObject {
                put("p_key", key)
                put("p_value", parsed)
            },
        )
    }

    /** Owner-only switch for Live Voice Translation in meetings (checked again server-side). */
    suspend fun saveLiveTranslation(enabled: Boolean, serverUrl: String) {
        Backend.rpcVoid(
            "classroom_set_live_translation",
            buildJsonObject {
                put("p_enabled", enabled)
                put("p_server_url", serverUrl.trim())
            },
        )
    }

    /** Current dubbing settings (also used to prefill the owner's edit form). */
    suspend fun dubbingSettings(): com.rork.pro.dubbing.DubbingStatus = com.rork.pro.dubbing.DubbingRepository.settings()

    /** Owner/finance-manage only; rejected server-side otherwise (see dubbing_update_settings). */
    suspend fun saveDubbingSettings(
        isEnabled: Boolean, pricePerMinute: Double, currency: String, minBillableMinutes: Double,
        freeTrialMinutesTotal: Double, maxSourceMinutes: Int, dubServerUrl: String,
    ) {
        Backend.rpcVoid(
            "dubbing_update_settings",
            buildJsonObject {
                put("p_is_enabled", isEnabled)
                put("p_price_per_minute", pricePerMinute)
                put("p_currency", currency)
                put("p_min_billable_minutes", minBillableMinutes)
                put("p_free_trial_minutes_total", freeTrialMinutesTotal)
                put("p_max_source_minutes", maxSourceMinutes)
                put("p_dub_server_url", dubServerUrl.trim())
            },
        )
    }

    // ---------- payments ----------

    /**
     * Every wallet row, including the ones switched off — the owner has to see a disabled
     * wallet to be able to turn it back on.
     */
    suspend fun manualPaymentAccounts(): List<ManualPaymentAccount> =
        Backend.client.from("manual_payment_accounts")
            .select { order("sort_order", SortOrder.ASCENDING) }
            .decodeList()

    /**
     * Publishes the number one wallet receives transfers on.
     *
     * A blank number is allowed and means "not ready yet": the wallet stays on the payment sheet
     * for the owner but is never offered to a learner, so nobody is told to transfer into thin air.
     */
    suspend fun saveManualPaymentAccount(
        brand: String,
        phone: String,
        holderName: String,
        instructions: String,
        isEnabled: Boolean,
    ) {
        val payload = buildJsonObject {
            put("phone", phone.filter { it.isDigit() })
            put("holder_name", holderName.trim().ifBlank { null })
            put("instructions", instructions.trim().ifBlank { null })
            put("is_enabled", isEnabled)
            put("updated_by", Backend.currentUserId)
            put("updated_at", java.time.Instant.now().toString())
        }
        Backend.client.from("manual_payment_accounts")
            .update(payload) { filter { eq("brand", brand.uppercase()) } }
    }

    /** Transfer proofs waiting on a decision, newest first. */
    suspend fun manualPaymentRequests(status: String? = "PENDING"): List<ManualPaymentRequest> =
        Backend.client.from("manual_payment_requests")
            .select(
                Columns.raw(
                    "*, student:profiles!manual_payment_requests_user_id_fkey(id, full_name, email, avatar_url), " +
                        // teacher_id is not shown, but Course requires it: a partial embed that
                        // leaves it out fails to decode and takes the whole queue down with it.
                        "course:courses!manual_payment_requests_course_id_fkey(id, teacher_id, title, title_ar, title_en)",
                ),
            ) {
                filter { status?.let { eq("status", it) } }
                order("created_at", SortOrder.DESCENDING)
                limit(100)
            }
            .decodeList()

    /**
     * How many transfers are waiting, for the badge on the console.
     *
     * Deliberately not [manualPaymentRequests].size: that pulls a hundred rows with their student
     * and course embeds on every console open, to render a single number.
     */
    suspend fun manualPaymentPendingCount(): Int =
        Backend.client.from("manual_payment_requests")
            .select(Columns.list("id")) { filter { eq("status", "PENDING") } }
            .decodeList<JsonObject>()
            .size

    /**
     * Approves or refuses one transfer.
     *
     * Approval is what actually unlocks the course: the server runs the same confirmation a
     * verified gateway webhook would, so the enrollment, the teacher's share and the payment
     * record all land exactly as they do for a card payment.
     */
    suspend fun reviewManualPayment(requestId: String, approve: Boolean, note: String = "") {
        Backend.rpcVoid(
            "review_manual_payment",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_approve", approve)
                put("p_note", note.trim().ifBlank { null })
            },
        )
    }

    // ---------- group booking (prices + availability) ----------

    /**
     * Every group with its booking settings, closed and unpriced ones included — those are
     * precisely the rows the owner needs to see in order to fix them.
     */
    suspend fun bookingGroups(): List<AdminBookingGroup> =
        Backend.client.postgrest.rpc("admin_booking_groups", buildJsonObject { }).decodeList()

    /**
     * Sets what a seat in one group costs, and whether it is bookable at all.
     *
     * Only the fields passed are changed; the price is free to move at any time, and an
     * already-approved subscription keeps the price it was approved at, because the amount
     * lives on the subscription row rather than being looked up from the group later.
     */
    suspend fun saveGroupBooking(
        groupId: String,
        monthlyPrice: Double? = null,
        currency: String? = null,
        isOpen: Boolean? = null,
        capacity: Int? = null,
        schedule: String? = null,
    ) {
        Backend.rpcVoid(
            "set_group_booking",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_monthly_price", monthlyPrice)
                put("p_currency", currency?.trim()?.ifBlank { null })
                put("p_is_open", isOpen)
                put("p_capacity", capacity)
                put("p_schedule", schedule?.trim()?.ifBlank { null })
            },
        )
    }

    /** Marks a teacher available or unavailable for new bookings. */
    suspend fun setTeacherBookingAvailable(teacherId: String, available: Boolean) {
        Backend.rpcVoid(
            "set_teacher_booking_available",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_available", available)
            },
        )
    }

    /** Subscription transfers waiting on a decision, newest first. */
    suspend fun subscriptionPaymentRequests(status: String? = "PENDING"): List<SubscriptionPaymentRequest> =
        Backend.client.from("subscription_payment_requests")
            .select(
                Columns.raw(
                    "*, student:profiles!subscription_payment_requests_user_id_fkey(id, full_name, email, avatar_url), " +
                        "teacher:profiles!subscription_payment_requests_teacher_id_fkey(id, full_name, email, avatar_url)",
                ),
            ) {
                filter { status?.let { eq("status", it) } }
                order("created_at", SortOrder.DESCENDING)
                limit(100)
            }
            .decodeList()

    /** How many subscription transfers are waiting, for the console badge. */
    suspend fun subscriptionPaymentPendingCount(): Int =
        runCatching {
            Backend.client.from("subscription_payment_requests")
                .select(Columns.list("id")) { filter { eq("status", "PENDING") } }
                .decodeList<JsonObject>()
                .size
        }.getOrDefault(0)

    /**
     * Confirms or refuses one subscription transfer.
     *
     * Approval is what actually seats the student: the server runs the same approval path a
     * teacher-entered subscription goes through, so the student appears in the teacher's
     * group and the earnings are recorded by the existing system — nothing is credited here.
     */
    suspend fun reviewSubscriptionPayment(requestId: String, approve: Boolean, note: String = "") {
        Backend.rpcVoid(
            "review_subscription_payment",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_approve", approve)
                put("p_note", note.trim().ifBlank { null })
            },
        )
    }

    suspend fun adPlacements(): List<AdmobPlacement> =
        Backend.client.from("admob_placements").select { order("sort_order", SortOrder.ASCENDING) }.decodeList()

    suspend fun saveAdPlacement(id: String?, screen: String, section: String, format: String, adUnitId: String, enabled: Boolean, displayIntervalSeconds: Int = 90, maxImpressions: Int = 10, freeContentOnly: Boolean = false) {
        val payload = buildJsonObject {
            put("screen", screen)
            put("section", section.ifBlank { null })
            put("format", format)
            put("ad_unit_id", adUnitId)
            put("is_enabled", enabled)
            put("display_interval_seconds", displayIntervalSeconds)
            put("max_impressions_per_session", maxImpressions)
            put("free_content_only", freeContentOnly)
        }
        if (id == null) {
            Backend.client.from("admob_placements").insert(payload)
        } else {
            Backend.client.from("admob_placements").update(payload) { filter { eq("id", id) } }
        }
    }

    suspend fun deleteAdPlacement(id: String) {
        Backend.client.from("admob_placements").delete { filter { eq("id", id) } }
    }

    // ---------- moderation, support, audit ----------
    suspend fun reviews(hidden: Boolean? = null): List<Review> =
        Backend.client.from("reviews")
            .select(Columns.raw("*, author:profiles!reviews_user_id_fkey(id, full_name, avatar_url)")) {
                filter { hidden?.let { eq("is_hidden", it) } }
                order("created_at", SortOrder.DESCENDING)
                limit(100)
            }
            .decodeList()

    suspend fun setReviewHidden(id: String, hidden: Boolean, reason: String) {
        Backend.client.from("reviews").update(
            buildJsonObject {
                put("is_hidden", hidden)
                put("hidden_by", Backend.currentUserId)
                put("hidden_reason", reason.ifBlank { null })
            },
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteReview(id: String) {
        Backend.client.from("reviews").delete { filter { eq("id", id) } }
    }

    suspend fun tickets(status: String? = null): List<SupportTicket> =
        Backend.client.from("support_tickets")
            .select {
                filter { status?.let { eq("status", it) } }
                order("created_at", SortOrder.DESCENDING)
            }
            .decodeList()

    suspend fun setTicketStatus(id: String, status: String) {
        Backend.client.from("support_tickets").update(
            buildJsonObject {
                put("status", status)
                if (status == "ASSIGNED") put("assigned_to", Backend.currentUserId)
            },
        ) { filter { eq("id", id) } }
    }

    suspend fun auditLogs(): List<AuditLog> =
        Backend.client.from("audit_logs")
            .select(Columns.raw("*, actor:profiles!audit_logs_actor_id_fkey(id, full_name, avatar_url)")) {
                order("created_at", SortOrder.DESCENDING)
                limit(150)
            }
            .decodeList()

    // ---------- placement tests ----------
    /**
     * Completed academy placement tests from today and yesterday only. The date window and
     * staff authorization are enforced by the database function, not by the phone.
     */
    suspend fun recentPlacementResults(): List<RecentPlacementResult> =
        Backend.client.postgrest.rpc("admin_recent_placement_results", buildJsonObject { }).decodeList()

    /**
     * Everyone whose app pinged in the last 90 seconds — the list behind the console's
     * "online now" count. Authorization and the time window are enforced by the database.
     */
    suspend fun onlineUsers(): List<OnlineUser> =
        Backend.client.postgrest.rpc("admin_online_users", buildJsonObject { }).decodeList()

    /** Academy placement tests only. Teacher exercises are managed on their own screen. */
    suspend fun tests(): List<PlacementTest> =
        Backend.client.from("placement_tests")
            .select {
                filter { eq("kind", "PLACEMENT") }
                order("sort_order", SortOrder.ASCENDING)
            }
            .decodeList()

    /**
     * Persists a drag-reordered list of placement tests: only rows whose position actually
     * changed get written, so reordering two adjacent tests is a single update, not a rewrite
     * of the whole list.
     */
    suspend fun reorderTests(ordered: List<PlacementTest>) {
        ordered.forEachIndexed { index, test ->
            if (test.sortOrder != index) {
                Backend.client.from("placement_tests")
                    .update(buildJsonObject { put("sort_order", index) }) { filter { eq("id", test.id) } }
            }
        }
    }

    suspend fun saveTest(
        id: String?,
        title: String,
        description: String,
        adaptive: Boolean,
        questionCount: Int,
        timeLimit: Int,
        passingScore: Double,
    ): PlacementTest {
        val payload = buildJsonObject {
            put("title", title)
            put("description", description.ifBlank { null })
            put("is_adaptive", adaptive)
            put("question_count", questionCount)
            put("time_limit_seconds", timeLimit)
            put("passing_score", passingScore)
            if (id == null) {
                put("owner_id", Backend.currentUserId)
                put("kind", "PLACEMENT")
                put("sort_order", tests().size)
            }
        }
        return if (id == null) {
            val test = Backend.client.from("placement_tests").insert(payload) { select() }.decodeSingle<PlacementTest>()
            Translator.translate("placement_tests", test.id)
            test
        } else {
            val test = Backend.client.from("placement_tests").update(payload) {
                select()
                filter { eq("id", id) }
            }.decodeSingle<PlacementTest>()
            Translator.translate("placement_tests", id)
            test
        }
    }

    suspend fun setTestStatus(id: String, status: String) {
        Backend.client.from("placement_tests")
            .update(buildJsonObject { put("status", status) }) { filter { eq("id", id) } }
    }

    suspend fun deleteTest(id: String) {
        Backend.client.from("placement_tests").delete { filter { eq("id", id) } }
    }

    suspend fun questionCount(testId: String): Int =
        Backend.client.from("test_questions").select { filter { eq("test_id", testId) } }.decodeList<JsonObject>().size

    /**
     * Adds one question to a test's bank.
     *
     * Media is entirely optional: a question can be plain text, or carry any combination of an
     * image, an audio clip and a video, and the runner shows exactly what was attached.
     */
    suspend fun addQuestion(
        testId: String,
        kind: String,
        skill: String,
        difficulty: Int,
        prompt: String,
        options: List<String>,
        correctIndexes: List<Int>,
        correctText: List<String> = emptyList(),
        points: Double = 1.0,
        explanation: String = "",
        imageUrl: String = "",
        audioUrl: String = "",
        videoUrl: String = "",
    ) {
        Backend.client.from("test_questions").insert(
            buildJsonObject {
                put("test_id", testId)
                put("kind", kind)
                put("skill", skill)
                put("difficulty", difficulty)
                put("prompt", prompt.trim())
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                put("correct_indexes", JsonArray(correctIndexes.map { JsonPrimitive(it) }))
                put("correct_text", JsonArray(correctText.map { JsonPrimitive(it) }))
                put("points", points)
                put("explanation", explanation.trim().ifBlank { null })
                put("media_image_url", imageUrl.trim().ifBlank { null })
                put("media_audio_url", audioUrl.trim().ifBlank { null })
                put("media_video_url", videoUrl.trim().ifBlank { null })
            },
        )
        // Auto-translate question prompt, options, explanation to the other language
        val inserted = Backend.client.from("test_questions")
            .select { filter { eq("test_id", testId) }; order("created_at", SortOrder.DESCENDING); limit(1) }
            .decodeList<JsonObject>()
        val qId = inserted.firstOrNull()?.get("id")?.toString()?.trim('"')
        if (qId != null) Translator.translate("test_questions", qId)
    }

    suspend fun questions(testId: String): List<JsonObject> =
        Backend.client.from("test_questions")
            .select {
                filter { eq("test_id", testId) }
                order("difficulty", SortOrder.ASCENDING)
            }
            .decodeList()

    suspend fun deleteQuestion(id: String) {
        Backend.client.from("test_questions").delete { filter { eq("id", id) } }
    }

    suspend fun updateQuestion(
        id: String,
        kind: String,
        skill: String,
        difficulty: Int,
        prompt: String,
        options: List<String>,
        correctIndexes: List<Int>,
        correctText: List<String> = emptyList(),
        explanation: String = "",
        imageUrl: String = "",
        audioUrl: String = "",
        videoUrl: String = "",
    ) {
        Backend.client.from("test_questions").update(
            buildJsonObject {
                put("kind", kind)
                put("skill", skill)
                put("difficulty", difficulty)
                put("prompt", prompt.trim())
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                put("correct_indexes", JsonArray(correctIndexes.map { JsonPrimitive(it) }))
                put("correct_text", JsonArray(correctText.map { JsonPrimitive(it) }))
                put("explanation", explanation.trim().ifBlank { null })
                put("media_image_url", imageUrl.trim().ifBlank { null })
                put("media_audio_url", audioUrl.trim().ifBlank { null })
                put("media_video_url", videoUrl.trim().ifBlank { null })
            },
        ) { filter { eq("id", id) } }
    }

    // ── Groups (admin/owner — all teachers) ───────────────────────────────

    suspend fun allGroups(): List<TeacherGroup> =
        Backend.client.from("teacher_groups")
            .select {
                order("name", SortOrder.ASCENDING)
                limit(500)
            }
            .decodeList()

    /** One teacher's groups, for the owner/admin acting on that teacher's behalf. */
    suspend fun teacherGroups(teacherId: String): List<TeacherGroup> =
        Backend.client.from("teacher_groups")
            .select {
                filter { eq("teacher_id", teacherId) }
                order("name", SortOrder.ASCENDING)
            }
            .decodeList()

    /** Owner/admin creating a group for a teacher. It starts INACTIVE, exactly like the teacher's own. */
    suspend fun createGroupFor(teacherId: String, name: String, level: String = ""): TeacherGroup =
        Backend.client.from("teacher_groups")
            .insert(
                buildJsonObject {
                    put("teacher_id", teacherId)
                    put("name", name.trim())
                    put("level", level.trim().ifBlank { null })
                    put("approval_status", "INACTIVE")
                },
            ) { select() }
            .decodeSingle()

    /** Sets or clears a group's picture on the teacher's behalf — pass null/blank to remove it. */
    suspend fun setGroupPhoto(groupId: String, photoUrl: String?) {
        Backend.rpcVoid(
            "set_group_photo",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_photo_url", photoUrl)
            },
        )
    }

    // ── Booking-screen showcase ───────────────────────────────────────────
    // How teachers are presented to students: the order they appear in, the
    // premium badge, and their picture. All three are the platform's own
    // decisions, so all three are owner/admin-only server functions — a
    // teacher cannot move themselves up the list or award themselves a badge.

    /**
     * Every approved teacher in the exact order the booking screen will use,
     * hidden ones included (their `visible` flag says which is which) so an
     * order can be arranged before a teacher is opened for booking.
     */
    suspend fun showcaseTeachers(): List<AdminShowcaseTeacher> =
        Backend.client.postgrest.rpc("admin_showcase_teachers", buildJsonObject { }).decodeList()

    /**
     * Saves the arrangement as one list, top to bottom — the whole order in a
     * single call rather than a request per nudge, so a fast run of up/down taps
     * can never land on the server half-applied and leave two teachers claiming
     * the same position.
     */
    suspend fun reorderTeachers(teacherIdsInOrder: List<String>) {
        if (teacherIdsInOrder.isEmpty()) return
        Backend.rpcVoid(
            "admin_reorder_teachers",
            buildJsonObject {
                put(
                    "p_teacher_ids",
                    JsonArray(teacherIdsInOrder.map { JsonPrimitive(it) }),
                )
            },
        )
    }

    /**
     * The premium badge. [badge] is the label written on it; leaving it null keeps
     * whatever label is already stored, and [clearBadge] drops back to the app's
     * default wording without touching the flag itself.
     */
    suspend fun setTeacherBadge(
        teacherId: String,
        isPremium: Boolean? = null,
        badge: String? = null,
        clearBadge: Boolean = false,
    ) {
        Backend.rpcVoid(
            "admin_set_teacher_badge",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_is_premium", isPremium)
                put("p_badge", badge?.trim()?.ifBlank { null })
                put("p_clear_badge", clearBadge)
            },
        )
    }

    /** The teacher's picture as students see it. Pass null to fall back to their account avatar. */
    suspend fun setTeacherPhoto(teacherId: String, photoUrl: String?) {
        Backend.rpcVoid(
            "admin_set_teacher_photo",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_photo_url", photoUrl)
            },
        )
    }

    /** The short line under the teacher's name on booking/showcase cards, e.g. "English Teacher for Kids". */
    suspend fun setTeacherHeadline(teacherId: String, headline: String) {
        Backend.rpcVoid(
            "admin_set_teacher_headline",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_headline", headline)
            },
        )
    }

    suspend fun adminDeleteGroup(id: String?, groupName: String) {
        if (id != null) {
            val group = Backend.client.from("teacher_groups")
                .select { filter { eq("id", id) } }
                .decodeList<TeacherGroup>()
                .firstOrNull()
            if (group != null) {
                // Delete subscriptions first (teacher_id + group_name)
                Backend.client.from("teacher_subscriptions").delete {
                    filter {
                        eq("teacher_id", group.teacherId)
                        eq("group_name", group.name)
                    }
                }
            }
            // Then delete the group record
            Backend.client.from("teacher_groups").delete { filter { eq("id", id) } }
        } else {
            // No teacher_groups record — delete subscriptions by group_name across all teachers
            Backend.client.from("teacher_subscriptions").delete {
                filter { eq("group_name", groupName) }
            }
        }
    }

    // ── Subscriptions (admin/owner — all teachers) ──────────────────────────

    suspend fun allSubscriptions(search: String? = null): List<Subscription> =
        Backend.client.from("teacher_subscriptions")
            .select {
                filter {
                    search?.takeIf { it.isNotBlank() }?.let { q ->
                        or {
                            ilike("student_name", "%$q%")
                            ilike("parent_name", "%$q%")
                            ilike("group_name", "%$q%")
                        }
                    }
                }
                order("next_renewal_date", SortOrder.ASCENDING)
                limit(500)
            }
            .decodeList<Subscription>()
            .let { PaymentSources.attach(it) }

    /** All subscriptions of one teacher (owner dashboard drill-down). */
    suspend fun teacherSubscriptions(teacherId: String): List<Subscription> =
        Backend.client.from("teacher_subscriptions")
            .select {
                filter { eq("teacher_id", teacherId) }
                order("next_renewal_date", SortOrder.ASCENDING)
            }
            .decodeList<Subscription>()
            .let { PaymentSources.attach(it) }

    /**
     * Compute subscription stats client-side. Only APPROVED subscriptions
     * count toward earnings and are shown as active.
     */
    suspend fun subscriptionStatsAll(): SubscriptionStats = statsOf(allSubscriptions())

    /** The same numbers for a list of subscriptions — used for one teacher's slice. */
    fun statsOf(all: List<Subscription>): SubscriptionStats {
        val subs = all.filter { it.status != "PAUSED" && it.approvalStatus == "APPROVED" }
        val today = java.time.LocalDate.now()
        val weekLater = today.plusDays(7)

        val total = subs.size
        val dueThisWeek = subs.count { s ->
            (s.status == "ACTIVE" || s.status == "DUE") &&
            runCatching { java.time.LocalDate.parse(s.nextRenewalDate) }.getOrNull()?.let {
                it >= today && it <= weekLater
            } == true
        }
        val overdue = subs.count { it.status == "OVERDUE" }
        val totalMonthly = subs.sumOf { it.monthlyAmount }

        return SubscriptionStats(
            totalSubscriptions = total,
            dueThisWeek = dueThisWeek,
            overdue = overdue,
            totalMonthlyValue = totalMonthly,
        )
    }

    /**
     * [level] is inherited from the group automatically — the add-student dialog no longer asks
     * for it. [studentUserId] links this row to the learner's actual registered account, found
     * via [TeacherRepository.searchStudentAccounts].
     */
    suspend fun adminSaveSubscription(
        id: String?,
        teacherId: String,
        groupName: String,
        parentName: String,
        studentName: String,
        startDate: String,
        monthlyAmount: Double,
        currency: String,
        nextRenewalDate: String,
        status: String,
        notes: String,
        level: String = "",
        studentUserId: String? = null,
    ): Subscription {
        val payload = buildJsonObject {
            put("teacher_id", teacherId)
            put("group_name", groupName)
            put("parent_name", parentName)
            put("student_name", studentName)
            put("start_date", startDate)
            put("monthly_amount", monthlyAmount)
            put("currency", currency.ifBlank { "EGP" })
            put("next_renewal_date", nextRenewalDate)
            put("status", status)
            put("level", level.ifBlank { null })
            put("notes", notes.ifBlank { null })
            put("approval_status", "APPROVED")
            put("student_user_id", studentUserId)
        }
        return if (id == null) {
            Backend.client.from("teacher_subscriptions").insert(payload) { select() }.decodeSingle()
        } else {
            Backend.client.from("teacher_subscriptions").update(payload) {
                select()
                filter { eq("id", id) }
            }.decodeSingle()
        }
    }

    /**
     * Owner/admin adding or editing a member of a teacher's group, the way the teacher does it
     * (with the parent's phone). A new member is approved straight away when the group is already
     * active; in an inactive group it waits, and activating the group approves everyone at once.
     * An edit never changes the approval status.
     */
    suspend fun saveSubscriptionFor(
        id: String?,
        teacherId: String,
        groupName: String,
        parentName: String,
        studentName: String,
        startDate: String,
        monthlyAmount: Double,
        currency: String,
        nextRenewalDate: String,
        status: String,
        notes: String,
        level: String = "",
        parentPhone: String = "",
        studentUserId: String? = null,
    ): Subscription {
        val payload = buildJsonObject {
            put("teacher_id", teacherId)
            put("group_name", groupName)
            put("parent_name", parentName)
            put("student_name", studentName)
            put("start_date", startDate)
            put("monthly_amount", monthlyAmount)
            put("currency", currency.ifBlank { "EGP" })
            put("next_renewal_date", nextRenewalDate)
            put("status", status)
            if (level.isNotBlank()) put("level", level)
            if (parentPhone.isNotBlank()) put("parent_phone", parentPhone)
            if (notes.isNotBlank()) put("notes", notes)
            put("student_user_id", studentUserId)
        }
        if (id != null) {
            return Backend.client.from("teacher_subscriptions").update(payload) {
                select()
                filter { eq("id", id) }
            }.decodeSingle()
        }
        val groupActive = Backend.client.from("teacher_groups")
            .select { filter { eq("teacher_id", teacherId) } }
            .decodeList<TeacherGroup>()
            .firstOrNull { it.name.trim().equals(groupName.trim(), ignoreCase = true) }
            ?.approvalStatus
            // A member of a group with no group row (legacy) counts as active, like everywhere else.
            ?.let { it == "APPROVED" } ?: true
        val withStatus = buildJsonObject {
            payload.forEach { (k, v) -> put(k, v) }
            put("approval_status", if (groupActive) "APPROVED" else "PENDING")
        }
        return Backend.client.from("teacher_subscriptions").insert(withStatus) { select() }.decodeSingle()
    }

    suspend fun adminDeleteSubscription(id: String) {
        Backend.client.from("teacher_subscriptions").delete { filter { eq("id", id) } }
    }

    suspend fun adminRenewSubscription(subscriptionId: String): Subscription {
        // Call the secured renew_subscription RPC which handles:
        // - Permission checks (only APPROVED subscriptions can be renewed)
        // - Preventing duplicate renewals
        // - Recording renewal history
        // - Crediting teacher ledger with their share only
        // - Recording earnings (teacher share + owner share)
        // - Updating subscription renewal date
        Backend.rpcVoid(
            "renew_subscription",
            buildJsonObject { put("p_subscription_id", subscriptionId) },
        )
        // Fetch the updated subscription to return
        return Backend.client.from("teacher_subscriptions")
            .select { filter { eq("id", subscriptionId) } }
            .decodeSingle<Subscription>()
    }

    // ── Subscription Earnings (admin/owner) ────────────────────────────────

    suspend fun setTeacherSubscriptionRate(teacherId: String, percentage: Double) {
        // Call the secured RPC which verifies OWNER/ADMIN role server-side.
        Backend.rpcVoid(
            "set_teacher_subscription_rate",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_percentage", percentage)
            },
        )
    }

    suspend fun getTeacherSubscriptionRate(teacherId: String): Double {
        val rates = Backend.client.from("teacher_subscription_rates")
            .select { filter { eq("teacher_id", teacherId) } }
            .decodeList<TeacherSubscriptionRate>()
        return rates.firstOrNull()?.percentage ?: 0.0
    }

    /**
     * Monthly subscription income across the platform, taken from the subscriptions that are
     * actually active right now rather than from the accrual ledger — see
     * [ActiveSubscriptionEarnings]. Pass a teacher id to scope it to one teacher.
     */
    suspend fun activeSubscriptionEarnings(teacherId: String? = null): ActiveSubscriptionEarnings =
        Backend.client.postgrest.rpc(
            "subscription_active_earnings",
            buildJsonObject { teacherId?.let { put("p_teacher", it) } },
        ).decodeList<ActiveSubscriptionEarnings>().firstOrNull() ?: ActiveSubscriptionEarnings()

    /**
     * Every teacher's subscription commission rate in one query, keyed by teacher id — used by
     * [activeSubscriptionGroups] to split each group's total by each subscription's own
     * teacher's rate instead of one ratio spread across every teacher in the group. A teacher
     * with no row here has never had a rate configured, which is the same 0% default
     * [getTeacherSubscriptionRate] falls back to for one teacher.
     */
    suspend fun allTeacherSubscriptionRates(): Map<String, Double> =
        Backend.client.from("teacher_subscription_rates")
            .select()
            .decodeList<TeacherSubscriptionRate>()
            .associate { it.teacherId to it.percentage }

    /**
     * The active-subscription monthly value grouped by group_name — computed on the server
     * (`subscription_active_groups`) with the exact filter of [activeSubscriptionEarnings], so the
     * groups always add up to its total and match what the teacher sees. Scoped to one teacher
     * when [teacherId] is set, otherwise the whole platform.
     */
    suspend fun activeSubscriptionGroups(teacherId: String? = null): List<ActiveSubscriptionGroup> =
        TeacherEarnings.activeGroups(teacherId)

    /** One teacher's earnings + wallet — the very same server figures the teacher sees in their
     *  own studio (see [TeacherEarnings]). */
    suspend fun teacherEarningsOverview(teacherId: String): TeacherEarningsOverview =
        TeacherEarnings.overview(teacherId)

    suspend fun allSubscriptionEarningsSummary(
        filter: String = "month",
        fromDate: String? = null,
        toDate: String? = null,
    ): SubscriptionEarningsSummary {
        // Compute earnings summary client-side from subscription_earnings table
        // instead of calling the subscription_earnings_summary RPC.
        val now = java.time.LocalDate.now()
        val (periodStart, periodEnd) = when (filter) {
            "today" -> now.toString() to now.toString()
            "week" -> now.with(java.time.DayOfWeek.MONDAY).toString() to now.toString()
            "month" -> now.withDayOfMonth(1).toString() to now.toString()
            "custom" -> (fromDate ?: now.withDayOfMonth(1).toString()) to (toDate ?: now.toString())
            else -> now.withDayOfMonth(1).toString() to now.toString()
        }

        val allEarnings = allSubscriptionEarningsInWindow(periodStart, periodEnd)
        // Prorated by day-overlap with the requested window — see
        // SubscriptionEarning.overlapFraction — instead of attributing a whole month's amount
        // to whichever single day it happened to renew on.
        val contributions = allEarnings.map { it to it.prorated(periodStart, periodEnd) }
            .filter { (_, p) -> p.total != 0.0 || p.teacherShare != 0.0 || p.ownerShare != 0.0 }

        val totalEarned = contributions.sumOf { it.second.total }
        val teacherShare = contributions.sumOf { it.second.teacherShare }
        val ownerShare = contributions.sumOf { it.second.ownerShare }
        val uniqueSubs = contributions.map { it.first.subscriptionId }.distinct().size

        val groups = contributions.groupBy { it.first.groupName }.map { (name, items) ->
            SubscriptionEarningsGroup(
                groupName = name,
                total = items.sumOf { it.second.total },
                teacherShare = items.sumOf { it.second.teacherShare },
                ownerShare = items.sumOf { it.second.ownerShare },
                count = items.size,
            )
        }.sortedByDescending { it.total }

        return SubscriptionEarningsSummary(
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalEarned = totalEarned,
            teacherShare = teacherShare,
            ownerShare = ownerShare,
            subscriptionCount = uniqueSubs,
            groups = groups,
        )
    }

    /** Course-sale earnings across every teacher — the owner-side counterpart of
     *  TeacherRepository.myCourseEarningsSummary. See [CourseEarningsSummary] for why this is
     *  never merged into the subscription figures above. */
    suspend fun allCourseEarningsSummary(
        filter: String = "month",
        fromDate: String? = null,
        toDate: String? = null,
    ): CourseEarningsSummary {
        val now = java.time.LocalDate.now()
        val (periodStart, periodEnd) = when (filter) {
            "today" -> now.toString() to now.toString()
            "week" -> now.with(java.time.DayOfWeek.MONDAY).toString() to now.toString()
            "month" -> now.withDayOfMonth(1).toString() to now.toString()
            "custom" -> (fromDate ?: now.withDayOfMonth(1).toString()) to (toDate ?: now.toString())
            else -> now.withDayOfMonth(1).toString() to now.toString()
        }

        val rows = Backend.client.from("orders")
            .select {
                filter {
                    eq("item_type", "COURSE")
                    isIn("status", listOf("PAID", "PARTIALLY_REFUNDED"))
                    gte("paid_at", "${periodStart}T00:00:00")
                    lte("paid_at", "${periodEnd}T23:59:59")
                }
            }
            .decodeList<CourseOrderRow>()

        // Net of refunds — see CourseOrderRow.net(): a PARTIALLY_REFUNDED order belongs in
        // this window, but only for the part the platform actually kept.
        val net = rows.map { it.net() }
        return CourseEarningsSummary(
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalEarned = net.sumOf { it.first },
            teacherShare = net.sumOf { it.second },
            ownerShare = net.sumOf { it.third },
            orderCount = rows.size,
        )
    }

    suspend fun teacherSubscriptionEarningsSummary(
        teacherId: String,
        filter: String = "month",
        fromDate: String? = null,
        toDate: String? = null,
    ): SubscriptionEarningsSummary {
        // Compute teacher-specific earnings summary client-side.
        val now = java.time.LocalDate.now()
        val (periodStart, periodEnd) = when (filter) {
            "today" -> now.toString() to now.toString()
            "week" -> now.with(java.time.DayOfWeek.MONDAY).toString() to now.toString()
            "month" -> now.withDayOfMonth(1).toString() to now.toString()
            "custom" -> (fromDate ?: now.withDayOfMonth(1).toString()) to (toDate ?: now.toString())
            else -> now.withDayOfMonth(1).toString() to now.toString()
        }

        val allEarnings = teacherSubscriptionEarningsInWindow(teacherId, periodStart, periodEnd)
        // Same day-overlap proration as allSubscriptionEarningsSummary — see
        // SubscriptionEarning.overlapFraction.
        val contributions = allEarnings.map { it to it.prorated(periodStart, periodEnd) }
            .filter { (_, p) -> p.total != 0.0 || p.teacherShare != 0.0 || p.ownerShare != 0.0 }

        val totalEarned = contributions.sumOf { it.second.total }
        val teacherShare = contributions.sumOf { it.second.teacherShare }
        val ownerShare = contributions.sumOf { it.second.ownerShare }
        val uniqueSubs = contributions.map { it.first.subscriptionId }.distinct().size

        val groups = contributions.groupBy { it.first.groupName }.map { (name, items) ->
            SubscriptionEarningsGroup(
                groupName = name,
                total = items.sumOf { it.second.total },
                teacherShare = items.sumOf { it.second.teacherShare },
                ownerShare = items.sumOf { it.second.ownerShare },
                count = items.size,
            )
        }.sortedByDescending { it.total }

        return SubscriptionEarningsSummary(
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalEarned = totalEarned,
            teacherShare = teacherShare,
            ownerShare = ownerShare,
            subscriptionCount = uniqueSubs,
            groups = groups,
        )
    }

    /** One teacher's course-sale earnings — the owner-side per-teacher drill-down counterpart
     *  of [allCourseEarningsSummary]. */
    suspend fun teacherCourseEarningsSummary(
        teacherId: String,
        filter: String = "month",
        fromDate: String? = null,
        toDate: String? = null,
    ): CourseEarningsSummary {
        val now = java.time.LocalDate.now()
        val (periodStart, periodEnd) = when (filter) {
            "today" -> now.toString() to now.toString()
            "week" -> now.with(java.time.DayOfWeek.MONDAY).toString() to now.toString()
            "month" -> now.withDayOfMonth(1).toString() to now.toString()
            "custom" -> (fromDate ?: now.withDayOfMonth(1).toString()) to (toDate ?: now.toString())
            else -> now.withDayOfMonth(1).toString() to now.toString()
        }

        val rows = Backend.client.from("orders")
            .select {
                filter {
                    eq("teacher_id", teacherId)
                    eq("item_type", "COURSE")
                    isIn("status", listOf("PAID", "PARTIALLY_REFUNDED"))
                    gte("paid_at", "${periodStart}T00:00:00")
                    lte("paid_at", "${periodEnd}T23:59:59")
                }
            }
            .decodeList<CourseOrderRow>()

        // Net of refunds — see CourseOrderRow.net(): a PARTIALLY_REFUNDED order belongs in
        // this window, but only for the part the platform actually kept.
        val net = rows.map { it.net() }
        return CourseEarningsSummary(
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalEarned = net.sumOf { it.first },
            teacherShare = net.sumOf { it.second },
            ownerShare = net.sumOf { it.third },
            orderCount = rows.size,
        )
    }

    suspend fun generateMonthlyReport(teacherId: String?, year: Int?, month: Int?) {
        // Generate monthly report client-side from subscription_earnings table.
        val now = java.time.LocalDate.now()
        val targetYear = year ?: now.year
        val targetMonth = month ?: now.monthValue
        val periodStart = java.time.LocalDate.of(targetYear, targetMonth, 1).toString()
        val periodEnd = java.time.LocalDate.of(targetYear, targetMonth, 1).plusMonths(1).minusDays(1).toString()

        val allEarnings = Backend.client.from("subscription_earnings")
            .select {
                order("created_at", SortOrder.DESCENDING)
                limit(500)
            }
            .decodeList<SubscriptionEarning>()
        // Prorated by day-overlap with the target calendar month — see
        // SubscriptionEarning.overlapFraction — so a saved report agrees with the live summary
        // screens, which prorate the same way. A subscription that renewed mid-month, or spans
        // two calendar months, now contributes only the days of it that actually fall inside
        // this specific month, instead of either the whole month's amount landing on one side
        // of the boundary or vanishing from both.
        val contributions = allEarnings
            .filter { teacherId == null || it.teacherId == teacherId }
            .map { it to it.prorated(periodStart, periodEnd) }
            .filter { (_, p) -> p.total != 0.0 || p.teacherShare != 0.0 || p.ownerShare != 0.0 }

        val totalEarned = contributions.sumOf { it.second.total }
        val ownerShare = contributions.sumOf { it.second.ownerShare }
        val uniqueSubs = contributions.map { it.first.subscriptionId }.distinct().size

        val groupData = contributions.groupBy { it.first.groupName }.map { (name, items) ->
            SubscriptionEarningsGroup(
                groupName = name,
                total = items.sumOf { it.second.total },
                teacherShare = items.sumOf { it.second.teacherShare },
                ownerShare = items.sumOf { it.second.ownerShare },
                count = items.size,
            )
        }

        val report = buildJsonObject {
            if (teacherId != null) put("teacher_id", teacherId)
            put("year", targetYear)
            put("month", targetMonth)
            put("total_earned", totalEarned)
            put("owner_share", ownerShare)
            put("currency", "EGP")
            put("subscription_count", uniqueSubs)
            put("data", kotlinx.serialization.json.JsonArray(groupData.map {
                kotlinx.serialization.json.JsonObject(mapOf(
                    "group_name" to kotlinx.serialization.json.JsonPrimitive(it.groupName),
                    "total" to kotlinx.serialization.json.JsonPrimitive(it.total),
                    "teacher_share" to kotlinx.serialization.json.JsonPrimitive(it.teacherShare),
                    "owner_share" to kotlinx.serialization.json.JsonPrimitive(it.ownerShare),
                    "count" to kotlinx.serialization.json.JsonPrimitive(it.count),
                ))
            }))
        }
        // Upsert: if a report for this teacher/year/month already exists, update it.
        Backend.client.from("subscription_earnings_reports").upsert(report) {
            onConflict = "teacher_id,year,month"
        }
    }

    suspend fun savedReports(teacherId: String? = null): List<SubscriptionEarningsReport> {
        // Read reports directly from the table instead of calling the RPC.
        return Backend.client.from("subscription_earnings_reports")
            .select {
                filter {
                    teacherId?.let { eq("teacher_id", it) }
                }
                order("year", SortOrder.DESCENDING)
            }
            .decodeList()
    }

    /**
     * Every subscription-earning row whose covered period overlaps [from]..[to], across every
     * teacher — paged through to the end rather than capped.
     *
     * The summaries used to build on `allSubscriptionEarnings()`, which returns the newest 100
     * rows and nothing else. That silently under-reports the moment the platform has more than
     * 100 earning rows (one renewal cycle with 100+ active subscriptions), and the number it
     * shows is wrong in a way nobody can see: it looks like a plausible total, not an error.
     * Filtering by period overlap server-side also means the client no longer downloads the
     * whole table to throw most of it away.
     */
    suspend fun allSubscriptionEarningsInWindow(from: String, to: String): List<SubscriptionEarning> =
        fetchSubscriptionEarningsPaged(from, to, teacherId = null)

    /** Same, scoped to one teacher — backs the per-teacher drill-downs. */
    suspend fun teacherSubscriptionEarningsInWindow(teacherId: String, from: String, to: String): List<SubscriptionEarning> =
        fetchSubscriptionEarningsPaged(from, to, teacherId = teacherId)

    private suspend fun fetchSubscriptionEarningsPaged(
        from: String,
        to: String,
        teacherId: String?,
    ): List<SubscriptionEarning> {
        val pageSize = 1000L
        val all = mutableListOf<SubscriptionEarning>()
        var offset = 0L
        while (true) {
            val page = Backend.client.from("subscription_earnings")
                .select {
                    filter {
                        teacherId?.let { eq("teacher_id", it) }
                        // Overlap test, matching SubscriptionEarning.overlapFraction: a period
                        // counts if it starts before the window ends and ends after it starts.
                        lte("period_start", to)
                        gte("period_end", from)
                    }
                    order("period_start", SortOrder.DESCENDING)
                    range(offset, offset + pageSize - 1)
                }
                .decodeList<SubscriptionEarning>()
            all += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return all
    }

    suspend fun allSubscriptionEarnings(page: Int = 0, pageSize: Int = 100): List<SubscriptionEarning> {
        val offset = page * pageSize
        return Backend.client.from("subscription_earnings")
            .select {
                order("created_at", SortOrder.DESCENDING)
                limit(pageSize.toLong())
                range(offset.toLong(), (offset + pageSize - 1).toLong())
            }
            .decodeList()
    }

    /** Recent earnings rows for one teacher (owner dashboard drill-down). */
    suspend fun teacherSubscriptionEarnings(teacherId: String, limit: Long = 50): List<SubscriptionEarning> =
        Backend.client.from("subscription_earnings")
            .select {
                filter { eq("teacher_id", teacherId) }
                order("created_at", SortOrder.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /** Withdrawal history of one teacher (owner dashboard drill-down). */
    suspend fun teacherPayouts(teacherId: String): List<Payout> =
        Backend.client.from("payouts")
            .select {
                filter { eq("teacher_id", teacherId) }
                order("requested_at", SortOrder.DESCENDING)
            }
            .decodeList()

    // ── Approval System ────────────────────────────────────────────────

    /**
     * Get pending request counts per teacher for the owner dashboard.
     *
     * This is a convenience feed for badges only: the teacher list, subscriptions
     * and approval requests all load through their own queries. When the RPC is
     * unavailable or fails (e.g. it has not been migrated to the backend yet) the
     * screens still open — they simply show zero pending requests.
     */
    suspend fun pendingRequestCounts(): List<PendingRequestCount> = runCatching {
        val result = Backend.rpcRaw("pending_request_counts")
        when (result) {
            is kotlinx.serialization.json.JsonArray -> {
                result.mapNotNull { el ->
                    runCatching {
                        val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        PendingRequestCount(
                            teacherId = obj["teacher_id"]?.toString()?.trim('"') ?: return@mapNotNull null,
                            teacherName = obj["teacher_name"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            teacherAvatar = obj["teacher_avatar"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            pendingCount = obj["pending_count"]?.toString()?.trim('"')?.toLongOrNull() ?: 0,
                        )
                    }.getOrNull()
                }
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /** List approval requests with optional filters. */
    /**
     * Requests that point at a subscription (renewal, activation, deletion…) only carry the
     * dates/amount in their payload. Pull the student, parent, group and phone from the
     * subscription itself so the owner can see WHAT they are approving. Never fails the list.
     */
    private suspend fun withSubscriptionDetails(list: List<ApprovalRequest>): List<ApprovalRequest> {
        val ids = list.filter { it.targetType == "subscription" && !it.targetId.isNullOrBlank() }
            .mapNotNull { it.targetId }.distinct()
        if (ids.isEmpty()) return list
        val rows = runCatching {
            Backend.client.from("teacher_subscriptions")
                .select { filter { isIn("id", ids) } }
                .decodeList<JsonObject>()
        }.getOrNull() ?: return list
        val byId = rows.mapNotNull { row ->
            (row["id"] as? JsonPrimitive)?.content?.let { it to row }
        }.toMap()
        val keys = listOf("student_name", "parent_name", "parent_phone", "group_name", "level", "monthly_amount", "currency", "start_date")
        return list.map { req ->
            val sub = req.targetId?.let { byId[it] } ?: return@map req
            val merged = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
            req.requestData?.forEach { (k, v) -> merged[k] = v }
            for (k in keys) {
                val existing = merged[k]
                val blank = existing == null || existing is kotlinx.serialization.json.JsonNull ||
                    (existing as? JsonPrimitive)?.content.isNullOrBlank()
                val fromSub = sub[k]
                if (blank && fromSub != null && fromSub !is kotlinx.serialization.json.JsonNull) merged[k] = fromSub
            }
            req.copy(requestData = JsonObject(merged))
        }
    }

    /**
     * [status] may be "DECIDED" (everything already approved or rejected). [action] narrows to one
     * request kind (e.g. RENEW_SUBSCRIPTION) and [limit] caps the rows, for history lists.
     */
    suspend fun approvalRequests(
        status: String? = "PENDING",
        teacherId: String? = null,
        action: String? = null,
        limit: Int? = null,
    ): List<ApprovalRequest> {
        val result = Backend.rpcRaw(
            "list_approval_requests",
            buildJsonObject {
                put("p_status", status)
                put("p_teacher", teacherId)
                put("p_action", action)
                put("p_limit", limit)
            },
        )
        val parsed = when (result) {
            is kotlinx.serialization.json.JsonArray -> {
                result.mapNotNull { el ->
                    runCatching {
                        val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        ApprovalRequest(
                            id = obj["id"]?.toString()?.trim('"') ?: return@mapNotNull null,
                            teacherId = obj["teacher_id"]?.toString()?.trim('"') ?: "",
                            actionType = obj["action_type"]?.toString()?.trim('"') ?: "",
                            targetType = obj["target_type"]?.toString()?.trim('"') ?: "",
                            targetId = obj["target_id"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            requestData = obj["request_data"] as? JsonObject,
                            status = obj["status"]?.toString()?.trim('"') ?: "PENDING",
                            reviewedBy = obj["reviewed_by"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            reviewedAt = obj["reviewed_at"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            reviewNote = obj["review_note"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            createdAt = obj["created_at"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            teacherName = obj["teacher_name"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            teacherAvatar = obj["teacher_avatar"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentSource = obj["payment_source"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentBrand = obj["payment_brand"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentAmount = obj["payment_amount"]?.toString()?.trim('"')?.toDoubleOrNull(),
                            paymentCurrency = obj["payment_currency"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentProofPath = obj["payment_proof_path"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentSenderPhone = obj["payment_sender_phone"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentSubmittedAt = obj["payment_submitted_at"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            paymentReviewedAt = obj["payment_reviewed_at"]?.toString()?.trim('"')?.takeIf { it != "null" },
                        )
                    }.getOrNull()
                }
            }
            else -> emptyList()
        }
        return withSubscriptionDetails(parsed)
    }

    /** Approve or reject an approval request. */
    suspend fun processApprovalRequest(requestId: String, decision: String, note: String = "") {
        Backend.rpcVoid(
            "process_approval_request",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_decision", decision)
                if (note.isNotBlank()) put("p_note", note)
            },
        )
    }

    /**
     * Staff: directly activate (approve) an individual subscription.
     * The subscription flips from PENDING/REJECTED to APPROVED immediately.
     */
    suspend fun activateSubscription(subscriptionId: String) {
        Backend.rpcVoid(
            "activate_subscription",
            buildJsonObject { put("p_subscription_id", subscriptionId) },
        )
    }

    /**
     * Staff: directly reject an individual subscription.
     * The subscription flips from PENDING to REJECTED via the secured
     * reject_subscription RPC (server-side role check + audit trail).
     */
    suspend fun rejectSubscription(subscriptionId: String, note: String = "") {
        Backend.rpcVoid(
            "reject_subscription",
            buildJsonObject {
                put("p_subscription_id", subscriptionId)
                if (note.isNotBlank()) put("p_note", note)
            },
        )
    }
}
