# 7PRO AI Tutor Worker — v5.9.2 (English-only, accurate, built to convince a paying student)

One turn: **hear** (Whisper large-v3-turbo, decoded twice in parallel — auto-language + forced-English, so
accented English is never silently misread as Arabic) → **think & correct** (DeepSeek V4 Flash, falling back to
Qwen3-30B-A3B) → **speak** (Deepgram Aura-2, falling back to Aura-1, then MeloTTS). All open-weight models on
Cloudflare Workers AI. Supabase (`ai_tutor_take_turn`) decides who may talk and how much.

## What this build gets right, on purpose

**Accuracy** (so corrections are trustworthy, not guesses):
- Two Whisper decodes at once, no extra wait — catches accented English being misread as Arabic.
- Confidence (high/medium/low) gates everything downstream: low confidence never produces a "correction" — that
  would just be a mishearing — and the tutor is told to check what it heard instead of guessing.
- `alt_transcript` carries the other reading when the two decodes disagree.

**Why a student keeps calling back, not just once:**
- A personalised opener when the tutor remembers the student (Supabase memory), not a fixed template every time.
- `explain_ar`: one short Egyptian-Arabic sentence per correction explaining the *rule*, not just the fix — this
  is what makes it feel like a lesson, not autocorrect.
- `pron_tip_ar`: a pronunciation tip when the recognizer was genuinely unsure about a word.
- `hint_ar`: a faithful Egyptian-Arabic translation under every English reply, text only, never spoken.
- End-of-call summary (mistakes, new words, praise) + long-term memory notes across calls.

**English-only, still:** `reply` and the voice are always English, even if the student explicitly asks for
Arabic — the tutor says so warmly, in English, then keeps helping. `hint_ar` / `explain_ar` / `pron_tip_ar` are
the only Arabic, and all three are text-only, never spoken.

**Speed, without cutting the above:** the two Whisper decodes run in parallel (no added wait); replies are kept
to one or two sentences by instruction; token budget is sized to exactly what's generated now (no wasted budget
on fields that aren't used).

**Code size:** trimmed from the original ~2,000 lines to under 1,900 by shortening the header and in-code
documentation and removing two functions left unused by earlier changes (an echo-guard helper and its distance
metric) — with no change in behaviour. Going further than this would mean minifying the code (stripping
whitespace/renaming variables), which saves nothing that matters on Cloudflare (scripts are allowed up to 1 MB;
this file is about 110 KB) and makes the Worker much harder to read or fix later — not recommended.

## Deploy (dashboard, ~5 minutes)

1. Cloudflare dashboard → **Workers & Pages** → Worker **`7pro-ai-tutor`** → **Edit code**.
2. Replace everything with `src/index.js` from this folder → **Deploy**.
3. Confirm **Settings → Bindings** still has Workers AI bound to variable name **`AI`**.
4. Open `https://7pro-ai-tutor.<your-subdomain>.workers.dev/health` — expect `{"ok":true,...}`.

No app rebuild needed — every field the app reads is still present in the response.

Deploying from a terminal instead: `npx wrangler deploy` inside this folder.
