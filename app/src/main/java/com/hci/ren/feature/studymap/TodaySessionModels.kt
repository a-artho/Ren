package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyTaskStatus

data class TodaySessionState(
    val date: String,
    val availableMinutes: Int? = null,
    val movedLaterTaskIds: Set<String> = emptySet(),
    val pulledInTaskIds: Set<String> = emptySet(),
    val doneTodayTaskIds: Set<String> = emptySet(),
    val removedFromPlanTaskIds: Set<String> = emptySet(),
) {
    val hasAvailabilityOverride: Boolean get() = availableMinutes != null
    val hasTaskChanges: Boolean
        get() = movedLaterTaskIds.isNotEmpty() ||
            pulledInTaskIds.isNotEmpty() ||
            doneTodayTaskIds.isNotEmpty() ||
            removedFromPlanTaskIds.isNotEmpty()

    val isEmpty: Boolean get() = !hasAvailabilityOverride && !hasTaskChanges
}

enum class TodaySessionTaskAction {
    MoveLater,
    PullIn,
    MarkDone,
    RemoveFromPlan,
    RestoreMovedLater,
    UndoPullIn,
    UndoDone,
    RestoreRemoved,
}

data class TodaySessionPlan(
    val date: String,
    val baseAvailableMinutes: Int,
    val availableMinutes: Int,
    val doTodayTasks: List<GeneratedStudyBlock>,
    val pulledInTasks: List<GeneratedStudyBlock>,
    val doneTodayTasks: List<GeneratedStudyBlock>,
    val wontFitTodayTasks: List<GeneratedStudyBlock>,
    val movedLaterTasks: List<GeneratedStudyBlock>,
    val removedFromPlanTasks: List<GeneratedStudyBlock>,
    val pullInCandidates: List<GeneratedStudyBlock>,
    val hasAvailabilityOverride: Boolean = false,
    val hasTaskChanges: Boolean = false,
) {
    val plannedMinutes: Int
        get() = doTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) } +
            pulledInTasks.sumOf { it.durationMinutes.coerceAtLeast(0) } +
            doneTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val committedPlannedMinutes: Int
        get() = doTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) } +
            wontFitTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val completedMinutes: Int
        get() = doneTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val overflowMinutes: Int
        get() = wontFitTodayTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val movedLaterMinutes: Int
        get() = movedLaterTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val removedMinutes: Int
        get() = removedFromPlanTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }

    val remainingMinutes: Int
        get() = (availableMinutes - plannedMinutes).coerceAtLeast(0)

    val overPlannedMinutes: Int
        get() = (plannedMinutes - availableMinutes).coerceAtLeast(0)

    val hasPendingChanges: Boolean
        get() = hasAvailabilityOverride || hasTaskChanges
}

class TodaySessionPlanner {
    fun plan(
        data: StudyMapData,
        date: String,
        availableMinutes: Int,
        session: TodaySessionState? = null,
        hasAvailabilityOverride: Boolean = false,
    ): TodaySessionPlan {
        val normalizedAvailableMinutes = availableMinutes.coerceIn(0, MaxTodaySessionMinutes)
        val activeSession = session?.takeIf { it.date == date }
        val todaySchedule = data.schedule.days.firstOrNull { it.date == date }
        val baseAvailableMinutes = todaySchedule?.capacityMinutes ?: data.dailyMinutes
        val committedTodayTasks = todaySchedule?.tasks.orEmpty().sortedBy { it.order }
        val futureTasks = data.schedule.days.asSequence()
            .filter { it.date > date }
            .flatMap { it.tasks.asSequence() }
            .sortedBy { it.order }
            .toList()
        val activeTasksById = data.activeTasks.associateBy { it.id }
        val removedIds = activeSession?.removedFromPlanTaskIds.orEmpty()
        val doneIds = activeSession?.doneTodayTaskIds.orEmpty()
        val pulledIds = activeSession?.pulledInTaskIds.orEmpty()
        val movedLaterIds = activeSession?.movedLaterTaskIds.orEmpty()
        val completedIds = data.activeTasks
            .filter { it.status == StudyTaskStatus.Completed }
            .mapTo(mutableSetOf()) { it.id }

        val doneTodayTasks = orderedByPlan(
            data.activeTasks,
            ids = doneIds + committedTodayTasks.filter { it.status == StudyTaskStatus.Completed }.map { it.id },
        ).filterNot { it.id in removedIds }
        val doneTodayIds = doneTodayTasks.mapTo(mutableSetOf()) { it.id }
        val movedLaterTasks = committedTodayTasks
            .filter { it.id in movedLaterIds && it.id !in doneTodayIds && it.id !in removedIds }
        val movedLaterTaskIds = movedLaterTasks.mapTo(mutableSetOf()) { it.id }
        val committedCandidates = committedTodayTasks
            .filterNot { it.id in movedLaterTaskIds }
            .filterNot { it.id in doneTodayIds }
            .filterNot { it.id in removedIds }
        val doTodayTasks = committedCandidates.todayTasksWithin(normalizedAvailableMinutes)
        val doTodayIds = doTodayTasks.mapTo(mutableSetOf()) { it.id }
        val wontFitTodayTasks = committedCandidates.filterNot { it.id in doTodayIds }
        val pulledInTasks = futureTasks
            .filter { it.id in pulledIds && it.id !in doneTodayIds && it.id !in removedIds }
        val removedFromPlanTasks = orderedByPlan(data.activeTasks, removedIds)
        val removedFromPlanTaskIds = removedFromPlanTasks.mapTo(mutableSetOf()) { it.id }
        val plannedTasks = doTodayTasks + pulledInTasks + doneTodayTasks
        val plannedIds = plannedTasks.mapTo(mutableSetOf()) { it.id }
        val remainingMinutes = (normalizedAvailableMinutes - plannedTasks.sumOf { it.durationMinutes.coerceAtLeast(0) })
            .coerceAtLeast(0)
        val pullInCandidates = if (remainingMinutes > 0) {
            futureTasks.asSequence()
                .filter { it.status == StudyTaskStatus.NotStarted }
                .filterNot { it.id in plannedIds }
                .filterNot { it.id in removedIds }
                .filter { task ->
                    task.dependencies.all { dependency ->
                        dependency in completedIds ||
                            dependency in doneTodayIds ||
                            activeTasksById[dependency] == null
                    }
                }
                .pullCandidatesFor(remainingMinutes)
        } else {
            emptyList()
        }
        return TodaySessionPlan(
            date = date,
            baseAvailableMinutes = baseAvailableMinutes,
            availableMinutes = normalizedAvailableMinutes,
            doTodayTasks = doTodayTasks,
            pulledInTasks = pulledInTasks,
            doneTodayTasks = doneTodayTasks,
            wontFitTodayTasks = wontFitTodayTasks,
            movedLaterTasks = movedLaterTasks,
            removedFromPlanTasks = removedFromPlanTasks,
            pullInCandidates = pullInCandidates,
            hasAvailabilityOverride = hasAvailabilityOverride,
            hasTaskChanges = movedLaterTaskIds.isNotEmpty() ||
                pulledInTasks.isNotEmpty() ||
                doneTodayTasks.any { it.id in doneIds && it.status != StudyTaskStatus.Completed } ||
                removedFromPlanTaskIds.isNotEmpty(),
        )
    }
}

fun TodaySessionState.applyTaskAction(
    taskId: String,
    action: TodaySessionTaskAction,
): TodaySessionState = when (action) {
    TodaySessionTaskAction.MoveLater -> copy(
        movedLaterTaskIds = movedLaterTaskIds + taskId,
        pulledInTaskIds = pulledInTaskIds - taskId,
        doneTodayTaskIds = doneTodayTaskIds - taskId,
        removedFromPlanTaskIds = removedFromPlanTaskIds - taskId,
    )
    TodaySessionTaskAction.PullIn -> copy(
        movedLaterTaskIds = movedLaterTaskIds - taskId,
        pulledInTaskIds = pulledInTaskIds + taskId,
        removedFromPlanTaskIds = removedFromPlanTaskIds - taskId,
    )
    TodaySessionTaskAction.MarkDone -> copy(
        movedLaterTaskIds = movedLaterTaskIds - taskId,
        doneTodayTaskIds = doneTodayTaskIds + taskId,
        removedFromPlanTaskIds = removedFromPlanTaskIds - taskId,
    )
    TodaySessionTaskAction.RemoveFromPlan -> copy(
        movedLaterTaskIds = movedLaterTaskIds - taskId,
        pulledInTaskIds = pulledInTaskIds - taskId,
        doneTodayTaskIds = doneTodayTaskIds - taskId,
        removedFromPlanTaskIds = removedFromPlanTaskIds + taskId,
    )
    TodaySessionTaskAction.RestoreMovedLater -> copy(
        movedLaterTaskIds = movedLaterTaskIds - taskId,
    )
    TodaySessionTaskAction.UndoPullIn -> copy(
        pulledInTaskIds = pulledInTaskIds - taskId,
    )
    TodaySessionTaskAction.UndoDone -> copy(
        doneTodayTaskIds = doneTodayTaskIds - taskId,
    )
    TodaySessionTaskAction.RestoreRemoved -> copy(
        removedFromPlanTaskIds = removedFromPlanTaskIds - taskId,
    )
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

private fun orderedByPlan(
    tasks: List<GeneratedStudyBlock>,
    ids: Set<String>,
): List<GeneratedStudyBlock> = tasks.filter { it.id in ids }.sortedBy { it.order }

private fun orderedByPlan(
    tasks: List<GeneratedStudyBlock>,
    ids: List<String>,
): List<GeneratedStudyBlock> = orderedByPlan(tasks, ids.toSet())

const val MaxTodaySessionMinutes = 1_440
private const val MaxPullInCandidateCount = 3
private val TodayAnchorStatuses = setOf(
    StudyTaskStatus.Completed,
    StudyTaskStatus.InProgress,
    StudyTaskStatus.Locked,
)
