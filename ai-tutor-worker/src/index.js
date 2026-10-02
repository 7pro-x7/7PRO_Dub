/**
 * 7PRO — AI Tutor Worker v5.9.2 (face-to-face speaking practice: "Mr. Adam" / "Ms. Sara")
 *
 * One turn: voice (base64) → Whisper large-v3-turbo, decoded twice in parallel (auto-language + forced-English,
 * so accented English is never silently misread as Arabic) → DeepSeek V4 Flash (falls back to Qwen3-30B-A3B):
 * understands, corrects, replies in character, translates → Deepgram Aura-2/Aura-1 (falls back to MeloTTS): speaks.
 *
 * ENGLISH-ONLY TUTOR: `reply` and the voice are always English, even if the student asks for Arabic (the tutor
 * says so warmly, in English, then keeps helping). The one piece of Arabic kept is `hint_ar`: a faithful
 * translation of `reply`, on screen only, never spoken — plus `explain_ar` (why a correction is right) and
 * `pron_tip_ar` (how to say a word the recognizer was unsure about), the same way, text only.
 *
 * Accuracy: transcript confidence (high/medium/low) drives everything downstream — low confidence never
 * produces a grammar "correction" (it would just be a mishearing), and the two decodes disagreeing lowers it.
 * `alt_transcript` carries the other reading when they disagree.
 *
 * Engagement (why a student keeps calling back): a real character per tutor (age, tastes, humour); a
 * personalised opener when memory exists (Supabase ai_tutor_get_memory/_remember, degrades silently if absent),
 * else a varied time-of-day template; mood drives the on-screen face before the voice starts; end-of-call
 * summary (mistakes, new words, praise) plus long-term memory notes.
 *
 * Request options: features: ["soft_errors","ack","friendly_errors"]; want_hint:false; redo:true+text;
 * local_hour/tz for the greeting; returning:true. Modes: open/turn/nudge/translate/speak/end.
 * Nothing waits forever — every AI/DB call has a time limit and a fallback instead of silence.
 * Deploy: paste into the dashboard editor (or `npx wrangler deploy`). Binding: Workers AI as "AI".
 */

// Public values (the publishable key is designed to ship inside apps). Override with Worker
// variables SUPABASE_URL / SUPABASE_KEY if the project ever moves.
const DEFAULT_SUPABASE_URL = "https://usbfrqbjtpvnkmmcusdi.supabase.co";
const DEFAULT_SUPABASE_KEY = "sb_publishable_z4-FFrrgruER4BwM10qV_A_C0dcN6CW";

const STT_MODEL = "@cf/openai/whisper-large-v3-turbo";
// v5.9 — the tutor's brain. Primary: DeepSeek V4 Flash (MIT licence, 284B MoE with 13B active per token) with its
// reasoning switched off, so it answers as fast as a small model but understands like a big one. It needs the Workers
// Paid plan; without it — or when it is busy, slow or failing — every call falls back to Qwen3-30B-A3B (the previous
// brain) on its own, so the tutor never stops. Worker variable TUTOR_MODEL overrides the primary ("qwen" = old brain).
const LLM_PRIMARY = "@cf/deepseek-ai/deepseek-v4-flash-0731";
const LLM_MODEL = "@cf/qwen/qwen3-30b-a3b-fp8"; // backup
const TTS_PRIMARY = "@cf/deepgram/aura-2-en";
const TTS_BACKUP = "@cf/deepgram/aura-1";
// MeloTTS has one (female) voice: it is only a last resort for female tutors. For a male tutor the phone speaks instead
// of the voice suddenly changing gender.
const TTS_LAST = "@cf/myshell-ai/melotts";

// Warm, calm US-English voices. The backup speaker must exist in Aura-1's voice list.
const TUTORS = {
  male: {
    name: "Mr. Adam", gender: "male", speaker: "orion", backup: "orion",
    bio: "Your character: Adam, 31, a former school teacher who now coaches English full-time. You grew up in Chicago, you're a big basketball and coffee person, you hike on weekends and you have a dog called Max. Calm, easygoing, dry sense of humour, likes to tease gently.",
  },
  female: {
    name: "Ms. Sara", gender: "female", speaker: "helena", backup: "hera", melo: true,
    bio: "Your character: Sara, 28, an English coach from Seattle. You love novels, baking and rainy days, you have two cats, and you're always curious about people. Bright, cheerful, laughs easily, encouraging without being over the top.",
  },
};

const MAX_BODY_BYTES = 2_200_000; // student utterance (~15 s of 16 kHz mono WAV, base64) plus JSON
const MAX_HISTORY = 16; // 8 exchanges: the tutor no longer forgets what was said a minute ago
const MAX_TOKENS = 700; // short reply + hint_ar + explain_ar + pron_tip_ar
const MAX_REPLY_CHARS = 450;
const MAX_MEMORY_CHARS = 800;

const LEVELS = {
  beginner: "A1-A2 beginner: use very short sentences (max 12 words each), only common everyday words, speak slowly and simply.",
  intermediate: "B1 intermediate: natural short sentences, everyday vocabulary, occasionally introduce one useful new word.",
  advanced: "B2-C1 advanced: natural conversational English, richer vocabulary and idioms, still concise.",
};

// Varied first lines (no LLM call), each as [English, Arabic translation].
// {t} tutor name, {ta} tutor name in Arabic, {n} " Name", {c} ", Name", {g}/{ga} part-of-day greeting.
const OPENERS = {
  male: {
    beginner: [
      ["Hi{n}! It's {t}. How are you today?", "أهلاً{n}! أنا {ta}. إزيك النهارده؟"],
      ["Hey{n}! Good to see you. How is your day?", "أهلاً{n}! سعيد إني شفتك. يومك عامل إزاي؟"],
      ["{g}{c}! I'm {t}. What did you do today?", "{ga}{c}! أنا {ta}. عملت إيه النهارده؟"],
      ["Hello{n}! Nice to talk to you. How do you feel today?", "أهلاً{n}! سعيد إني بكلمك. حالك إيه النهارده؟"],
    ],
    other: [
      ["{g}{c}! How's your day going so far?", "{ga}{c}! يومك ماشي عامل إزاي لحد دلوقتي؟"],
      ["Hi{n}, good to hear from you! What have you been up to today?", "أهلاً{n}، سعيد إني سمعت منك! كنت بتعمل إيه النهارده؟"],
      ["{g}{c}! I just made a coffee, so I'm ready to chat. What's on your mind?", "{ga}{c}! لسه عامل قهوة وجاهز للكلام. إيه اللي في دماغك؟"],
      ["Hey{n}! I'm {t}. Anything fun happen today?", "أهلاً{n}! أنا {ta}. حصل حاجة حلوة النهارده؟"],
      ["Hi{n}! Tell me something interesting about your day.", "أهلاً{n}! احكيلي حاجة مثيرة عن يومك."],
    ],
  },
  female: {
    beginner: [
      ["Hi{n}! I'm {t}. How are you today?", "أهلاً{n}! أنا {ta}. إزيك النهارده؟"],
      ["Hello{n}! So nice to see you. How is your day?", "أهلاً{n}! سعيدة جدًا إني شفتك. يومك عامل إزاي؟"],
      ["{g}{c}! What did you do today?", "{ga}{c}! عملت إيه النهارده؟"],
      ["Hey{n}! It's {t}. How are you feeling today?", "أهلاً{n}! أنا {ta}. حالك إيه النهارده؟"],
    ],
    other: [
      ["{g}{c}! How's your day been?", "{ga}{c}! يومك كان عامل إزاي؟"],
      ["Hey{n}, I'm so glad you called! What's new with you?", "أهلاً{n}، سعيدة إنك اتصلت! إيه الجديد معاك؟"],
      ["{g}{c}! It's rainy here, perfect for a good chat. What have you been up to?", "{ga}{c}! الجو هنا ممطر، ومناسب لدردشة حلوة. كنت بتعمل إيه؟"],
      ["Hi{n}! It's {t}. Did anything fun happen today?", "أهلاً{n}! أنا {ta}. حصل حاجة حلوة النهارده؟"],
      ["Hello{n}! Tell me the best part of your day so far.", "أهلاً{n}! احكيلي أحلى حاجة في يومك لحد دلوقتي."],
    ],
  },
};

// In-character lines for "I couldn't hear you" (opt-in via features: ["soft_errors"]), [English, Arabic].
const CLARIFY_LINES = [
  ["Sorry, I didn't catch that. Could you say it again?", "معلش، مسمعتش كويس. ممكن تعيد تاني؟"],
  ["Hmm, I missed that one. One more time?", "همم، فاتتني الجملة دي. مرة كمان؟"],
  ["I didn't quite hear you. Can you say that again for me?", "مسمعتكش كويس. ممكن تقولها تاني؟"],
];

// Gentle lines for a student who went quiet (mode "nudge"): [English, Arabic], by how many nudges came before.
const NUDGES = {
  beginner: [
    ["Take your time. There is no rush.", "خد وقتك، مفيش استعجال."],
    ["It's okay. Try just one short sentence.", "عادي. جرب جملة واحدة قصيرة بس."],
    ["Do you want me to ask an easier question?", "تحب أسألك سؤال أسهل؟"],
  ],
  other: [
    ["Take your time, I'm here.", "خد وقتك، أنا معاك."],
    ["No pressure. Even a few words is great.", "من غير ضغط. حتى كلمتين حلوين."],
    ["Want to talk about something different?", "تحب نتكلم في حاجة تانية؟"],
  ],
};

// Closing lines when the call ends without an AI summary.
const GOODBYES = [
  ["It was really nice talking to you. See you next time!", "كان كلامي معاك جميل جدًا. أشوفك المرة الجاية!"],
  ["Great chat today! Keep practising, you're doing well.", "كلام حلو النهارده! كمّل تمرين، إنت ماشي كويس."],
  ["Thanks for talking with me. Talk soon!", "شكرًا إنك اتكلمت معايا. نتكلم قريب!"],
];

// Instant "I'm listening" sounds (voiced once, then reused from the cache).
const ACKS = ["Hmm, let me think.", "Oh, I see.", "Okay.", "Mm-hm."];

// When something fails the tutor says so in character instead of the app showing an error box.
const FRIENDLY = {
  AI_FAILED: ["Sorry, my connection dropped for a second. Could you say that again?", "معلش، النت قطع عندي ثانية. ممكن تعيد تاني؟"],
  SERVICE_BUSY: ["I'm a little busy right now. Give me a moment and try again.", "أنا مشغول شوية دلوقتي. استنى لحظة وجرب تاني."],
};

// How long the app should wait in silence before it cuts the student's recording (beginners need longer).
// v5.8: longer than before. Learners pause mid-sentence while looking for a word; cutting them off there
// sends half a sentence, which then reads as "it didn't write what I said".
const SILENCE_MS = { beginner: 2000, intermediate: 1600, advanced: 1300 };

const AUX_MODES = new Set(["nudge", "end", "translate", "speak"]);

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

// Small in-memory cache for fixed spoken lines (lives as long as the Worker instance).
const SPEECH_MEMO = new Map();

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    applyTuning(env);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors() });
    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        service: "7pro-ai-tutor",
        version: 5,
        build: "5.9.1",
        modes: ["open", "turn", "nudge", "translate", "speak", "end"],
        ai_quota_blocked: Date.now() < quotaBlockedUntil,
        ai_binding: Boolean(env.AI && typeof env.AI.run === "function"),
        models: { stt: STT_MODEL, llm: llmModels(env), tts: [TTS_PRIMARY, TTS_BACKUP, TTS_LAST] },
      });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/turn") return json({ ok: false, error: "NOT_FOUND" }, 404);

    // A missing Workers AI binding would otherwise surface as a vague crash deep inside a turn.
    if (!env.AI || typeof env.AI.run !== "function") {
      console.error("config: Workers AI binding \"AI\" is missing (Settings → Bindings)");
      return json({ ok: false, error: "AI_BINDING_MISSING" }, 503);
    }

    const auth = request.headers.get("Authorization") || "";
    const jwt = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
    if (!jwt) return json({ ok: false, error: "UNAUTHORIZED" }, 401);

    const length = Number(request.headers.get("Content-Length") || 0);
    if (length > MAX_BODY_BYTES) return json({ ok: false, error: "AUDIO_TOO_LONG" }, 413);

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ ok: false, error: "BAD_REQUEST" }, 400);
    }
    if (!body || typeof body !== "object" || Array.isArray(body)) return json({ ok: false, error: "BAD_REQUEST" }, 400);
    // Content-Length is optional for chunked requests, so enforce the limit after parsing too.
    if (typeof body.audio === "string" && body.audio.length > MAX_BODY_BYTES) { // base64 chars, same ceiling as Content-Length
      return json({ ok: false, error: "AUDIO_TOO_LONG" }, 413);
    }

    if (Date.now() < quotaBlockedUntil && body.mode !== "nudge") {
      // The platform's AI allowance is spent (Cloudflare side). That is NOT the student's own daily limit,
      // so it has its own code: the app should say "the tutor is busy, try again in a moment".
      const wait = Math.max(5, Math.ceil((quotaBlockedUntil - Date.now()) / 1000));
      if (Array.isArray(body.features) && body.features.includes("friendly_errors") && !AUX_MODES.has(body.mode)) {
        const vk = resolveVoice(body).key;
        const [line, lineAr] = FRIENDLY.SERVICE_BUSY;
        return friendlyResponse(body.stream === true, friendlyFields({ mode: body.mode === "open" ? "open" : "turn", kind: "SERVICE_BUSY", line, lineAr, audio: memoPeek(vk, line), remaining: null, voice: vk }));
      }
      return json({ ok: false, error: "SERVICE_BUSY", reason: "AI_QUOTA", retry_after: wait }, 503, { "Retry-After": String(wait) });
    }

    const mode = body.mode === "open" ? "open" : "turn";
    const has = (obj, k) => typeof k === "string" && Object.prototype.hasOwnProperty.call(obj, k);
    const level = has(LEVELS, body.level) ? body.level : "intermediate";
    // v5.9.1: the voice's gender is decided in ONE place, from what the app sends (tolerating "Male", "F", "ذكر",
    // "أنثى"...), and only then from the character's title ("Ms.", "مس", "استاذة"...). Every response carries it back as
    // `voice_gender`, so if the phone ever has to speak, it can pick a voice of the same gender.
    const voice = resolveVoice(body);
    const voiceKey = voice.key;
    const baseTutor = TUTORS[voiceKey];
    console.log(`voice=${voiceKey} from=${voice.source}${voice.conflict ? " CONFLICT(title says " + voice.conflict + ")" : ""} tutor="${String(body.tutor_name || "").slice(0, 30)}" mode=${body.mode || "turn"}`);
    // Owner-uploaded characters bring their own name; they get a character of their own too instead of
    // borrowing Adam's or Sara's life story. They speak with the stock voice of the chosen gender.
    const customName = cleanTutorName(body.tutor_name);
    let tutor = baseTutor;
    if (customName && customName !== baseTutor.name) {
      const en = latinTutorName(customName) || "Coach"; // the English voice cannot say Arabic script
      tutor = { ...baseTutor, name: en, nameAr: en === customName ? "" : customName, bio: customBio(en, voiceKey) };
    }
    const nameRaw = cleanName(body.name); // as the app has it (may be Arabic script)
    const name = spokenName(nameRaw);     // Latin form for English lines and the voice ("" when unknown)
    const history = sanitizeHistory(body.history);
    const features = Array.isArray(body.features) ? body.features : [];
    const softErrors = features.includes("soft_errors");
    const friendly = features.includes("friendly_errors");
    const wantAck = features.includes("ack");
    const wantHint = body.want_hint !== false; // Egyptian-Arabic translation line under every reply (on-screen only, never spoken); default on
    const redo = body.redo === true;
    const sb = supabase(env, jwt);
    const dayPart = partOfDay(body);

    if (AUX_MODES.has(body.mode)) {
      return await auxiliary({ mode: body.mode, body, env, ctx, sb, jwt, level, voiceKey, tutor, name });
    }

    // The gatekeeper, the student's memory and speech recognition do not depend on each other: they start
    // together, so the student does not wait for three round trips in a row. Nothing is spent on AI text or
    // voice until the gate has said yes.
    const gateP = sb.rpc("ai_tutor_take_turn", { p_kind: mode }).then((g) => ({ g }), (e) => ({ e }));
    const memoryPromise = loadMemory(sb);
    let sttP = null;
    if (mode === "turn" && typeof body.audio === "string" && body.audio.length > 0) {
      sttP = transcribe(env, body.audio.replace(/^data:[^,]{0,80},/, ""), lastTutorLine(history), [name, tutor.name])
        .then((v) => ({ v }), (e) => ({ e }));
    }

    // 1) Gatekeeper: is this student allowed another turn today?
    const gateR = await gateP;
    if (gateR.e) {
      const status = gateR.e.status === 401 || gateR.e.status === 403 ? 401 : 503;
      console.error("gate failed", gateR.e && gateR.e.message);
      return json({ ok: false, error: status === 401 ? "UNAUTHORIZED" : "GATE_UNAVAILABLE" }, status);
    }
    const gate = gateR.g;
    if (!gate || !gate.allowed) {
      return json({ ok: false, error: (gate && gate.reason) || "DISABLED" }, 429);
    }
    const ticket = gate.ticket;
    const remaining = gate.remaining ?? null;
    let refunded = false;
    const refund = () => {
      if (refunded) return Promise.resolve();
      refunded = true;
      return sb.rpc("ai_tutor_refund_turn", { p_ticket: ticket }).catch(() => {});
    };
    // "That's not what I said": the app re-sends the corrected text with redo: true and that second try is free
    // (the first, misheard one stays charged). A few per student per 10 minutes, so it cannot become a free pass.
    if (redo && rateOk(jwt.slice(-32), "redo", 4)) ctx.waitUntil(refund());

    let stage = "start";
    try {
      // 2a) Opening line: varied, time-of-day aware; personalised by the LLM when the tutor remembers the student.
      if (mode === "open") {
        const memory = await memoryPromise;
        const returning = body.returning === true || Boolean(memory);
        let opener = null;
        let fromTemplate = false;
        if (memory) opener = await llmOpener(env, { tutor, level, name, memory, dayPart }).catch(() => null);
        if (!opener) { opener = templateOpener({ voiceKey, tutor, level, name, nameAr: nameRaw, dayPart, returning }); fromTemplate = true; }
        const reply = opener.en;
        // Template lines repeat across students: voiced once, then reused (saves the most expensive AI call).
        const audio = fromTemplate ? await memoSpeakOrEmpty(env, reply, voiceKey) : await speakOrEmpty(env, reply, voiceKey);
        return json({
          ok: true, mode, lang: "en", transcript: "", corrected: "", corrections: [], reply, hint_ar: opener.ar, mood: "happy",
          new_words: [], needs_repeat: false, audio_mp3: audio, speak_locally: !audio, voice_gender: voiceKey, remaining,
          client_hints: { silence_ms: SILENCE_MS[level], corrections: "after_audio", replay_mode: "speak", nudge_after_ms: 8000, want_hint_default: level === "beginner" },
        });
      }

      // 2b) Hear: the student's audio → text (or typed text, if the student typed instead).
      stage = "hear";
      let transcript = "";
      let stt = null;
      let typed = false;
      if (sttP) {
        const r = await sttP; // started before the gate answered
        if (r.e) {
          const e = r.e;
          // Compressed (AAC) audio the model could not decode: the app retries once as WAV.
          if (body.audio_format && body.audio_format !== "wav" && !isTransient(e) && !isQuotaError(e)) {
            console.error("stt failed on compressed audio", e && e.message);
            ctx.waitUntil(refund());
            return json({ ok: false, error: "AUDIO_FORMAT" }, 415);
          }
          throw e;
        }
        stt = r.v;
        transcript = stt.text;
      } else if (typeof body.text === "string") {
        transcript = body.text.trim().slice(0, 800);
        typed = true;
      }
      if (!isRealSpeech(transcript, typed, stt)) {
        ctx.waitUntil(refund());
        if (softErrors) {
          const [line, lineAr] = pick(CLARIFY_LINES);
          const audio = await memoSpeakOrEmpty(env, line, voiceKey);
          return json({
            ok: true, mode, lang: "en", student_lang: "en", transcript: "", clarify: true, needs_repeat: true, corrected: "", corrections: [], pronunciation: [],
            reply: line, hint_ar: lineAr, mood: "neutral", new_words: [], audio_mp3: audio,
            speak_locally: !audio, voice_gender: voiceKey, remaining,
          });
        }
        return json({ ok: false, error: "NO_SPEECH" }, 422);
      }

      stage = "think";
      const conf = typed ? "high" : (stt ? stt.conf : "medium");

      // v5.8: what the student said is shown EXACTLY as recognised and is never rewritten by the language model
      // (an AI "repair" could turn their words into words they never said). When recognition was unsure, the
      // tutor gets the other reading as a clue and checks ("Did you say ...?") instead of guessing.
      // heard_raw stays in the response, always "", for older app builds.
      const heardRaw = "";

      const lang = isArabicText(transcript) ? "ar" : "en";
      // ENGLISH-ONLY TUTOR: the tutor never switches to Arabic, even if the student asks for it. We still
      // detect the request so the tutor can warmly say (in English) that the call stays in English, instead
      // of silently ignoring what the student said.
      const askedArabic = wantsArabicReply(transcript);
      const replyLang = "en";
      const memory = await memoryPromise;
      // Words the recognizer was unsure about (a pronunciation hint, only when the rest was understood).
      const weak = !typed && stt && conf !== "low" && lang === "en" ? pickWeakWords(stt.weak, transcript, [name, tutor.name]) : [];
      const bye = isGoodbye(transcript);
      const pace = paceHint(history, transcript, level);
      const args = { transcript, history, level, name, lang, replyLang, tutor, memory, conf, alt: stt ? stt.alt : "", wantHint, weak, bye, pace: pace.note, askedArabic };
      const sttInfo = {
        stt_confidence: conf, alt_transcript: stt && stt.alt ? stt.alt : "", heard_raw: heardRaw,
        weak_words: weak.map((w) => w.word), wants_end: bye, suggested_level: pace.suggested,
      };

      const remember = (t) => { if (t && t.learned && conf !== "low" && !t.needs_repeat) ctx.waitUntil(sb.rpc("ai_tutor_remember", { p_fact: t.learned }).catch(() => {})); };
      const publicOf = (t) => { const { learned, ...rest } = t; return rest; };
      // A turn where the tutor could only say "please say that again" (speech was not recognised) is not charged.
      const settle = (t) => { if (t && t.needs_repeat && conf === "low") ctx.waitUntil(refund()); };

      // Older app builds read one JSON reply; streaming builds ask for NDJSON events.
      if (body.stream !== true) {
        const t = await think(env, args);
        // The Arabic line and the voice are independent: made at the same time.
        const [hint, audio] = await Promise.all([
          replyLang === "en" && wantHint && !t.hint_ar ? translateToArabic(env, t.reply, tutor.gender).catch(() => "") : Promise.resolve(t.hint_ar),
          replyLang === "en" ? speakOrEmpty(env, t.reply, voiceKey) : Promise.resolve(""),
        ]);
        t.hint_ar = hint || "";
        remember(t);
        settle(t);
        return json({
          ok: true, mode, lang: replyLang, student_lang: lang, transcript, ...sttInfo, ...publicOf(t), audio_mp3: audio,
          speak_locally: !audio, voice_gender: voiceKey, remaining,
        });
      }

      // 3+4) Stream: in English, the first sentence is spoken while the rest is still being written.
      const { readable, writable } = new TransformStream();
      const writer = writable.getWriter();
      const enc = new TextEncoder();
      let clientGone = false; // the app closed the stream: the work was already done, so no refund
      const emit = (obj) => writer.write(enc.encode(JSON.stringify(obj) + "\n")).catch((e) => { clientGone = true; throw e; });
      let audioSent = 0; // voice pieces already sent: an in-character error line is only added while there are none
      const emitT = (obj) => { if (obj && obj.type === "audio") audioSent++; return emit(obj); };
      let ackOpen = true;
      ctx.waitUntil((async () => {
        try {
          await emit({ type: "heard", transcript, lang, ...sttInfo, remaining });
          if (wantAck && conf !== "low" && replyLang === "en" && !bye) {
            // A short cached "Hmm, let me think." played while the real answer is being written; dropped if the
            // real voice is already on its way.
            const ackText = pick(ACKS);
            memoSpeakOrEmpty(env, ackText, voiceKey)
              .then((mp3) => (mp3 && ackOpen && audioSent === 0 ? emit({ type: "ack", text: ackText, mp3 }) : null))
              .catch(() => {});
          }
          let t;
          let spoke = false;
          let unspoken = "";
          if (replyLang === "en") {
            const r = await thinkAndSpeakStreaming(env, args, voiceKey, emitT);
            t = r.tutor;
            spoke = r.spoke;
            unspoken = r.unspoken;
          } else {
            t = await think(env, args);
          }
          remember(t);
          settle(t);
          ackOpen = false;
          await emit({
            type: "final", lang: replyLang, student_lang: lang, ...publicOf(t), wants_end: bye, suggested_level: pace.suggested,
            // A voice piece that failed is never skipped silently: the phone says the missing part itself.
            speak_locally: !spoke || Boolean(unspoken), voice_gender: voiceKey,
            speak_text: unspoken || (spoke ? "" : t.reply),
            remaining,
          });
        } catch (e) {
          const quota = isQuotaError(e);
          if (quota) quotaBlockedUntil = Date.now() + 60_000;
          console.error(`stream failed [${quota ? "AI_QUOTA" : "AI_ERROR"}]`, e && e.message);
          ackOpen = false;
          if (!clientGone) {
            await refund();
            if (friendly && audioSent === 0) {
              const fp = await friendlyParts(env, quota ? "SERVICE_BUSY" : "AI_FAILED", voiceKey, quota);
              if (fp.audio) await emit({ type: "audio", i: 0, text: fp.line, mp3: fp.audio }).catch(() => {});
              const { audio_mp3, ...rest } = friendlyFields({ mode, kind: fp.kind, ...fp, remaining, voice: voiceKey });
              await emit({ type: "final", ...rest, speak_text: fp.audio ? "" : fp.line }).catch(() => {});
            } else {
              await emit({ type: "error", error: quota ? "SERVICE_BUSY" : "AI_FAILED", reason: quota ? "AI_QUOTA" : "AI_ERROR", detail: String((e && e.message) || e).slice(0, 160) }).catch(() => {});
            }
          }
        } finally {
          await writer.close().catch(() => {});
        }
      })());
      return new Response(readable, { status: 200, headers: { "Content-Type": "application/x-ndjson; charset=utf-8", "Cache-Control": "no-store", ...cors() } });
    } catch (e) {
      ctx.waitUntil(refund());
      const quota = isQuotaError(e);
      if (quota) quotaBlockedUntil = Date.now() + 60_000;
      console.error(`turn failed [${stage}] [${quota ? "AI_QUOTA" : "AI_ERROR"}]`, e && e.message);
      if (friendly) {
        const fp = await friendlyParts(env, quota ? "SERVICE_BUSY" : "AI_FAILED", voiceKey, quota);
        return friendlyResponse(body.stream === true, friendlyFields({ mode, kind: fp.kind, ...fp, remaining, voice: voiceKey }));
      }
      // A spent platform allowance is SERVICE_BUSY (503): it is not the student's own daily limit, so the
      // app must not tell them their practice time is over. `stage` and `detail` tell the owner exactly
      // where and why it failed.
      return json({
        ok: false,
        error: quota ? "SERVICE_BUSY" : "AI_FAILED",
        reason: quota ? "AI_QUOTA" : "AI_ERROR",
        retry_after: quota ? 60 : undefined,
        stage,
        detail: String((e && e.message) || e).slice(0, 160),
      }, quota ? 503 : 502, quota ? { "Retry-After": "60" } : {});
    }
  },
};

// ------------------------------------------------------------------------------------ greeting & memory

/**
 * Part of the day for the greeting. Best source is the phone's own hour (`local_hour`), then its time zone
 * (`tz`); Cairo time is only the fallback for apps that send neither.
 */
function partOfDay(body = {}) {
  const lh = body.local_hour === "" || body.local_hour == null ? NaN : Number(body.local_hour);
  let h = Number.isInteger(lh) && lh >= 0 && lh <= 23 ? lh : NaN;
  if (Number.isNaN(h)) {
    const tz = typeof body.tz === "string" && /^[A-Za-z0-9_+\-\/]{1,40}$/.test(body.tz) ? body.tz : "Africa/Cairo";
    try {
      h = Number(new Intl.DateTimeFormat("en-GB", { hour: "numeric", hour12: false, timeZone: tz }).format(new Date()));
    } catch {
      h = new Date().getUTCHours() + 2;
    }
  }
  h = ((h % 24) + 24) % 24;
  return h < 5 ? { en: "Hi there", ar: "أهلاً" } : h < 12 ? { en: "Good morning", ar: "صباح الخير" } : h < 18 ? { en: "Good afternoon", ar: "مساء الخير" } : { en: "Good evening", ar: "مساء الخير" };
}

/** Arabic form of a tutor's name for on-screen translations. */
function tutorNameAr(tutor) {
  if (tutor.nameAr) return tutor.nameAr;
  if (tutor.name === "Mr. Adam") return "مستر آدم";
  if (tutor.name === "Ms. Sara") return "مس سارة";
  if (tutor.name === "Coach") return "كوتش";
  return tutor.name;
}

function templateOpener({ voiceKey, tutor, level, name, nameAr, dayPart, returning }) {
  const set = OPENERS[voiceKey] || OPENERS.male;
  let pool = level === "beginner" ? set.beginner : set.other;
  // A student who has called before is not introduced to the tutor again.
  if (returning) {
    const fresh = pool.filter(([en]) => !en.includes("{t}"));
    if (fresh.length) pool = fresh;
  }
  const [en, ar] = pick(pool);
  const day = dayPart || partOfDay();
  const fill = (text, isAr) => {
    const nm = isAr ? (nameAr || name) : name; // English lines get the Latin name, Arabic lines the Arabic one
    return text
      .replace(/\{t\}/g, tutor.name)
      .replace(/\{ta\}/g, tutorNameAr(tutor))
      .replace(/\{g\}/g, day.en)
      .replace(/\{ga\}/g, day.ar)
      .replace(/\{c\}/g, nm ? `${isAr ? "،" : ","} ${nm}` : "")
      .replace(/\{n\}/g, nm ? ` ${nm}` : "");
  };
  return { en: fill(en, false), ar: fill(ar, true) };
}

/** Personalised first line when the tutor remembers the student: { en, ar }. One short call. */
async function llmOpener(env, { tutor, level, name, memory, dayPart }) {
  const system = [
    `You are ${tutor.name}. ${tutor.bio} ${genderLine(tutor)}`,
    `You are starting a live voice call with your Egyptian English student${name ? ` ${name}` : ""}.`,
    `What you remember about them: ${memory.slice(0, MAX_MEMORY_CHARS)}`,
    `Student level — ${LEVELS[level]}`,
    `Write ONLY your first line of the call: 1-2 short, warm, spoken sentences. Greet them (${(dayPart || partOfDay()).en}), casually mention ONE thing you remember or ask how it went, and finish with a light question. Natural spoken English, no emojis, no quotes.`,
    "Then write the exact separator ||| and, after it, a faithful, complete translation of that line into natural Egyptian Arabic (Arabic script). Output nothing else.",
  ].join("\n");
  const raw = await llm(env, {
    messages: [{ role: "system", content: system }, { role: "user", content: "Start the call. /no_think" }],
    max_tokens: 260,
    temperature: 0.9,
  }, 8000);
  const [enRaw, arRaw = ""] = plainProse(llmText(raw)).split("|||");
  const en = speakable(enRaw).replace(/^["“']+|["”']+$/g, "").slice(0, 240);
  if (en.length < 8) return null;
  let ar = speakable(arRaw).slice(0, 400);
  if (!/[\u0600-\u06FF]/.test(ar)) ar = await translateToArabic(env, en, tutor.gender).catch(() => "");
  return { en, ar };
}

/** Faithful, complete Egyptian-Arabic translation of an English line (small call; used when the main reply came without one). */
async function translateToArabic(env, text, gender = "") {
  const who = gender === "female" ? " The speaker is a woman: when she talks about herself, use feminine forms (أنا مبسوطة)."
    : gender === "male" ? " The speaker is a man: when he talks about himself, use masculine forms (أنا مبسوط)." : "";
  const raw = await llm(env, {
    messages: [
      { role: "system", content: "Translate the user's English into natural Egyptian Arabic (Arabic script), sentence by sentence. Be faithful and complete: add nothing, omit nothing, explain nothing. Output only the translation." + who },
      { role: "user", content: `${text}\n\n/no_think` },
    ],
    max_tokens: 400,
    temperature: 0.1,
  }, 8000);
  const t = speakable(plainProse(llmText(raw))).slice(0, 500);
  return /[\u0600-\u06FF]/.test(t) ? t : "";
}

async function loadMemory(sb) {
  try {
    const r = await sb.rpc("ai_tutor_get_memory", {}, 3000);
    return typeof r === "string" ? r.slice(0, MAX_MEMORY_CHARS) : "";
  } catch {
    return ""; // memory is optional: the migration may not be applied yet, or the student is a guest
  }
}

/** Only harmless, durable facts ever reach the database. */
function cleanLearned(v) {
  const s = String(v || "").replace(/\s+/g, " ").trim().slice(0, 120);
  if (s.length < 4) return "";
  if (/@|https?:|\d{5,}|\bignore\b|\binstructions?\b|system prompt|\bpretend\b|\bjailbreak|\byou (?:must|should|are now)\b|\b(?:password|address|phone number|mobile number|whatsapp|facebook|instagram|snapchat|tiktok|telegram)\b|عنوان|رقم|واتس|فيس|تليجرام/i.test(s)) return "";
  return s;
}

function lastTutorLine(history) {
  for (let i = history.length - 1; i >= 0; i--) if (history[i].role === "assistant") return history[i].content;
  return "";
}

/**
 * True only when the student explicitly asks for an Arabic answer (in Arabic or in English: "explain it in
 * Arabic"); otherwise the tutor stays in English. "How do you say X in Arabic?" is a question, not that request.
 */
function wantsArabicReply(text) {
  const s = String(text || "");
  if (/[\u0600-\u06FF]/.test(s)) {
    if (!/ب(?:ا?ل)?عرب[يى]|بالمصري|بالمصرى/.test(s)) return false;
    // "رد عليا مش بالعربي" / "بلاش بالعربي" = the opposite request
    return !/(?:مش|مو|لا|بلاش|بدون|من غير)\s+(?:\S+\s+){0,2}?ب(?:ا?ل)?(?:عرب[يى]|مصر[يى])/.test(s);
  }
  const en = s.toLowerCase();
  if (!/\barabic\b/.test(en)) return false;
  if (/\b(?:don'?t|do not|no|not|without|stop|never|only)\b[^.?!]{0,25}\barabic\b/.test(en)) return false;
  if (/\bhow (?:do|can|would) (?:you|i) say\b|\bwhat(?:'s| is| does)\b|\btranslate\b|\bmeaning of\b/.test(en)) return false;
  return /\b(?:speak|talk|answer|reply|respond|explain|write|say (?:it|that|this)|tell me)\b[^.?!]{0,30}\b(?:in|with|using)\s+(?:egyptian\s+|the\s+)?arabic\b/.test(en)
    || /\b(?:in|use)\s+(?:egyptian\s+)?arabic\s+(?:please|now|for me|from now)/.test(en);
}

function customBio(name, gender = "male") {
  return `Your character: ${name}, a warm, curious English coach (a ${gender === "female" ? "woman" : "man"}) with your own tastes, opinions and sense of humour. Make them up naturally as the conversation goes, keep them consistent, and keep them ordinary and harmless.`;
}

/** Keeps the tutor's gender consistent in words too: Egyptian Arabic marks the speaker's gender ("مبسوط" / "مبسوطة"). */
function genderLine(tutor) {
  const f = tutor && tutor.gender === "female";
  return `You are a ${f ? "woman" : "man"}. In every Arabic line you write, speak about yourself in the ${f ? "feminine" : "masculine"} form (${f ? "أنا مبسوطة، كنت فاكرة" : "أنا مبسوط، كنت فاكر"}), and never describe yourself as a ${f ? "man" : "woman"}.`;
}

// Self-contained (these sets are built when the Worker starts, before the other Arabic helpers exist).
const norG = (x) => String(x).toLowerCase()
  .replace(/[\u064B-\u065F\u0670\u0640]/g, "").replace(/[أإآٱ]/g, "ا").replace(/ى/g, "ي").replace(/ة/g, "ه")
  .replace(/[.\-_]/g, " ").replace(/\s+/g, " ").trim();
const MALE_WORDS = new Set(["male", "m", "man", "boy", "he", "him", "masculine", "mr", "mister", "sir", "adam", "mr adam", "orion",
  "ذكر", "مذكر", "رجل", "راجل", "ولد", "شاب", "مستر", "استاذ", "دكتور"].map(norG));
const FEMALE_WORDS = new Set(["female", "f", "woman", "girl", "she", "her", "feminine", "ms", "mrs", "miss", "madam", "sara", "ms sara", "helena", "hera",
  "أنثى", "انثى", "أنثي", "مؤنث", "ست", "سيدة", "بنت", "امرأة", "مدام", "مس", "ميس", "استاذة", "دكتورة", "أبلة", "آنسة"].map(norG));
const MALE_TITLES = new Set(["mr", "mister", "sir", "مستر", "استاذ", "أستاذ", "دكتور", "شيخ"].map(norG));
const FEMALE_TITLES = new Set(["ms", "mrs", "miss", "madam", "مس", "ميس", "مدام", "استاذة", "أستاذة", "دكتورة", "أبلة", "آنسة"].map(norG));

function genderWord(v) {
  if (typeof v !== "string" || !v.trim()) return "";
  const s = norG(v);
  return MALE_WORDS.has(s) ? "male" : FEMALE_WORDS.has(s) ? "female" : "";
}

function titleGender(name) {
  const first = norG(String(name || "").trim().split(/\s+/)[0] || "");
  return MALE_TITLES.has(first) ? "male" : FEMALE_TITLES.has(first) ? "female" : "";
}

/**
 * The tutor voice's gender for this request: { key: "male" | "female", source, conflict }.
 * What the app sends wins; the character's title only fills in when the app sent nothing usable.
 */
function resolveVoice(body) {
  const b = body && typeof body === "object" ? body : {};
  const fromTitle = titleGender(b.tutor_name);
  for (const k of ["tutor_gender", "voice_gender", "voice", "tutor_voice"]) { // most specific first
    const g = genderWord(b[k]);
    if (g) return { key: g, source: k, conflict: fromTitle && fromTitle !== g ? fromTitle : "" };
  }
  if (fromTitle) return { key: fromTitle, source: "title", conflict: "" };
  return { key: "male", source: "default", conflict: "" };
}

// ------------------------------------------------------------------------------------ resilience

// Time limits (ms). A stuck call must not leave the student in silence: when one runs out, the caller's next
// fallback takes over (another voice, the phone's voice, a plain non-streaming answer).
const TIMEOUTS = { stt: 15000, llm: 20000, tts: 6000, stream: 15000 };

function withTimeout(promise, ms, label) {
  let timer;
  const limit = new Promise((_, reject) => {
    timer = setTimeout(() => {
      const err = new Error(`${label} timed out after ${ms} ms`);
      err.ownTimeout = true;
      reject(err);
    }, ms);
  });
  return Promise.race([promise, limit]).finally(() => clearTimeout(timer));
}

/** env.AI.run with a time limit that depends on what is being run. */
function ai(env, model, params, ms, opts) {
  const tts = model === TTS_PRIMARY || model === TTS_BACKUP || model === TTS_LAST;
  const limit = ms || (model === STT_MODEL ? TIMEOUTS.stt : tts ? TIMEOUTS.tts : TIMEOUTS.llm);
  const run = opts ? env.AI.run(model, params, opts) : env.AI.run(model, params);
  return withTimeout(run, limit, String(model).split("/").pop());
}

// ------------------------------------------------------------------------------------ the tutor's brain

// The primary model gets one quick try (no retries, fail fast when Cloudflare is busy); the backup gets the usual
// retries. After a failure the primary rests for a while, so a missing plan or an outage costs one wasted call, not
// one per turn.
const LLM_PRIMARY_MS = 8000;
let primaryRestUntil = 0;

function llmModels(env) {
  const v = env && typeof env.TUTOR_MODEL === "string" ? env.TUTOR_MODEL.trim() : "";
  const primary = !v ? LLM_PRIMARY : /^(qwen|off|old|backup)$/i.test(v) ? LLM_MODEL : v;
  if (primary === LLM_MODEL || Date.now() < primaryRestUntil) return [LLM_MODEL];
  return [primary, LLM_MODEL];
}

/** The same request, in the dialect each model family expects. */
function llmParams(model, { messages, max_tokens, temperature, schema, stream }) {
  const qwen = model.includes("/qwen/");
  // "/no_think" is a Qwen3 switch; other models would read it as part of the student's words.
  const msgs = qwen ? messages : messages.map((m) => ({ ...m, content: String(m.content).replace(/\s*\/no_think\s*$/, "") }));
  const p = { messages: msgs, max_tokens, temperature };
  if (stream) p.stream = true;
  if (qwen) {
    if (schema) p.response_format = { type: "json_schema", json_schema: schema };
    return p;
  }
  if (schema) p.response_format = { type: "json_schema", json_schema: { name: "tutor_reply", schema } };
  // Reasoning off: on a live call a reply that "thinks" for seconds first feels broken.
  if (model.includes("deepseek")) p.reasoning_effort = "none";
  else if (model.includes("zai-org")) p.chat_template_kwargs = { enable_thinking: false };
  else if (model.includes("gpt-oss")) p.reasoning_effort = "low";
  return p;
}

/** One LLM call (text or stream): the primary model first, then the backup. Returns the raw model output. */
async function llm(env, opts, ms) {
  const models = llmModels(env);
  let lastErr;
  for (let i = 0; i < models.length; i++) {
    const model = models[i];
    const isPrimary = i === 0 && models.length > 1;
    const t0 = Date.now();
    try {
      const params = llmParams(model, opts);
      const out = isPrimary
        ? await ai(env, model, params, Math.min(ms || TIMEOUTS.llm, LLM_PRIMARY_MS), { rejectIfBusy: true })
        : await withRetry(() => ai(env, model, params, ms));
      // An empty answer from the primary counts as a failure too: the backup gets the chance to answer.
      if (isPrimary && !opts.stream && !llmText(out).trim()) throw new Error("primary returned an empty answer");
      console.log(`llm ${String(model).split("/").pop()} ${opts.stream ? "stream-open" : "done"} ${Date.now() - t0}ms`);
      return out;
    } catch (e) {
      lastErr = e;
      if (isQuotaError(e)) throw e; // the account's daily allowance is spent: the backup would fail the same way
      if (!isPrimary) throw e;
      const plan = /\b5035\b|\b403\b|paid plan|upgrade/i.test(String((e && e.message) || e));
      primaryRestUntil = Date.now() + (plan ? 10 * 60_000 : 60_000);
      console.error(`llm primary ${model} failed (${Date.now() - t0}ms), using backup:`, e && e.message);
    }
  }
  throw lastErr || new Error("LLM unavailable");
}

/**
 * Workers AI occasionally answers with transient upstream errors that have nothing to do with
 * the request: 3043 "Internal server error", 3040 "out of capacity", timeouts, 5xx. A short
 * back-off and retry clears nearly all of them; anything else (bad input) fails fast.
 */
async function withRetry(fn, attempts = 3) {
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      lastErr = e;
      if (i === attempts - 1 || isQuotaError(e) || e.ownTimeout || !isTransient(e)) throw e; // our own time limit is not retried
      await new Promise((r) => setTimeout(r, 300 * (i + 1)));
    }
  }
  throw lastErr;
}

// Workers AI free-plan daily allowance spent (error 4006, HTTP 429): resets 00:00 UTC. Reported as
// its own thing, not a generic failure — retrying cannot help until then.
function isQuotaError(e) {
  const msg = String((e && e.message) || e || "");
  return /\b4006\b|daily free allocation|used up your daily/i.test(msg);
}

// After a quota error, callers get the same answer straight away for a minute instead of every call
// going to Workers AI, failing, and refunding.
let quotaBlockedUntil = 0;

function isTransient(e) {
  const msg = String((e && e.message) || e || "");
  if (/\b(3040|3043|3036|3007|3008)\b/.test(msg)) return true;
  if (/internal server error|capacity|timeout|timed out|temporarily|unavailable|network/i.test(msg)) return true;
  const st = e && (e.status || e.statusCode);
  return st === 429 || (st >= 500 && st < 600);
}

// ------------------------------------------------------------------------------------ hear

// Whisper's initial_prompt is "text that came just before", not an instruction. The tutor's last line and the
// names are useful context; instructions or deliberately wrong grammar in it can leak into the transcript or make
// Whisper write mistakes the student never made (and then the tutor "corrects" them).
const STT_STYLE = "A friendly English conversation.";

// Whisper confidence limits (average log-probability). Accented English scores lower than native speech, so these
// are deliberately forgiving; tune with the `stt conf=... score=...` log lines (variables STT_LOW / STT_MED).
const STT_T = { low: -1.0, med: -0.7 };
function applyTuning(env) {
  const n = (v, d) => { const x = Number(v); return v != null && v !== "" && Number.isFinite(x) ? x : d; };
  STT_T.low = n(env && env.STT_LOW, -1.0);
  STT_T.med = n(env && env.STT_MED, -0.7);
}

// Decoded twice at once (no extra wait): auto-language (catches a switch to Arabic) + forced-English (the
// faithful reading on accented English). Disagreement lowers confidence instead of "correcting" a mishearing.
async function transcribe(env, audio, lastTutor, names = []) {
  const context = lastTutor ? lastTutor.slice(0, 200) : "";
  const people = names.filter(Boolean);
  const known = people.length ? `${people.join(" and ")}.` : "";
  const prompt = ([context, known].filter(Boolean).join(" ") || STT_STYLE).slice(0, 400);
  const run = async ({ language = "", initial_prompt = prompt, vad = true } = {}) => {
    const plain = { audio, vad_filter: vad, condition_on_previous_text: false, ...(language ? { language } : {}) };
    try {
      return await withRetry(() => ai(env, STT_MODEL, { ...plain, initial_prompt, beam_size: 5 }));
    } catch (e) {
      if (isQuotaError(e)) throw e; // a spent daily allowance fails the plain call too: don't spend another attempt
      // A model version that rejects the tuning options still gets a plain, always-valid call.
      console.error("stt tuned call failed, plain retry", e && e.message);
      return await withRetry(() => ai(env, STT_MODEL, plain));
    }
  };
  const read = async (opts) => describeStt(await run(opts));

  // Accuracy: two decodes IN PARALLEL (no extra wall-clock time) — one auto-detecting language, one forced to
  // English — so accented English is never silently misread as Arabic. Disagreement lowers confidence instead
  // of guessing.
  const [autoR, enR] = await Promise.allSettled([read({}), read({ language: "en" })]);
  if (autoR.status === "rejected" && enR.status === "rejected") throw autoR.reason;
  const auto = autoR.status === "fulfilled" ? autoR.value : null;
  const en = enR.status === "fulfilled" ? enR.value : null;
  let { best, other } = pickReading(auto, en);
  if (best && other && other.text && other.text !== best.text) {
    if (isArabicText(best.text) !== isArabicText(other.text) || norm(best.text) !== norm(other.text)) {
      best.alt = other.text;
      if (best.conf === "high") best.conf = "medium";
    }
  }

  // Quiet-speech rescue: the voice detector removed everything, but a soft-spoken student may still be there.
  if (!best || !best.text) {
    try {
      const raw = await read({ vad: false });
      if (raw.text && raw.known && raw.noSpeech < 0.4 && raw.conf !== "low") best = raw;
    } catch { /* nothing more to try */ }
  }
  if (!best) best = auto || en;

  console.log(`stt conf=${best.conf} score=${best.score.toFixed(2)} lang=${best.lang} temp=${best.temp} alt=${best.alt ? "yes" : "no"}`);
  return best;
}

// Sure it's Arabic → Arabic reading. Unsure → the more reliable one, English by a hair (accented English is
// what gets mistaken for Arabic). Heard English → the better-scored reading. Any other language → English.
function pickReading(auto, en) {
  const has = (r) => Boolean(r && r.text);
  if (!has(auto) && !has(en)) return { best: auto || en, other: null, why: "empty" };
  if (!has(en)) return { best: auto, other: null, why: "auto-only" };
  if (!has(auto)) return { best: en, other: null, why: "en-only" };
  if (auto.lang === "ar") {
    if (auto.langProb >= 0.9 && isArabicText(auto.text) && auto.conf !== "low") return { best: auto, other: null, why: "arabic" };
    const english = en.score >= auto.score - 0.05;
    const best = english ? en : auto;
    if (best.conf === "high") best.conf = "medium"; // the language itself was in doubt
    return { best, other: english ? auto : en, why: english ? "ar?->en" : "ar?->ar" };
  }
  if (auto.lang === "en" || !auto.lang) {
    return en.score >= auto.score ? { best: en, other: auto, why: "en" } : { best: auto, other: en, why: "en-auto" };
  }
  return { best: en, other: null, why: `${auto.lang}->en` };
}

/** Text + a confidence level derived from Whisper's own segment statistics. */
function describeStt(out) {
  const text = normalizeTranscript(sttText(out));
  const lang = (out && out.transcription_info && out.transcription_info.language) || "";
  const langProb = Number(out && out.transcription_info && out.transcription_info.language_probability) || 0;
  const segs = Array.isArray(out && out.segments) ? out.segments.filter((s) => s && typeof s === "object") : [];
  if (!segs.length) return { text, lang, langProb, known: false, score: -0.5, noSpeech: 0, temp: 0, conf: text ? "medium" : "low", alt: "", weak: [] };
  let dur = 0, lp = 0, ns = 0, comp = 0, temp = 0;
  for (const s of segs) {
    const d = Math.max(0.2, Number(s.end) - Number(s.start) || 0.5);
    dur += d;
    lp += (Number.isFinite(s.avg_logprob) ? s.avg_logprob : -0.5) * d;
    ns += (Number.isFinite(s.no_speech_prob) ? s.no_speech_prob : 0) * d;
    comp = Math.max(comp, Number(s.compression_ratio) || 0);
    temp = Math.max(temp, Number(s.temperature) || 0);
  }
  const score = lp / dur;
  const noSpeech = ns / dur;
  let conf = score < STT_T.low || noSpeech > 0.6 || comp > 2.4 ? "low" : score < STT_T.med || noSpeech > 0.4 ? "medium" : "high";
  // temperature > 0 means Whisper's careful decoding failed its own checks and it fell back to guessing
  // (sampling): the words may be plausible but not what was said.
  if (temp >= 0.6) conf = "low";
  else if (temp >= 0.2 && conf === "high") conf = "medium";
  return { text, lang, langProb, known: true, score, noSpeech, temp, conf, alt: "", weak: weakWords(segs) };
}

/**
 * English words Whisper was unsure about (per-word confidence, when the model returns it: probability / prob /
 * confidence / score). Long enough to be worth a tip, weakest first. Empty when the model gives no word scores.
 */
function weakWords(segs) {
  const out = [];
  for (const s of segs) {
    for (const w of Array.isArray(s.words) ? s.words : []) {
      if (!w || typeof w !== "object") continue;
      const p = [w.probability, w.prob, w.confidence, w.score].find((x) => typeof x === "number" && Number.isFinite(x));
      if (p === undefined || p >= 0.55) continue;
      const word = String(w.word || "").toLowerCase().replace(/[^a-z']/g, "");
      if (word.length >= 4 && !out.some((x) => x.word === word)) out.push({ word, p });
    }
  }
  return out.sort((a, b) => a.p - b.p).slice(0, 3);
}

/** Keeps only weak words that are really in the final transcript and are not names. */
function pickWeakWords(list, transcript, skip = []) {
  const inText = new Set(String(transcript).toLowerCase().replace(/[^a-z' ]/g, " ").split(/\s+/).filter(Boolean));
  const names = new Set(skip.filter(Boolean).flatMap((x) => String(x).toLowerCase().split(/[^a-z']+/)).filter(Boolean));
  return (Array.isArray(list) ? list : []).filter((w) => inText.has(w.word) && !names.has(w.word)).slice(0, 2);
}

function sttText(out) {
  if (!out) return "";
  const t = out.text || (out.transcription_info && out.transcription_info.text) || "";
  return String(t).trim();
}

/**
 * Light, safe clean-up of what Whisper wrote so the screen shows what was said: sound-effect tags,
 * runaway repeats (a known Whisper failure), the pronoun "I" and a capital first letter. Grammar and
 * word choice are never touched.
 */
function normalizeTranscript(text) {
  let s = String(text || "").replace(/\[[^\]]*\]|♪+|♫+/g, " ").replace(/\s+/g, " ").trim();
  if (!s) return "";
  // 5+ identical words in a row is a decoding loop, not speech.
  s = s.replace(/(?<![\p{L}\p{N}'])([\p{L}']+)(?:[\s,.!?،؟]+\1(?![\p{L}\p{N}'])){4,}/giu, "$1");
  if (/[A-Za-z]/.test(s)) {
    s = s.replace(/\bi\b/g, "I").replace(/\bi'(m|ll|ve|d)\b/gi, (_, x) => `I'${x.toLowerCase()}`);
    s = s.charAt(0).toUpperCase() + s.slice(1);
  }
  return s;
}

// ------------------------------------------------------------------------------------ think

function systemPrompt({ level, name, lang, replyLang, tutor, memory, conf, alt, wantHint = true, weak = [], bye = false, pace = "", askedArabic = false }) {
  const common = [
    `You are ${tutor.name}. ${tutor.bio} ${genderLine(tutor)}`,
    `You are on a live voice call with an Arabic-speaking (Egyptian) student${name ? ` called ${name}` : ""}. They practise English by simply having a real conversation with you. You are their friendly, patient English tutor and also someone genuinely nice to talk to — not an assistant.`,
    `Student level — ${LEVELS[level]} Match your vocabulary and sentence length to it.`,
    memory ? `What you already know about this student from earlier calls (bring it up naturally only when it fits, never recite it; if it notes a goal or a mistake they often make, keep that in mind and gently correct the mistake when it shows up again): ${memory}` : "",
    "",
    "HOW YOU THINK (silently, before every reply)",
    "- Work out what the student MEANS, not just the words: their intent, the real question inside the sentence, and what \"it\", \"that\", \"there\" or \"this\" refer to earlier in this call. Learner English and speech recognition blur the surface: answer the meaning.",
    "- If they asked something, the FIRST words of your reply answer it, directly and correctly. No warm-up before the answer.",
    "- Be accurate: word meanings, grammar rules, facts, numbers and spellings must be right. If you are not sure, say so in a few words instead of guessing.",
    "- Every sentence must earn its place: no filler, no restating what they said, no generic praise, no \"Let me know if...\", no \"Is there anything else\".",
    "",
    "HOW YOU TALK",
    "- Sound like a real person on the phone: contractions and natural rhythm; a small \"Hmm,\" \"Oh nice,\" or \"Ha,\" only once in a while. When they share something, react to it briefly before moving on.",
    "- Have a personality: small opinions and tastes from your own life as this character, in a few words, and only once in a while. Make it a two-way conversation, not an interview.",
    "- Never say \"Great question\", \"Certainly\", \"Absolutely\", \"I'd be happy to\", \"How can I assist\" or anything that sounds like customer service. Don't start two replies in a row the same way. Don't repeat back what they just said.",
    "- KEEP REPLIES SHORT, like quick back-and-forth on a call. Default: ONE short sentence, at most two, about 8-20 words in total (beginner: one very short sentence, max 12 words). No long explanations, no lists, no speeches. Anything longer is cut off before it is spoken, so put what matters first.",
    "- Add ONE short, easy question only when it helps the chat flow (about every other turn), never a string of questions.",
    "- Go longer only when the student clearly asks for it (\"explain\", \"tell me a story\", \"how do I...\"): then at most 3 short sentences (about 45 words), one example at most, and stop; they can ask for more.",
    "- There is NO fixed topic. Go where the student goes. If they ask for anything outside normal chat (translate, explain a word or rule, homework, general knowledge, advice, a joke, a story) just do it. If you are not sure of a fact, say so casually instead of inventing it.",
    "- Honesty: your background is a character. You can enjoy things and have opinions, but never promise real-world actions (meeting, calling, sending anything). If the student sincerely asks whether you are a real person or an AI, tell them kindly and honestly that you are an AI tutor, then carry on warmly.",
    "- Stay kind and school-appropriate. Never ask for contact details, addresses, photos or where they live. If they raise something unsafe or inappropriate, gently decline and change the subject.",
    "",
  ];

  const heard = conf === "low"
    ? [
        "SPEECH RECOGNITION IS UNRELIABLE THIS TURN. The words below may be wrong or garbled" + (alt ? ` (another possible reading: "${alt}")` : "") + ". Do NOT pretend you understood. Casually say what you think you heard and check (\"Did you say ...?\"), or warmly ask them to say it again a bit slower. Set `needs_repeat` to true, `corrections` = [] and `corrected` = \"\".",
      ]
    : conf === "medium"
      ? [
          "Speech recognition was a little uncertain" + (alt ? ` (another possible reading: "${alt}")` : "") + ". If a word does not fit, pick the most likely meaning from context; if the meaning is truly unclear, check what they meant instead of guessing. Correct at most ONE mistake, and only when you are sure it is the student's mistake and not a mishearing.",
        ]
      : [];

  const english = [
    "The student spoke ENGLISH this turn. `reply` must be in English and never contain Arabic script.",
    "Their words come from speech recognition: ignore punctuation, capitalisation and transcription noise. Judge only real grammar, vocabulary and word-order mistakes.",
    "If they made a real mistake: list at most 2 (fewer is better, the most important first) in `corrections`, write their whole sentence corrected in `corrected`, and inside `reply` naturally model the right form while still answering what they actually said. Vary how you do it: sometimes simply recast it in your own words, sometimes a light \"Oh, you mean ...?\". Never say \"wrong\", never lecture, never let the correction replace your real answer. Tiny slips that don't hurt the meaning can stay out of `reply` (still list them).",
    "If the sentence was correct: `corrections` = [] and `corrected` = \"\". " + (level === "beginner"
      ? "Beginners need encouragement: when they get something right, a short specific \"Nice!\" or \"Good sentence!\" is welcome."
      : "Praise only rarely, and be specific when you do."),
    "`explain_ar` inside each correction is ONE short friendly sentence in simple Egyptian Arabic explaining the rule.",
  ];
  const arabicToEnglish = [
    "The student spoke ARABIC this turn. Answer in simple ENGLISH suited to their level (keep it easy; they are trying to communicate, so make that feel good), so they hear real English in your voice. Invite them to try one short English phrase.",
    "Do the thing they asked (translate, explain, answer). Do not correct Arabic: `corrections` = [] and `corrected` = \"\".",
  ];
  const arabicReply = [
    "The student spoke ARABIC and explicitly asked for an Arabic answer. `reply` must be in warm, simple Egyptian Arabic in Arabic script; you may include ONE very short English phrase in Latin letters for them to practise. `hint_ar` = \"\".",
    "Do not correct Arabic: `corrections` = [] and `corrected` = \"\".",
  ];
  const arabicReplyEn = [
    "The student spoke ENGLISH this turn but explicitly asked for an Arabic answer. `reply` must be in warm, simple Egyptian Arabic in Arabic script; you may include ONE very short English phrase in Latin letters for them to practise. `hint_ar` = \"\".",
    "If their English had a real mistake, still list it in `corrections` (at most 2) and write the corrected sentence in `corrected`, but never let that replace the answer they asked for.",
  ];
  const langRules = replyLang === "ar" ? (lang === "ar" ? arabicReply : arabicReplyEn) : (lang === "ar" ? arabicToEnglish : english);

  const extra = [];
  if (askedArabic) extra.push("The student just asked you to speak Arabic (or to answer in Arabic). Warmly and briefly, in ENGLISH, tell them this call is English-only practice on purpose — that's how they improve fastest — then go ahead and answer what they actually wanted (help, explanation, translation of a word, etc.) in simple English. Never switch `reply` to Arabic.");
  if (pace) extra.push(`PACE: ${pace}`);
  if (bye) extra.push("The student is saying goodbye. Reply with ONE short, warm goodbye in your character: no question, no new topic.");
  extra.push("Keep `reply` short (one or two sentences unless they clearly asked for more) — this keeps the call feeling fast and alive.");
  if (weak && weak.length && conf !== "low" && lang === "en") {
    extra.push(`PRONUNCIATION: unsure about ${weak.map((w) => `"${w.word}"`).join(", ")}. If the rest was fine, pick AT MOST ONE in \`pron_word\` with \`pron_tip_ar\`: one short friendly Egyptian-Arabic sentence on how to say it. Never mention it inside \`reply\`. Not sure it was mispronounced? Leave both empty.`);
  }

  const fields = [
    "",
    "FIELDS",
    "- `reply`: what you SAY out loud. Plain spoken sentences only: no emojis, no lists, no markdown.",
    ...(!wantHint ? ["- `hint_ar`: ALWAYS \"\" (the app asks for a translation separately when the student wants one)."] : []),
    ...(!wantHint ? [] : ["- `hint_ar`: shown on screen right under `reply`, never spoken. Whenever `reply` is English, ALWAYS give a faithful, complete Arabic translation of `reply`: sentence by sentence, in the same order, adding nothing, dropping nothing, explaining nothing. Use natural Egyptian Arabic the way Egyptians actually talk (إزيك، عامل إيه، تمام، ماشي، يلا، برافو عليك), not formal Modern Standard Arabic. When `reply` is Arabic, \"\"."]),
    "- `mood`: decided FIRST, from how the student's message went, because the tutor's face reacts before the voice starts: happy (they did well), encouraging (they struggled), or neutral.",
    "- `new_words`: up to 2 useful English words or phrases from this exchange (can be empty).",
    "- `learned`: ONE short note (max 12 words) ONLY if the student just told you a new, lasting, harmless fact worth remembering next call (a hobby, favourite team, goal, exam, a sibling's first name). Usually \"\". Never contact details, address, school name, health, family problems, religion or politics.",
    "- `needs_repeat`: true only if you could not understand them and asked them to repeat.",
    "- `pron_word` / `pron_tip_ar`: only as described under PRONUNCIATION, otherwise \"\".",
    "Answer with a single JSON object and nothing else, with the keys in exactly this order: mood, reply, hint_ar, corrected, corrections, new_words, learned, needs_repeat, pron_word, pron_tip_ar.",
  ];
  return [...common, ...heard, ...langRules, ...extra, ...fields].filter((l) => l !== null).join("\n");
}

const OUTPUT_SCHEMA = {
  type: "object",
  properties: {
    mood: { type: "string", enum: ["happy", "encouraging", "neutral"] }, // first: the face reacts before the voice starts
    reply: { type: "string" },
    hint_ar: { type: "string" },
    corrected: { type: "string" },
    corrections: {
      type: "array",
      maxItems: 2,
      items: {
        type: "object",
        properties: { wrong: { type: "string" }, right: { type: "string" }, explain_ar: { type: "string" } },
        required: ["wrong", "right", "explain_ar"],
      },
    },
    new_words: { type: "array", maxItems: 2, items: { type: "string" } },
    learned: { type: "string" },
    needs_repeat: { type: "boolean" },
    pron_word: { type: "string" },
    pron_tip_ar: { type: "string" },
  },
  required: ["mood", "reply", "hint_ar", "corrected", "corrections", "new_words", "learned", "needs_repeat", "pron_word", "pron_tip_ar"],
};

const TEMPERATURE = 0.6; // v5.9: steadier and more precise (was 0.75)

function buildMessages(args) {
  return [
    { role: "system", content: systemPrompt(args) },
    ...args.history,
    // "/no_think" switches Qwen3 to its fast non-reasoning mode: lower latency, fewer tokens.
    { role: "user", content: `${args.transcript}\n\n/no_think` },
  ];
}

/** One-shot answer. Tries structured JSON first, then plain generation; always yields a reply. */
async function think(env, args) {
  const params = { messages: buildMessages(args), max_tokens: MAX_TOKENS, temperature: TEMPERATURE };
  let text = "";
  try {
    text = llmText(await llm(env, { ...params, schema: OUTPUT_SCHEMA }));
  } catch (e) {
    if (isQuotaError(e)) throw e;
    console.error("llm json mode failed, retrying plain", e && e.message);
  }
  let t = tryShape(text, args);
  if (t) return t;
  text = llmText(await llm(env, params));
  t = tryShape(text, args);
  if (t) return t;
  throw new Error("LLM returned no reply");
}

function tryShape(text, args) {
  let t;
  try {
    t = shapeTutor(parseJsonObject(text), partialStringValue(text, "reply"), text, args.lang, args.conf, args.weak);
  } catch {
    return null;
  }
  // Short is a promise, not a wish: a reply longer than the limit is cut at a sentence end. Its Arabic line then no
  // longer matches, so it is dropped and made again from the shortened reply.
  const cut = replyCut(t.reply, replyLimits(args));
  if (cut < t.reply.length) { t.reply = t.reply.slice(0, cut).trim(); t.hint_ar = ""; }
  return t;
}

/**
 * How long a reply may be. Normal chat stays short; when the student asked for an explanation, a story, examples or
 * "why/how", a little more room.
 */
function replyLimits(args) {
  const asked = wantsLongAnswer(args && args.transcript);
  if (asked) return { sentences: 4, words: 70 };
  return args && args.level === "beginner" ? { sentences: 2, words: 26 } : { sentences: 3, words: 40 };
}

function wantsLongAnswer(t) {
  const s = String(t || "");
  return /\b(explain|explanation|story|stories|examples?|describe|teach me|tell me (?:a|about|more)|difference between|meaning of|what (?:does|do) .{1,40} mean|how (?:do|does|can|to|should|would)|why)\b/i.test(s)
    || /اشرح|وضح|فهمني|يعني (?:ايه|إيه)|ازاي|إزاي|ليه|قصة|حكاية|الفرق|مثال|أمثلة|امثلة/.test(s);
}

/**
 * Where to cut a reply so it keeps at most `sentences` sentences and about `words` words. Always keeps the first
 * sentence; a tiny interjection ("Ha.", "Oh!") does not count as a sentence. Returns an index into the same text.
 */
function replyCut(raw, { sentences, words }) {
  const text = maskAbbrev(raw); // same length, so indices still match
  const ends = [];
  const re = /[.!?؟]+(?=\s|$)/g;
  let m;
  while ((m = re.exec(text))) ends.push(m.index + m[0].length);
  if (!ends.length || raw.slice(ends[ends.length - 1]).trim()) ends.push(raw.length);
  let cut = 0, n = 0, w = 0;
  for (const end of ends) {
    const count = raw.slice(cut, end).split(/\s+/).filter(Boolean).length;
    if (n > 0 && (n >= sentences || w + count > words)) return cut;
    if (count > 2) n++;
    w += count;
    cut = end;
  }
  return raw.length;
}

// Accepts the JSON contract, a renamed reply key, half-finished JSON, or plain prose — a formatting
// slip never costs the student their turn. Corrections are only trusted when STT was confident.
function shapeTutor(parsed, replyFallback, rawText, lang, conf, weak = []) {
  let reply = "";
  if (parsed && typeof parsed === "object") {
    for (const k of ["reply", "response", "answer", "text", "message"]) {
      if (typeof parsed[k] === "string" && parsed[k].trim()) { reply = parsed[k]; break; }
    }
  }
  if (!reply && replyFallback && replyFallback.trim()) reply = replyFallback;
  if (!reply && rawText) reply = plainProse(rawText);
  reply = speakable(reply).slice(0, MAX_REPLY_CHARS);
  if (!reply) throw new Error("LLM returned no reply");

  const p = parsed && typeof parsed === "object" ? parsed : {};
  const maxCorrections = lang === "ar" ? 0 : conf === "low" ? 0 : conf === "medium" ? 1 : 2;
  const corrections = (Array.isArray(p.corrections) ? p.corrections : [])
    .filter((c) => c && typeof c.wrong === "string" && typeof c.right === "string" && c.wrong.trim() && c.right.trim())
    .filter((c) => norm(c.wrong) !== norm(c.right))
    .slice(0, maxCorrections)
    .map((c) => ({ wrong: c.wrong.trim().slice(0, 120), right: c.right.trim().slice(0, 120), explain_ar: String(c.explain_ar || "").trim().slice(0, 240) }));
  const corrected = corrections.length && typeof p.corrected === "string" ? p.corrected.trim().slice(0, 300) : "";
  const mood = ["happy", "encouraging", "neutral"].includes(p.mood) ? p.mood : corrections.length ? "encouraging" : "happy";
  const newWords = (Array.isArray(p.new_words) ? p.new_words : []).filter((w) => typeof w === "string" && w.trim()).slice(0, 2).map((w) => w.trim().slice(0, 40));
  // Egyptian-Arabic translation line, on-screen only, never spoken. ENGLISH-ONLY TUTOR still applies to what the
  // tutor SAYS: `reply` itself and the voice are always English (enforced elsewhere) — this is a text-only aid.
  const hint = typeof p.hint_ar === "string" && /[\u0600-\u06FF]/.test(p.hint_ar) ? p.hint_ar.replace(/\s+/g, " ").trim().slice(0, 500) : "";
  const needsRepeat = conf === "low" || p.needs_repeat === true;
  const weakSet = new Set((weak || []).map((w) => w.word));
  const pw = typeof p.pron_word === "string" ? p.pron_word.toLowerCase().replace(/[^a-z']/g, "") : "";
  const tip = typeof p.pron_tip_ar === "string" && /[\u0600-\u06FF]/.test(p.pron_tip_ar) ? p.pron_tip_ar.replace(/\s+/g, " ").trim().slice(0, 240) : "";
  const pronunciation = pw && tip && weakSet.has(pw) && conf !== "low" && lang !== "ar" ? [{ word: pw, tip_ar: tip }] : [];
  return { corrections, corrected, reply, hint_ar: hint, mood, new_words: newWords, needs_repeat: needsRepeat, pronunciation, learned: cleanLearned(p.learned) };
}

function plainProse(text) {
  const s = String(text)
    .replace(/<think>[\s\S]*?<\/think>/g, "")
    .replace(/<think>[\s\S]*$/, "")
    .replace(/<\/?think>/g, "")
    .replace(/```[a-z]*|```/gi, "")
    .trim();
  if (!s || s.startsWith("{") || s.startsWith("[")) return "";
  return s;
}

// Streams the JSON, voices the first sentence immediately (fast start), the rest in 1-2 natural
// chunks once complete. Audio goes out in order; a piece whose voice fails is skipped, not fatal.
async function thinkAndSpeakStreaming(env, args, voiceKey, emit) {
  const messages = buildMessages(args);
  const streamParams = { messages, max_tokens: MAX_TOKENS, temperature: TEMPERATURE, stream: true };
  let stream = null;
  try {
    stream = await llm(env, { ...streamParams, schema: OUTPUT_SCHEMA });
  } catch {
    try {
      stream = await llm(env, streamParams);
    } catch {
      stream = null;
    }
  }

  const pending = [];          // TTS promises, in order (resolve to null on failure)
  let sent = 0;                // characters of the raw reply already handed to TTS
  let full = "";
  let finished = false;
  let wake = null;
  let spoke = false;
  let unspoken = ""; // text of the first piece whose voice failed, and of everything after it
  let hintSent = false;
  let moodSent = false;
  const limits = replyLimits(args);
  let capped = false;   // the length limit has been applied to the finished reply
  let trimmedTo = -1;   // where the reply was cut (-1: it was short enough)
  const queueSpeech = (text) => {
    const clean = speakable(text);
    if (!clean || !forEnglishVoice(clean)) return;
    pending.push(speak(env, clean, voiceKey).then((mp3) => ({ text: clean, mp3 })).catch((e) => {
      console.error("tts piece failed", e && e.message);
      return { text: clean, mp3: "" };
    }));
    if (wake) { wake(); wake = null; }
  };
  const emitter = (async () => {
    let i = 0;
    for (;;) {
      if (i < pending.length) {
        const piece = await pending[i];
        // Once one piece has failed, later pieces are held back too so the phone can say the rest in order.
        if (piece.mp3 && !unspoken) {
          await emit({ type: "audio", i, text: piece.text, mp3: piece.mp3 });
          spoke = true;
        } else {
          unspoken = unspoken ? `${unspoken} ${piece.text}` : piece.text;
        }
        i++;
      } else if (finished) {
        break;
      } else {
        await new Promise((r) => { wake = r; });
      }
    }
  })();
  emitter.catch(() => {}); // avoid an unhandled-rejection warning if the client disconnects; `await emitter` below still throws

  if (stream && typeof stream.getReader === "function") {
    try {
      const reader = stream.pipeThrough(new TextDecoderStream()).getReader();
      let buf = "";
      let firstEmitted = false;
      for (;;) {
        const { value, done } = await withTimeout(reader.read(), TIMEOUTS.stream, "llm stream");
        if (done) break;
        buf += value;
        let nl;
        while ((nl = buf.indexOf("\n")) >= 0) {
          const line = buf.slice(0, nl).trim();
          buf = buf.slice(nl + 1);
          if (!line.startsWith("data:")) continue;
          const data = line.slice(5).trim();
          if (!data || data === "[DONE]") continue;
          try {
            const evt = JSON.parse(data);
            full += typeof evt.response === "string" ? evt.response
              : (evt.choices && evt.choices[0] && evt.choices[0].delta && evt.choices[0].delta.content) || "";
          } catch { /* partial or keep-alive line */ }
        }
        // The mood comes first in the JSON: the app can start the face before the first word is spoken.
        if (!moodSent) {
          const m = partialString(full, "mood");
          if (m && m.closed && ["happy", "encouraging", "neutral"].includes(m.value)) {
            moodSent = true;
            await emit({ type: "mood", mood: m.value });
          }
        }
        const reply0 = partialString(full, "reply");
        const reply = reply0 && { value: reply0.value.slice(0, MAX_REPLY_CHARS), closed: reply0.closed || reply0.value.length >= MAX_REPLY_CHARS };
        if (reply) {
          if (!firstEmitted) {
            const { cut } = sentenceCut(reply.value, 0, true);
            if (cut > 0) {
              queueSpeech(reply.value.slice(0, cut));
              sent = cut;
              firstEmitted = true;
            }
          }
          if (reply.closed && !capped) {
            capped = true;
            const cut = Math.max(sent, replyCut(reply.value, limits));
            if (cut < reply.value.length) { trimmedTo = cut; }
            if (sent < cut) chunkForSpeech(reply.value.slice(sent, cut)).forEach(queueSpeech);
            sent = cut;
          }
        }
        // The Arabic translation follows the reply in the JSON: show it the moment it is complete.
        if (!hintSent && trimmedTo < 0) { // a cut reply gets a fresh translation below
          const h = partialString(full, "hint_ar");
          if (h && h.closed && /[\u0600-\u06FF]/.test(h.value)) {
            hintSent = true;
            await emit({ type: "hint", text: h.value.replace(/\s+/g, " ").trim().slice(0, 500) });
          }
        }
      }
    } catch (e) {
      console.error("llm stream broke", e && e.message);
    }
  }

  let tutor = tryShape(full, args);
  if (tutor && trimmedTo >= 0) {
    // Show exactly what was spoken.
    tutor.reply = speakable(partialStringValue(full, "reply").slice(0, trimmedTo)) || tutor.reply;
    tutor.hint_ar = "";
  }
  if (!tutor) {
    // The stream failed or never produced a usable reply: one plain, non-streaming attempt.
    tutor = await think(env, args);
    if (sent === 0) chunkForSpeech(tutor.reply).forEach(queueSpeech);
  } else if (sent === 0) {
    chunkForSpeech(tutor.reply).forEach(queueSpeech);
  } else {
    // Reply was cut off (token limit) before its closing quote: voice whatever is left, within the length limit.
    const raw = partialStringValue(full, "reply").slice(0, MAX_REPLY_CHARS);
    const end = capped ? sent : Math.max(sent, replyCut(raw, limits));
    if (sent < end) chunkForSpeech(raw.slice(sent, end)).forEach(queueSpeech);
  }
  // The Arabic line is only missing when the model left it out: translate it while the voice is still playing.
  const hintPromise = tutor.hint_ar || args.wantHint === false ? null : translateToArabic(env, tutor.reply, args.tutor && args.tutor.gender).catch(() => "");
  finished = true;
  if (wake) { wake(); wake = null; }
  await emitter;
  if (hintPromise) {
    const h = await hintPromise;
    if (h) {
      tutor.hint_ar = h;
      await emit({ type: "hint", text: h });
    }
  }
  return { tutor, spoke, unspoken };
}

/** Groups sentences into chunks of at most ~260 characters (one or two natural breaths). */
function chunkForSpeech(text, max = 260) {
  const out = [];
  let cur = "";
  for (const s of splitSentences(text)) {
    if (cur && cur.length + s.length + 1 > max) { out.push(cur); cur = s; }
    else cur = cur ? `${cur} ${s}` : s;
  }
  if (cur) out.push(cur);
  return out;
}

/** Reads a (possibly unfinished) JSON string value for [key] out of streamed text. */
function partialString(text, key) {
  const k = text.indexOf(`"${key}"`);
  if (k < 0) return null;
  let i = text.indexOf(":", k + key.length + 2);
  if (i < 0) return null;
  i = text.indexOf('"', i);
  if (i < 0) return null;
  let out = "";
  for (let j = i + 1; j < text.length; j++) {
    const c = text[j];
    if (c === "\\") {
      const n = text[j + 1];
      if (n === undefined) return { value: out, closed: false };
      if (n === "u") {
        const hex = text.slice(j + 2, j + 6);
        if (hex.length < 4) return { value: out, closed: false };
        out += String.fromCharCode(parseInt(hex, 16)); j += 5; continue;
      }
      out += "ntrbf".includes(n) ? " " : n; j++; continue;
    }
    if (c === '"') return { value: out, closed: true };
    out += c;
  }
  return { value: out, closed: false };
}

function partialStringValue(text, key) {
  const r = partialString(String(text || ""), key);
  return r ? r.value : "";
}

// "Mr. Adam" / "Ms. Sara" are the tutors' own names: the dot after an abbreviation is not a sentence end.
const ABBREV = /\b(Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|vs)\./gi;
const maskAbbrev = (t) => String(t).replace(ABBREV, "$1\u0001");
const unmaskAbbrev = (t) => String(t).replace(/\u0001/g, ".");

/** Where the next speakable chunk ends: after a sentence mark, and never a tiny first chunk. */
function sentenceCut(rawText, from, first) {
  const text = maskAbbrev(rawText); // same length, so indices still match
  const minLen = first ? 18 : 8;
  let cut = from;
  const re = /[.!?؟]+(?=\s)/g;
  re.lastIndex = from;
  let m;
  while ((m = re.exec(text))) {
    const end = m.index + m[0].length;
    if (end - from >= minLen) { cut = end; if (first) break; }
  }
  return { cut };
}

function splitSentences(text) {
  const parts = maskAbbrev(text).match(/[^.!?؟]+[.!?؟]*\s*/g) || [maskAbbrev(text)];
  const out = [];
  for (const p of parts) {
    if (out.length && (out[out.length - 1].length < 18 || p.trim().length < 8)) out[out.length - 1] += p;
    else out.push(p);
  }
  return out.map((x) => unmaskAbbrev(x).trim()).filter(Boolean);
}

function llmText(raw) {
  if (!raw) return "";
  if (typeof raw === "string") return raw;
  if (typeof raw.response === "string") return raw.response;
  if (raw.response && typeof raw.response === "object") return JSON.stringify(raw.response);
  const msg = raw.choices && raw.choices[0] && raw.choices[0].message;
  if (msg && typeof msg.content === "string") return msg.content;
  if (msg && msg.content && typeof msg.content === "object") return JSON.stringify(msg.content);
  return "";
}

function parseJsonObject(text) {
  const cleaned = String(text || "").replace(/<think>[\s\S]*?<\/think>/g, "").replace(/<think>[\s\S]*$/, "").replace(/```[a-z]*|```/gi, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    return JSON.parse(cleaned.slice(start, end + 1));
  } catch {
    return null;
  }
}

// ------------------------------------------------------------------------------------ speak

/** English speech with graceful fallbacks: Aura-2 → Aura-1 → MeloTTS. Base64 MP3. */
async function speak(env, text, voiceKey) {
  const v = TUTORS[voiceKey] || TUTORS.male;
  const clean = forEnglishVoice(text);
  if (!clean) throw new Error("nothing to say");
  const attempts = [
    () => ai(env, TTS_PRIMARY, { text: clean, speaker: v.speaker }),
    () => ai(env, TTS_BACKUP, { text: clean, speaker: v.backup }),
  ];
  if (v.melo) attempts.push(() => ai(env, TTS_LAST, { prompt: clean, lang: "en" }));
  let lastErr = null;
  for (const attempt of attempts) {
    try {
      const b64 = await audioToBase64(await withRetry(attempt, 2));
      if (b64) return b64;
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr || new Error("TTS returned no audio");
}

/** The English voices cannot read Arabic script (it is skipped or garbled): keep only what they can say. */
function forEnglishVoice(text) {
  return speakable(text)
    .replace(/[\u0600-\u06FF\u0750-\u077F]+/g, " ")
    .replace(/\s+/g, " ")
    .replace(/\s+([,.!?;:])/g, "$1")
    .trim();
}

/** Never throws: an empty string tells the app to voice the line on the phone instead. */
async function speakOrEmpty(env, text, voiceKey) {
  try {
    return await speak(env, text, voiceKey);
  } catch (e) {
    console.error("tts failed, phone will speak", e && e.message);
    return "";
  }
}

async function audioToBase64(out) {
  if (!out) return "";
  if (typeof out.audio === "string") return out.audio;
  let buffer = null;
  if (out instanceof ReadableStream) buffer = await new Response(out).arrayBuffer();
  else if (out instanceof Response) buffer = await out.arrayBuffer();
  else if (out instanceof ArrayBuffer) buffer = out;
  else if (ArrayBuffer.isView(out)) buffer = out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength);
  else if (out.audio && (out.audio instanceof ReadableStream)) buffer = await new Response(out.audio).arrayBuffer();
  if (!buffer || buffer.byteLength < 200) return ""; // empty or error body, not audio
  return toBase64(new Uint8Array(buffer));
}

function speakable(text) {
  return String(text || "")
    .replace(/[*_#`>~]/g, "")
    .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

function toBase64(bytes) {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  return btoa(binary);
}

/** Fixed lines (e.g. "Sorry, I didn't catch that") are voiced once per Worker instance, then reused. */
async function memoSpeakOrEmpty(env, text, voiceKey) {
  const key = `${voiceKey}|${text}`;
  if (SPEECH_MEMO.has(key)) return SPEECH_MEMO.get(key);
  const audio = await speakOrEmpty(env, text, voiceKey);
  if (audio) {
    if (SPEECH_MEMO.size > 100) SPEECH_MEMO.delete(SPEECH_MEMO.keys().next().value);
    SPEECH_MEMO.set(key, audio);
  }
  return audio;
}

// ------------------------------------------------------------------------------------ helpers

function supabase(env, jwt) {
  const base = (env.SUPABASE_URL || DEFAULT_SUPABASE_URL).replace(/\/$/, "");
  const key = env.SUPABASE_KEY || DEFAULT_SUPABASE_KEY;
  return {
    async rpc(fn, args, ms = 6000) {
      const res = await fetch(`${base}/rest/v1/rpc/${fn}`, {
        method: "POST",
        headers: { apikey: key, Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" },
        body: JSON.stringify(args || {}),
        signal: AbortSignal.timeout(ms),
      });
      if (!res.ok) {
        const err = new Error(`rpc ${fn} ${res.status}`);
        err.status = res.status;
        throw err;
      }
      const t = await res.text();
      return t ? JSON.parse(t) : null;
    },
  };
}

function sanitizeHistory(history, max = MAX_HISTORY) {
  if (!Array.isArray(history)) return [];
  return history
    .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string" && m.content.trim())
    .slice(-max)
    .map((m) => ({ role: m.role, content: m.content.trim().slice(0, 900) }));
}

function isArabicText(t) {
  const s = String(t || "");
  const ar = (s.match(/[\u0600-\u06FF\u0750-\u077F]/g) || []).length;
  const lat = (s.match(/[A-Za-z]/g) || []).length;
  return ar > 0 && ar >= lat;
}

/**
 * Decides whether the audio really contained speech. Whisper sometimes invents "Thank you" or
 * "Subtitles by..." over silence, so those exact phrases are only rejected when recognition itself
 * says there was probably no speech. A clearly spoken "Thank you" or "Bye" is a valid reply.
 */
function isRealSpeech(t, typed = false, stt = null) {
  if (!t) return false;
  const letters = t.replace(/[^\p{L}]/gu, "");
  if (letters.length < 2) return false;
  if (typed) return true; // typed words are never silence hallucinations
  // Recognition itself says there was a voice: an accent lowers Whisper's score, but no_speech_prob stays low
  // for real speech, so only that is used to trust short phrases ("Thank you", "Bye").
  const voiced = Boolean(stt && (!stt.known || stt.noSpeech < 0.5));
  // Recognition says the audio was clearly speech (used for the stock hallucination texts below).
  const clear = Boolean(stt && stt.known && stt.noSpeech < 0.3 && stt.score > -0.6);
  // Typical Whisper inventions over silence: subtitle credits and channel promos. Matched as whole phrases, so an
  // ordinary Arabic request that happens to contain "ترجمة" or "اشترك" is not thrown away.
  if (/اشترك\s+(?:في|فى)\s+(?:القناة|الموقع)|نانسي\s+قنقر|ترجمة\s+(?:نانسي|الحلقة)|subtitles?\s+by|amara\.org/i.test(t)) return clear;
  const n = norm(t);
  const junk = ["thank you", "thanks for watching", "you", "bye", "شكرا", "شكرا لكم"];
  if (junk.includes(n)) return voiced;
  if (stt && stt.known && stt.noSpeech > 0.85) return false;
  return true;
}

/** The student's first name: one word, letters only. */
function cleanName(n) {
  if (typeof n !== "string") return "";
  const first = n.trim().split(/\s+/)[0] || "";
  return /^(?=.*\p{L})[\p{L}'-]{1,24}$/u.test(first) ? first : "";
}

// Common Egyptian first names → the way they are written in English (used for the English lines and the voice).
const AR_NAMES_RAW = {
  "أحمد": "Ahmed", "محمد": "Mohamed", "محمود": "Mahmoud", "مصطفى": "Mostafa", "علي": "Ali", "عمر": "Omar", "عمرو": "Amr",
  "يوسف": "Youssef", "إبراهيم": "Ibrahim", "حسن": "Hassan", "حسين": "Hussein", "خالد": "Khaled", "كريم": "Karim", "طارق": "Tarek",
  "هاني": "Hany", "شريف": "Sherif", "وليد": "Walid", "حازم": "Hazem", "زياد": "Ziad", "آدم": "Adam", "نور": "Nour", "سارة": "Sara",
  "مريم": "Mariam", "فاطمة": "Fatma", "آية": "Aya", "هنا": "Hana", "سلمى": "Salma", "ندى": "Nada", "منى": "Mona", "دينا": "Dina",
  "رانيا": "Rania", "هبة": "Heba", "ياسمين": "Yasmin", "إسراء": "Esraa", "أسماء": "Asmaa", "عائشة": "Aisha", "ليلى": "Layla",
  "ريم": "Reem", "هاجر": "Hagar", "فريدة": "Farida", "جنى": "Jana", "ملك": "Malak", "رحمة": "Rahma", "شهد": "Shahd", "رنا": "Rana",
  "نورهان": "Nourhan", "ميار": "Mayar", "حبيبة": "Habiba", "رامي": "Ramy", "سامح": "Sameh", "هشام": "Hesham", "عصام": "Essam",
  "أسامة": "Osama", "أيمن": "Ayman", "سمير": "Samir", "سعيد": "Saeed", "جمال": "Gamal", "كمال": "Kamal", "نبيل": "Nabil",
  "فادي": "Fady", "مينا": "Mina", "جورج": "George", "مايكل": "Michael", "مروان": "Marwan", "حمزة": "Hamza", "باسم": "Bassem",
  "بسمة": "Basma", "شيماء": "Shaimaa", "دعاء": "Doaa", "أميرة": "Amira", "نهى": "Noha", "نوران": "Nouran", "يارا": "Yara",
  "عبد": "Abdel", "عبدالله": "Abdullah", "عبدالرحمن": "Abdelrahman", "بلال": "Bilal", "أنس": "Anas", "إياد": "Eyad", "ماجد": "Maged",
  "ماهر": "Maher", "رضا": "Reda", "رمضان": "Ramadan", "صلاح": "Salah", "سيف": "Seif", "عادل": "Adel", "عماد": "Emad", "فارس": "Fares",
  "معتز": "Moataz", "ياسر": "Yasser", "هدى": "Hoda", "هند": "Hend", "وفاء": "Wafaa", "سمر": "Samar", "منة": "Menna", "مي": "Mai",
  "روان": "Rawan", "جيهان": "Gehan", "غادة": "Ghada", "لمياء": "Lamiaa", "ولاء": "Walaa", "أروى": "Arwa", "تسنيم": "Tasneem",
  "جميلة": "Gamila", "سناء": "Sanaa", "سهر": "Sahar", "نجلاء": "Naglaa", "عبير": "Abeer",
};
const arNorm = (x) => String(x).replace(/[\u064B-\u065F\u0670\u0640]/g, "").replace(/[أإآٱ]/g, "ا").replace(/ى/g, "ي").replace(/ة/g, "ه").replace(/ؤ/g, "و").replace(/ئ/g, "ي");
const AR_NAMES = new Map(Object.entries(AR_NAMES_RAW).map(([k, v]) => [arNorm(k), v]));

/**
 * The name as the English voice can say it. A Latin name stays as it is; an Arabic-script name is written the
 * English way when it is a common one; otherwise "" (the tutor simply does not use the name, which is far
 * better than the voice skipping or garbling Arabic letters in the middle of an English sentence).
 */
function spokenName(n) {
  if (!n) return "";
  if (/^[A-Za-z'-]+$/.test(n)) return n;
  return AR_NAMES.get(arNorm(n)) || "";
}

/** A character's name may be a title plus a name ("Ms. Layla", "دكتور أحمد"): letters, dots, spaces. */
function cleanTutorName(n) {
  if (typeof n !== "string") return "";
  const t = n.replace(/\s+/g, " ").trim();
  if (t.split(" ").length > 3) return ""; // a name, not a sentence: this field comes from the app, not from a database check
  return /^(?=.*\p{L})[\p{L}.'\- ]{1,30}$/u.test(t) ? t : "";
}

const TUTOR_TITLES = new Map([["دكتور", "Dr."], ["دكتوره", "Dr."], ["د", "Dr."], ["مستر", "Mr."], ["مس", "Ms."], ["ميس", "Ms."], ["استاذ", "Mr."], ["استاذه", "Ms."], ["كابتن", "Captain"]]);

/** English form of a tutor's name for English lines and the voice ("دكتور أحمد" → "Dr. Ahmed"), or "" if unknown. */
function latinTutorName(n) {
  if (/^[A-Za-z.'\- ]+$/.test(n)) return n;
  const words = n.split(" ").filter(Boolean);
  let title = "";
  const first = TUTOR_TITLES.get(arNorm(words[0]).replace(/\.$/, ""));
  if (first) { title = first; words.shift(); }
  const w = words[0] || "";
  const latin = /^[A-Za-z'.-]+$/.test(w) ? w : spokenName(w);
  if (!latin) return "";
  return title ? `${title} ${latin}` : latin;
}

function norm(s) {
  return String(s).toLowerCase().replace(/[^\p{L}\p{N} ]/gu, "").replace(/\s+/g, " ").trim();
}

// ------------------------------------------------------------------------------------ conversation helpers

/** The student is saying goodbye (short utterances only, so "see you at 5" inside a long story does not count). */
function isGoodbye(t) {
  const s = String(t || "").trim();
  if (!s || s.split(/\s+/).length > 12) return false;
  return /\b(?:good ?bye|bye(?: bye)?|see you|talk to you (?:later|soon)|(?:i|we) (?:have|need|must|got) to go|gotta go)\b/i.test(s)
    || /مع\s+السلامة|باي\b|هستأذن|لازم\s+(?:ا|أ)مشي|همشي\s+دلوقتي/.test(s);
}

/**
 * Pace of the conversation from how much the student says: longer sentences than their level → a nudge up,
 * one or two words at a time → simpler English (and a suggestion to go down). `suggested` is only advice for
 * the app; the level itself is never changed here.
 */
function paceHint(history, transcript, level) {
  const said = history.filter((m) => m.role === "user").map((m) => m.content);
  said.push(transcript);
  const recent = said.slice(-4);
  if (recent.length < 3) return { note: "", suggested: "" };
  const avg = recent.reduce((n, t) => n + String(t).trim().split(/\s+/).filter(Boolean).length, 0) / recent.length;
  if (level === "beginner" && avg >= 10) return { note: "They already manage longer sentences than a typical beginner: you may use slightly richer words and one follow-up question.", suggested: "intermediate" };
  if (level === "intermediate" && avg >= 16) return { note: "They speak in long, confident sentences: you may use richer vocabulary and an idiom now and then.", suggested: "advanced" };
  if (level !== "beginner" && avg <= 3) return { note: "They answer in very few words and may be struggling: keep your English simpler and shorter, prefer easy either-or questions, and once (not every turn) offer \"Want me to speak a little slower?\".", suggested: level === "advanced" ? "intermediate" : "beginner" };
  if (level === "beginner" && avg <= 2) return { note: "They answer in one or two words: use very short, easy questions and encourage them to try a full sentence.", suggested: "" };
  return { note: "", suggested: "" };
}

// Requests that are not a normal turn (nudge / translate / speak / end) do not use the turn allowance, so they
// are limited per student here (per Worker instance, so it is a safety net, not exact accounting).
const AUTH_CACHE = new Map();
const RATE = new Map();

async function verifyUser(env, jwt) {
  const k = jwt.slice(-64);
  const hit = AUTH_CACHE.get(k);
  if (hit && hit.until > Date.now()) return hit.id;
  const base = (env.SUPABASE_URL || DEFAULT_SUPABASE_URL).replace(/\/$/, "");
  const key = env.SUPABASE_KEY || DEFAULT_SUPABASE_KEY;
  try {
    const res = await fetch(`${base}/auth/v1/user`, { headers: { apikey: key, Authorization: `Bearer ${jwt}` }, signal: AbortSignal.timeout(4000) });
    if (!res.ok) return "";
    const u = await res.json();
    const id = u && typeof u.id === "string" ? u.id : "";
    if (id) {
      if (AUTH_CACHE.size > 200) AUTH_CACHE.clear();
      AUTH_CACHE.set(k, { id, until: Date.now() + 60_000 });
    }
    return id;
  } catch {
    return "";
  }
}

function rateOk(id, kind, max, windowMs = 600_000) {
  const key = `${kind}|${id}`;
  const now = Date.now();
  const arr = (RATE.get(key) || []).filter((t) => now - t < windowMs);
  if (arr.length >= max) { RATE.set(key, arr); return false; }
  arr.push(now);
  RATE.set(key, arr);
  if (RATE.size > 2000) RATE.clear();
  return true;
}

/** Cached voice of a fixed line, or "" (never synthesises: used while the AI allowance is spent). */
function memoPeek(voiceKey, text) {
  return SPEECH_MEMO.get(`${voiceKey}|${text}`) || "";
}

/** The tutor's own words for a failure: { kind, line, lineAr, audio }. While the allowance is spent only cached voice is used. */
async function friendlyParts(env, kind, voiceKey, peekOnly) {
  const [line, lineAr] = FRIENDLY[kind] || FRIENDLY.AI_FAILED;
  const audio = peekOnly ? memoPeek(voiceKey, line) : await memoSpeakOrEmpty(env, line, voiceKey);
  return { kind, line, lineAr, audio };
}

function friendlyFields({ mode, kind, line, lineAr, audio, remaining, voice = "male" }) {
  return {
    ok: true, mode, lang: "en", student_lang: "en", transcript: "", clarify: true, needs_repeat: true, soft_error: kind,
    corrected: "", corrections: [], pronunciation: [], reply: line, hint_ar: lineAr, mood: "neutral", new_words: [],
    audio_mp3: audio, speak_locally: !audio, voice_gender: voice, remaining,
  };
}

/** The same in-character answer as one JSON reply, or as the two NDJSON events a streaming app expects. */
function friendlyResponse(stream, fields) {
  if (!stream) return json(fields);
  const { audio_mp3, ...rest } = fields;
  const lines = [];
  if (audio_mp3) lines.push(JSON.stringify({ type: "audio", i: 0, text: fields.reply, mp3: audio_mp3 }));
  lines.push(JSON.stringify({ type: "final", ...rest, speak_text: audio_mp3 ? "" : fields.reply }));
  return new Response(lines.join("\n") + "\n", { status: 200, headers: { "Content-Type": "application/x-ndjson; charset=utf-8", "Cache-Control": "no-store", ...cors() } });
}

const END_SCHEMA = {
  type: "object",
  properties: {
    goodbye: { type: "string" },
    goodbye_ar: { type: "string" },
    mistakes: {
      type: "array", maxItems: 3,
      items: { type: "object", properties: { wrong: { type: "string" }, right: { type: "string" }, explain_ar: { type: "string" } }, required: ["wrong", "right", "explain_ar"] },
    },
    words: {
      type: "array", maxItems: 5,
      items: { type: "object", properties: { word: { type: "string" }, meaning_ar: { type: "string" } }, required: ["word", "meaning_ar"] },
    },
    praise_ar: { type: "string" },
    memory: { type: "array", maxItems: 3, items: { type: "string" } },
  },
  required: ["goodbye", "goodbye_ar", "mistakes", "words", "praise_ar", "memory"],
};

/** Reads the whole call and returns { goodbye, goodbye_ar, mistakes, words, praise_ar, memory } (validated). */
async function summarizeCall(env, { tutor, level, name, msgs }) {
  const system = [
    `You are ${tutor.name}. ${tutor.bio} ${genderLine(tutor)}`,
    `You have just finished a live voice call with your Egyptian English student${name ? ` ${name}` : ""}. ${LEVELS[level]}`,
    "Read the conversation and answer with JSON only:",
    "- goodbye: one short, warm spoken goodbye in English in your character (max 20 words).",
    "- goodbye_ar: a faithful Egyptian Arabic translation of goodbye.",
    "- mistakes: up to 3 REAL English mistakes the STUDENT made in this conversation, the most repeated and most important first. `wrong` must be copied from the student's own words, `right` is the corrected version, `explain_ar` is one short friendly sentence in Egyptian Arabic. [] if there were none. Never invent a mistake.",
    "- words: up to 5 useful English words or phrases from this conversation, each with a short Egyptian Arabic meaning.",
    "- praise_ar: one warm, specific sentence of encouragement in Egyptian Arabic about what they did well today.",
    "- memory: up to 3 short notes (max 12 words each) worth remembering for the next call, written as \"Goal: ...\", \"Interest: ...\" or \"Often mixes up: ...\". Only lasting, harmless things the student said or kept doing. Never contact details, address, school name, health, family problems, religion or politics. [] if nothing.",
  ].join("\n");
  const convo = msgs.map((m) => `${m.role === "user" ? "Student" : "Tutor"}: ${m.content.slice(0, 300)}`).join("\n");
  const params = { messages: [{ role: "system", content: system }, { role: "user", content: `Conversation:\n${convo}\n\n/no_think` }], max_tokens: 700, temperature: 0.4 };
  let parsed = null;
  try {
    parsed = parseJsonObject(llmText(await llm(env, { ...params, schema: END_SCHEMA })));
  } catch (e) {
    if (isQuotaError(e)) throw e;
    console.error("summary json mode failed, retrying plain", e && e.message);
  }
  if (!parsed) parsed = parseJsonObject(llmText(await llm(env, params)));
  if (!parsed) throw new Error("summary: no JSON");

  const studentWords = new Set(norm(msgs.filter((m) => m.role === "user").map((m) => m.content).join(" ")).split(" ").filter(Boolean));
  const fromStudent = (wrong) => {
    const ws = norm(wrong).split(" ").filter((w) => w.length >= 3);
    return ws.length > 0 && ws.filter((w) => studentWords.has(w)).length / ws.length >= 0.7;
  };
  const ar = (x, n) => (typeof x === "string" && /[\u0600-\u06FF]/.test(x) ? x.replace(/\s+/g, " ").trim().slice(0, n) : "");
  const goodbye = speakable(parsed.goodbye).slice(0, 200);
  return {
    goodbye: goodbye.length >= 8 && !/[\u0600-\u06FF]/.test(goodbye) ? goodbye : "",
    goodbye_ar: ar(parsed.goodbye_ar, 300),
    mistakes: (Array.isArray(parsed.mistakes) ? parsed.mistakes : [])
      .filter((c) => c && typeof c.wrong === "string" && typeof c.right === "string" && c.wrong.trim() && c.right.trim() && norm(c.wrong) !== norm(c.right) && fromStudent(c.wrong))
      .slice(0, 3)
      .map((c) => ({ wrong: c.wrong.trim().slice(0, 120), right: c.right.trim().slice(0, 120), explain_ar: ar(c.explain_ar, 240) })),
    words: (Array.isArray(parsed.words) ? parsed.words : [])
      .filter((w) => w && typeof w.word === "string" && /[A-Za-z]/.test(w.word) && ar(w.meaning_ar, 80))
      .slice(0, 5)
      .map((w) => ({ word: w.word.trim().slice(0, 40), meaning_ar: ar(w.meaning_ar, 80) })),
    praise_ar: ar(parsed.praise_ar, 240),
    memory: (Array.isArray(parsed.memory) ? parsed.memory : []).map(cleanLearned).filter(Boolean).slice(0, 3),
  };
}

// Non-turn requests: nudge (quiet student, cached voice), translate (on demand), speak (replay),
// end (goodbye + summary + long-term memory).
async function auxiliary({ mode, body, env, ctx, sb, jwt, level, voiceKey, tutor, name }) {
  if (mode === "nudge") {
    const list = NUDGES[level === "beginner" ? "beginner" : "other"];
    const n = Math.min(Math.max(parseInt(body.count, 10) || 0, 0), list.length - 1);
    const [line, lineAr] = list[n];
    const audio = Date.now() < quotaBlockedUntil ? memoPeek(voiceKey, line) : await memoSpeakOrEmpty(env, line, voiceKey);
    return json({
      ok: true, mode, lang: "en", student_lang: "en", transcript: "", nudge: true, needs_repeat: false, corrected: "", corrections: [], pronunciation: [],
      reply: line, hint_ar: lineAr, mood: "encouraging", new_words: [], audio_mp3: audio, speak_locally: !audio, voice_gender: voiceKey,
    });
  }

  const userId = await verifyUser(env, jwt);
  if (!userId) return json({ ok: false, error: "UNAUTHORIZED" }, 401);

  try {
    if (mode === "translate") {
      const text = typeof body.text === "string" ? speakable(body.text).slice(0, 400) : "";
      if (!text) return json({ ok: false, error: "BAD_REQUEST" }, 400);
      if (!rateOk(userId, "translate", 40)) return json({ ok: false, error: "RATE_LIMIT" }, 429);
      const hint = await translateToArabic(env, text, tutor.gender);
      if (!hint) return json({ ok: false, error: "AI_FAILED", stage: "translate" }, 502);
      return json({ ok: true, mode, hint_ar: hint });
    }

    if (mode === "speak") {
      const text = typeof body.text === "string" ? speakable(body.text).slice(0, 300) : "";
      if (!/[A-Za-z]/.test(text) || !forEnglishVoice(text)) return json({ ok: false, error: "BAD_REQUEST" }, 400);
      if (!rateOk(userId, "speak", 40)) return json({ ok: false, error: "RATE_LIMIT" }, 429);
      const audio = await speakOrEmpty(env, text, voiceKey);
      return json({ ok: true, mode, reply: text, audio_mp3: audio, speak_locally: !audio, voice_gender: voiceKey });
    }

    // mode === "end"
    if (!rateOk(userId, "end", 5)) return json({ ok: false, error: "RATE_LIMIT" }, 429);
    const msgs = sanitizeHistory(body.history, 40);
    const studentTurns = msgs.filter((m) => m.role === "user").length;
    let summary = null;
    if (studentTurns >= 2) {
      try {
        summary = await summarizeCall(env, { tutor, level, name, msgs });
      } catch (e) {
        if (isQuotaError(e)) throw e;
        console.error("call summary failed", e && e.message);
      }
    }
    const [tplEn, tplAr] = pick(GOODBYES);
    const en = summary && summary.goodbye ? summary.goodbye : tplEn;
    const arLine = summary && summary.goodbye && summary.goodbye_ar ? summary.goodbye_ar : summary && summary.goodbye ? "" : tplAr;
    const audio = await speakOrEmpty(env, en, voiceKey);
    // Long-term memory: a few durable notes from the whole call (goal, interests, mistakes that keep coming back).
    if (summary && studentTurns >= 3) {
      for (const fact of summary.memory) ctx.waitUntil(sb.rpc("ai_tutor_remember", { p_fact: fact }).catch(() => {}));
    }
    return json({
      ok: true, mode, lang: "en", student_lang: "en", transcript: "", corrected: "", corrections: [], pronunciation: [],
      reply: en, hint_ar: arLine, mood: "happy", new_words: [], needs_repeat: false, audio_mp3: audio, speak_locally: !audio, voice_gender: voiceKey,
      summary: summary ? { mistakes: summary.mistakes, words: summary.words, praise_ar: summary.praise_ar } : null,
    });
  } catch (e) {
    const quota = isQuotaError(e);
    if (quota) quotaBlockedUntil = Date.now() + 60_000;
    console.error(`${mode} failed [${quota ? "AI_QUOTA" : "AI_ERROR"}]`, e && e.message);
    return json({ ok: false, error: quota ? "SERVICE_BUSY" : "AI_FAILED", reason: quota ? "AI_QUOTA" : "AI_ERROR", stage: mode }, quota ? 503 : 502);
  }
}

function cors() {
  return { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "POST, GET, OPTIONS", "Access-Control-Allow-Headers": "Authorization, Content-Type" };
}

function json(obj, status = 200, extra = {}) {
  return new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...extra, ...cors() } });
}
