package com.rork.pro.ui.i18n

/** Virtual Classroom: hub card, session list, lobby, in-call panel, whiteboard. */
object StrClassroom {
    val hubCard = Tr("Virtual classroom", "الفصل الافتراضي")
    val hubCardSub = Tr("Live video lessons with your teacher", "حصص فيديو مباشرة مع معلمك")

    val title = Tr("Virtual classroom", "الفصل الافتراضي")
    val upcoming = Tr("Upcoming", "القادمة")
    val live = Tr("Live now", "مباشرة الآن")
    val past = Tr("Past sessions", "الحصص السابقة")
    val noSessions = Tr("No sessions yet", "لا توجد حصص بعد")
    val noSessionsBody = Tr("Sessions your teacher schedules for you will show up here.", "ستظهر هنا الحصص التي يحددها معلمك لك.")
    val noSessionsTeacherBody = Tr("Schedule your first live session for your students.", "حدد أول حصة مباشرة لطلابك.")
    val newSession = Tr("New session", "حصة جديدة")

    // Empty-state hero (see ClassroomEmptyState). Sessions start the moment they are created and
    // every invited student is notified, so the copy promises exactly that and nothing more.
    val emptyTeacherTitle = Tr("Your first live class starts here", "أول حصة مباشرة تبدأ من هنا")
    val emptyTeacherBody = Tr(
        "Pick your students and tap create. The class goes live right away and every student gets an invite.",
        "اختر طلابك واضغط إنشاء. تبدأ الحصة فورًا وتصل الدعوة لكل طالب.",
    )
    val emptyStudentTitle = Tr("Your first live class is on its way", "حصتك المباشرة الأولى في الطريق إليك")
    val emptyStudentBody = Tr(
        "When your teacher invites you, you'll get a notification and the class will show up here, ready to join.",
        "عندما يدعوك معلمك سيصلك إشعار وتظهر الحصة هنا جاهزة للانضمام.",
    )
    val createFirstSession = Tr("Create your first session", "إنشاء أول حصة")

    val joinNow = Tr("Join now", "انضم الآن")

    /** Shown instead of the lobby on Android 7 and older, where the video SDK cannot run. */
    val unsupportedTitle = Tr("Live classes need a newer Android", "الحصص المباشرة تحتاج نسخة أندرويد أحدث")
    val unsupportedBody = Tr(
        "Live video inside the app needs Android 8.0 or newer. You can still join from your browser. Everything else in the app works normally.",
        "الفيديو المباشر داخل التطبيق يتطلب أندرويد 8.0 أو أحدث. تقدر تدخل الحصة من المتصفح. باقي ميزات التطبيق تعمل بشكل طبيعي.",
    )
    val openInBrowser = Tr("Open the class in your browser", "افتح الحصة في المتصفح")
    val startsIn = Tr("Starts in %s", "تبدأ خلال %s")
    val scheduledFor = Tr("Scheduled for %s", "مجدولة في %s")
    val ended = Tr("Ended", "انتهت")
    val canceled = Tr("Canceled", "أُلغيت")
    val expired = Tr("Expired", "منتهية الصلاحية")
    val liveNow = Tr("Live", "مباشرة")
    val minutesShort = Tr("%d min", "%d د")

    // Create / edit session
    val createSession = Tr("Create session", "إنشاء حصة")
    val sessionTitle = Tr("Session title", "عنوان الحصة")

    /** Fallback name for a session the teacher no longer titles by hand. */
    val autoSessionTitle = Tr("Live session", "حصة مباشرة")

    /** The normal generated name: "Live session • 15 Sep • 7:30 PM". */
    val autoSessionTitleAt = Tr("Live session • %s", "حصة مباشرة • %s")
    val maxParticipants = Tr("Maximum participants", "أقصى عدد للمشاركين")
    val inviteStudents = Tr("Invite students", "دعوة الطلاب")
    val searchStudents = Tr("Search students by name or email", "ابحث عن الطلاب بالاسم أو البريد")
    val noStudentsFound = Tr("No students found", "لا يوجد طلاب مطابقون")
    val studentsInvited = Tr("%d students invited", "تمت دعوة %d طالب")
    val noLinkedStudents = Tr(
        "None of your students have a linked account yet. Link an account from Subscriptions to invite them here.",
        "لا يوجد لديك طلاب مرتبطون بحساب بعد. اربط حساب الطالب من صفحة الاشتراكات حتى تقدر تدعوه هنا.",
    )
    val selectWholeGroup = Tr("Select whole group", "اختيار الجروب كامل")
    val groupSelectedCount = Tr("%d of %d selected", "تم اختيار %d من %d")
    val create = Tr("Create", "إنشاء")
    val cancelSession = Tr("Cancel session", "إلغاء الحصة")
    val cancelSessionConfirm = Tr("Cancel this session? Every invited student will be notified.", "إلغاء هذه الحصة؟ سيتم إشعار جميع الطلاب المدعوين.")

    // Lobby / pre-join
    val lobbyTitle = Tr("Ready to join?", "هل أنت مستعد للانضمام؟")

    /** Pulsing badge above the session title on the redesigned lobby hero. */
    val lobbyBadge = Tr("Ready when you are", "جاهزة في انتظارك")

    /** "with <teacher name>" under the session title. */
    val lobbyWithTeacher = Tr("with %s", "مع %s")

    /** One-line hook under the title — sells the moment, not just states the state. */
    val lobbyHook = Tr(
        "One tap and you're face-to-face with your teacher. Don't keep the class waiting.",
        "خطوة واحدة وتبقى وش في وش مع معلمك — الحصة مستنياك.",
    )
    val lobbyVideoReady = Tr("Video ready", "الفيديو جاهز")
    val lobbyAudioReady = Tr("Audio ready", "الصوت جاهز")
    val checkingAccess = Tr("Checking your access…", "جارٍ التحقق من صلاحيتك…")
    val notAuthorized = Tr("You are not invited to this session", "أنت غير مدعو لهذه الحصة")
    val tooEarly = Tr("This session hasn't opened for joining yet", "لم يفتح باب الانضمام لهذه الحصة بعد")
    val sessionFull = Tr("This session is full right now", "هذه الحصة ممتلئة حاليًا")
    val sessionEndedError = Tr("This session has already ended", "انتهت هذه الحصة بالفعل")
    val sessionCanceledError = Tr("This session was canceled", "تم إلغاء هذه الحصة")
    val sessionExpiredError = Tr(
        "This session expired because it never started",
        "انتهت صلاحية هذه الحصة لأنها لم تبدأ في موعدها",
    )
    val participantsCount = Tr("%d participants", "%d مشارك")

    // In call
    val endForEveryone = Tr("End for everyone", "إنهاء الحصة للجميع")
    val leaveCall = Tr("Leave", "مغادرة")
    val endSessionConfirm = Tr("End this session for everyone now?", "إنهاء هذه الحصة للجميع الآن؟")
    val weakConnection = Tr("Weak connection — reconnecting…", "الاتصال ضعيف — جارٍ إعادة الاتصال…")

    /**
     * Two separate messages instead of one alarming red line for every hiccup: the socket being
     * briefly down while the internet is fine is a quiet amber "syncing", and only a real loss of
     * connectivity is red.
     */
    val syncing = Tr("Syncing…", "جارٍ المزامنة…")
    val offline = Tr("No internet connection", "لا يوجد اتصال بالإنترنت")
    val reconnectingCall = Tr("Reconnecting…", "جارٍ إعادة الاتصال…")
    val reconnected = Tr("Back online", "تم الاتصال من جديد")
    /** Shown in green once [ConnectionState.LIVE] is reached — see CallScreen's status row. */
    val connectionActive = Tr("Active", "نشط")
    /**
     * Shown when [JitsiMeetView.join] never reaches CONFERENCE_JOINED within the join-watchdog
     * window in ClassroomCallActivity — the fix for the call silently spinning on "Joining…"
     * forever with no feedback and no way out other than force-closing the app.
     */
    val joinTimedOut = Tr(
        "Couldn't join the meeting. Check your connection and try again.",
        "تعذّر الانضمام إلى الاجتماع. تحقّق من اتصالك بالإنترنت وحاول مرة أخرى.",
    )
    val retryJoin = Tr("Retry", "إعادة المحاولة")

    // Hand raise
    val raiseHand = Tr("Raise hand", "رفع اليد")
    val lowerHand = Tr("Lower hand", "خفض اليد")
    val raisedHands = Tr("Raised hands", "الأيدي المرفوعة")
    val noRaisedHands = Tr("No raised hands", "لا توجد أيدٍ مرفوعة")
    val noOneElseJoined = Tr("No one else has joined yet", "لا يوجد أحد غيرك انضم بعد")

    // Chat / Q&A
    val chat = Tr("Chat", "الدردشة")
    val askQuestion = Tr("Ask a question", "اطرح سؤالًا")
    val typeMessage = Tr("Type a message…", "اكتب رسالة…")
    val questionsTab = Tr("Questions", "الأسئلة")
    val markAnswered = Tr("Mark answered", "تحديد كمجاب عنه")
    val answered = Tr("Answered", "تمت الإجابة")
    val noChatMessages = Tr("No messages yet", "لا توجد رسائل بعد")
    val noQuestionsYet = Tr("No questions yet", "لا توجد أسئلة بعد")

    // Whiteboard
    val whiteboard = Tr("Whiteboard", "السبورة")
    val videoTab = Tr("Video", "الفيديو")
    val pen = Tr("Pen", "قلم")
    val eraser = Tr("Eraser", "ممحاة")
    val clearBoard = Tr("Clear board", "مسح السبورة")
    val uploadBackground = Tr("Add image, video or PDF", "إضافة صورة أو فيديو أو PDF")
    val clearBoardConfirm = Tr("Clear the whiteboard for everyone?", "مسح السبورة لجميع المشاركين؟")
    val pdfLoadFailed = Tr(
        "Couldn't load the shared PDF page. Check your connection.",
        "تعذّر تحميل صفحة الـ PDF المشتركة. تحقق من اتصالك.",
    )

    // Attendance
    val attendance = Tr("Attendance", "الحضور")
    val joinedAt = Tr("Joined %s", "انضم في %s")
    val leftAt = Tr("Left %s", "غادر في %s")
    val neverJoined = Tr("Did not join", "لم ينضم")

    // Mic / camera / screen share (bottom control bar)
    val muteMic = Tr("Mute microphone", "كتم المايك")
    val unmuteMic = Tr("Unmute microphone", "إلغاء كتم المايك")
    val turnOffCamera = Tr("Turn off camera", "إيقاف الكاميرا")
    val turnOnCamera = Tr("Turn on camera", "تشغيل الكاميرا")
    val shareScreen = Tr("Share screen", "مشاركة الشاشة")
    val stopSharingScreen = Tr("Stop sharing screen", "إيقاف مشاركة الشاشة")
    val screenShareActive = Tr("You are sharing your screen", "أنت تشارك شاشتك الآن")
    val stopSharing = Tr("Stop", "إيقاف")

    // Image / file upload
    val addImage = Tr("Add an image", "إضافة صورة")
    val fromGallery = Tr("Photo or video from gallery", "صورة أو فيديو من المعرض")
    val fromFiles = Tr("Photo, video or PDF from files", "صورة أو فيديو أو ملف PDF من ملفات الهاتف")
    val imageShownToStudents = Tr("The image is now visible to all students", "الصورة ظاهرة الآن لجميع الطلاب")
    val shareMaterial = Tr("Share photo or video", "مشاركة صورة أو فيديو")

    // Zoom-style call chrome (top bar, bottom toolbar, "More"/"Share" sheets)
    val more = Tr("More", "المزيد")
    val shareShort = Tr("Share", "مشاركة")
    val muteShort = Tr("Mute", "كتم")
    val unmuteShort = Tr("Unmute", "إلغاء الكتم")
    val startVideo = Tr("Start video", "بدء الفيديو")
    val stopVideo = Tr("Stop video", "إيقاف الفيديو")
    val endShort = Tr("End", "إنهاء")
    val flipCamera = Tr("Switch camera", "تبديل الكاميرا")
    val pinchToZoom = Tr("Pinch to zoom", "اسحب بإصبعين للتكبير")
    val uploadingShare = Tr("Uploading…", "جارٍ الرفع…")

    // The "material shared" banner on the main call screen — shown to everyone the moment the
    // teacher shares something, without anyone having to switch to the whiteboard to notice it.
    val sharedMediaBannerImage = Tr("An image was shared", "تمت مشاركة صورة")
    val sharedMediaBannerVideo = Tr("A video was shared", "تمت مشاركة فيديو")
    val sharedMediaBannerFile = Tr("A file was shared", "تمت مشاركة ملف")
    val viewSharedMedia = Tr("View", "عرض")
    val presentingNow = Tr("Someone is presenting their screen", "جارٍ مشاركة الشاشة الآن")

    // Search
    val searchSessions = Tr("Search classes", "ابحث عن حصة")
    val searchByNameOrEmail = Tr("Search by name or email", "ابحث بالاسم أو البريد")
    val noResults = Tr("No results", "لا توجد نتائج")

    val micLockedByTeacher = Tr("Your microphone is locked by the teacher", "قام المعلم بقفل المايك الخاص بك")
    val cameraLockedByTeacher = Tr("Your camera is locked by the teacher", "قام المعلم بقفل الكاميرا الخاصة بك")

    // Invite link (copy-to-share)
    val copyInviteLink = Tr("Copy invite link", "نسخ رابط الحصة")
    val linkCopied = Tr("Link copied — the student can open it in a browser", "تم نسخ الرابط — يقدر الطالب يفتحه من المتصفح")

    // Incoming call (push)
    val incomingCall = Tr("Incoming class", "حصة واردة")
    val acceptCall = Tr("Accept", "قبول")
    val declineCall = Tr("Decline", "رفض")

    // Teacher participant controls
    val participantsTab = Tr("Participants", "المشاركون")
    val lockMic = Tr("Lock mic", "قفل المايك")
    val unlockMic = Tr("Unlock mic", "فتح المايك")
    val lockCamera = Tr("Lock camera", "قفل الكاميرا")
    val unlockCamera = Tr("Unlock camera", "فتح الكاميرا")

    // Admin
    val adminAllSessions = Tr("Virtual classrooms", "الفصول الافتراضية")
    val adminAllSessionsSub = Tr("Every teacher's scheduled and live sessions", "حصص جميع المعلمين المجدولة والمباشرة")
    val lastCrashTitle = Tr("Something went wrong last time", "حدث خطأ غير متوقع في المرة السابقة")
    val copyText = Tr("Copy text", "نسخ النص")

    // Meeting fixes round
    val sessionEndedNotice = Tr("The class has ended", "انتهت الحصة")
    val durationTitle = Tr("Class length", "مدة الحصة")
    val startTitle = Tr("Start", "موعد البداية")
    val startNow = Tr("Now", "الآن")
    val startIn1h = Tr("In 1 hour", "بعد ساعة")
    val startTomorrow = Tr("Tomorrow", "بكرة")
    val startLaterHint = Tr(
        "Students get the invite now and can join 10 minutes before the start.",
        "الطلاب هيوصلهم الدعوة دلوقتي، ويقدروا يدخلوا قبل الموعد بـ 10 دقايق.",
    )
    val muteAllStudents = Tr("Mute all", "كتم الجميع")
    val unmuteAllStudents = Tr("Unmute all", "فتح المايكات")
    val removeStudent = Tr("Remove from class", "إخراج من الحصة")
    val removeStudentConfirm = Tr("Remove %s from this class? They won't be able to rejoin.", "إخراج %s من الحصة؟ مش هيقدر يرجع تاني.")
    val removedFromClass = Tr("You were removed from this class", "تم إخراجك من الحصة")
    val leftOpenClassTitle = Tr("You left a class that's still running", "خرجت من حصة لسه شغالة")
    val leftOpenClassBody = Tr("Tap to get back in.", "اضغط للرجوع للحصة.")
    val crashFriendlyTitle = Tr("The class hit a small problem", "حصلت مشكلة بسيطة في الحصة")
    val crashFriendlyBody = Tr(
        "Nothing was lost. If it keeps happening, copy the details and send them to support.",
        "مفيش حاجة ضاعت. لو اتكررت، انسخ التفاصيل وابعتها للدعم.",
    )
    val copyDetailsForSupport = Tr("Copy details for support", "نسخ التفاصيل للدعم")
    val stopSharingMedia = Tr("End sharing", "إنهاء العرض")
    val hideSharedMedia = Tr("Hide", "إخفاء")
    val showSharedMedia = Tr("Show", "عرض")
    val frameOverlayTitle = Tr("Green frame while sharing", "إطار أخضر أثناء مشاركة الشاشة")
    val frameOverlayBody = Tr(
        "To show the green frame around the screen even when you switch to other apps, allow 7PRO to display over other apps.",
        "عشان يظهر الإطار الأخضر حوالين الشاشة حتى وإنت في تطبيقات تانية، فعّل «الظهور فوق التطبيقات» لـ 7PRO.",
    )
    val frameOverlayEnable = Tr("Allow", "تفعيل")
    val alreadyInClass = Tr("You're already in a live class. Leave it first.", "أنت بالفعل داخل حصة مباشرة. غادرها أولًا.")
}
