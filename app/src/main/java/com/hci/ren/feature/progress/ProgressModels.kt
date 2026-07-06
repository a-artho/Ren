package com.hci.ren.feature.progress

import com.hci.ren.feature.studymap.FocusSessionRecord
import com.hci.ren.feature.studymap.StudyProject
import com.hci.ren.feature.studymap.currentStudyCalendar
import com.hci.ren.feature.studymap.toStudyCalendar
import com.hci.ren.feature.studymap.toStudyDate
import java.util.Calendar

data class WeeklyFocusDay(
    val date: String,
    val focusMinutes: Int,
)

data class WeeklyFocusSummary(
    val days: List<WeeklyFocusDay>,
    val goalMinutesPerDay: Int,
) {
    val totalFocusMinutes: Int get() = days.sumOf { it.focusMinutes }
    val studyDays: Int get() = days.count { it.focusMinutes > 0 }
    val maxChartMinutes: Int
        get() = roundChartMaximum(
            maxOf(
                goalMinutesPerDay + ChartMinuteStep,
                (goalMinutesPerDay * 4 + 2) / 3,
                days.maxOfOrNull { it.focusMinutes } ?: 0,
            ),
        )
}

fun buildWeeklyFocusSummary(
    project: StudyProject,
    today: String = currentStudyCalendar(project.preferences).toStudyDate(),
): WeeklyFocusSummary {
    val weekStart = (today.toStudyCalendar() ?: currentStudyCalendar(project.preferences))
        .startOfStudyWeek()
    val history = project.focusSessionHistoryByDate
    val days = (0 until DaysPerWeek).map { index ->
        val date = weekStart.copyDay().apply { add(Calendar.DAY_OF_MONTH, index) }.toStudyDate()
        WeeklyFocusDay(
            date = date,
            focusMinutes = history[date].orEmpty().focusMinutes(),
        )
    }
    return WeeklyFocusSummary(
        days = days,
        goalMinutesPerDay = (project.dailyMinutesOverride ?: project.preferences.dailyStudyMinutes)
            .coerceIn(0, MaxProgressGoalMinutes),
    )
}

private fun Calendar.startOfStudyWeek(): Calendar = copyDay().apply {
    val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + DaysPerWeek - Calendar.MONDAY) % DaysPerWeek
    add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
}

private fun Calendar.copyDay(): Calendar = clone() as Calendar

private fun List<FocusSessionRecord>.focusMinutes(): Int =
    sumOf { it.focusSeconds }.toCeilMinutes()

private fun Int.toCeilMinutes(): Int =
    if (this <= 0) 0 else (this + SecondsPerMinute - 1) / SecondsPerMinute

private fun roundChartMaximum(minutes: Int): Int {
    val normalized = minutes.coerceAtLeast(ChartMinuteStep)
    val step = chartMinuteStepFor(normalized)
    return ((normalized + step - 1) / step) * step
}

private fun chartMinuteStepFor(minutes: Int): Int = ChartMinuteStep

private const val DaysPerWeek = 7
private const val SecondsPerMinute = 60
private const val ChartMinuteStep = 120
private const val MaxProgressGoalMinutes = 1_440
