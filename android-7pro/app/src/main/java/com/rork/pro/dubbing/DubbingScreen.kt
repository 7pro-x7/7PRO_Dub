package com.rork.pro.dubbing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rork.pro.ui.i18n.StrDubbing
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Create + pay for + track a dubbing job.
 *
 * [dubServerBaseUrl] is the same kind of remote-configured URL `TutorApi` already gets for
 * the AI Tutor Worker — fetch it from wherever that config lives (e.g. `app_settings`), do
 * not hardcode it.
 *
 * Payment is intentionally NOT built here: once [quoteAmount] is known, hand off to whatever
 * screen/sheet already collects a Paymob method or a manual-transfer proof for other item
 * types (see CommerceRepository.startCheckout / submitManualPayment) — pass it
 * `itemType = "DUBBING"` and `planId = job.id`, exactly like an AI Tutor plan purchase.
 * On success, call [DubbingRepository.start] and begin polling [DubbingRepository.status].
 */
@Composable
fun DubbingScreen(
    dubServerBaseUrl: String,
    onPay: (jobId: String, amount: Double, currency: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceType by remember { mutableStateOf(DubbingSourceType.YOUTUBE) }
    var direction by remember { mutableStateOf(DubbingDirection.EN_TO_AR) }
    var youtubeUrl by remember { mutableStateOf("") }
    var pickedVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<DubbingJob?>(null) }
    var quoteText by remember { mutableStateOf<String?>(null) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedVideoUri = uri
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr(StrDubbing.title))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = sourceType == DubbingSourceType.YOUTUBE,
                onClick = { sourceType = DubbingSourceType.YOUTUBE }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(tr(StrDubbing.fromYoutube))
            }
            SegmentedButton(selected = sourceType == DubbingSourceType.UPLOAD,
                onClick = { sourceType = DubbingSourceType.UPLOAD }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(tr(StrDubbing.fromPhone))
            }
        }

        if (sourceType == DubbingSourceType.YOUTUBE) {
            OutlinedTextField(
                value = youtubeUrl, onValueChange = { youtubeUrl = it },
                label = { Text(tr(StrDubbing.youtubeLink)) }, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Button(onClick = { pickVideo.launch("video/*") }) {
                Text(pickedVideoUri?.lastPathSegment ?: tr(StrDubbing.pickVideo))
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = direction == DubbingDirection.EN_TO_AR,
                onClick = { direction = DubbingDirection.EN_TO_AR }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(tr(StrDubbing.enToAr))
            }
            SegmentedButton(selected = direction == DubbingDirection.AR_TO_EN,
                onClick = { direction = DubbingDirection.AR_TO_EN }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2)) {
                Text(tr(StrDubbing.arToEn))
            }
        }

        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        quoteText?.let { Text(it) }

        Button(
            enabled = !busy && (sourceType == DubbingSourceType.UPLOAD && pickedVideoUri != null ||
                sourceType == DubbingSourceType.YOUTUBE && youtubeUrl.isNotBlank()),
            onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val seconds = when (sourceType) {
                            DubbingSourceType.YOUTUBE ->
                                DubbingRepository.probeYoutubeDurationSeconds(dubServerBaseUrl, youtubeUrl)
                            DubbingSourceType.UPLOAD ->
                                DubbingRepository.probeLocalDurationSeconds(context, pickedVideoUri!!)
                        }
                        if (seconds <= 0) error(tr(StrDubbing.durationUnreadable))

                        val uploadPath = if (sourceType == DubbingSourceType.UPLOAD) {
                            DubbingRepository.uploadLocalVideo(context, pickedVideoUri!!, jobHint = System.currentTimeMillis().toString())
                        } else null

                        val created = DubbingRepository.createJob(
                            sourceType = sourceType,
                            youtubeUrl = youtubeUrl.takeIf { sourceType == DubbingSourceType.YOUTUBE },
                            uploadStoragePath = uploadPath,
                            direction = direction,
                            sourceSeconds = seconds,
                        )
                        job = created

                        val quote = DubbingRepository.quote(created.id)
                        val amount = quote["total_amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        val currency = quote["currency"]?.jsonPrimitive?.content ?: ""
                        val freeMinutes = quote["free_minutes_applied"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        quoteText = if (amount <= 0.0)
                            trf(StrDubbing.freeTrial, if (freeMinutes % 1.0 == 0.0) freeMinutes.toLong().toString() else freeMinutes.toString())
                        else
                            trf(StrDubbing.cost, if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(java.util.Locale.US, "%.2f", amount), currency)

                        onPay(created.id, amount, currency)
                    } catch (e: Exception) {
                        error = e.message ?: tr(StrDubbing.unexpected)
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text(tr(StrDubbing.getQuote))
        }

        job?.let { j ->
            Text(trf(StrDubbing.jobStatus, j.status.toString()))
        }
    }
}

/**
 * Call after the payment screen reports success (order PAID → job QUEUED server-side).
 * Starts processing, then poll [DubbingRepository.status] (e.g. every 5s) until DONE/FAILED.
 */
suspend fun beginProcessing(dubServerBaseUrl: String, jobId: String, youtubeUrl: String?): DubbingJob =
    DubbingRepository.start(dubServerBaseUrl, jobId, youtubeUrl)
