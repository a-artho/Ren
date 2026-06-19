package com.hci.ren.feature.pdfupload.presentation

data class PlanSetupUiState(
    val documentUri: String = "",
    val currentStep: PlanSetupStep = PlanSetupStep.Goal,
    val selectedGoal: StudyGoal? = null,
    val selectedDeadline: StudyDeadline? = null,
    val customDeadlineDate: String? = null,
    val customDeadlineLabel: String? = null,
    val selectedDailyTime: DailyStudyTime? = null,
    val customMinutesText: String = "",
    val selectedDays: Set<StudyDay> = emptySet(),
    val isAdvancedMessageVisible: Boolean = false,
    val generatedSubmission: PlanSetupSubmission? = null,
) {
    val canContinue: Boolean
        get() = when (currentStep) {
            PlanSetupStep.Goal -> selectedGoal != null
            PlanSetupStep.Deadline -> when (selectedDeadline) {
                StudyDeadline.ChooseDate -> customDeadlineDate != null
                null -> false
                else -> true
            }
            PlanSetupStep.DailyTime -> when (selectedDailyTime) {
                DailyStudyTime.Custom -> customMinutes != null
                null -> false
                else -> true
            }
            PlanSetupStep.StudyDays -> selectedDays.isNotEmpty()
        }

    val customMinutes: Int?
        get() = customMinutesText.toIntOrNull()?.takeIf { it > 0 }

    val progress: Float
        get() = currentStep.number / PlanSetupStep.entries.size.toFloat()

    fun toSubmission(): PlanSetupSubmission? {
        val goal = selectedGoal ?: return null
        val deadline = selectedDeadline ?: return null
        val dailyTime = selectedDailyTime ?: return null
        val minutes = dailyTime.minutes ?: customMinutes ?: return null
        if (selectedDays.isEmpty()) return null

        return PlanSetupSubmission(
            documentUri = documentUri,
            goal = goal,
            deadline = deadline,
            deadlineDate = customDeadlineDate,
            dailyStudyMinutes = minutes,
            studyDays = selectedDays,
        )
    }
}

data class PlanSetupSubmission(
    val documentUri: String,
    val goal: StudyGoal,
    val deadline: StudyDeadline,
    val deadlineDate: String?,
    val dailyStudyMinutes: Int,
    val studyDays: Set<StudyDay>,
)

enum class PlanSetupStep {
    Goal,
    Deadline,
    DailyTime,
    StudyDays,
}

val PlanSetupStep.number: Int
    get() = ordinal + 1

enum class StudyGoal {
    LearnThoroughly,
    PrepareForExam,
    ReviseKnown,
    FinishQuickly,
    OngoingStudy,
}

enum class StudyDeadline {
    Today,
    InThreeDays,
    InOneWeek,
    ChooseDate,
    NoFixedDeadline,
}

enum class DailyStudyTime(val minutes: Int?) {
    FifteenMinutes(15),
    ThirtyMinutes(30),
    FortyFiveMinutes(45),
    OneHour(60),
    TwoHours(120),
    Custom(null),
}

enum class StudyDay {
    Monday,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
    Sunday,
}

enum class StudyDayShortcut {
    EveryDay,
    Weekdays,
    Weekends,
}

fun daysForShortcut(shortcut: StudyDayShortcut): Set<StudyDay> = when (shortcut) {
    StudyDayShortcut.EveryDay -> StudyDay.entries.toSet()
    StudyDayShortcut.Weekdays -> setOf(
        StudyDay.Monday,
        StudyDay.Tuesday,
        StudyDay.Wednesday,
        StudyDay.Thursday,
        StudyDay.Friday,
    )
    StudyDayShortcut.Weekends -> setOf(StudyDay.Saturday, StudyDay.Sunday)
}

