package com.rork.pro.ui.screens.tutor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.rork.pro.character.CharacterRepository
import com.rork.pro.character.TutorCharacter
import com.rork.pro.tutor.AudioPayload
import com.rork.pro.tutor.AvatarFeed
import com.rork.pro.tutor.SpokenClip
import com.rork.pro.tutor.TutorEvent
import com.rork.pro.tutor.AvatarStore
import com.rork.pro.tutor.LineRole
import com.rork.pro.tutor.TranscriptLine
import com.rork.pro.tutor.TutorActivity
import com.rork.pro.tutor.TutorApi
import com.rork.pro.tutor.TutorCorrection
import com.rork.pro.tutor.TutorException
import com.rork.pro.tutor.TutorFailure
import com.rork.pro.tutor.TutorHistoryLine
import com.rork.pro.tutor.TutorMood
import com.rork.pro.tutor.TutorStatus
import com.rork.pro.tutor.TutorTurn
import com.rork.pro.tutor.TutorVoice
import com.rork.pro.tutor.VisemeTrack
import com.rork.pro.tutor.VoiceCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class TutorPhase { LOBBY, CALL, SUMMARY }

/** One exchange of the call, kept for the end-of-call summary. */
data class Exchange(
    val student: String,
    val corrected: String,
    val corrections: List<TutorCorrection>,
    val tutor: String,
    val newWords: List<String>,
)

data class TutorUiState(
    val loading: Boolean = true,
    val status: TutorStatus? = null,
    val loadFailed: Boolean = false,
    val phase: TutorPhase = TutorPhase.LOBBY,
    /** No topic picker any more: the tutor talks about whatever the student wants. */
    val topic: String = "free",
    val level: String = "intermediate",
    val activity: TutorActivity = TutorActivity.IDLE,
    val connecting: Boolean = false,
    val hearingSpeech: Boolean = false,
    val micOn: Boolean = true,
    val captions: Boolean = true,
    val tutorLine: String = "",
    /** Arabic gist of [tutorLine] (on screen only). */
    val tutorHint: String = "",
    val studentLine: String = "",
    /** "high" / "medium" / "low": how sure the tutor is that it heard [studentLine] correctly. */
    val studentConf: String = "high",
    /** Another possible reading of [studentLine] the student can tap to send instead. */
    val studentAlt: String = "",
    /** The sentence being corrected by hand (the student tapped "edit"); null when not editing. */
    val editDraft: String? = null,
    /** Bumps every time editing starts, so the text box reloads the draft. */
    val editTick: Int = 0,
    val lastCorrection: TutorCorrection? = null,
    val lastCorrected: String = "",
    /** The tutor's last answer can be played again ("Replay last reply"). */
    val canReplay: Boolean = false,
    val notice: TutorFailure? = null,
    val silenceHint: Boolean = false,
    val canRetry: Boolean = false,
    val remaining: Int = 0,
    val elapsedSec: Int = 0,
    val exchanges: List<Exchange> = emptyList(),
    /** The whole call in order, tutor and student lines, for the end-of-call conversation view and its export. */
    val transcript: List<TranscriptLine> = emptyList(),
    val modelFile: File? = null,
    val modelProgress: Float? = null,
    /** "male" (Mr. Adam) or "female" (Ms. Sara) — the voice sent to the Worker either way. */
    val voice: String = "male",
    /** Language of the tutor's current line, for caption direction. */
    val tutorLang: String = "en",
    /** Owner-uploaded characters available to talk to, alongside Mr. Adam and Ms. Sara. */
    val characters: List<TutorCharacter> = emptyList(),
    /** Selected custom character, if any — null means Mr. Adam/Ms. Sara by [voice]. */
    val character: TutorCharacter? = null,
    /** Whether the selected custom tutor should use its recorded voice reference. */
    val characterVoiceMode: String = "system",
    /** Owner setting: whether Mr. Adam / Ms. Sara are offered to students. */
    val showMale: Boolean = true,
    val showFemale: Boolean = true,
)

/**
 * The gender to send as `tutor_gender` with every request — read from the exact same persona as
 * `tutor_name` (the selected custom character when there is one, its own [TutorCharacter.voice],
 * never the plain [TutorUiState.voice] toggle on its own), so the two can never disagree.
 */
private val TutorUiState.tutorGender: String get() = character?.voice ?: voice

/**
 * Runs the call as a simple half-duplex loop — listen → hear/think/speak on the Worker → play —
 * so the tutor never hears itself. Every step is cancellable: ending the call, muting the mic or
 * tapping the tutor to interrupt all just cancel the current job.
 */
class TutorViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(TutorUiState())
    val state: StateFlow<TutorUiState> = _state.asStateFlow()

    val feed = AvatarFeed()
    private val capture = VoiceCapture()
    private val voice = TutorVoice(app)
    private var loopJob: Job? = null
    private var speakJob: Job? = null
    private var timerJob: Job? = null
    private var studentName: String? = null
    private var pendingPcm: ByteArray? = null
    private var pendingTyped: String? = null
    /** The voice clips of the tutor's last answer, kept so "Replay last reply" needs no new request. */
    private var lastClips: List<SpokenClip> = emptyList()
    private val prefs by lazy { app.getSharedPreferences("7pro.tutor", Context.MODE_PRIVATE) }
    private var silences = 0
    private val history = ArrayList<TutorHistoryLine>()

    init {
        feed.micLevel = { capture.level }
        feed.clipPositionMs = { voice.positionMs }
        _state.update { it.copy(voice = prefs.getString(KEY_VOICE, "male") ?: "male") }
        refresh()
        viewModelScope.launch {
            val list = runCatching { CharacterRepository.list() }.getOrNull() ?: emptyList()
            val savedId = prefs.getString(KEY_CHARACTER, null)
            val selected = list.find { c -> c.id == savedId }
            val vis = CharacterRepository.defaultsVisible()
            var showMale = vis["male"] ?: true
            var showFemale = vis["female"] ?: true
            // Never leave the student with nobody to talk to.
            if (!showMale && !showFemale && list.isEmpty()) { showMale = true; showFemale = true }
            _state.update {
                it.copy(
                    characters = list, character = selected, characterVoiceMode = selected?.voiceMode ?: "system",
                    // A saved custom character carries its own gender; without this, `voice` would stay
                    // whatever the default Adam/Sara toggle was last set to (e.g. before this character
                    // was ever picked), and every request after an app restart would send a `tutor_name`
                    // and `voice`/`tutor_gender` that no longer agree.
                    voice = selected?.voice ?: it.voice,
                    showMale = showMale, showFemale = showFemale,
                )
            }
            // The saved choice may now be hidden: move to whatever is still offered.
            if (selected == null) {
                val female = _state.value.voice == "female"
                when {
                    female && !showFemale -> if (showMale) setVoice("male") else list.firstOrNull()?.let { selectCharacter(it) }
                    !female && !showMale -> if (showFemale) setVoice("female") else list.firstOrNull()?.let { selectCharacter(it) }
                }
            }
        }
    }

    /** Talk to an owner-uploaded character instead of Mr. Adam / Ms. Sara. */
    fun selectCharacter(c: TutorCharacter?) {
        prefs.edit().putString(KEY_CHARACTER, c?.id).apply()
        _state.update {
            it.copy(
                character = c,
                voice = c?.voice ?: it.voice,
                characterVoiceMode = c?.voiceMode ?: "system",
                modelFile = if (c != null) null else it.modelFile,
            )
        }
        if (c == null) _state.value.status?.let { prefetchModel(it) }
    }

    /** Choose the tutor: male voice + Mr. Adam's face, or female voice + Ms. Sara's face. */
    fun setVoice(v: String) {
        val chosen = if (v == "female") "female" else "male"
        if (chosen == _state.value.voice && _state.value.character == null) return
        prefs.edit().putString(KEY_VOICE, chosen).apply()
        prefs.edit().remove(KEY_CHARACTER).apply()
        _state.update { it.copy(voice = chosen, modelFile = null, character = null, characterVoiceMode = "system") }
        _state.value.status?.let { prefetchModel(it) }
    }

    private val female: Boolean get() = _state.value.voice == "female"

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadFailed = false) }
            val status = runCatching { TutorApi.status() }.getOrNull()
            _state.update { it.copy(loading = false, status = status, loadFailed = status == null, remaining = status?.remaining ?: 0) }
            if (status != null) prefetchModel(status)
        }
    }

    /** Defaults the level from the learner's placement result (A1-A2 beginner, B1 intermediate, B2+ advanced). */
    fun presetLevel(cefr: String?) {
        val level = when (cefr?.uppercase()?.take(2)) {
            "A1", "A2" -> "beginner"
            "B2", "C1", "C2" -> "advanced"
            "B1" -> "intermediate"
            else -> return
        }
        _state.update { it.copy(level = level) }
    }

    fun setTopic(topic: String) = _state.update { it.copy(topic = topic) }
    fun setLevel(level: String) = _state.update { it.copy(level = level) }
    fun toggleCaptions() = _state.update { it.copy(captions = !it.captions) }

    /** A model set in Supabase wins; otherwise (or if its download fails) the one bundled in the app. */
    private fun prefetchModel(status: TutorStatus) {
        val ctx = getApplication<Application>()
        val wantFemale = female
        viewModelScope.launch {
            // A model uploaded in Supabase replaces Mr. Adam; Ms. Sara always uses her bundled model.
            val remote = !wantFemale && status.avatarUrl.isNotBlank()
            var file = if (remote) AvatarStore.cached(ctx, status.avatarVersion) else null
            if (file == null && remote) {
                _state.update { it.copy(modelProgress = 0f) }
                file = AvatarStore.ensure(ctx, status.avatarUrl, status.avatarVersion) { p -> _state.update { it.copy(modelProgress = p) } }
            }
            if (file == null) file = AvatarStore.bundled(ctx, wantFemale)
            if (wantFemale == female) _state.update { it.copy(modelFile = file, modelProgress = null) }
        }
    }

    fun modelFailed() = _state.update { it.copy(modelFile = null) }

    // ------------------------------------------------------------------------------ the call

    fun startCall(name: String?) {
        val status = _state.value.status ?: return
        if (!status.enabled || status.remaining <= 0) return
        studentName = name
        history.clear()
        silences = 0
        pendingPcm = null
        pendingTyped = null
        lastClips = emptyList()
        _state.update {
            it.copy(
                canReplay = false,
                phase = TutorPhase.CALL, connecting = true, activity = TutorActivity.THINKING, micOn = true,
                tutorLine = "", tutorHint = "", studentLine = "", studentConf = "high", studentAlt = "",
                lastCorrection = null, lastCorrected = "", notice = null,
                silenceHint = false, canRetry = false, elapsedSec = 0, exchanges = emptyList(), transcript = emptyList(),
            )
        }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) { delay(1000); _state.update { it.copy(elapsedSec = it.elapsedSec + 1) } }
        }
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            val s = _state.value
            val opening = runCatching {
                TutorApi.open(
                    status.workerUrl, s.topic, s.level, studentName, s.voice,
                    s.characterVoiceMode, s.character?.name, s.character?.id, s.tutorGender,
                )
            }
            (opening.exceptionOrNull() as? CancellationException)?.let { throw it }
            _state.update { it.copy(connecting = false) }
            opening.onSuccess { turn ->
                history.add(TutorHistoryLine("assistant", turn.reply))
                _state.update { it.copy(transcript = it.transcript + TranscriptLine(LineRole.TUTOR, turn.reply, turn.hintAr, atSec = it.elapsedSec)) }
                speak(turn)
            }.onFailure { e ->
                // A greeting that failed is not worth ending the call over: say hello in text and listen.
                if (e is TutorException && e.reason in setOf(TutorFailure.USER_LIMIT, TutorFailure.GLOBAL_LIMIT, TutorFailure.DISABLED, TutorFailure.UNAUTHORIZED)) {
                    fail(e.reason); return@launch
                }
            }
            listenLoop()
        }
    }

    private suspend fun listenLoop() {
        val status = _state.value.status ?: return
        while (currentCoroutineContext().isActive && _state.value.phase == TutorPhase.CALL) {
            if (!_state.value.micOn) return
            if (_state.value.remaining <= 0) { endCall(); return }
            setActivity(TutorActivity.LISTENING)
            val heard = run {
                val watcher = viewModelScope.launch {
                    while (isActive) { _state.update { it.copy(hearingSpeech = capture.hearingSpeech) }; delay(80) }
                }
                // Beginners pause between words: they get a longer silence before the recording is closed.
                try { capture.listenOnce(VoiceCapture.endSilenceFor(_state.value.level)) } finally { watcher.cancel(); _state.update { it.copy(hearingSpeech = false) } }
            }
            when (heard) {
                is VoiceCapture.Result.MicUnavailable -> { _state.update { it.copy(micOn = false, notice = null) }; setActivity(TutorActivity.IDLE); return }
                is VoiceCapture.Result.Silence -> {
                    silences++
                    if (silences >= 2) {
                        _state.update { it.copy(micOn = false, silenceHint = true) }
                        setActivity(TutorActivity.IDLE)
                        return
                    }
                    continue
                }
                is VoiceCapture.Result.Speech -> {
                    silences = 0
                    _state.update { it.copy(silenceHint = false, notice = null, canRetry = false) }
                    pendingPcm = heard.pcm
                    pendingTyped = null
                    if (!sendTurn(status.workerUrl)) return
                }
            }
        }
    }

    /** Sends the pending utterance (or typed line); false when the loop should stop and wait. */
    private suspend fun sendTurn(workerUrl: String): Boolean {
        val pcm = pendingPcm
        val typed = pendingTyped
        if (pcm == null && typed.isNullOrBlank()) return true
        setActivity(TutorActivity.THINKING)
        _state.update {
            it.copy(
                tutorLine = "", tutorHint = "", lastCorrection = null, lastCorrected = "",
                studentLine = typed ?: it.studentLine, studentConf = "high", studentAlt = "",
            )
        }

        var useWav = prefs.getBoolean(KEY_WAV_ONLY, false)
        var attempt = 0
        while (true) {
            val payload = pcm?.let { if (useWav) AudioPayload.wav(it) else AudioPayload.aac(it) ?: AudioPayload.wav(it) }
            val result = runCatching { streamTurn(workerUrl, payload, typed) }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            currentCoroutineContext().ensureActive()
            val failure = (result.exceptionOrNull() as? TutorException)?.reason
                ?: if (result.isFailure) TutorFailure.NETWORK else null
            if (failure == null) break
            if (failure == TutorFailure.AUDIO_FORMAT && !useWav && attempt == 0) {
                // This Worker/model can't read AAC: switch to WAV from now on and resend.
                prefs.edit().putBoolean(KEY_WAV_ONLY, true).apply()
                useWav = true; attempt++
                continue
            }
            if (failure == TutorFailure.NO_SPEECH || failure == TutorFailure.AUDIO_TOO_LONG) {
                pendingPcm = null; pendingTyped = null
                _state.update { it.copy(notice = failure) }
                return true
            }
            fail(failure)
            return false
        }
        pendingPcm = null
        pendingTyped = null
        return true
    }

    /** Streams one turn: plays each sentence as it arrives, then records corrections for the summary. */
    private suspend fun streamTurn(workerUrl: String, payload: AudioPayload?, typed: String?) {
        val s = _state.value
        val clips = Channel<SpokenClip>(Channel.UNLIMITED)
        val player = viewModelScope.launch {
            for (clip in clips) playClip(clip)
        }
        speakJob = player
        var played = 0
        val turnClips = ArrayList<SpokenClip>()
        val turn = try {
            TutorApi.turnStream(
                workerUrl, payload, typed, s.topic, s.level, studentName, history, s.voice,
                s.characterVoiceMode, s.character?.name, s.character?.id, s.tutorGender,
            ) { event ->
                when (event) {
                    is TutorEvent.Heard -> _state.update {
                        it.copy(studentLine = event.transcript, studentConf = event.confidence, studentAlt = event.alt)
                    }
                    is TutorEvent.Hint -> _state.update { it.copy(tutorHint = event.text) }
                    is TutorEvent.Audio -> {
                        _state.update { it.copy(tutorLine = (it.tutorLine + " " + event.text).trim(), tutorLang = "en") }
                        val clip = runCatching {
                            if (event.format == "pcm_s16le") voice.decodePcm(event.mp3, event.sampleRate, event.text)
                            else voice.decode(event.mp3, event.text)
                        }
                        (clip.exceptionOrNull() as? CancellationException)?.let { throw it }
                        clip.getOrNull()?.let { clips.send(it); turnClips.add(it); played++ }
                    }
                }
            }
        } catch (e: Throwable) {
            clips.close()
            player.cancel()
            throw e
        }
        // Arabic replies (and any reply the server could not voice) are spoken by the phone itself.
        if ((turn.speakLocally || played == 0) && turn.reply.isNotBlank()) {
            _state.update { it.copy(tutorLine = turn.reply, tutorLang = turn.lang) }
            if (played == 0) setActivity(TutorActivity.THINKING)
            val local = runCatching { voice.synthesizeLocally(turn.reply, turn.lang, turn.voiceGender == "female") }
            (local.exceptionOrNull() as? CancellationException)?.let { throw it }
            local.getOrNull()?.let { clips.send(it); turnClips.add(it) }
        }
        if (turn.clarify) {
            // The tutor only asked the student to repeat: not a real turn, so nothing is logged or counted.
            clips.close()
            _state.update { it.copy(tutorLine = turn.reply.ifBlank { it.tutorLine }, tutorHint = turn.hintAr, studentLine = "", studentConf = "high", studentAlt = "") }
            player.join()
            return
        }
        clips.close()
        if (turnClips.isNotEmpty()) lastClips = turnClips.toList()
        history.add(TutorHistoryLine("user", turn.transcript))
        history.add(TutorHistoryLine("assistant", turn.reply))
        while (history.size > 8) history.removeAt(0)
        feed.driver.mood = moodOf(turn.mood)
        _state.update {
            it.copy(
                studentLine = turn.transcript,
                tutorLine = turn.reply.ifBlank { it.tutorLine },
                tutorHint = turn.hintAr.ifBlank { it.tutorHint },
                tutorLang = turn.lang,
                remaining = turn.remaining ?: (it.remaining - 1).coerceAtLeast(0),
                exchanges = it.exchanges + Exchange(turn.transcript, turn.corrected, turn.corrections, turn.reply, turn.newWords),
                transcript = it.transcript +
                    TranscriptLine(LineRole.STUDENT, turn.transcript, corrected = if (turn.corrections.isNotEmpty()) turn.corrected else "", atSec = it.elapsedSec) +
                    TranscriptLine(LineRole.TUTOR, turn.reply, turn.hintAr.ifBlank { it.tutorHint }, atSec = it.elapsedSec),
            )
        }
        player.join()
        // The correction card appears only now, after the tutor has finished speaking (or the student
        // interrupted), so it never competes with the voice for the student's attention.
        _state.update { it.copy(lastCorrection = turn.corrections.firstOrNull(), lastCorrected = turn.corrected, canReplay = lastClips.isNotEmpty()) }
    }

    private suspend fun playClip(clip: SpokenClip) {
        feed.visemes = VisemeTrack(clip)
        feed.clip = clip
        setActivity(TutorActivity.SPEAKING)
        try {
            voice.play(clip)
        } finally {
            feed.clip = null
            feed.visemes = null
        }
    }

    private fun moodOf(m: String) = when (m) { "happy" -> TutorMood.HAPPY; "encouraging" -> TutorMood.ENCOURAGING; else -> TutorMood.NEUTRAL }

    /** The student typed instead of speaking (noisy place, shy, or the mic won't cooperate). */
    fun sendTyped(text: String) {
        val status = _state.value.status ?: return
        val line = text.trim().take(400)
        if (line.isEmpty() || _state.value.activity == TutorActivity.THINKING) return
        if (_state.value.editDraft != null) {
            // The corrected sentence replaces the one that was misheard: drop it and the tutor's answer to it.
            if (history.size >= 2 && history[history.size - 2].role == "user") {
                history.removeAt(history.size - 1)
                history.removeAt(history.size - 1)
            }
            _state.update {
                val t = it.transcript
                val dropPair = t.size >= 2 && t[t.size - 2].role == LineRole.STUDENT && t[t.size - 1].role == LineRole.TUTOR
                it.copy(exchanges = it.exchanges.dropLast(1), transcript = if (dropPair) t.dropLast(2) else t, editDraft = null)
            }
        }
        loopJob?.cancel()
        speakJob?.cancel()
        pendingPcm = null
        pendingTyped = line
        silences = 0
        _state.update { it.copy(notice = null, silenceHint = false, canRetry = false) }
        loopJob = viewModelScope.launch {
            if (!sendTurn(status.workerUrl)) return@launch
            if (_state.value.micOn) listenLoop() else setActivity(TutorActivity.IDLE)
        }
    }

    /** The student says the last sentence was not written the way they said it: open the text box with it, ready to fix. */
    fun editLastTranscript() {
        val s = _state.value
        if (s.studentLine.isBlank() || s.activity == TutorActivity.THINKING) return
        _state.update { it.copy(editDraft = it.studentLine, editTick = it.editTick + 1) }
    }

    fun cancelEdit() = _state.update { it.copy(editDraft = null) }

    /** First-run microphone check: true once real speech reaches the phone. */
    suspend fun micCheck(): Boolean {
        val ok = capture.listenOnce() is VoiceCapture.Result.Speech
        if (ok) prefs.edit().putBoolean(KEY_MIC_CHECKED, true).apply()
        return ok
    }

    val micChecked: Boolean get() = prefs.getBoolean(KEY_MIC_CHECKED, false)
    fun micLevel(): Float = capture.level

    private suspend fun speak(turn: TutorTurn) {
        feed.driver.mood = moodOf(turn.mood)
        _state.update { it.copy(tutorLine = turn.reply, tutorHint = turn.hintAr, tutorLang = turn.lang) }
        val decoded = when {
            turn.audioBase64.isNotBlank() && turn.audioFormat == "pcm_s16le" -> runCatching { voice.decodePcm(turn.audioBase64, turn.sampleRate, turn.reply) }
            turn.audioMp3.isNotBlank() -> runCatching { voice.decode(turn.audioMp3, turn.reply) }
            else -> Result.failure(IllegalStateException("no audio"))
        }
        (decoded.exceptionOrNull() as? CancellationException)?.let { throw it }
        // The Worker's own resolved gender for this turn wins here, not the phone's local toggle — it is
        // exactly the gender `tutor_name` was voiced as, even if the phone's own state were ever stale.
        val clip = decoded.getOrNull()
            ?: runCatching { voice.synthesizeLocally(turn.reply, turn.lang, turn.voiceGender == "female") }.getOrNull()
            ?: return
        lastClips = listOf(clip)
        feed.visemes = VisemeTrack(clip)
        feed.clip = clip
        setActivity(TutorActivity.SPEAKING)
        speakJob = viewModelScope.launch { voice.play(clip) }
        try {
            speakJob?.join()
        } finally {
            feed.clip = null
            feed.visemes = null
            _state.update { it.copy(canReplay = true) }
        }
    }

    /**
     * "Replay last reply": plays the tutor's last answer again from the clips already on the phone (no request,
     * no reply used). The microphone is paused while it plays, like during any answer, and listening resumes after.
     */
    fun replayLast() {
        val clips = lastClips
        val s = _state.value
        if (clips.isEmpty() || s.phase != TutorPhase.CALL || s.connecting || s.activity == TutorActivity.THINKING) return
        loopJob?.cancel()
        speakJob?.cancel()
        silences = 0
        _state.update { it.copy(notice = null, silenceHint = false, canRetry = false) }
        loopJob = viewModelScope.launch {
            val player = launch { for (clip in clips) playClip(clip) }
            speakJob = player
            player.join() // a tap on the tutor (interrupt) cancels the player, which also ends the wait
            if (_state.value.phase == TutorPhase.CALL) {
                if (_state.value.micOn) listenLoop() else setActivity(TutorActivity.IDLE)
            }
        }
    }

    /** Tap on the tutor while he speaks: stop and hand the floor to the student. */
    fun interrupt() {
        if (_state.value.activity == TutorActivity.SPEAKING) speakJob?.cancel()
    }

    fun toggleMic() {
        val on = !_state.value.micOn
        _state.update { it.copy(micOn = on, silenceHint = false) }
        if (on) {
            silences = 0
            if (loopJob?.isActive != true) loopJob = viewModelScope.launch { listenLoop() }
        } else {
            if (_state.value.activity == TutorActivity.LISTENING) {
                loopJob?.cancel()
                setActivity(TutorActivity.IDLE)
            }
        }
    }

    fun retry() {
        val status = _state.value.status ?: return
        _state.update { it.copy(notice = null, canRetry = false, micOn = true) }
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            if ((pendingPcm != null || pendingTyped != null) && !sendTurn(status.workerUrl)) return@launch
            listenLoop()
        }
    }

    private fun fail(reason: TutorFailure) {
        when (reason) {
            TutorFailure.USER_LIMIT -> { _state.update { it.copy(remaining = 0) }; endCall() }
            else -> _state.update {
                it.copy(
                    notice = reason,
                    canRetry = reason == TutorFailure.NETWORK || reason == TutorFailure.AI_FAILED,
                    activity = TutorActivity.IDLE,
                    micOn = false,
                )
            }
        }
        feed.driver.activity = TutorActivity.IDLE
    }

    fun endCall() {
        loopJob?.cancel(); speakJob?.cancel(); timerJob?.cancel()
        voice.stop()
        feed.clip = null
        lastClips = emptyList()
        setActivity(TutorActivity.IDLE)
        _state.update { it.copy(phase = TutorPhase.SUMMARY, connecting = false, canReplay = false) }
        refreshQuietly()
    }

    fun backToLobby() {
        _state.update { it.copy(phase = TutorPhase.LOBBY, notice = null) }
        refreshQuietly()
    }

    private fun refreshQuietly() {
        viewModelScope.launch {
            runCatching { TutorApi.status() }.getOrNull()?.let { st -> _state.update { it.copy(status = st, remaining = st.remaining) } }
        }
    }

    private fun setActivity(a: TutorActivity) {
        feed.driver.activity = a
        _state.update { it.copy(activity = a) }
    }

    private companion object {
        const val KEY_WAV_ONLY = "audio_wav_only"
        const val KEY_MIC_CHECKED = "mic_checked"
        const val KEY_VOICE = "tutor_voice"
        const val KEY_CHARACTER = "tutor_character_id"
    }

    override fun onCleared() {
        loopJob?.cancel(); speakJob?.cancel(); timerJob?.cancel()
        voice.stop()
        voice.release()
    }
}
