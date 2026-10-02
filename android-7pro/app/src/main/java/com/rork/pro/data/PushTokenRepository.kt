package com.rork.pro.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.rork.pro.ui.i18n.AppLanguage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PushTokenRepository"

/**
 * Registers/unregisters this device's FCM token against the signed-in account, so
 * `classroom-call-push` (see the Edge Function) knows where to ring an invited student.
 *
 * Every failure here is swallowed on purpose: a push token is a nice-to-have delivery path, not
 * something that should ever block sign-in, sign-out, or any other flow if Firebase/Play
 * services are unavailable (an emulator without Play services, a device that blocked Google
 * services, etc.) — the rest of the app, including the in-app session list, works the same either way.
 */
object PushTokenRepository {

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> cont.resume(token) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /** Called once the account's session is confirmed (see SessionViewModel.loadEverything). */
    suspend fun syncToken() {
        runCatching {
            val token = currentToken() ?: return
            Backend.rpcVoid(
                "register_push_token",
                buildJsonObject {
                    put("p_token", token)
                    put("p_platform", "android")
                    put("p_locale", AppLanguage.current.code)
                },
            )
        }.onFailure { Log.w(TAG, "Failed to sync push token", it) }
    }

    /** Called on sign-out, while the account is still authenticated, so RLS still allows the delete. */
    suspend fun clearToken() {
        runCatching {
            val token = currentToken() ?: return
            Backend.rpcVoid(
                "unregister_push_token",
                buildJsonObject { put("p_token", token) },
            )
        }.onFailure { Log.w(TAG, "Failed to clear push token", it) }
    }

    /** Called by ClassroomFcmService.onNewToken — a token can rotate at any time, not just at sign-in. */
    suspend fun onTokenRefreshed(token: String) {
        if (Backend.currentUserId == null) return
        runCatching {
            Backend.rpcVoid(
                "register_push_token",
                buildJsonObject {
                    put("p_token", token)
                    put("p_platform", "android")
                    put("p_locale", AppLanguage.current.code)
                },
            )
        }.onFailure { Log.w(TAG, "Failed to register refreshed push token", it) }
    }
}
