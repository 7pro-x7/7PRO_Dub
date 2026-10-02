package com.rork.pro.ui.i18n

/** Course authoring: the shared manager list and the course editor used by owners and teachers. */
object StrStudio {
    // Manager list
    val myCourses = Tr("My courses", "دوراتي")
    val allCourses = Tr("All courses", "كل الدورات")
    val manageAllCourses = Tr("Create and manage every course on the platform", "أنشئ وأدر كل دورة على المنصة")
    val manageMyCourses = Tr("Create and manage your own courses", "أنشئ وأدر دوراتك الخاصة")
    val newCourse = Tr("New course", "دورة جديدة")
    val searchCourses = Tr("Search courses", "ابحث في الدورات")
    val allStatuses = Tr("All", "الكل")
    val noCoursesYet = Tr("No courses yet", "لا توجد دورات بعد")
    val noCoursesBody = Tr(
        "Create your first course, add sections and lessons, then publish it.",
        "أنشئ دورتك الأولى، أضف الأقسام والدروس، ثم انشرها.",
    )
    val noCoursesMatch = Tr("No course matched this filter.", "لا توجد دورة تطابق هذا الفلتر.")
    val createCourseTitle = Tr("Create a course", "إنشاء دورة")
    val courseTitleHint = Tr("Course title", "عنوان الدورة")
    val ownedBy = Tr("Teacher", "المعلم")
    val chooseTeacher = Tr("Course owner", "مالك الدورة")
    val create = Tr("Create", "إنشاء")
    val cancel = Tr("Cancel", "إلغاء")
    val lessonsCount = Tr("%d lessons", "%d درس")
    val sectionsCount = Tr("%d sections", "%d قسم")
    val enrolledCount = Tr("%d enrolled", "%d ملتحق")

    // Editor — shell
    val editCourse = Tr("Edit course", "تعديل الدورة")
    val details = Tr("Course details", "تفاصيل الدورة")
    val curriculum = Tr("Curriculum", "المنهج")
    val pricing = Tr("Pricing", "التسعير")
    val saveDraft = Tr("Save draft", "حفظ كمسودة")
    val saved = Tr("Saved", "تم الحفظ")
    val publish = Tr("Publish", "نشر")
    val submitForReview = Tr("Submit for review", "إرسال للمراجعة")
    val unpublish = Tr("Move back to draft", "إعادة إلى المسودة")
    val publishedNotice = Tr("This course is live for students.", "هذه الدورة متاحة للطلاب الآن.")
    val reviewNotice = Tr(
        "Waiting for the owner to review this course.",
        "بانتظار مراجعة المالك لهذه الدورة.",
    )
    val reviewRequiredNotice = Tr(
        "Publishing sends this course to the owner for review.",
        "النشر يرسل هذه الدورة إلى المالك للمراجعة.",
    )
    val draftNotice = Tr(
        "Only you can see this draft. Publish it when it is ready.",
        "أنت وحدك من يرى هذه المسودة. انشرها عندما تكون جاهزة.",
    )
    val rejectedNotice = Tr("Changes were requested:", "تم طلب تعديلات:")

    // Editor — details
    val cover = Tr("Cover image", "صورة الغلاف")
    val addCover = Tr("Add a cover image", "أضف صورة غلاف")
    val replaceCover = Tr("Replace", "استبدال")
    val removeCover = Tr("Remove", "إزالة")
    val titleField = Tr("Title", "العنوان")
    val subtitleField = Tr("Short subtitle", "عنوان فرعي قصير")
    val descriptionField = Tr("Description", "الوصف")
    val levelField = Tr("Level", "المستوى")
    val categoryField = Tr("Category", "التصنيف")
    val noCategory = Tr("None", "بدون")
    val newCategoryField = Tr("New category name", "اسم تصنيف جديد")
    val addCategoryAction = Tr("Add", "إضافة")
    val priceField = Tr("Price", "السعر")
    val currencyField = Tr("Currency", "العملة")
    val freeCourse = Tr("Free course", "دورة مجانية")
    val freeCourseSub = Tr("Students enroll without paying", "يلتحق الطلاب دون دفع")
    val certificate = Tr("Certificate on completion", "شهادة عند الإتمام")
    val certificateSub = Tr("Issue a certificate when the course is finished", "إصدار شهادة عند إنهاء الدورة")

    // Editor — curriculum
    val addSection = Tr("Add section", "إضافة قسم")
    val sectionTitle = Tr("Section title", "عنوان القسم")
    val renameSection = Tr("Rename section", "إعادة تسمية القسم")
    val deleteSection = Tr("Delete section", "حذف القسم")
    val deleteSectionBody = Tr(
        "Lessons inside stay in the course and move to the ungrouped list.",
        "تبقى الدروس داخل الدورة وتنتقل إلى القائمة غير المصنفة.",
    )
    val ungrouped = Tr("Ungrouped lessons", "دروس غير مصنفة")
    val noLessonsInSection = Tr("No lessons in this section yet.", "لا توجد دروس في هذا القسم بعد.")
    val emptyCurriculum = Tr(
        "Add a section to group your lessons, or add a lesson directly.",
        "أضف قسمًا لتجميع دروسك، أو أضف درسًا مباشرة.",
    )
    val addLesson = Tr("Add lesson", "إضافة درس")
    val editLesson = Tr("Edit lesson", "تعديل الدرس")
    val newLesson = Tr("New lesson", "درس جديد")
    val lessonTitle = Tr("Lesson title", "عنوان الدرس")
    val lessonKind = Tr("Lesson type", "نوع الدرس")
    val lessonText = Tr("Lesson text", "نص الدرس")
    val minutesField = Tr("Minutes", "الدقائق")
    val freePreview = Tr("Free preview", "معاينة مجانية")
    val freePreviewSub = Tr("Anyone can watch this lesson", "يمكن لأي شخص مشاهدة هذا الدرس")
    val moveUp = Tr("Move up", "تحريك لأعلى")
    val moveDown = Tr("Move down", "تحريك لأسفل")
    val delete = Tr("Delete", "حذف")
    val save = Tr("Save", "حفظ")
    val previewBadge = Tr("Preview", "معاينة")

    // Media
    val media = Tr("Media", "الوسائط")
    val uploadVideo = Tr("Upload video", "رفع فيديو")
    val uploadDocument = Tr("Upload file", "رفع ملف")
    val uploadImage = Tr("Upload image", "رفع صورة")
    val uploading = Tr("Uploading…", "جارٍ الرفع…")
    val orPasteLink = Tr("Or paste a link", "أو الصق رابطًا")
    val mediaUrl = Tr("Media link", "رابط الوسائط")
    val attached = Tr("File attached", "تم إرفاق ملف")
    val uploadLimit = Tr("Up to 50 MB per file", "حتى ٥٠ ميجابايت لكل ملف")

    // Danger zone
    val dangerZone = Tr("Danger zone", "منطقة الخطر")
    val archiveCourse = Tr("Archive course", "أرشفة الدورة")
    val archiveCourseSub = Tr(
        "Hide it from students while keeping enrollments and history.",
        "إخفاؤها عن الطلاب مع الاحتفاظ بالالتحاقات والسجل.",
    )
    val deleteCourse = Tr("Delete course", "حذف الدورة")
    val deleteCourseSub = Tr(
        "Only possible while nobody has enrolled or paid.",
        "ممكن فقط طالما لم يلتحق أو يدفع أحد.",
    )
    val deleteCourseConfirm = Tr(
        "Delete this course permanently? Its sections and lessons go with it.",
        "حذف هذه الدورة نهائيًا؟ ستُحذف أقسامها ودروسها معها.",
    )
    val confirmDelete = Tr("Yes, delete", "نعم، احذف")

    // Force delete (owner/admin only)
    val forceDeleteCourse = Tr("Force delete course", "حذف الدورة بالقوة")
    val forceDeleteCourseSub = Tr(
        "Delete this course even if students are enrolled. This cannot be undone.",
        "حذف هذه الدورة حتى لو هناك طلاب ملتحقون. لا يمكن التراجع عن هذا الإجراء.",
    )
    val forceDeleteCourseConfirm = Tr(
        "This will permanently delete the course AND remove all enrolled students. Continue?",
        "سيؤدي هذا إلى حذف الدورة نهائيًا وإزالة جميع الطلاب الملتحقين. هل تريد المتابعة؟",
    )
}
