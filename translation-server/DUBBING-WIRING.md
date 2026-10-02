# توصيل ميزة الدبلجة داخل translation-server الموجود

خطوات صغيرة، ملفات موجودة بالفعل ونعدلها + ملفان جديدان (`app/dubbing.py`, `app/dub_routes.py`
مرفقان في نفس المجلد).

## 1) app/config.py — إضافة مفتاح service_role
المسارات الجديدة تحتاج `SUPABASE_SERVICE_ROLE_KEY` (وليس الـ anon key) لكتابة نتيجة المهمة
ورفع الفيديو الناتج إلى Storage. أضف هذا السطر داخل `class Settings` بجانب `supabase_anon_key`:

```python
    supabase_service_key: str = field(default_factory=lambda: _env("SUPABASE_SERVICE_ROLE_KEY"))
```

وفي `.env.example` أضف:
```
SUPABASE_SERVICE_ROLE_KEY=
YTDLP_ENABLED=true
```

## 2) requirements.txt — إضافة yt-dlp
```
# YouTube source download (Unlicense)
yt-dlp==2025.9.26
```
(تحقق من أحدث إصدار وقت التنفيذ الفعلي)

## 3) app/main.py — تسجيل الراوتر الجديد
بعد سطر `from .rooms import Participant, rooms` أضف:
```python
from .dub_routes import router as dub_router
```
وبعد `app.add_middleware(CORSMiddleware, ...)` أضف:
```python
app.include_router(dub_router)
```

## 4) Dockerfile
لا تعديل مطلوب — `ffmpeg` مثبت بالفعل في الصورة الحالية، و`yt-dlp` سيُثبَّت تلقائيًا مع
`pip3 install -r requirements.txt`.

## 5) docker-compose.yml / متغيرات البيئة عند التشغيل
أضف `SUPABASE_SERVICE_ROLE_KEY` كمتغير بيئة سري (من إعدادات Supabase → Project Settings → API
→ service_role) — **لا يوضع هذا المفتاح داخل تطبيق الأندرويد أبدًا**، فقط على هذا الخادم.

## 6) قاعدة البيانات
شغّل `20260927000000_dubbing_feature_skeleton.sql` (في مجلد `supabase/migrations/`) ثم
عدّل `public.dubbing_settings` بالسعر ومدة التجربة المجانية الحقيقيين من الأدمن/المالك.

## 7) دِلاء التخزين (Storage buckets) — اثنان، كلاهما خاص (private)
- `dubbing_uploads` — فيديوهات الهاتف المرفوعة من التطبيق مباشرة (`Backend.storage` في Android).
  المستخدم يكتب في مساره الخاص فقط: `{user_id}/...`.
- `dubbing` — نواتج الدبلجة، يرفعها خادم الدبلجة بمفتاح service_role فقط. القراءة للمستخدم عبر
  signed URL (`DubbingRepository.signedOutputUrl`)، وليس عبر رابط عام أبدًا.

## 8) العمود المفقود في checkout/index.ts (اختياري وليس ضروريًا)
`backend/functions/checkout/index.ts` يمرر `item_type` كسلسلة نصية خامًا لدوال Postgres دون أي
تحقق وقت التشغيل (تعريف TypeScript الموجود سطر 20 هو تلميح فحص فقط)، لذلك تعمل "DUBBING" بدون
أي تعديل على هذا الملف. إن أردت اتساقًا في التوثيق فقط، وسّع النوع إلى:
`item_type: "COURSE" | "LIVE" | "AI_TUTOR" | "DUBBING";`

## ما لم يُختبر بعد (صريح)
هذا الكود لم يُشغَّل على بيئة حقيقية (لا يوجد اتصال شبكة هنا لتشغيل Docker/Supabase فعليًا).
قبل الإنتاج: اختبر رفع فيديو حقيقي، تحقق من دقة مزامنة الصوت مع الفيديو (lip-sync) على فيديوهات
حقيقية وليس عينات قصيرة، واضبط `atempo` وحدود الجودة حسب النتائج.
