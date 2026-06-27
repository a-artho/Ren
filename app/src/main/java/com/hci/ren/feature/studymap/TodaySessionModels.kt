package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyTaskStatus

data class TodaySessionState(
    val date: String,
    val availableMinutes: Int,
)

data class TodaySessionPlan(
    val date: String,
    val baseAvailableMinutes: Int,
    val availableMinutes: Int,
    val doTodayTasks: List<GeneratedStudyBlock>,
    val wontFitTodayTasks: List<GeneratedStudyBlock>,
    val pullInCandidates: List<GeneratedStudyBlock>,
    val hasAvailabilityOverride: Boolean = false,
) {
    val plannedMinutes: Int
        get() = doTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val committedPlannedMinutes: Int
        get() = doTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) } +
            wontFitTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val completedMinutes: Int
        get() = doTodayTasks
            .filter { it.status == StudyTaskStatus.Completed }
            .sumOf { it.durationMinutes.coerceAtLeast(0) }

    val remainingMinutes: Int
        get() = (availableMinutes - plannedMinutes).coerceAtLeast(0)

    val hasPendingChanges: Boolean
        get() = hasAvailabilityOverride
}

class TodaySessionPlanner {
    fun plan(
        data: StudyMapData,
        date: String,
        availableMinutes: Int,
        hasAvailabilityOverride: Boolean = false,
    ): TodaySessionPlan {
        val normalizedAvailableMinutes = availableMinutes.coerceIn(0, MaxTodaySessionMinutes)
        val todaySchedule = data.schedule.days.firstOrNull { it.date == date }
        val baseAvailableMinutes = todaySchedule?.capacityMinutes ?: data.dailyMinutes
        val committedTodayTasks = todaySchedule?.tasks.orEmpty().sortedBy { it.order }
        val doTodayTasks = committedTodayTasks.todayTasksWithin(normalizedAvailableMinutes)
        val doTodayIds = doTodayTasks.mapTo(mutableSetOf()) { it.id }
        val wontFitTodayTasks = committedTodayTasks.filterNot { it.id in doTodayIds }
        val remainingMinutes = (normalizedAvailableMinutes - doTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) })
            .coerceAtLeast(0)
        val pullInCandidates = if (remainingMinutes > 0) {
            val completedIds = data.activeTasks
                .filter { it.status == StudyTaskStatus.Completed }
                .mapTo(mutableSetOf()) { it.id }
            data.schedule.days.asSequence()
                .filter { it.date > date }
                .flatMap { it.tasks.asSequence() }
                .filter { it.status == StudyTaskStatus.NotStarted }
                .filter { task ->
                    task.dependencies.all { dependency ->
                        dependency in completedIds || data.activeTasks.none { it.id == dependency }
                    }
                }
                .sortedBy { it.order }
                .pullCandidatesFor(remainingMinutes)
        } else {
            emptyList()
        }
        return TodaySessionPlan(
            date = date,
            baseAvailableMinutes = baseAvailableMinutes,
            availableMinutes = normalizedAvailableMinutes,
            doTodayTasks = doTodayTasks,
            wontFitTodayTasks = wontFitTodayTasks,
            pullInCandidates = pullInCandidates,
            hasAvailabilityOverride = hasAvailabilityOverride,
        )
    }
}

private fun List<GeneratedStudyBlock>.todayTasksWithin(capacityMinutes: Int): List<GeneratedStudyBlock> {
    val anchorEnd = indexOfLast { it.status in TodayAnchorStatuses } + 1
    val anchoredPrefix = take(anchorEnd)
    var used = anchoredPrefix.sumOf { it.durationMinutes.coerceAtLeast(0) }
    return buildList {
        addAll(anchoredPrefix)
        for (task in this@todayTasksWithin.drop(anchorEnd)) {
            val minutes = task.durationMinutes.coerceAtLeast(1)
            if (used + minutes > capacityMinutes) break
            add(task)
            used += minutes
        }
    }
}

private fun Sequence<GeneratedStudyBlock>.pullCandidatesFor(remainingMinutes: Int): List<GeneratedStudyBlock> {
    val candidates = toList()
    var used = 0
    val fittingPrefix = buildList {
        for (task in candidates) {
            val minutes = task.durationMinutes.coerceAtLeast(1)
            if (used + minutes > remainingMinutes) break
            add(task)
            used += minutes
            if (size == MaxPullInCandidateCount) break
        }
    }
    return fittingPrefix.ifEmpty { candidates.take(1) }
}

const val MaxTodaySessionMinutes = 1_440
private const val MaxPullInCandidateCount = 3
private val TodayAnchorStatuses = setOf(
    StudyTaskStatus.Completed,
    StudyTaskStatus.InProgress,
    StudyTaskStatus.Locked,
)
