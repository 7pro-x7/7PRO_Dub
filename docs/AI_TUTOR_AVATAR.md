# Mr. Adam — 3D avatar specification

The app renders the tutor in real time with Filament. Until a model is uploaded (or on phones
without OpenGL ES 3.0 / under ~1.4 GB RAM) the call shows the animated 2D "voice presence"
instead, so the feature works on every phone from day one.

## What the .glb must contain

| Requirement | Value |
|---|---|
| Format | glTF 2.0 binary (`.glb`), Y-up, facing +Z (towards the camera) |
| Framing | Head-and-shoulders bust preferred (a full body also works; the app crops to the upper third) |
| Size | ≤ 8 MB (hard limit 30 MB); ≤ 40k triangles; textures ≤ 1024 px (JPEG/PNG or KTX2) |
| Materials | Standard PBR (metallic-roughness). No transparency on skin. |
| Head joint (optional, recommended) | A node named `Head` (also recognised: `mixamorig:Head`, `CC_Base_Head`, `DEF-head`, `J_Bip_C_Head`) |
| Idle animation (optional) | Animation #0 loops (breathing/idle). Head and face are driven on top of it. |
| Licence | Must allow commercial use (e.g. CC0 from MakeHuman/MPFB, or a model you commissioned). |

### Face shape keys (morph targets) — names must match exactly

Either set works; having both is best.

**Visemes (15):** `viseme_sil, viseme_PP, viseme_FF, viseme_TH, viseme_DD, viseme_kk, viseme_CH, viseme_SS, viseme_nn, viseme_RR, viseme_aa, viseme_E, viseme_I, viseme_O, viseme_U`

**ARKit (used for blinking, eyes, smile, brows — and lip-sync if there are no visemes):**
`jawOpen, mouthClose, mouthFunnel, mouthPucker, mouthSmileLeft, mouthSmileRight, mouthStretchLeft, mouthStretchRight, mouthPressLeft, mouthPressRight, mouthRollLower, mouthUpperUpLeft, mouthUpperUpRight, mouthLowerDownLeft, mouthLowerDownRight, tongueOut, eyeBlinkLeft, eyeBlinkRight, eyeLookUpLeft, eyeLookUpRight, eyeLookDownLeft, eyeLookDownRight, eyeLookInLeft, eyeLookInRight, eyeLookOutLeft, eyeLookOutRight, browInnerUp, browOuterUpLeft, cheekSquintLeft, cheekSquintRight`

Minimum for a convincing result: `jawOpen, eyeBlinkLeft, eyeBlinkRight, mouthSmileLeft, mouthSmileRight` plus the visemes (or `mouthFunnel, mouthPucker, mouthClose, mouthStretchLeft/Right`).
Missing shapes are simply skipped; the app logs what it found (`adb logcat -s TutorAvatar`).

## Making it (free tools)

1. **Blender** (GPL, free) + **MPFB** add-on (the MakeHuman plugin; generated humans are CC0).
2. Create the character (friendly male teacher, ~35 years, neat hair, shirt) → add the default skeleton.
3. Model the face shape keys listed above on the body mesh (Object Data → Shape Keys), named exactly.
   A 3D freelancer familiar with "ARKit blendshapes" can do this step quickly from this list.
4. Delete everything below the chest (or keep full body), apply modifiers except Armature.
5. **File → Export → glTF 2.0**: format *glTF Binary*, include *Shape Keys*, *Skinning*, *+Y Up*; images *JPEG*, max 1024.
6. Optional shrink: `npx @gltf-transform/cli optimize tutor.glb tutor-small.glb --texture-size 1024` (MIT-licensed tool).

## Publishing it (no app update needed)

1. Supabase → Storage → bucket **`tutor-assets`** → upload `tutor.glb` (only Owner/Admin can upload).
2. SQL editor, signed in as the owner:

```sql
select ai_tutor_configure(p_avatar_url => 'https://usbfrqbjtpvnkmmcusdi.supabase.co/storage/v1/object/public/tutor-assets/tutor.glb');
```

Every phone downloads it once (with a progress line in the tutor lobby) and keeps it. Uploading a new
version: replace the file, then run `select ai_tutor_configure(p_bump_avatar => true);`.

## Custom characters: automatic eyes and mouth, then precise editing

Owner-uploaded characters (a photo or a drawing) are animated as a 2D cutout: the mouth opens and both eyes blink, driven by the same audio envelope as the 3D tutors. What makes that look right is knowing exactly where the eyes and mouth are, so:

1. **Automatic placement on upload.** As soon as the picture is chosen (and its background removed) the app runs `FaceRigFinder`:
   - **Realistic faces / photos:** MediaPipe Face Landmarker (Apache-2.0, on-device) gives the eye centres and sizes and the lip corners and thickness. Model: `assets/character_ai/face_landmarker.task`, downloaded by the `downloadFaceLandmarkerModel` Gradle task (best-effort: if the download fails the build still works). Pin `faceModelMd5` in `app/build.gradle.kts` after the first build.
   - **Drawings and mascots** (which a landmark model does not recognise): `HeuristicRig` estimates the head box (ending at the neck when there is one), then looks for the darkest blobs in the eye zones and the reddest / darkest blob in the mouth zone. The screen tells the owner this is only an estimate.
2. **Owner fine-tuning (`FaceRigEditor`).** Left eye, right eye and mouth are each an outline the owner adjusts like drawing: drag the green centre dot to move, drag the white dots to widen or heighten, tap the picture to drop the selected part there. A magnifier follows the finger, a zoom slider magnifies around the selected part, arrow buttons nudge by 0.3 % of the picture, sliders set width and height, "Mirror the eyes" copies one eye onto the other, and "Preview the animation" plays the real blink and mouth motion before saving.
3. **Editing later.** In the characters list the pencil icon reopens the editor for an existing character (`CharacterRepository.updateRig`).

Stored in `ai_tutor_characters` (migration `20260923140000_ai_tutor_character_face_rig.sql`), every value a fraction 0..1 of the image: `mouth_x, mouth_y, mouth_w, mouth_h` (centre, half width, half height), `eye_l_x, eye_l_y, eye_r_x, eye_r_y` (centres, left = smaller x on screen), `eye_w, eye_h` (half size, shared by both eyes), and `rig_source` (`landmarks`, `estimated`, `manual`, or `legacy` for characters made before this). Old characters keep working: their eyes sit on the old single eye line.

Rendering (`drawCharacterFace`): at rest the picture is drawn untouched. While the character talks or blinks, only small crops around the eyes and the mouth are warped on a mesh, and every crop fades to zero movement at its edge, so there are no seams. Nothing is painted over the face except the inside of an open mouth and a faint lash line on shut eyes.

- **Blink:** the character's own upper-lid skin (with its real lashes on the edge) slides down along the eye's curve while the lower lid lifts a little; the eye folds away between them and the lids meet low, on the lower lid's line. Brows stay still. The rhythm is human (`BlinkClock`): a 75 ms close, a 40 ms hold, a slower 170 ms opening, every 2.2 to 5.5 s at random, sometimes a double blink.
- **Talking:** the lips part along the mouth's own curve (corners higher than the middle). The upper lip and upper teeth stay exactly as in the picture; the lower lip and chin move down smoothly by at most a third of the mouth's half width, and the gap shows a shaded interior (no teeth are drawn over the real ones). The lip-sync level is smoothed (`JawSmoother`) so the lips never jitter.

## After the call: the conversation, copy, TXT and PDF

When a call ends, the summary screen shows the whole conversation in order: every tutor line (with its Arabic translation under it) and every student line (with the corrected sentence under it when the tutor fixed something), each with its time in the call. Three buttons sit above it:

- **Copy** puts the conversation on the clipboard as plain text.
- **Save TXT** and **Save PDF** open Android's "Save as" picker so the student chooses where the file goes (Downloads, Drive...). No storage permission is needed. The PDF is drawn with Android's own text engine (`tutor/Transcript.kt`), so Arabic is shaped and laid out right-to-left correctly, English stays left-to-right, and long conversations continue on further pages. The TXT/PDF also list the corrections and the new words of the call.

The conversation is kept in memory for the current summary only (`TutorUiState.transcript`); nothing is uploaded or stored on the server. If the student fixes a misheard sentence by hand, the wrong sentence and the tutor's answer to it are dropped from the conversation.

## Paid replies: reply packs the owner prices, paid through the app's normal checkout

The tutor can be free, partly free or paid-only, and the owner decides which, from the app. What a student buys is a **pack of replies**:

- A pack gives N replies (say 100) that stay valid for **30 days from the purchase**. Replies not used by then simply expire; the student then **renews or buys more**.
- Every purchase is its own pack with its own 30 days (buying again never extends an older pack). Replies are spent from the pack that expires first, so nothing expires needlessly.
- **Free replies** (the owner's "free replies a day", 0 = none) are separate: each day they are spent first, so paid replies are only touched after the day's free ones are used.
- A reply that fails on the server is handed back automatically (to the free count or to its pack). Greetings never use replies.

**Owner (Admin console → Money → "AI Tutor prices", owner only)**
- **Free replies a day.**
- **Reply packs:** name and description (English + Arabic), **number of replies**, **price**, **price after discount** (students see the old price crossed out next to the new one, with a "Save X%" badge) with an optional number of days the discount lasts, currency, **days valid** (30 by default), order and on/off. A pack that already has orders can't be deleted: switch it off. Four draft packs (30 / 100 / 300 / 1,000 replies at 19 / 45 / 119 / 369 EGP) are created switched off: review the prices and turn them on.
- **Coupons:** the normal coupon screen has "The coupon works on": courses only, courses + AI Tutor, or AI Tutor only.
- **Sales:** students with replies left, paid orders, revenue per currency, and replies sold / used / expired unused.

**Student (AI Tutor lobby → "Buy replies", or automatically when the replies run out)**
- Sees the packs (replies, days valid, price per reply), taps Buy, and gets the same payment sheet as a course: Paymob card, mobile wallet, or a manual wallet transfer the owner approves. A coupon can be typed on the sheet; a 100% coupon adds the replies straight away.
- The price charged is always computed by the server (`ai_tutor_quote`); the app only displays it.
- The lobby and the plans screen show how many replies are left and when the first pack ends, with a warning in the last 5 days. A refunded order switches its pack off.

**How it is built (no new payment code):** a pack is an order of type `AI_TUTOR` riding the existing `checkout` Edge Function, Paymob webhook and manual-payment review; `quote_checkout`, `create_order`, `evaluate_coupon` and `confirm_order_payment` hand `AI_TUTOR` to their AI versions (migrations `20260923225258_ai_tutor_paid_plans.sql` and `20260924010000_ai_tutor_reply_packs.sql`, both applied). `ai_tutor_take_turn` spends free replies first, then the soonest-expiring pack (`ai_tutor_entitlements.replies_used`); `ai_tutor_refund_turn` gives a failed reply back. The Worker needed no change.

**What it costs you (Cloudflare, September 2026 rates, about 52 EGP per USD):** roughly 0.16 EGP per reply (the voice is about 90 % of it), plus Paymob's 2.75 % + 3 EGP per card payment. Price packs at about 2× that: e.g. 45 EGP for 100 replies. Keep the free replies small (about 3 a day) and use the global daily limit as your spending cap: with the Workers Free plan the AI stops after 10,000 neurons a day (about 35 replies for the whole app).
