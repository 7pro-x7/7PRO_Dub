package com.arena.arabicdub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.arena.arabicdub.domain.DubEvent
import com.arena.arabicdub.domain.DubSettings
import com.arena.arabicdub.domain.DubStage
import com.arena.arabicdub.domain.PipelineResult
import com.arena.arabicdub.domain.Segment
import com.arena.arabicdub.domain.SourceSpec
import com.arena.arabicdub.pipeline.DubbingPipeline
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RunStatus { IDLE, RUNNING, DONE, ERROR }

data class UiState(
    val status: RunStatus = RunStatus.IDLE,
    val stage: DubStage? = null,
    val overall: Float = 0f,
    val stageIndeterminate: Boolean = false,
    val logs: List<String> = emptyList(),
    val resultFile: File? = null,
    val resultIsVideo: Boolean = true,
    val segments: List<Segment> = emptyList(),
    val error: String? = null,
    // إعدادات الدبلجة
    val whisperModel: String = "base",
    val onlineTranslate: Boolean = true,
    val backgroundVolume: Int = 18, // نسبة مئوية
    val burnSubtitles: Boolean = true,
)

class DubViewModel(application: Application) : AndroidViewModel(application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipeline = DubbingPipeline(application)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var runJob: Job? = null

    fun update(transform: (UiState) -> UiState) = _ui.update(transform)

    fun start(spec: SourceSpec) {
        runJob?.cancel()
        val current = _ui.value
        _ui.value = UiState(
            whisperModel = current.whisperModel,
            onlineTranslate = current.onlineTranslate,
            backgroundVolume = current.backgroundVolume,
            burnSubtitles = current.burnSubtitles,
        )
        runJob = scope.launch {
            _ui.update { it.copy(status = RunStatus.RUNNING) }
            try {
                val settings = DubSettings(
                    whisperModel = _ui.value.whisperModel,
                    useOnlineTranslate = _ui.value.onlineTranslate,
                    backgroundVolume = _ui.value.backgroundVolume / 100f,
                    burnSubtitles = _ui.value.burnSubtitles,
                )
                val result: PipelineResult =
                    pipeline.run(spec, settings) { event -> applyEvent(event) }
                _ui.update {
                    it.copy(
                        status = RunStatus.DONE,
                        resultFile = result.outputFile,
                        resultIsVideo = result.isVideo,
                        segments = result.segments,
                        overall = 1f,
                    )
                }
            } catch (e: CancellationException) {
                _ui.value = UiState(
                    whisperModel = current.whisperModel,
                    onlineTranslate = current.onlineTranslate,
                    backgroundVolume = current.backgroundVolume,
                    burnSubtitles = current.burnSubtitles,
                )
            } catch (e: Exception) {
                _ui.update { it.copy(status = RunStatus.ERROR, error = friendlyError(e)) }
            }
        }
    }

    private fun applyEvent(event: DubEvent) {
        when (event) {
            is DubEvent.StageChanged -> {
                val stages = DubStage.values()
                val base = stages.take(stages.indexOf(event.stage)).sumOf { it.weight }
                _ui.update {
                    it.copy(
                        stage = event.stage,
                        stageIndeterminate = false,
                        overall = base.toFloat(),
                    )
                }
            }

            is DubEvent.Progress -> {
                _ui.update { state ->
                    val stage = state.stage ?: return@update state
                    val stages = DubStage.values()
                    val base = stages.take(stages.indexOf(stage)).sumOf { it.weight }
                    state.copy(
                        stageIndeterminate = event.indeterminate,
                        overall = if (event.indeterminate) {
                            state.overall
                        } else {
                            (base + stage.weight * event.stageLocal.coerceIn(0f, 1f)).toFloat()
                        },
                    )
                }
            }

            is DubEvent.Log -> _ui.update { it.copy(logs = (it.logs + event.message).takeLast(80)) }
        }
    }

    fun cancel() {
        pipeline.cancel()
        runJob?.cancel()
    }

    fun reset() {
        runJob?.cancel()
        val current = _ui.value
        _ui.value = UiState(
            whisperModel = current.whisperModel,
            onlineTranslate = current.onlineTranslate,
            backgroundVolume = current.backgroundVolume,
            burnSubtitles = current.burnSubtitles,
        )
    }

    override fun onCleared() {
        scope.cancel()
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("MyMemory", ignoreCase = true) ||
                msg.contains("حد يومي", ignoreCase = true) ->
                "خدمة الترجمة المجانية (MyMemory) غير متاحة حاليًا.\n" +
                    "جرّب إعادة المحاولة بعد قليل، أو فعّل خيار «الترجمة داخل الجهاز» في الإعدادات."
            msg.contains("يوتيوب", ignoreCase = true) ||
                msg.contains("Piped", ignoreCase = true) ->
                "تعذر جلب الفيديو من يوتيوب (الخوادم المجانية أحيانًا تكون مشغولة).\n" +
                    "أعد المحاولة بعد لحظات، أو استخدم ملفًا من جهازك."
            else -> msg
        }
    }
}
