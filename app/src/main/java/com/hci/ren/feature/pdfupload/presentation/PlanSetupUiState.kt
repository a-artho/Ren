package com.hci.ren.feature.pdfupload.presentation

import java.util.Calendar
import java.util.TimeZone

data class PlanSetupUiState(
    val documentUris: List<String> = emptyList(),
    val currentStep: PlanSetupStep = PlanSetupStep.PlanTitle,
    val planTitle: String = "",
    val selectedDeadline: StudyDeadline? = null,
    val customDeadlineDate: String? = null,
    val customDeadlineLabel: String? = null,
    val selectedDailyTime: DailyStudyTime? = null,
    val customHoursText: String = "",
    val customMinutesText: String = "",
    val selectedDays: Set<StudyDay> = emptySet(),
    val generatedSubmission: PlanSetupSubmission? = null,
) {
    val canContinue: Boolean
        get() = canContinueAt()

    fun canContinueAt(
        nowMillis: Long = System.currentTimeMillis(),
        localTimeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean = when (currentStep) {
        PlanSetupStep.PlanTitle -> planTitle.isNotBlank()
        PlanSetupStep.Deadline -> hasReadyDeadline
        PlanSetupStep.DailyTime -> hasReadyDailyTime
        PlanSetupStep.StudyDays -> {
            planTitle.isNotBlank() &&
                hasReadyDeadline &&
                hasReadyDailyTime &&
                selectedDays.isNotEmpty() &&
                studyDaysDeadlineError(nowMillis, localTimeZone) == null
        }
    }

    val customMinutes: Int?
        get() {
            val hours = customHoursText.toIntOrNull() ?: 0
            val minutes = customMinutesText.toIntOrNull() ?: 0
            if (hours !in 0..24 || minutes !in 0..59) return null
            return (hours * 60 + minutes).takeIf { it in 1..1_440 }
        }

    val customTimeError: String?
        get() {
            if (selectedDailyTime != DailyStudyTime.Custom) return null
            val hoursText = customHoursText.trim()
            val minutesText = customMinutesText.trim()
            if (hoursText.isBlank() && minutesText.isBlank()) {
                return "Enter hours, minutes, or both."
            }

            val hours = hoursText.toIntOrNull() ?: 0
            val minutes = minutesText.toIntOrNull() ?: 0
            return when {
                hours !in 0..24 -> "Hours should be 0-24."
                minutes !in 0..59 -> "Minutes should be 0-59."
                hours == 24 && minutes > 0 -> "Max is 24 hours. Heroic, but no."
                hours == 0 && minutes == 0 -> "Needs at least 1 minute. Tiny, but still something."
                else -> null
            }
        }

    val progress: Float
        get() = (MaterialSelectionStepNumber + currentStep.number) / PlanCreationTotalSteps.toFloat()

    private val hasReadyDeadline: Boolean
        get() = when (selectedDeadline) {
            StudyDeadline.ChooseDate -> customDeadlineDate != null
            null -> false
            else -> true
        }

    private val hasReadyDailyTime: Boolean
        get() = when (selectedDailyTime) {
            DailyStudyTime.Custom -> customMinutes != null
            null -> false
            else -> true
        }

    fun studyDaysDeadlineError(
        nowMillis: Long = System.currentTimeMillis(),
        localTimeZone: TimeZone = TimeZone.getDefault(),
    ): String? {
        if (selectedDays.isEmpty()) return null
        val end = deadlineCalendar(nowMillis, localTimeZone) ?: return null
        return if (hasSelectedStudyDayBeforeDeadline(end, nowMillis, localTimeZone)) {
            null
        } else {
            "No picked day lands before the deadline. Tiny issue."
        }
    }

    fun toSubmission(): PlanSetupSubmission? {
        val deadline = selectedDeadline ?: return null
        val dailyTime = selectedDailyTime ?: return null
        val minutes = dailyTime.minutes ?: customMinutes ?: return null
        val title = planTitle.trim().takeIf { it.isNotBlank() } ?: return null
        if (deadline == StudyDeadline.ChooseDate && customDeadlineDate == null) return null
        if (selectedDays.isEmpty()) return null
        if (studyDaysDeadlineError() != null) return null

        return PlanSetupSubmission(
            documentUris = documentUris,
            goal = StudyGoal.PrepareForExam,
            deadline = deadline,
            deadlineDate = customDeadlineDate,
            dailyStudyMinutes = minutes,
            studyDays = selectedDays,
            planTitle = title,
        )
    }

    private fun deadlineCalendar(
        nowMillis: Long,
        localTimeZone: TimeZone,
    ): Calendar? {
        val today = dayOnly(nowMillis, localTimeZone)
        return when (selectedDeadline) {
            StudyDeadline.Tomorrow -> today.plusDays(1)
            StudyDeadline.InThreeDays -> today.plusDays(3)
            StudyDeadline.InOneWeek -> today.plusDays(7)
            StudyDeadline.ChooseDate -> customDeadlineDate?.toLocalDateCalendar(localTimeZone)
            null,
            -> null
        }
    }

    private fun hasSelectedStudyDayBeforeDeadline(
        deadline: Calendar,
        nowMillis: Long,
        localTimeZone: TimeZone,
    ): Boolean {
        val cursor = dayOnly(nowMillis, localTimeZone)
        while (cursor.before(deadline)) {
            if (cursor.studyDay in selectedDays) return true
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return false
    }
}

data class PlanSetupSubmission(
    val documentUris: List<String>,
    val goal: StudyGoal,
    val deadline: StudyDeadline,
    val deadlineDate: String?,
    val dailyStudyMinutes: Int,
    val studyDays: Set<StudyDay>,
    val planTitle: String = "",
)

enum class PlanSetupStep {
    PlanTitle,
    Deadline,
    DailyTime,
    StudyDays,
}

val PlanSetupStep.number: Int
    get() = ordinal + 1

const val MaterialSelectionStepNumber = 1
const val PlanCreationTotalSteps = 5

enum class StudyGoal {
    PrepareForExam,
}

enum class StudyDeadline {
    Tomorrow,
    InThreeDays,
    InOneWeek,
    ChooseDate,
}

enum class DailyStudyTime(val minutes: Int?) {
    OneHour(60),
    TwoHours(120),
    ThreeHours(180),
    FiveHours(300),
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

private fun dayOnly(
    millis: Long,
    timeZone: TimeZone,
): Calendar = Calendar.getInstance(timeZone).apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun Calendar.plusDays(days: Int): Calendar =
    (clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }

private val Calendar.studyDay: StudyDay
    get() = when (get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> StudyDay.Monday
        Calendar.TUESDAY -> StudyDay.Tuesday
        Calendar.WEDNESDAY -> StudyDay.Wednesday
        Calendar.THURSDAY -> StudyDay.Thursday
        Calendar.FRIDAY -> StudyDay.Friday
        Calendar.SATURDAY -> StudyDay.Saturday
        else -> StudyDay.Sunday
    }

private fun String.toLocalDateCalendar(timeZone: TimeZone): Calendar? {
    val parts = split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return runCatching {
        Calendar.getInstance(timeZone).apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
            timeInMillis
        }
    }.getOrNull()
}

