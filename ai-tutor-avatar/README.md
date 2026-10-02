# Mr. Adam — 3D tutor model (tutor.glb)

Original, code-generated 3D bust (CC0 — yours to use commercially). Stylised, not photoreal.

- `tutor.glb` — 3.5 MB, ~78k triangles, glTF 2.0 with 30 ARKit-named face shapes
  (jaw, lips, smile, blink, eye look, brows), a `Head` node the app turns for natural head motion,
  PBR materials. Works with the app's renderer as-is.
- `build.py` — regenerates it (`python3 build.py`, needs only numpy). Colours, hair, proportions are
  all parameters at the top of each section.
- `render.py` — quick software preview renders (numpy + Pillow).

## Put it live (no app update)
1. Supabase → Storage → bucket `tutor-assets` → upload `tutor.glb`.
2. SQL editor (owner account):
   `select ai_tutor_configure(p_avatar_url => 'https://usbfrqbjtpvnkmmcusdi.supabase.co/storage/v1/object/public/tutor-assets/tutor.glb');`

Phones download it once on the next visit to the tutor screen.
