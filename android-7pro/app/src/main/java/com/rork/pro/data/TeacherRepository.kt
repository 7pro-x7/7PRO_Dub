package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Teacher Studio data. Every query is scoped to the signed-in teacher and the
 * database policies enforce the same boundary, so one teacher can never read another's data.
 */
object TeacherRepository {

    private fun me(): String = Backend.currentUserId ?: error("UNAUTHORIZED")

    /** Normalizes a group name: trims whitespace and collapses multiple spaces. */
    private fun normalizeGroupName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ")

    /**
     * Lets a teacher add a course category by name, right from the course editor. The server
     * only grants teachers INSERT on this table (see the `teachers_insert_categories` policy) —
     * they can add a new category but never rename or remove one, which stays owner/admin-only
     * from the CMS screen. New categories are published immediately, same as an admin-added one.
     */
    suspend fun createCategory(name: String): Category {
        val slug = name.lowercase().trim().replace(' ', '-')
        val payload = buildJsonObject {
            put("name", name.trim())
            put("slug", slug)
        }
        return Backend.client.from("categories").insert(payload) { select() }.decodeSingle()
    }

    suspend fun analytics(): JsonObject =
        Backend.rpcRaw(
            "teacher_analytics",
            buildJsonObject {
                put("p_teacher", me())
                put("p_from", null as String?)
                put("p_to", null as String?)
            },
        ) as JsonObject

    /**
     * Authoritative balance from the server `teacher_balance` RPC (sum of the
     * ledger: AVAILABLE credits minus PAYOUT reservations, plus paid/pending
     * splits). Falls back to a client-side mirror of the same semantics when
     * the RPC is unavailable.
     */
    suspend fun balance(): TeacherBalance {
        val result = runCatching {
            Backend.rpcRaw(
                "teacher_balance",
                buildJsonObject { put("p_teacher", me()) },
            ) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()

        if (result != null) {
            val num = { key: String -> result[key]?.toString()?.trim('"')?.toDoubleOrNull() ?: 0.0 }
            return TeacherBalance(
                pending = num("pending"),
                available = num("available"),
                paid = num("paid"),
                gross = num("gross"),
                currency = result["currency"]?.toString()?.trim('"')?.takeIf { it != "null" && it.isNotBlank() } ?: "EGP",
                minimumPayout = result["minimum_payout"]?.toString()?.trim('"')?.toDoubleOrNull() ?: 1000.0,
            )
        }

        // Fallback: mirror the server semantics from the ledger rows.
        val entries = Backend.client.from("teacher_ledger")
            .select { filter { eq("teacher_id", me()) } }
            .decodeList<LedgerEntry>()

        // Mirror the server's teacher_balance(): available/withdrawable money is COURSE
        // earnings only (EARNING rows whose order is a COURSE order) plus the PAYOUT rows that
        // reserve/pay it out. Subscription earnings (no order) never fund a withdrawal.
        // If the orders lookup fails, fall back to "has an order" as the course proxy.
        val courseOrderIds = runCatching {
            Backend.client.from("orders")
                .select { filter { eq("teacher_id", me()); eq("item_type", "COURSE") } }
                .decodeList<com.rork.pro.data.Order>() // fully qualified: `Order` here is postgrest's sort enum
                .map { it.id }
                .toSet()
        }.getOrNull()
        fun isCourseEarning(l: LedgerEntry): Boolean =
            l.kind == "EARNING" && l.orderId != null && (courseOrderIds == null || l.orderId in courseOrderIds)
        val courseEntries = entries.filter { it.kind == "PAYOUT" || isCourseEarning(it) }

        // Same arithmetic as the RPC: withdrawal reservations are stored negative with status
        // AVAILABLE, and the lifetime PAID total is subtracted so paid money never comes back.
        val paid = -entries.filter { it.kind == "PAYOUT" && it.status == "PAID" }.sumOf { it.amount }
        val availableCredits = courseEntries.filter { it.status == "AVAILABLE" }.sumOf { it.amount }
        val available = availableCredits - paid
        val pending = courseEntries.filter { it.status == "PENDING" && it.kind == "EARNING" }.sumOf { it.amount }
        val gross = courseEntries.filter { it.amount > 0 && it.kind == "EARNING" }.sumOf { it.amount }

        return TeacherBalance(
            pending = pending.coerceAtLeast(0.0),
            available = available.coerceAtLeast(0.0),
            paid = paid.coerceAtLeast(0.0),
            gross = gross,
            currency = entries.firstOrNull()?.currency ?: "EGP",
        )
    }

    suspend fun myCourses(): List<Course> =
        Backend.client.from("courses")
            .select {
                filter { eq("teacher_id", me()) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun saveCourse(
        id: String?,
        title: String,
        subtitle: String,
        description: String,
        thumbnailUrl: String,
        level: String,
        categoryId: String?,
        price: Double,
        currency: String,
        isFree: Boolean,
    ): Course {
        val payload = buildJsonObject {
            put("teacher_id", me())
            put("title", title)
            put("subtitle", subtitle)
            put("description", description)
            put("thumbnail_url", thumbnailUrl.ifBlank { null })
            put("level", level.ifBlank { null })
            put("category_id", categoryId)
            put("base_price", price)
            put("base_currency", currency)
            put("is_free", isFree)
        }
        return if (id == null) {
            Backend.client.from("courses").insert(payload) { select() }.decodeSingle()
        } else {
            Backend.client.from("courses").update(payload) {
                select()
                filter { eq("id", id) }
            }.decodeSingle()
        }
    }

    suspend fun submitCourseForReview(courseId: String) {
        Backend.client.from("courses")
            .update(buildJsonObject { put("status", "PENDING_REVIEW") }) { filter { eq("id", courseId) } }
    }

    suspend fun lessons(courseId: String): List<Lesson> =
        Backend.client.from("lessons")
            .select {
                filter { eq("course_id", courseId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun saveLesson(
        id: String?,
        courseId: String,
        title: String,
        kind: String,
        videoUrl: String,
        documentUrl: String,
        content: String,
        durationSeconds: Int,
        sortOrder: Int,
        isPreview: Boolean,
    ) {
        val payload = buildJsonObject {
            put("course_id", courseId)
            put("title", title)
            put("kind", kind)
            put("video_url", videoUrl.ifBlank { null })
            put("document_url", documentUrl.ifBlank { null })
            put("content", content.ifBlank { null })
            put("duration_seconds", durationSeconds)
            put("sort_order", sortOrder)
            put("is_preview", isPreview)
        }
        if (id == null) {
            Backend.client.from("lessons").insert(payload)
        } else {
            Backend.client.from("lessons").update(payload) { filter { eq("id", id) } }
        }
    }

    suspend fun deleteLesson(id: String) {
        Backend.client.from("lessons").delete { filter { eq("id", id) } }
    }

    suspend fun ledger(): List<LedgerEntry> =
        Backend.client.from("teacher_ledger")
            .select {
                filter { eq("teacher_id", me()) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList()

    suspend fun payouts(): List<Payout> =
        Backend.client.from("payouts")
            .select {
                filter { eq("teacher_id", me()) }
                order("requested_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun requestPayout(amount: Double, method: String, destination: String, note: String) {
        Backend.rpcVoid(
            "request_payout",
            buildJsonObject {
                put("p_amount", amount)
                put("p_method", method)
                put("p_destination", destination)
                put("p_note", note)
            },
        )
    }

    suspend fun updateTeacherProfile(headline: String, bio: String, photoUrl: String, subjects: List<String>, languages: List<String>, years: Int) {
        Backend.client.from("teacher_profiles").update(
            buildJsonObject {
                put("headline", headline)
                put("bio", bio)
                put("photo_url", photoUrl.ifBlank { null })
                put("subjects", kotlinx.serialization.json.JsonArray(subjects.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("languages", kotlinx.serialization.json.JsonArray(languages.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("experience_years", years)
            },
        ) { filter { eq("id", me()) } }
    }

    // ── Groups ────────────────────────────────────────────────────────────

    suspend fun myGroups(): List<TeacherGroup> =
        Backend.client.from("teacher_groups")
            .select {
                filter { eq("teacher_id", me()) }
                order("name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList()

    /**
     * Groups are created directly by the teacher and start INACTIVE — they never
     * count toward earnings until the owner approves the activation request.
     */
    suspend fun createGroup(name: String, level: String = ""): TeacherGroup {
        val payload = buildJsonObject {
            put("teacher_id", me())
            put("name", name.trim())
            put("level", level.trim().ifBlank { null })
            put("approval_status", "INACTIVE")
        }
        return Backend.client.from("teacher_groups").insert(payload) { select() }.decodeSingle()
    }

    /** Edits a group's name/level directly. Members follow when the group is renamed. */
    suspend fun updateGroup(id: String, name: String, level: String, ownerId: String? = null) {
        val normalized = normalizeGroupName(name)
        val oldName = Backend.client.from("teacher_groups")
            .select { filter { eq("id", id) } }
            .decodeList<TeacherGroup>()
            .firstOrNull()
            ?.name

        Backend.client.from("teacher_groups").update(
            buildJsonObject {
                put("name", normalized)
                put("level", level.trim().ifBlank { null })
            },
        ) { filter { eq("id", id) } }

        // Subscriptions reference their group by name — keep members attached.
        if (oldName != null && oldName != normalized) {
            Backend.client.from("teacher_subscriptions").update(
                buildJsonObject { put("group_name", normalized) },
            ) {
                filter {
                    eq("teacher_id", ownerId ?: me())
                    eq("group_name", oldName)
                }
            }
        }
    }

    /**
     * Sets or clears the group's picture. Goes through the server, which also lets
     * owner/admin staff set it on the teacher's behalf — pass null/blank to remove it.
     */
    suspend fun setGroupPhoto(groupId: String, photoUrl: String?) {
        Backend.rpcVoid(
            "set_group_photo",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_photo_url", photoUrl)
            },
        )
    }

    /** Deletes a group and its members directly — no approval needed. */
    suspend fun deleteGroup(id: String?, groupName: String) {
        // Members are keyed by group name, so remove them together with the group.
        Backend.client.from("teacher_subscriptions").delete {
            filter {
                eq("teacher_id", me())
                eq("group_name", groupName)
            }
        }
        if (id != null && id != "pending") {
            Backend.client.from("teacher_groups").delete { filter { eq("id", id) } }
        }
    }

    /**
     * Sends the group for owner/admin approval. The group turns PENDING until
     * approved (then it counts toward earnings) or rejected.
     */
    suspend fun activateGroup(groupId: String) {
        Backend.rpcVoid(
            "activate_group",
            buildJsonObject { put("p_group_id", groupId) },
        )
    }

    /**
     * Sends an individual subscription for owner/admin approval. The subscription
     * turns PENDING until approved (then it counts toward earnings) or rejected.
     */
    suspend fun activateSubscription(subscriptionId: String) {
        Backend.rpcVoid(
            "activate_subscription",
            buildJsonObject { put("p_subscription_id", subscriptionId) },
        )
    }

    // ── Subscriptions ───────────────────────────────────────────────────────

    /** Refresh subscription statuses server-side (DUE / OVERDUE). Called on app open. */
    suspend fun refreshSubscriptionStatuses() {
        runCatching { Backend.rpcVoid("refresh_subscription_statuses") }
    }

    /**
     * Fetch subscription stats from the server RPC. Only APPROVED subscriptions
     * count toward earnings and are shown as active.
     */
    suspend fun subscriptionStats(): SubscriptionStats {
        val result = runCatching {
            Backend.rpcRaw(
                "subscription_stats",
                buildJsonObject { put("p_teacher", me()) },
            ) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()

        if (result != null) {
            return SubscriptionStats(
                totalSubscriptions = result["total_subscriptions"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
                dueThisWeek = result["due_this_week"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
                overdue = result["overdue"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
                totalMonthlyValue = result["total_monthly_value"]?.toString()?.trim('"')?.toDoubleOrNull() ?: 0.0,
            )
        }

        // Fallback: compute client-side if RPC fails
        val subs = mySubscriptions().filter { it.status != "PAUSED" && it.approvalStatus == "APPROVED" }
        val today = java.time.LocalDate.now()
        val weekLater = today.plusDays(7)

        return SubscriptionStats(
            totalSubscriptions = subs.size,
            dueThisWeek = subs.count { s ->
                (s.status == "ACTIVE" || s.status == "DUE") &&
                runCatching { java.time.LocalDate.parse(s.nextRenewalDate) }.getOrNull()?.let {
                    it >= today && it <= weekLater
                } == true
            },
            overdue = subs.count { it.status == "OVERDUE" },
            totalMonthlyValue = subs.sumOf { it.monthlyAmount },
        )
    }

    suspend fun mySubscriptions(): List<Subscription> =
        Backend.client.from("teacher_subscriptions")
            .select {
                filter { eq("teacher_id", me()) }
                order("next_renewal_date", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<Subscription>()
            .let { PaymentSources.attach(it) }

    suspend fun mySubscriptionsWithSearch(query: String): List<Subscription> {
        var q = Backend.client.from("teacher_subscriptions")
            .select {
                filter {
                    eq("teacher_id", me())
                    if (query.isNotBlank()) {
                        or {
                            ilike("student_name", "%$query%")
                            ilike("parent_name", "%$query%")
                            ilike("group_name", "%$query%")
                        }
                    }
                }
                order("next_renewal_date", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
        return PaymentSources.attach(q.decodeList<Subscription>())
    }

    /**
     * Looks up registered app accounts by email or name — used by the add-student form so a
     * teacher/owner/admin identifies a learner by their actual account instead of typing a
     * free-text level. Reuses the same server-side search the Virtual Classroom invite screen
     * uses (restricted to STUDENT accounts, and to TEACHER/OWNER/ADMIN callers).
     */
    suspend fun searchStudentAccounts(query: String): List<ClassroomStudentHit> =
        ClassroomRepository.searchStudents(query)

    /**
     * Members are created/edited directly by the teacher — no approval. The
     * server mirrors the group's status onto the member automatically, so a
     * member of an inactive group never counts toward earnings.
     *
     * [level] is no longer typed by hand in the add-student form — it is passed through
     * automatically from the group the student belongs to. [studentUserId] links the row to the
     * learner's actual registered account, found via [searchStudentAccounts].
     */
    suspend fun saveSubscription(
        id: String?,
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
            put("teacher_id", me())
            put("group_name", normalizeGroupName(groupName))
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
        return if (id == null) {
            Backend.client.from("teacher_subscriptions").insert(payload) { select() }.decodeSingle()
        } else {
            Backend.client.from("teacher_subscriptions").update(payload) {
                select()
                filter { eq("id", id) }
            }.decodeSingle()
        }
    }

    suspend fun deleteSubscription(id: String) {
        Backend.client.from("teacher_subscriptions").delete { filter { eq("id", id) } }
    }

    /**
     * Renews a member via the secured RPC. The server refuses to renew
     * members of groups that are not active or subscriptions that are not
     * approved, so the balance is only ever credited for active subscriptions.
     * The RPC also prevents duplicate renewals and credits the teacher's
     * share only (not the full amount).
     */
    suspend fun renewSubscription(subscriptionId: String): Subscription {
        Backend.rpcVoid(
            "renew_subscription",
            buildJsonObject { put("p_subscription_id", subscriptionId) },
        )
        return Backend.client.from("teacher_subscriptions")
            .select { filter { eq("id", subscriptionId) } }
            .decodeSingle()
    }

    suspend fun renewalHistory(subscriptionId: String): List<SubscriptionRenewal> =
        Backend.client.from("subscription_renewals")
            .select {
                filter { eq("subscription_id", subscriptionId) }
                order("renewed_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList()

    // ── Subscription Earnings ──────────────────────────────────────────────

    suspend fun mySubscriptionRate(): TeacherSubscriptionRate =
        Backend.client.from("teacher_subscription_rates")
            .select { filter { eq("teacher_id", me()) } }
            .decodeList<TeacherSubscriptionRate>()
            .firstOrNull() ?: TeacherSubscriptionRate(teacherId = me())

    /** My own active subscriptions' monthly value and my share of it — see
     *  [ActiveSubscriptionEarnings]. */
    suspend fun myActiveSubscriptionEarnings(): ActiveSubscriptionEarnings =
        Backend.client.postgrest.rpc(
            "subscription_active_earnings",
            buildJsonObject { put("p_teacher", me()) },
        ).decodeList<ActiveSubscriptionEarnings>().firstOrNull() ?: ActiveSubscriptionEarnings()

    /** My active subscriptions' monthly value by group — server-side, so the groups always add
     *  up to [myActiveSubscriptionEarnings] and match what the owner sees for me. */
    suspend fun myActiveSubscriptionGroups(): List<ActiveSubscriptionGroup> =
        TeacherEarnings.activeGroups(me())

    /** My course + subscription earnings and wallet — the same object the owner reads for me. */
    suspend fun myEarningsOverview(): TeacherEarningsOverview = TeacherEarnings.overview(me())

    suspend fun mySubscriptionEarningsSummary(
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

        val allEarnings = mySubscriptionEarningsInWindow(periodStart, periodEnd)
        // Prorated by day-overlap with the requested window, not "did the renewal date happen
        // to land inside it" — a monthly subscription earns continuously across the whole
        // month it covers. See SubscriptionEarning.overlapFraction for why this matters: a
        // 1000/month subscription now reports its true ~233 share for a 7-day week instead of
        // either the full 1000 (the week it renewed) or 0 (every other week).
        val contributions = allEarnings.map { it to it.prorated(periodStart, periodEnd) }
            .filter { (_, p) -> p.total != 0.0 || p.teacherShare != 0.0 || p.ownerShare != 0.0 }

        val totalEarned = contributions.sumOf { it.second.total }
        val teacherShare = contributions.sumOf { it.second.teacherShare }
        val ownerShare = contributions.sumOf { it.second.ownerShare }
        val uniqueSubs = contributions.map { it.first.subscriptionId }.distinct().size

        // Group by group_name
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

    /**
     * Earnings from one-time course sales — always queried straight from `orders`, never mixed
     * with [mySubscriptionEarningsSummary]. A course purchase is a single event on a single day
     * (unlike a subscription's monthly charge), so this needs no proration: the window filters
     * `paid_at` directly and each order's full teacher_amount/platform_amount counts once, on
     * the day it was actually paid.
     */
    suspend fun myCourseEarningsSummary(
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
                    eq("teacher_id", me())
                    eq("item_type", "COURSE")
                    isIn("status", listOf("PAID", "PARTIALLY_REFUNDED"))
                    gte("paid_at", "${periodStart}T00:00:00")
                    lte("paid_at", "${periodEnd}T23:59:59")
                }
            }
            .decodeList<CourseOrderRow>()

        // Net of refunds — see CourseOrderRow.net().
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

    /**
     * Every one of my subscription-earning rows overlapping [from]..[to], paged to the end.
     *
     * [mySubscriptionEarningsSummary] used to sum `mySubscriptionEarnings()` — the newest 100
     * rows — so a teacher with a long subscription history was quietly shown a partial total
     * that looked complete. Filtering by period overlap server-side is both correct and
     * cheaper than downloading the history to discard most of it.
     */
    suspend fun mySubscriptionEarningsInWindow(from: String, to: String): List<SubscriptionEarning> {
        val pageSize = 1000L
        val all = mutableListOf<SubscriptionEarning>()
        var offset = 0L
        while (true) {
            val page = Backend.client.from("subscription_earnings")
                .select {
                    filter {
                        eq("teacher_id", me())
                        // Same overlap test as SubscriptionEarning.overlapFraction.
                        lte("period_start", to)
                        gte("period_end", from)
                    }
                    order("period_start", Order.DESCENDING)
                    range(offset, offset + pageSize - 1)
                }
                .decodeList<SubscriptionEarning>()
            all += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return all
    }

    suspend fun mySubscriptionEarnings(page: Int = 0, pageSize: Int = 100): List<SubscriptionEarning> {
        val offset = page * pageSize
        return Backend.client.from("subscription_earnings")
            .select {
                filter { eq("teacher_id", me()) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(pageSize.toLong())
                range(offset.toLong(), (offset + pageSize - 1).toLong())
            }
            .decodeList()
    }

    suspend fun mySavedReports(): List<SubscriptionEarningsReport> =
        Backend.client.from("subscription_earnings_reports")
            .select {
                filter { eq("teacher_id", me()) }
                order("year", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList()

    /** Pending approval requests for the logged-in teacher. */
    suspend fun myPendingApprovals(): List<ApprovalRequest> {
        val result = runCatching {
            Backend.rpcRaw(
                "list_approval_requests",
                buildJsonObject {
                    put("p_status", "PENDING")
                    put("p_teacher", me())
                },
            )
        }.getOrNull()
        return when (result) {
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
                            reviewedBy = null,
                            reviewedAt = null,
                            reviewNote = null,
                            createdAt = obj["created_at"]?.toString()?.trim('"')?.takeIf { it != "null" },
                            teacherName = null,
                            teacherAvatar = null,
                        )
                    }.getOrNull()
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Shortcut: ledger scoped to the logged-in teacher (for the dashboard).
     * Uses the same query as `ledger()` but is explicitly named for the
     * teacher dashboard so the intent is clear.
     */
    suspend fun myLedger(): List<LedgerEntry> = ledger()

    /**
     * Shortcut: payouts scoped to the logged-in teacher (for the dashboard).
     * Uses the same query as `payouts()` but is explicitly named for clarity.
     */
    suspend fun myPayouts(): List<Payout> = payouts()

}
