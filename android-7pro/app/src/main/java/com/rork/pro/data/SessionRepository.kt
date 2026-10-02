package com.rork.pro.data

import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Authentication, the signed-in profile, permissions and notifications. */
object SessionRepository {

    /**
     * Creates an account and signs straight in — 7PRO never asks anyone to confirm an email.
     *
     * Sign-up normally returns a session immediately; if the server hands one back later than
     * expected, the credentials that were just accepted are used to sign in, so the learner is
     * never parked on a "check your inbox" screen.
     */
    suspend fun signUp(email: String, password: String, fullName: String, phone: String?) {
        Backend.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName.trim())
                phone?.takeIf { it.isNotBlank() }?.let { put("phone", it.trim()) }
            }
        }
        if (Backend.auth.currentSessionOrNull() == null) {
            signIn(email, password)
        }
    }

    /**
     * Starts Google sign-in.
     *
     * The browser handles the Google step and returns to the app through the `sevenpro://auth`
     * deep link, where [Backend] picks the session up — so this call finishing only means the
     * browser opened, not that the user is signed in yet.
     *
     * One person always ends up with one account: Google verifies the email address, and the
     * server attaches that identity to the existing account with the same address instead of
     * starting a second one.
     */
    suspend fun signInWithGoogle() {
        Backend.auth.signInWith(Google)
    }

    suspend fun signIn(email: String, password: String) {
        Backend.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun sendPasswordReset(email: String) {
        Backend.auth.resetPasswordForEmail(email.trim())
    }

    suspend fun signOut() {
        Backend.auth.signOut()
    }

    /**
     * The signed-in profile, rebuilt from the account itself when it is missing.
     *
     * Google sign-in creates the account before the app ever sees it, so a first Google sign-in
     * (or a profile that was lost) would otherwise leave someone signed in with nothing to show.
     * Asking the server to restore it also refreshes the name and picture from the identity used.
     */
    suspend fun myProfile(): Profile? {
        val userId = Backend.currentUserId ?: return null
        val existing: Profile? = Backend.client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull()
        if (existing != null) return existing
        return runCatching { Backend.rpc<Profile>("ensure_my_profile") }.getOrNull()
    }

    suspend fun myPermissions(): Set<String> {
        val userId = Backend.currentUserId ?: return emptySet()
        return Backend.client.from("admin_permissions")
            .select { filter { eq("user_id", userId) } }
            .decodeList<AdminPermission>()
            .map { it.permission }
            .toSet()
    }

    suspend fun myTeacherProfile(): TeacherProfile? {
        val userId = Backend.currentUserId ?: return null
        return Backend.client.from("teacher_profiles").select { filter { eq("id", userId) } }.decodeSingleOrNull()
    }

    suspend fun updateProfile(fullName: String, phone: String?, countryCode: String?) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        Backend.client.from("profiles").update(
            buildJsonObject {
                put("full_name", fullName.trim())
                put("phone", phone?.trim())
                put("country_code", countryCode?.trim()?.uppercase())
            },
        ) { filter { eq("id", userId) } }
    }

    /**
     * Stores the profile picture on the account itself, so it follows the person everywhere
     * they appear in the app. Passing null clears it back to the initial badge.
     */
    suspend fun updateAvatar(avatarUrl: String?) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        Backend.client.from("profiles").update(
            buildJsonObject { put("avatar_url", avatarUrl?.ifBlank { null }) },
        ) { filter { eq("id", userId) } }
    }

    suspend fun notificationPreferences(): List<NotificationPreference> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("notification_kind_preferences")
            .select { filter { eq("user_id", userId) } }
            .decodeList()
    }

    suspend fun setNotificationPreference(kind: String, enabled: Boolean) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        Backend.client.from("notification_kind_preferences").upsert(
            buildJsonObject {
                put("user_id", userId)
                put("kind", kind)
                put("enabled", enabled)
            },
        )
    }

    suspend fun notifications(): List<AppNotification> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("notifications")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(60)
            }
            .decodeList()
    }

    suspend fun markNotificationRead(id: Long) {
        val userId = Backend.currentUserId ?: return
        Backend.client.from("notifications").update(
            buildJsonObject { put("read_at", java.time.Instant.now().toString()) },
        ) {
            filter {
                eq("id", id)
                eq("user_id", userId)
            }
        }
    }

    /** Removes a single notification from the signed-in user's own list. */
    suspend fun deleteNotification(id: Long) {
        val userId = Backend.currentUserId ?: return
        Backend.client.from("notifications").delete {
            filter {
                eq("id", id)
                eq("user_id", userId)
            }
        }
    }

    /** Clears every notification belonging to the signed-in user. */
    suspend fun clearNotifications() {
        val userId = Backend.currentUserId ?: return
        Backend.client.from("notifications").delete {
            filter { eq("user_id", userId) }
        }
    }

    suspend fun myTickets(): List<SupportTicket> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("support_tickets")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun createTicket(subject: String, category: String, firstMessage: String) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        val ticket = Backend.client.from("support_tickets")
            .insert(
                buildJsonObject {
                    put("user_id", userId)
                    put("subject", subject)
                    put("category", category)
                },
            ) { select() }
            .decodeSingle<SupportTicket>()
        Backend.client.from("ticket_messages").insert(
            buildJsonObject {
                put("ticket_id", ticket.id)
                put("sender_id", userId)
                put("body", firstMessage)
            },
        )
    }

    suspend fun ticketMessages(ticketId: String): List<TicketMessage> =
        Backend.client.from("ticket_messages")
            .select {
                filter { eq("ticket_id", ticketId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun replyToTicket(ticketId: String, body: String, isStaff: Boolean) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        Backend.client.from("ticket_messages").insert(
            buildJsonObject {
                put("ticket_id", ticketId)
                put("sender_id", userId)
                put("body", body)
                put("is_staff", isStaff)
            },
        )
    }
}
