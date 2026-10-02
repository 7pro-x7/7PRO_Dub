package com.rork.pro.ui.i18n

/** Tests hub, teacher exercises for learners, and the exercise studio for teachers and staff. */
object StrEx {
    // ---- hub
    val hubTitle = Tr("Tests & exercises", "الاختبارات والتدريبات")
    val hubIntro = Tr(
        "Two different things: the academy places your level, your teacher trains it.",
        "أمران مختلفان: الأكاديمية تحدد مستواك، ومعلمك يدرّبك عليه.",
    )
    val placementCard = Tr("Placement test", "اختبار تحديد المستوى")
    val placementCardSub = Tr(
        "One official test from the academy that shows your level.",
        "اختبار رسمي واحد من الأكاديمية يحدد مستواك.",
    )
    val exercisesCard = Tr("Teacher exercises", "تدريبات المعلمين")
    val exercisesCardSub = Tr(
        "Pick a teacher and practise with the exercises they published.",
        "اختر معلمًا وتدرّب على التمارين التي نشرها.",
    )
    val open = Tr("Open", "فتح")
    // The two halves of [hubIntro], used as the headings of the hub's two groups.
    val hubGroupAcademy = Tr("The academy places your level", "الأكاديمية تحدد مستواك")
    val hubGroupTeacher = Tr("Your teacher trains it", "معلمك يدرّبك عليه")

    // ---- learner: choose a teacher
    val chooseTeacher = Tr("Choose a teacher", "اختر معلمًا")
    val chooseTeacherBody = Tr(
        "Every teacher publishes their own exercises. Pick one to see theirs.",
        "كل معلم ينشر تدريباته الخاصة. اختر معلمًا لعرض تدريباته.",
    )
    val noTeachers = Tr("No teacher exercises yet", "لا توجد تدريبات بعد")
    val noTeachersBody = Tr(
        "No teacher has published an exercise yet. Check back soon.",
        "لم ينشر أي معلم تدريبًا حتى الآن. عد قريبًا.",
    )
    val exercisesCount = Tr("%d exercises", "%d تدريب")
    val teachersKicker = Tr("Practice", "تدريب")
    val teachersCount = Tr("%d teachers", "%d معلم")
    val availableExercises = Tr("Available exercises", "التدريبات المتاحة")
    val noExercisesYetTeacher = Tr("Nothing to practise yet", "لا يوجد ما تتدرب عليه بعد")
    val teacherExercises = Tr("Exercises", "التدريبات")
    val noExercises = Tr("No exercises published", "لا توجد تدريبات منشورة")
    val noExercisesBody = Tr(
        "This teacher has not published an exercise yet.",
        "لم ينشر هذا المعلم أي تدريب بعد.",
    )
    val startExercise = Tr("Start exercise", "ابدأ التدريب")
    val exerciseTag = Tr("Exercise", "تدريب")
    val completeToUnlock = Tr("Score %d%% or higher on the previous exercise to unlock this one", "احصل على %d%% أو أكثر في التدريب السابق لفتح هذا التدريب")
    val retakeLabel = Tr("Retake exercise", "إعادة المحاولة")
    val tryAgainLabel = Tr("Try again", "حاول مرة أخرى")
    val lastScoreLabel = Tr("Last score: %d%%", "آخر نتيجة: %d%%")

    // ---- learner: choose a level (teacher -> level -> exercises)
    val chooseLevel = Tr("Choose a level", "اختر المستوى")
    val chooseLevelBody = Tr(
        "Your teacher organised their exercises into levels. Pick one to see its exercises.",
        "رتّب المعلم تدريباته في مستويات. اختر مستوى لعرض تدريباته.",
    )
    val levelsCount = Tr("%d levels", "%d مستوى")
    val levelTag = Tr("Level %d", "المستوى %d")
    val generalLevel = Tr("General exercises", "تدريبات عامة")
    val levelProgress = Tr("%1${'$'}d of %2${'$'}d passed", "اجتزت %1${'$'}d من %2${'$'}d")
    val levelDone = Tr("Completed", "مكتمل")
    val lockedByTeacher = Tr(
        "Finish the exercises before this one to unlock it",
        "أكمل التدريبات السابقة لفتح هذا التدريب",
    )
    val joinTeacherToStart = Tr("Join this teacher to start", "اشترك مع المعلم للبدء")
    val studentsOnlyNote = Tr(
        "You can browse everything. Only this teacher's students can take the exercises.",
        "يمكنك تصفّح كل شيء. المشاركة في التدريبات لطلاب هذا المعلم فقط.",
    )
    val levelLocked = Tr("Finish the previous level to unlock this one", "أكمل المستوى السابق لفتح هذا المستوى")
    val noExercisesInLevel = Tr("No exercises in this level yet", "لا توجد تدريبات في هذا المستوى بعد")

    // ---- studio entry points
    val myExercises = Tr("My exercises", "تدريباتي")
    val myExercisesSub = Tr("Create, publish and edit your own exercises", "أنشئ وانشر وعدّل تدريباتك")
    val staffMine = Tr("My exercises", "تماريني")
    val staffMineSub = Tr("Exercises you created or copied", "التمارين اللي عملتها أو نسختها")
    val staffAll = Tr("Everyone's exercises", "تمارين الكل")
    val staffAllSub = Tr("Search every teacher's exercises and copy the ones you want to use", "ابحث في تمارين كل المعلمين وانسخ اللي عايز تستخدمه")
    val staffByTeacher = Tr("By teacher", "حسب المعلم")
    val copyToMine = Tr("Copy to mine", "نسخ لتماريني")
    val copiedToMine = Tr("Copied to your exercises as a draft", "اتنسخ لتماريني كمسودة")
    val alreadyMine = Tr("Yours", "تمرينك")
    val allExercises = Tr("Teacher exercises", "تدريبات المعلمين")
    val allExercisesSub = Tr("Review and manage every teacher's exercises", "راجع وأدر تدريبات كل المعلمين")

    // ---- exercise editor
    val newExercise = Tr("New exercise", "تدريب جديد")
    val newExerciseHint = Tr("Build a fresh exercise for your students", "ابنِ تدريبًا جديدًا لطلابك")
    val titleField = Tr("Exercise title", "عنوان التدريب")
    val descriptionField = Tr("Description", "الوصف")
    val questionsField = Tr("Questions asked", "عدد الأسئلة")
    val minutesField = Tr("Minutes", "الدقائق")
    val createExercise = Tr("Create exercise", "إنشاء التدريب")
    val saveChanges = Tr("Save changes", "حفظ التعديلات")
    val edit = Tr("Edit", "تعديل")
    val noExercisesYet = Tr("No exercises yet", "لا توجد تدريبات بعد")
    val noExercisesYetBody = Tr(
        "Create your first exercise, add a few questions, then publish it.",
        "أنشئ تدريبك الأول، أضف بعض الأسئلة، ثم انشره.",
    )
    val noExercisesAdmin = Tr("Nothing published by teachers", "لا توجد تدريبات من المعلمين")
    val noExercisesAdminBody = Tr(
        "Teacher exercises appear here as soon as a teacher creates one.",
        "تظهر هنا تدريبات المعلمين بمجرد إنشاء أي معلم لتدريب.",
    )
    val questionsInBank = Tr("Questions in bank", "أسئلة في البنك")
    val askedPerAttempt = Tr("Asked per attempt", "المطروح في المحاولة")
    val timeLimit = Tr("Time limit", "المدة")
    val by = Tr("By %s", "بواسطة %s")
    val publish = Tr("Publish", "نشر")
    val unpublish = Tr("Unpublish", "إلغاء النشر")
    val delete = Tr("Delete", "حذف")
    val deleteExercise = Tr("Delete exercise?", "حذف التدريب؟")
    val deleteExerciseBody = Tr(
        "The exercise and its questions are removed for everyone. This cannot be undone.",
        "سيُحذف التدريب وأسئلته للجميع. لا يمكن التراجع عن ذلك.",
    )
    val addQuestionsFirst = Tr(
        "Add at least one question before publishing.",
        "أضف سؤالًا واحدًا على الأقل قبل النشر.",
    )
    val close = Tr("Close", "إغلاق")

    // ---- questions
    val questions = Tr("Questions", "الأسئلة")
    val addQuestion = Tr("Add question", "إضافة سؤال")
    val editQuestion = Tr("Edit question", "تعديل السؤال")
    val questionField = Tr("Question", "السؤال")
    val questionKind = Tr("Answer type", "نوع الإجابة")
    val optionsField = Tr("Options (comma separated)", "الخيارات (مفصولة بفاصلة)")
    val correctOption = Tr("Correct option number", "رقم الخيار الصحيح")
    val correctAnswers = Tr("Correct option numbers (e.g. 1,3)", "أرقام الخيارات الصحيحة (مثال: 1,3)")
    val correctText = Tr("Accepted answers (comma separated)", "الإجابات المقبولة (مفصولة بفاصلة)")
    val skill = Tr("Skill", "المهارة")
    val explanation = Tr("Explanation (optional)", "الشرح (اختياري)")
    val noQuestions = Tr("No questions yet", "لا توجد أسئلة بعد")
    val removeQuestion = Tr("Remove", "حذف")

    // ---- media from the phone
    val media = Tr("Media (optional)", "الوسائط (اختياري)")
    val image = Tr("Image", "صورة")
    val audio = Tr("Audio", "صوت")
    val video = Tr("Video", "فيديو")
    val addFromPhone = Tr("Add from phone", "أضف من الهاتف")
    val replaceFile = Tr("Replace", "استبدال")
    val removeFile = Tr("Remove", "إزالة")
    val uploading = Tr("Uploading…", "جارٍ الرفع…")
    val attached = Tr("Attached", "تم الإرفاق")

    // ---- question wizard (add/edit question)
    val stepType = Tr("Type", "النوع")
    val stepContent = Tr("Content", "المحتوى")
    val stepMedia = Tr("Media", "الوسائط")
    val stepPreview = Tr("Preview", "المعاينة")
    val chooseKind = Tr("How will the student answer?", "كيف سيجيب الطالب؟")
    val studentView = Tr("Student view", "معاينة الطالب")
    val teacherView = Tr("Teacher view", "معاينة المعلم")
    val previewHint = Tr(
        "This is exactly what the student sees when this question comes up.",
        "هذا بالضبط ما سيراه الطالب عند وصوله لهذا السؤال.",
    )
    val previewFillFirst = Tr(
        "Finish the conversation in the previous step to preview it.",
        "أكمل المحادثة في الخطوة السابقة لمعاينتها.",
    )
    val previewTypedAnswer = Tr("Student types their answer", "يكتب الطالب إجابته هنا")
    val correctAnswerLabel = Tr("Correct", "صحيحة")
    val questionAdded = Tr("Question added", "تمت إضافة السؤال")
    val questionAddedBody = Tr(
        "It's saved in this exercise's question bank.",
        "تم حفظه في بنك أسئلة هذا التدريب.",
    )
    val addAnotherQuestion = Tr("Add another question", "إضافة سؤال آخر")
    val trueLabel = Tr("True", "صحيح")
    val falseLabel = Tr("False", "خطأ")

    // ---- studio dashboard (redesigned teacher exercise screen)
    val summaryExercises = Tr("Exercises", "التمارين")
    val summaryPublished = Tr("Published", "منشور")
    val summaryQuestions = Tr("Questions", "الأسئلة")
    val readinessLabel = Tr("Publish rate", "نسبة النشر")
    val searchExercises = Tr("Search exercises", "ابحث في التمارين")
    val filterAll = Tr("All", "الكل")
    val filterPublished = Tr("Published", "منشور")
    val filterDraft = Tr("Draft", "مسودة")
    val noMatches = Tr("Nothing matches", "لا توجد نتائج")
    val noMatchesBody = Tr(
        "Try a different word, or clear the filter.",
        "جرّب كلمة أخرى، أو امسح عامل التصفية.",
    )
    val chipQuestions = Tr("%d in bank", "%d في البنك")
    val chipPerAttempt = Tr("%d per attempt", "%d لكل محاولة")
    val chipMinutes = Tr("%d min", "%d دقيقة")
    val readyToPublish = Tr("Ready to publish", "جاهز للنشر")
    val needsMore = Tr("%d more needed", "ناقص %d سؤال")
    val hideQuestions = Tr("Hide questions", "إخفاء الأسئلة")
    val questionBank = Tr("Question bank", "بنك الأسئلة")
    val questionStudioTitle = Tr("Build the perfect challenge", "ابنِ التحدي المثالي")
    val questionStudioSubtitle = Tr("Every great exercise starts with one irresistible question.", "كل تدريب قوي بيبدأ بسؤال يشد الطالب من أول لحظة.")
    val questionBankHook = Tr("Shape the student experience", "صمّم تجربة الطالب")
    val questionBankHookBody = Tr("Add, refine, and organize questions that turn practice into progress.", "أضف وعدّل ورتّب الأسئلة اللي بتحوّل التدريب لتقدّم حقيقي.")
    val questionsReady = Tr("questions ready", "سؤال جاهز")
    val studentResults = Tr("Student results", "نتائج الطلاب")
    val results = Tr("Results", "النتائج")
    val summaryAttempts = Tr("Attempts", "المحاولات")
    val summaryAverage = Tr("Average", "المتوسط")
    val unknownStudent = Tr("Student", "طالب")
    val attemptReview = Tr("Attempt review", "مراجعة المحاولة")
    val noAttempts = Tr("No one has sat this yet", "لم يحلّه أحد بعد")
    val noAttemptsBody = Tr(
        "Results appear here once a student finishes the exercise.",
        "تظهر النتائج هنا بمجرد أن يُنهي طالب التمرين.",
    )
    val noStoredAnswers = Tr("Nothing stored", "لا توجد إجابات محفوظة")
    val noStoredAnswersBody = Tr(
        "This attempt has no saved answers to review.",
        "هذه المحاولة ليس بها إجابات محفوظة لمراجعتها.",
    )
    val levels = Tr("Levels", "الليفلات")
    val noLevelsYet = Tr("None yet", "لا يوجد بعد")
    val levelName = Tr("Level name", "اسم الليفل")
    val addLevel = Tr("Add level", "إضافة ليفل")
    val fileUnderLevel = Tr("File under level", "صنّفه تحت ليفل")
    val noLevel = Tr("No level", "بدون ليفل")
    val linkToCourse = Tr("Link to a course", "اربطه بكورس")
    val noCourseLink = Tr("No course", "بدون كورس")

    // ---- creating: pick the level up front
    val chooseLevelForNew = Tr("Level", "الليفل")
    val chooseLevelForNewHint = Tr(
        "Students will find this exercise under the level you pick.",
        "الطلاب هيلاقوا التدريب ده جوه الليفل اللي تختاره.",
    )
    val pickLevelFirst = Tr("Pick a level to create the exercise", "اختر ليفل عشان تنشئ التدريب")
    val noLevelsForNew = Tr(
        "You have no levels yet — add one from “Levels” below, or create it without a level.",
        "لسه مفيش ليفلات — أضف ليفل من «الليفلات» تحت، أو أنشئ التدريب بدون ليفل.",
    )

    val moveToFirst = Tr("Move to first", "نقل لأول التدريبات")
    val moveToLast = Tr("Move to last", "نقل لآخر التدريبات")
    val moveToPositionTitle = Tr("Move to position", "نقل لمكان معين")
    val moveToPositionBody = Tr("Pick where this exercise should sit.", "اختر المكان اللي عايز التدريب يقف فيه.")
    val currentPosition = Tr("Current", "الحالي")
    val positionOf = Tr("%d of %d", "%d من %d")
    val levelButtonMeta = Tr("%d exercises · %d published", "%d تدريب · %d منشور")

    // ---- copying the owner's exercises
    val copyFromOwner = Tr("Copy from the owner's exercises", "نسخ من تدريبات المالك")
    val copyFromOwnerTitle = Tr("Owner's exercises", "تدريبات المالك")
    val copyFromOwnerBody = Tr(
        "Copy a whole level at once, or open a level and copy only the exercises you want. The copy is yours: it starts as a draft, and changing it never affects the original.",
        "انسخ الليفل كله مرة واحدة، أو افتح الليفل وانسخ التدريبات اللي تحتاجها بس. النسخة بتاعتك: بتبدأ مسودة، وأي تعديل عليها مش بيأثر على الأصل.",
    )
    val copyFromOwnerEmpty = Tr(
        "The owner has no published exercises to copy yet.",
        "المالك ماعندهوش تدريبات منشورة للنسخ لسه.",
    )
    val copyQuestionsCount = Tr("%s questions", "%s سؤال")
    val copiedBefore = Tr("Already copied", "تم نسخه قبل كده")
    val copyLevelAll = Tr("Copy the whole level (%s)", "نسخ الليفل كله (%s)")
    val copyLevelOpen = Tr("Open the level", "فتح الليفل")
    val copyLevelBack = Tr("All levels", "كل الليفلات")
    val copyLevelExercises = Tr("%s exercises", "%s تدريب")
    val copyLevelAllCopied = Tr("All copied", "اتنسخ كله")
    val copyUnfiled = Tr("Exercises without a level", "تدريبات بدون ليفل")
    val copyLevelDone = Tr("%s exercises copied as drafts into a level of yours", "اتنسخ %s تدريب كمسودة في ليفل عندك")
    val copyLevelNothing = Tr("Everything in this level is already copied", "كل تدريبات الليفل ده منسوخة قبل كده")
    val copyDone = Tr("Copied to your exercises as a draft", "اتنسخ لتدريباتك كمسودة")

    // ---- moving a question to another exercise
    val moveQuestion = Tr("Move", "نقل")
    val moveQuestionTitle = Tr("Move to another exercise", "نقل لتدريب آخر")
    val moveQuestionBody = Tr(
        "The question leaves this exercise and is added at the end of the one you pick.",
        "السؤال هيتشال من التدريب ده ويتضاف في آخر التدريب اللي تختاره.",
    )
    val moveNoTargets = Tr(
        "Create another exercise first to move questions into it.",
        "أنشئ تدريب تاني الأول عشان تنقل له أسئلة.",
    )
    val moveDropsBelow = Tr(
        "This exercise is published and will have fewer questions than one attempt asks for.",
        "التدريب ده منشور وهيبقى فيه أسئلة أقل من المطلوب في المحاولة الواحدة.",
    )
    val moveInLevel = Tr("Level: %s", "الليفل: %s")
    val lockExercise = Tr("Lock exercise", "قفل التمرين")
    val unlockExercise = Tr("Unlock exercise", "فتح التمرين")
    val lockQuestion = Tr("Lock", "قفل")
    val unlockQuestion = Tr("Unlock", "فتح")
    val lockedChip = Tr("Locked", "مقفول")
}
