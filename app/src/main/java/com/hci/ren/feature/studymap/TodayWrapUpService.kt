package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.StudyTaskStatus

data class TodayWrapUpSummary(
    val completedTasks: Int,
    val removedTasks: Int,
    val movedForwardTasks: Int,
    val movedForwardMinutes: Int,
)

data class TodayWrapUpResult(
    val project: StudyProject,
    val summary: TodayWrapUpSummary,
)

class TodayWrapUpService {
    fun wrapUp(
        project: StudyProject,
        date: String,
        session: TodaySessionState?,
    ): TodayWrapUpResult? {
        if (date.toStudyCalendar() == null) return null
        val activeSession = session?.takeIf { it.date == date } ?: TodaySessionState(date = date)
        val data = buildStudyMapData(
            plan = project.plan,
            preferences = project.preferences,
            dailyMinutesOverride = project.dailyMinutesOverride,
            dailyAvailableMinutesByDate = project.dailyAvailableMinutesByDate,
            taskStateById = project.taskStateById,
            today = date.toStudyCalendar() ?: return null,
        )
        val minutesUntilReset = minutesUntilStudyDayReset(
            nowMillis = System.currentTimeMillis(),
            resetOffsetHours = project.preferences.studyDayResetOffsetHours,
        )
        val baseAvailableMinutes = effectiveTodayAvailableMinutes(
            requestedMinutes = todayBaseAvailableMinutes(project, data, date),
            minutesUntilReset = minutesUntilReset,
        )
        val availableMinutes = effectiveTodayAvailableMinutes(
            requestedMinutes = activeSession.availableMinutes ?: baseAvailableMinutes,
            minutesUntilReset = minutesUntilReset,
        )
        val todayPlan = TodaySessionPlanner().plan(
            data = data,
            date = date,
            availableMinutes = availableMinutes,
            session = activeSession,
            hasAvailabilityOverride = activeSession.availableMinutes != null && availableMinutes != baseAvailableMinutes,
        )
        val sourceTaskIds = project.plan.blocks.mapTo(mutableSetOf()) { it.id }
        val state = project.taskStateById.toMutableMap()
        var completedCount = 0
        var removedCount = 0

        todayPlan.doneTodayTasks
            .filter { it.id in activeSession.doneTodayTaskIds }
            .forEach { task ->
                if (state.setTaskState(task.id, sourceTaskIds, StudyTaskState(status = StudyTaskStatus.Completed))) {
                    completedCount += 1
                }
            }

        todayPlan.removedFromPlanTasks.forEach { task ->
            if (state.setTaskState(task.id, sourceTaskIds, StudyTaskState(status = StudyTaskStatus.ExcludedByUser))) {
                removedCount += 1
            }
        }

        val updatedProject = project.copy(
            taskStateById = state,
            dailyAvailableMinutesByDate = project.dailyAvailableMinutesByDate + (date to 0),
        )
        return TodayWrapUpResult(
            project = updatedProject,
            summary = TodayWrapUpSummary(
                completedTasks = completedCount,
                removedTasks = removedCount,
                movedForwardTasks = todayPlan.unfinishedWorkForwardTasks.size,
                movedForwardMinutes = todayPlan.unfinishedWorkForwardMinutes,
            ),
        )
    }
}

private fun MutableMap<String, StudyTaskState>.setTaskState(
    taskId: String,
    sourceTaskIds: Set<String>,
    state: StudyTaskState,
): Boolean {
    if (taskId !in sourceTaskIds) return false
    val current = this[taskId] ?: StudyTaskState()
    if (state.isDefault) {
        remove(taskId)
    } else {
        this[taskId] = state
    }
    return state != current
}
