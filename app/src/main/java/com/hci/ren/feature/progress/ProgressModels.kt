package com.hci.ren.feature.progress

import com.hci.ren.feature.studymap.FocusSessionRecord
import com.hci.ren.feature.studymap.FocusSessionOutcome
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

data class StudyConsistencyDay(
    val date: String,
    val focusMinutes: Int,
    val completionRatio: Float,
) {
    val hasFocus: Boolean get() = focusMinutes > 0
}

data class StudyConsistencyWeek(
    val weeksAgo: Int,
    val days: List<StudyConsistencyDay>,
) {
    val activeDays: Int get() = days.count { it.hasFocus }
    val totalFocusMinutes: Int get() = days.sumOf { it.focusMinutes }
}

data class StudyConsistencySummary(
    val weeks: List<StudyConsistencyWeek>,
    val currentStreakDays: Int,
    val mostConsistentWeeksAgo: Int?,
) {
    val activeDays: Int get() = weeks.sumOf { it.activeDays }
    val weekCount: Int get() = weeks.size
}

data class BestRhythmBucket(
    val plannedFocusMinutes: Int,
    val attemptedRounds: Int,
    val cleanRounds: Int,
) {
    val cleanRate: Float
        get() = if (attemptedRounds == 0) 0f else cleanRounds.toFloat() / attemptedRounds.toFloat()
    val cleanRatePercent: Int get() = (cleanRate * 100f).roundToNearestInt()
    val hasRounds: Boolean get() = attemptedRounds > 0
}

data class BestRhythmSummary(
    val buckets: List<BestRhythmBucket>,
    val bestBucket: BestRhythmBucket?,
) {
    val hasData: Boolean get() = buckets.any { it.hasRounds }
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

fun buildStudyConsistencySummary(
    project: StudyProject,
    today: String = currentStudyCalendar(project.preferences).toStudyDate(),
): StudyConsistencySummary {
    val todayCalendar = today.toStudyCalendar() ?: currentStudyCalendar(project.preferences)
    val weekStart = todayCalendar.startOfStudyWeek()
    val history = project.focusSessionHistoryByDate
    val goalMinutes = (project.dailyMinutesOverride ?: project.preferences.dailyStudyMinutes)
        .coerceIn(0, MaxProgressGoalMinutes)
    val weeks = (0 until ConsistencyWeekCount).map { weeksAgo ->
        val rowStart = weekStart.copyDay().apply { add(Calendar.DAY_OF_MONTH, -DaysPerWeek * weeksAgo) }
        StudyConsistencyWeek(
            weeksAgo = weeksAgo,
            days = (0 until DaysPerWeek).map { dayIndex ->
                val date = rowStart.copyDay().apply { add(Calendar.DAY_OF_MONTH, dayIndex) }.toStudyDate()
                val focusMinutes = history[date].orEmpty().focusMinutes()
                StudyConsistencyDay(
                    date = date,
                    focusMinutes = focusMinutes,
                    completionRatio = focusCompletionRatio(focusMinutes, goalMinutes),
                )
            },
        )
    }
    return StudyConsistencySummary(
        weeks = weeks,
        currentStreakDays = currentFocusStreakDays(history, todayCalendar),
        mostConsistentWeeksAgo = weeks
            .filter { it.activeDays > 0 }
            .maxWithOrNull(
                compareBy<StudyConsistencyWeek> { it.activeDays }
                    .thenBy { it.totalFocusMinutes }
                    .thenBy { -it.weeksAgo },
            )
            ?.weeksAgo,
    )
}

fun buildBestRhythmSummary(project: StudyProject): BestRhythmSummary {
    val buckets = project.focusSessionHistoryByDate.values
        .flatten()
        .filter { it.focusSeconds > 0 && it.plannedFocusSeconds > 0 }
        .groupBy { it.plannedFocusSeconds.toCeilMinutes() }
        .map { (plannedFocusMinutes, records) ->
            BestRhythmBucket(
                plannedFocusMinutes = plannedFocusMinutes,
                attemptedRounds = records.size,
                cleanRounds = records.count { it.isCleanCompletedRound },
            )
        }
        .sortedBy { it.plannedFocusMinutes }
    return BestRhythmSummary(
        buckets = buckets,
        bestBucket = buckets.maxWithOrNull(
            compareBy<BestRhythmBucket> { it.cleanRate }
                .thenBy { it.cleanRounds }
                .thenByDescending { it.plannedFocusMinutes },
        ),
    )
}

private fun Calendar.startOfStudyWeek(): Calendar = copyDay().apply {
    val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + DaysPerWeek - Calendar.MONDAY) % DaysPerWeek
    add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
}

private fun Calendar.copyDay(): Calendar = clone() as Calendar

private fun List<FocusSessionRecord>.focusMinutes(): Int =
    sumOf { it.focusSeconds }.toCeilMinutes()

private val FocusSessionRecord.isCleanCompletedRound: Boolean
    get() = outcome == FocusSessionOutcome.FocusRoundEnded && interruptionCount == 0

private fun focusCompletionRatio(focusMinutes: Int, goalMinutes: Int): Float {
    if (focusMinutes <= 0) return 0f
    if (goalMinutes <= 0) return 1f
    return (focusMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
}

private fun currentFocusStreakDays(
    history: Map<String, List<FocusSessionRecord>>,
    today: Calendar,
): Int {
    val cursor = today.copyDay()
    var streak = 0
    while (history[cursor.toStudyDate()].orEmpty().focusMinutes() > 0) {
        streak++
        cursor.add(Calendar.DAY_OF_MONTH, -1)
    }
    return streak
}

private fun Int.toCeilMinutes(): Int =
    if (this <= 0) 0 else (this + SecondsPerMinute - 1) / SecondsPerMinute

private fun Float.roundToNearestInt(): Int = (this + 0.5f).toInt()

private fun roundChartMaximum(minutes: Int): Int {
    val normalized = minutes.coerceAtLeast(ChartMinuteStep)
    val step = chartMinuteStepFor(normalized)
    return ((normalized + step - 1) / step) * step
}

private fun chartMinuteStepFor(minutes: Int): Int = ChartMinuteStep

private const val DaysPerWeek = 7
private const val ConsistencyWeekCount = 4
private const val SecondsPerMinute = 60
private const val ChartMinuteStep = 120
private const val MaxProgressGoalMinutes = 1_440
