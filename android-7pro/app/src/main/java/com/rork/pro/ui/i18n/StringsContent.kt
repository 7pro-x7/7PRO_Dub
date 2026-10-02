package com.rork.pro.ui.i18n

/** Reading lessons (blocks + downloads), exercises inside courses, and the merged Content hub. */
object StrContent {
    // Content hub
    val hubTitle = Tr("Content", "المحتوى")
    val hubSub = Tr("Courses, lessons and exercises in one place", "الدورات والدروس والتمارين في مكان واحد")
    val tabCourses = Tr("Courses", "الدورات")
    val tabExercises = Tr("Exercise bank", "بنك التمارين")
    val tile = Tr("Content", "المحتوى")

    // Lesson blocks
    val blockHeading = Tr("Heading", "عنوان")
    val blockParagraph = Tr("Paragraph", "فقرة")
    val blockImage = Tr("Image", "صورة")
    val blockList = Tr("List", "قائمة")
    val addBlock = Tr("+ %s", "+ %s")
    val lessonContent = Tr("Lesson content", "محتوى الدرس")
    val blocksHint = Tr("Build the lesson from blocks; use the arrows to reorder.", "ابنِ الدرس من كتل، ورتّبها بالأسهم.")
    val noBlocks = Tr("No content yet — add a heading, paragraph, image or list.", "لسه مفيش محتوى — أضف عنوان أو فقرة أو صورة أو قائمة.")
    val headingText = Tr("Heading text", "نص العنوان")
    val paragraphText = Tr("Paragraph", "نص الفقرة")
    val listItems = Tr("List items — one per line", "عناصر القائمة — كل عنصر في سطر")
    val imageCaption = Tr("Caption (optional)", "تعليق على الصورة (اختياري)")
    val pickImage = Tr("Choose image", "اختيار صورة")
    val replaceImage = Tr("Replace image", "تغيير الصورة")
    val removeBlock = Tr("Remove block", "حذف الكتلة")
    val readingLesson = Tr("Reading lesson · text & images", "درس مقروء · نص وصور")

    // Downloads
    val downloadsTitle = Tr("Downloads for students", "التحميل للطلاب")
    val allowPdf = Tr("PDF file (text and images)", "ملف PDF (بالنص والصور)")
    val allowTxt = Tr("TXT file (text only)", "ملف TXT (نص فقط)")
    val downloadsHint = Tr(
        "Files are generated from the lesson automatically. Optionally attach a ready PDF to use instead.",
        "الملفات بتتولّد تلقائيًا من محتوى الدرس. ولو حابب، ارفع ملف PDF جاهز يتحمّل بدلها.",
    )
    val readyPdf = Tr("Ready-made PDF (optional)", "ملف PDF جاهز (اختياري)")
    val downloadPdf = Tr("Download PDF", "تحميل PDF")
    val downloadTxt = Tr("Download TXT", "تحميل TXT")
    val preparing = Tr("Preparing file…", "بيتجهز الملف…")
    val savedToDownloads = Tr("Saved to Downloads/7PRO", "اتحفظ في التنزيلات/7PRO")
    val open = Tr("Open", "فتح")
    val downloadFailed = Tr("Couldn't create the file. Try again.", "معرفناش نجهز الملف. جرّب تاني.")

    // Exercises inside courses
    val linkExercise = Tr("Link exercise", "ربط تمرين")
    val linkedFromBank = Tr("Linked from the exercise bank", "مربوط من بنك التمارين")
    val questionsShort = Tr("%d questions", "%d سؤال")
    val usedInCourses = Tr("Used in %d courses", "مستخدم في %d دورة")
    val unlink = Tr("Remove from course", "إزالة من الدورة")
    val draftBadge = Tr("Draft", "مسودة")
    val linkSheetTitle = Tr("Link exercises to the course", "ربط تمارين بالدورة")
    val scopeMine = Tr("My exercises", "تماريني")
    val scopeAll = Tr("All exercises", "كل التمارين")
    val scopeAllHint = Tr(
        "\"All exercises\" is shown to the owner and permitted admins only.",
        "«كل التمارين» تظهر للمالك وللأدمن المصرّح له فقط.",
    )
    val searchExercises = Tr("Search by exercise or teacher", "ابحث باسم التمرين أو المعلم")
    val addTo = Tr("Add to", "يُضاف إلى")
    val noUnit = Tr("No unit", "بدون وحدة")
    val alreadyLinked = Tr("Already in this course", "مربوط بهذه الدورة بالفعل")
    val linkCount = Tr("Link %d exercises", "ربط %d تمرين")
    val linkNote = Tr(
        "Linking doesn't copy: any edit to the exercise shows in every linked course.",
        "الربط لا ينسخ التمرين: أي تعديل عليه يظهر في كل الدورات المربوطة.",
    )
    val noLinkable = Tr("No exercises match.", "مفيش تمارين مطابقة.")
    val linkLocked = Tr(
        "Linking exercises to your courses is switched off for your account. Ask the owner to enable it.",
        "ربط التمارين بدوراتك مقفول لحسابك. اطلب من المالك تفعيله.",
    )
    val unitExercises = Tr("Unit exercises", "تمارين الوحدة")
    val courseExercises = Tr("Course exercises", "تمارين الدورة")
    val start = Tr("Start", "ابدأ")
    val linkedCourses = Tr("Linked courses (tap to add or remove)", "الدورات المربوطة (اضغط للإضافة أو الإزالة)")

    // Admin permission
    val permCourseContent = Tr("Course lessons & linked exercises", "دروس الدورات وربط التمارين")
}
