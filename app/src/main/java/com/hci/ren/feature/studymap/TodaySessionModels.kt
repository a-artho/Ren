package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import java.util.Calendar
import java.util.TimeZone

data class TodaySessionState(
    val date: String,
    val availableMinutes: Int? = null,
    val movedLaterTaskIds: Set<String> = emptySet(),
    val pulledInTaskIds: Set<String> = emptySet(),
    val doneTodayTaskIds: Set<String> = emptySet(),
    val removedFromPlanTaskIds: Set<String> = emptySet(),
    val focusSessions: List<FocusSessionRecord> = emptyList(),
) {
    val hasAvailabilityOverride: Boolean get() = availableMinutes != null
    val hasTaskChanges: Boolean
        get() = movedLaterTaskIds.isNotEmpty() ||
            pulledInTaskIds.isNotEmpty() ||
            doneTodayTaskIds.isNotEmpty() ||
            removedFromPlanTaskIds.isNotEmpty()
    val focusSeconds: Int get() = focusSessions.sumOf { it.focusSeconds }
    val breakSeconds: Int get() = focusSessions.sumOf { it.breakSeconds }
    val spentFocusMinutes: Int get() = focusSeconds.toBudgetMinutes()
    val spentBreakMinutes: Int get() = breakSeconds.toBudgetMinutes()

    val isEmpty: Boolean get() = !hasAvailabilityOverride && !hasTaskChanges && focusSessions.isEmpty()
}

data class FocusSessionRecord(
    val taskId: String,
    val plannedFocusMinutes: Int,
    val plannedFocusSeconds: Int = plannedFocusMinutes * FocusSessionSecondsPerMinute,
    val plannedBreakMinutes: Int,
    val focusSeconds: Int,
    val flowOvertimeSeconds: Int = 0,
    val breakSeconds: Int,
    val awaySeconds: Int,
    val interruptionCount: Int,
    val outcome: FocusSessionOutcome,
    val endedAtMillis: Long,
) {
    val consumedMinutes: Int get() = (focusSeconds + breakSeconds).toBudgetMinutes()
    val hasTrackedTime: Boolean
        get() = focusSeconds > 0 || breakSeconds > 0 || awaySeconds > 0 || interruptionCount > 0
}

enum class FocusSessionOutcome {
    FocusRoundEnded,
    FocusStopped,
    BreakEnded,
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
    val untrackedCompletedMinutes: Int = 0,
    val fitMode: ScheduleFitMode = ScheduleFitMode.Reserved,
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
    val activeWorkMinutes: Int
        get() = doTodayTasks.fitMinutesTotal(fitMode) +
            pulledInTasks.fitMinutesTotal(fitMode)

    val plannedMinutes: Int
        get() = doTodayTasks.fitMinutesTotal(fitMode) +
            pulledInTasks.fitMinutesTotal(fitMode) +
            untrackedCompletedMinutes

    val committedPlannedMinutes: Int
        get() = doTodayTasks.fitMinutesTotal(fitMode) +
            wontFitTodayTasks.fitMinutesTotal(fitMode)

    val completedMinutes: Int
        get() = doneTodayTasks.fitMinutesTotal(fitMode)

    val overflowMinutes: Int
        get() = wontFitTodayTasks.fitMinutesTotal(fitMode)

    val movedLaterMinutes: Int
        get() = movedLaterTasks.fitMinutesTotal(fitMode)

    val removedMinutes: Int
        get() = removedFromPlanTasks.fitMinutesTotal(fitMode)

    val unfinishedWorkForwardTasks: List<GeneratedStudyBlock>
        get() = doTodayTasks + pulledInTasks + wontFitTodayTasks + movedLaterTasks

    val unfinishedWorkForwardMinutes: Int
        get() = unfinishedWorkForwardTasks.fitMinutesTotal(fitMode)

    val remainingMinutes: Int
        get() = (availableMinutes - plannedMinutes).coerceAtLeast(0)

    val overPlannedMinutes: Int
        get() = (plannedMinutes - availableMinutes).coerceAtLeast(0)

    val hasPendingChanges: Boolean
        get() = hasAvailabilityOverride || hasTaskChanges

    val hasWrapUpWork: Boolean
        get() = hasPendingChanges ||
            doTodayTasks.isNotEmpty() ||
            pulledInTasks.isNotEmpty() ||
            doneTodayTasks.isNotEmpty() ||
            wontFitTodayTasks.isNotEmpty() ||
            movedLaterTasks.isNotEmpty() ||
            removedFromPlanTasks.isNotEmpty()
}

internal fun TodaySessionPlan.canWrapUpToday(isTodayClosed: Boolean): Boolean =
    hasWrapUpWork && (!isTodayClosed || hasPendingChanges)

enum class TodayClockPressureStatus {
    Clear,
    Plenty,
    StartSoon,
    ActNow,
    DoesNotFit,
}

data class TodayClockPressure(
    val status: TodayClockPressureStatus,
    val activeWorkMinutes: Int,
    val minutesUntilReset: Int,
    val bufferMinutes: Int,
)

internal fun todayClockPressure(
    activeWorkMinutes: Int,
    minutesUntilReset: Int,
): TodayClockPressure {
    val normalizedWork = activeWorkMinutes.coerceAtLeast(0)
    val normalizedResetWindow = minutesUntilReset.coerceAtLeast(0)
    val bufferMinutes = normalizedResetWindow - normalizedWork
    val status = when {
        normalizedWork == 0 -> TodayClockPressureStatus.Clear
        bufferMinutes < 0 -> TodayClockPressureStatus.DoesNotFit
        bufferMinutes <= 15 -> TodayClockPressureStatus.ActNow
        bufferMinutes <= 45 -> TodayClockPressureStatus.StartSoon
        else -> TodayClockPressureStatus.Plenty
    }
    return TodayClockPressure(
        status = status,
        activeWorkMinutes = normalizedWork,
        minutesUntilReset = normalizedResetWindow,
        bufferMinutes = bufferMinutes,
    )
}

internal fun minutesUntilStudyDayReset(
    nowMillis: Long,
    resetOffsetHours: Int,
    timeZone: TimeZone = TimeZone.getDefault(),
): Int {
    val now = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
    }
    val nextReset = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, resetOffsetHours.coerceIn(0, 23))
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    return ((nextReset.timeInMillis - nowMillis + 59_999L) / 60_000L)
        .toInt()
        .coerceAtLeast(0)
}

internal fun effectiveTodayAvailableMinutes(
    requestedMinutes: Int,
    minutesUntilReset: Int,
): Int =
    requestedMinutes
        .coerceIn(0, MaxTodaySessionMinutes)
        .coerceAtMost(minutesUntilReset.coerceIn(0, MaxTodaySessionMinutes))

internal fun effectiveAvailableMinutesForStudyDate(
    date: String,
    requestedMinutes: Int,
    resetOffsetHours: Int,
    nowMillis: Long = System.currentTimeMillis(),
): Int {
    val normalized = requestedMinutes.coerceIn(0, MaxTodaySessionMinutes)
    val currentStudyDate = currentStudyCalendar(resetOffsetHours, nowMillis).toStudyDate()
    if (date != currentStudyDate) return normalized
    return effectiveTodayAvailableMinutes(
        requestedMinutes = normalized,
        minutesUntilReset = minutesUntilStudyDayReset(
            nowMillis = nowMillis,
            resetOffsetHours = resetOffsetHours,
        ),
    )
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
        val scheduledForwardTasks = data.schedule.days.asSequence()
            .filter { it.date > date }
            .flatMap { it.tasks.asSequence() }
            .sortedBy { it.order }
            .toList()
        val forwardTasks = (scheduledForwardTasks + data.schedule.unscheduledTasks)
            .distinctBy { it.id }
            .sortedBy { it.order }
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
        val pulledInTasks = forwardTasks
            .filter { it.id in pulledIds && it.id !in doneTodayIds && it.id !in removedIds }
            .filter { task -> task.canPullForward(completedIds, doneTodayIds, activeTasksById) }
        val fitMode = localTodayFitMode(
            tasks = committedCandidates + pulledInTasks,
            capacityMinutes = normalizedAvailableMinutes,
            scheduledFitMode = todaySchedule?.fitMode ?: data.schedule.fitMode,
        )
        val doTodayTasks = committedCandidates.todayTasksWithin(normalizedAvailableMinutes, fitMode)
        val doTodayIds = doTodayTasks.mapTo(mutableSetOf()) { it.id }
        val wontFitTodayTasks = committedCandidates.filterNot { it.id in doTodayIds }
        val removedFromPlanTasks = orderedByPlan(data.activeTasks, removedIds)
        val removedFromPlanTaskIds = removedFromPlanTasks.mapTo(mutableSetOf()) { it.id }
        val trackedFocusTaskIds = activeSession
            ?.focusSessions
            .orEmpty()
            .filter { it.focusSeconds > 0 || it.breakSeconds > 0 }
            .mapTo(mutableSetOf()) { it.taskId }
        val untrackedCompletedTasks = doneTodayTasks.filterNot { it.id in trackedFocusTaskIds }
        val activePlannedTasks = doTodayTasks + pulledInTasks
        val plannedIds = (activePlannedTasks + doneTodayTasks).mapTo(mutableSetOf()) { it.id }
        val remainingMinutes = (
                normalizedAvailableMinutes -
                activePlannedTasks.fitMinutesTotal(fitMode) -
                untrackedCompletedTasks.fitMinutesTotal(fitMode)
            )
            .coerceAtLeast(0)
        val pullInCandidates = if (remainingMinutes > 0) {
            forwardTasks.asSequence()
                .filterNot { it.id in plannedIds }
                .filterNot { it.id in removedIds }
                .filter { task -> task.canPullForward(completedIds, doneTodayIds, activeTasksById) }
                .pullCandidatesFor(remainingMinutes, fitMode)
        } else {
            emptyList()
        }
        return TodaySessionPlan(
            date = date,
            baseAvailableMinutes = baseAvailableMinutes,
            availableMinutes = normalizedAvailableMinutes,
            untrackedCompletedMinutes = untrackedCompletedTasks.fitMinutesTotal(fitMode),
            fitMode = fitMode,
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

fun TodaySessionState.appendFocusSession(record: FocusSessionRecord): TodaySessionState {
    if (record.taskId.isBlank() || !record.hasTrackedTime) return this
    return copy(focusSessions = focusSessions + record)
}

private fun localTodayFitMode(
    tasks: List<GeneratedStudyBlock>,
    capacityMinutes: Int,
    scheduledFitMode: ScheduleFitMode,
): ScheduleFitMode {
    if (scheduledFitMode == ScheduleFitMode.LikelyFallback) return ScheduleFitMode.LikelyFallback
    val reservedTasks = tasks.todayTasksWithin(capacityMinutes, ScheduleFitMode.Reserved)
    val likelyTasks = tasks.todayTasksWithin(capacityMinutes, ScheduleFitMode.LikelyFallback)
    val reservedMinutes = reservedTasks.fitMinutesTotal(ScheduleFitMode.Reserved)
    val likelyMinutes = likelyTasks.fitMinutesTotal(ScheduleFitMode.LikelyFallback)
    return if (
        likelyTasks.size > reservedTasks.size ||
        (
            likelyTasks.size == reservedTasks.size &&
                likelyMinutes <= capacityMinutes &&
                reservedMinutes > capacityMinutes
        )
    ) {
        ScheduleFitMode.LikelyFallback
    } else {
        ScheduleFitMode.Reserved
    }
}

private fun List<GeneratedStudyBlock>.todayTasksWithin(
    capacityMinutes: Int,
    fitMode: ScheduleFitMode,
): List<GeneratedStudyBlock> {
    val anchorEnd = indexOfLast { it.status in TodayAnchorStatuses } + 1
    val anchoredPrefix = take(anchorEnd)
    var used = anchoredPrefix.fitMinutesTotal(fitMode)
    return buildList {
        addAll(anchoredPrefix)
        for (task in this@todayTasksWithin.drop(anchorEnd)) {
            val minutes = fitMode.fitMinutes(task).coerceAtLeast(1)
            if (used + minutes > capacityMinutes) break
            add(task)
            used += minutes
        }
    }
}

private fun Sequence<GeneratedStudyBlock>.pullCandidatesFor(
    remainingMinutes: Int,
    fitMode: ScheduleFitMode,
): List<GeneratedStudyBlock> {
    val candidates = toList()
    var used = 0
    val fittingPrefix = buildList {
        for (task in candidates) {
            val minutes = fitMode.fitMinutes(task).coerceAtLeast(1)
            if (used + minutes > remainingMinutes) break
            add(task)
            used += minutes
            if (size == MaxPullInCandidateCount) break
        }
    }
    return fittingPrefix.ifEmpty { candidates.take(1) }
}

private fun GeneratedStudyBlock.canPullForward(
    completedIds: Set<String>,
    doneTodayIds: Set<String>,
    activeTasksById: Map<String, GeneratedStudyBlock>,
): Boolean = status in PullableForwardStatuses &&
    dependencies.all { dependency ->
        dependency in completedIds ||
            dependency in doneTodayIds ||
            activeTasksById[dependency] == null
    }

private fun orderedByPlan(
    tasks: List<GeneratedStudyBlock>,
    ids: Set<String>,
): List<GeneratedStudyBlock> = tasks.filter { it.id in ids }.sortedBy { it.order }

private fun orderedByPlan(
    tasks: List<GeneratedStudyBlock>,
    ids: List<String>,
): List<GeneratedStudyBlock> = orderedByPlan(tasks, ids.toSet())

private fun Iterable<GeneratedStudyBlock>.fitMinutesTotal(fitMode: ScheduleFitMode): Int =
    sumOf { fitMode.fitMinutes(it).coerceAtLeast(0) }

private fun Int.toBudgetMinutes(): Int =
    if (this <= 0) 0 else (this + FocusSessionSecondsPerMinute - 1) / FocusSessionSecondsPerMinute

internal fun todayBaseAvailableMinutes(
    project: StudyProject,
    data: StudyMapData,
    today: String,
): Int = project.dailyAvailableMinutesByDate[today]
    ?: data.schedule.days.firstOrNull { it.date == today }?.capacityMinutes
    ?: data.dailyMinutes

const val MaxTodaySessionMinutes = 1_440
private const val FocusSessionSecondsPerMinute = 60
private const val MaxPullInCandidateCount = 3
private val TodayAnchorStatuses = setOf(
    StudyTaskStatus.Completed,
    StudyTaskStatus.InProgress,
    StudyTaskStatus.Locked,
)
private val PullableForwardStatuses = setOf(
    StudyTaskStatus.NotStarted,
    StudyTaskStatus.Unscheduled,
    StudyTaskStatus.OverCapacity,
)
