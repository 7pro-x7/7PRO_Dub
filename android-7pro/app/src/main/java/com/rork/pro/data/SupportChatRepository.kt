package com.rork.pro.data

import io.github.jan.supabase.storage.storage
import kotlin.time.toDuration

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ------------------------------------------------------------------ models

/** One line in a support chat: a message, a team-only note, or an event (claimed / passed on / ended). */
@Serializable
data class SupportMessage(
    val id: Long,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val kind: String = "TEXT",
    @SerialName("from_staff") val fromStaff: Boolean = false,
    val body: String,
    val meta: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isNote: Boolean get() = kind == "NOTE"
    val isEvent: Boolean get() = kind == "EVENT"
    val isImage: Boolean get() = kind == "IMAGE"
    val isFile: Boolean get() = kind == "FILE"
    fun metaText(key: String): String? = runCatching { meta[key]?.jsonPrimitive?.content }.getOrNull()
}

/** A student's own chat as listed on the support home. */
@Serializable
data class MySupportChat(
    val id: String,
    val topic: String = "OTHER",
    val status: String = "WAITING",
    @SerialName("agent_name") val agentName: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("last_sender") val lastSender: String? = null,
    @SerialName("user_unread") val userUnread: Int = 0,
    val rating: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** A row in the team's inbox. */
@Serializable
data class SupportInboxRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val topic: String = "OTHER",
    val status: String = "WAITING",
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("assigned_name") val assignedName: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("last_sender") val lastSender: String? = null,
    @SerialName("staff_unread") val staffUnread: Int = 0,
    val rating: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "—"
}

@Serializable
data class SupportStatus(val online: Boolean = false, val agents: List<String> = emptyList())

@Serializable
data class SupportStaffState(
    val available: Boolean = true,
    val waiting: Int = 0,
    val mine: Int = 0,
    @SerialName("mine_unread") val mineUnread: Int = 0,
    val team: Int = 0,
)

@Serializable
data class SupportTeamMember(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val available: Boolean = true,
    val online: Boolean = false,
)

@Serializable
data class SupportChatUser(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val online: Boolean = false,
)

@Serializable
data class SupportChatCourse(
    val id: String,
    val title: String,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("access_type") val accessType: String? = null,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
}

/** Header data for an open chat, shaped by who is looking. */
@Serializable
data class SupportChatInfo(
    val id: String,
    val topic: String = "OTHER",
    val status: String = "WAITING",
    val rating: Int? = null,
    @SerialName("rating_comment") val ratingComment: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("agent_name") val agentName: String? = null,
    @SerialName("agent_avatar") val agentAvatar: String? = null,
    @SerialName("agent_online") val agentOnline: Boolean = false,
    /** When the other side last read the chat — drives the double tick. */
    @SerialName("other_last_read_at") val otherLastReadAt: String? = null,
    @SerialName("is_staff_view") val isStaffView: Boolean = false,
    val user: SupportChatUser? = null,
    val courses: List<SupportChatCourse> = emptyList(),
) {
    val isClosed: Boolean get() = status == "CLOSED"
    val isWaiting: Boolean get() = status == "WAITING"
}

// ------------------------------------------------------------------ repository

/**
 * Live customer-support chat. Every write is a server function that checks who is asking;
 * reads go through row-level security, so a student can never see the team's internal notes.
 *
 * Delivery: the sender's write is the record; a content-free "changed" ping on the chat's
 * broadcast channel tells the other side to fetch, the Postgres change stream is a second path,
 * and the screens also poll — so nothing depends on one mechanism working.
 */
object SupportChatRepository {

    object Topic {
        const val PAYMENT = "PAYMENT"
        const val COURSE = "COURSE"
        const val ACCOUNT = "ACCOUNT"
        const val OTHER = "OTHER"
        val ALL = listOf(PAYMENT, COURSE, ACCOUNT, OTHER)
    }

    // ---- student ----
    suspend fun status(): SupportStatus = Backend.rpc("support_status")
    suspend fun myChats(): List<MySupportChat> = Backend.rpc("support_my_chats")

    /** Starts a chat, or adds to the one already open. Returns the chat id. */
    suspend fun start(topic: String, body: String): String =
        Backend.rpc<String>(
            "support_start_chat",
            buildJsonObject {
                put("p_topic", topic)
                put("p_body", body)
            },
        )

    suspend fun rate(chatId: String, rating: Int, comment: String?) {
        Backend.rpcVoid(
            "support_rate",
            buildJsonObject {
                put("p_chat", chatId)
                put("p_rating", rating)
                put("p_comment", comment)
            },
        )
    }

    // ---- both sides ----
    suspend fun info(chatId: String): SupportChatInfo =
        Backend.rpc("support_chat_info", buildJsonObject { put("p_chat", chatId) })

    suspend fun messages(chatId: String, afterId: Long = 0): List<SupportMessage> =
        Backend.client.from("support_chat_messages")
            .select {
                filter {
                    eq("chat_id", chatId)
                    if (afterId > 0) gt("id", afterId)
                }
                order("id", Order.ASCENDING)
                limit(500)
            }
            .decodeList()

    suspend fun send(chatId: String, body: String, note: Boolean = false): SupportMessage =
        Backend.rpc(
            "support_send",
            buildJsonObject {
                put("p_chat", chatId)
                put("p_body", body)
                put("p_note", note)
            },
        )

    /** Private bucket for photos and files sent in a support chat (see the attachments migration). */
    const val ATTACHMENT_BUCKET = "support-attachments"
    const val MAX_ATTACHMENT_BYTES: Long = 15L * 1024 * 1024

    /**
     * Uploads a photo or file into `<me>/<chat>/…` and posts it as a message. The server checks
     * the same things as for a text message (my chat or I'm support staff, not closed, rate
     * limit) and refuses a path outside my own folder for this chat.
     */
    suspend fun sendAttachment(chatId: String, file: PickedFile, mime: String?, image: Boolean): SupportMessage {
        val uid = Backend.currentUserId ?: error("UNAUTHORIZED")
        val safe = file.name.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9._-]"), "_").takeLast(80).ifBlank { "file" }
        val path = "$uid/$chatId/${System.currentTimeMillis()}-$safe"
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Backend.client.storage.from(ATTACHMENT_BUCKET).upload(path, file.bytes) { upsert = false }
        }
        return Backend.rpc(
            "support_send_attachment",
            buildJsonObject {
                put("p_chat", chatId)
                put("p_kind", if (image) "IMAGE" else "FILE")
                put("p_path", path)
                put("p_name", file.name)
                put("p_mime", mime)
                put("p_size", file.bytes.size.toLong())
            },
        )
    }

    private val signedCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    /** A short-lived link to an attachment; cached for a while so scrolling doesn't re-sign. */
    suspend fun attachmentUrl(path: String): String {
        val now = System.currentTimeMillis()
        signedCache[path]?.let { (url, until) -> if (until > now) return url }
        val url = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Backend.client.storage.from(ATTACHMENT_BUCKET)
                .createSignedUrl(path, 60.toDuration(kotlin.time.DurationUnit.MINUTES))
        }
        signedCache[path] = url to (now + 50 * 60_000L)
        return url
    }

    suspend fun markRead(chatId: String) {
        runCatching { Backend.rpcVoid("support_mark_read", buildJsonObject { put("p_chat", chatId) }) }
    }

    suspend fun close(chatId: String) {
        Backend.rpcVoid("support_close", buildJsonObject { put("p_chat", chatId) })
    }

    /** Permanently deletes a chat and all its messages: the student's own, or any chat for the team. */
    suspend fun delete(chatId: String) {
        Backend.rpcVoid("support_delete_chat", buildJsonObject { put("p_chat", chatId) })
    }

    // ---- team ----
    suspend fun staffState(): SupportStaffState = Backend.rpc("support_staff_state")

    suspend fun inbox(filter: String, query: String?): List<SupportInboxRow> =
        Backend.rpc(
            "support_inbox",
            buildJsonObject {
                put("p_filter", filter)
                put("p_query", query?.trim()?.takeIf { it.isNotEmpty() })
            },
        )

    suspend fun setAvailable(available: Boolean) {
        Backend.rpcVoid("support_set_available", buildJsonObject { put("p_available", available) })
    }

    suspend fun claim(chatId: String) {
        Backend.rpcVoid("support_claim", buildJsonObject { put("p_chat", chatId) })
    }

    suspend fun transfer(chatId: String, toUserId: String) {
        Backend.rpcVoid(
            "support_transfer",
            buildJsonObject {
                put("p_chat", chatId)
                put("p_to", toUserId)
            },
        )
    }

    suspend fun team(): List<SupportTeamMember> = Backend.rpc("support_team")

    // ---- realtime ----

    object Live {
        /** Content-free: "something changed, fetch". Never carries a message (notes must not leak). */
        const val CHANGED = "changed"
        const val TYPING = "typing"
    }

    fun openChatChannel(chatId: String): RealtimeChannel = Backend.client.channel("support-chat-$chatId")

    fun openInboxChannel(): RealtimeChannel =
        Backend.client.channel("support-inbox-${Backend.currentUserId ?: "anon"}")

    fun messageInserts(channel: RealtimeChannel, chatId: String): Flow<Unit> =
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "support_chat_messages"
            filter("chat_id", FilterOperator.EQ, chatId)
        }.map { }

    fun chatUpdates(channel: RealtimeChannel, chatId: String): Flow<Unit> =
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "support_chats"
            filter("id", FilterOperator.EQ, chatId)
        }.map { }

    /** Any chat the signed-in person can see changing — for the team inbox. */
    fun anyChatChange(channel: RealtimeChannel): Flow<Unit> =
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "support_chats"
        }.map { }

    fun liveFlow(channel: RealtimeChannel, event: String): Flow<JsonObject> = channel.broadcastFlow<JsonObject>(event)

    suspend fun ping(channel: RealtimeChannel, event: String, payload: JsonObject = buildJsonObject { }) {
        runCatching { channel.broadcast(event, payload) }
    }
}
