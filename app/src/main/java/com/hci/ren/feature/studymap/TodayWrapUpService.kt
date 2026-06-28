package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.GeneratedStudyBlock

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
            taskProgressById = project.taskProgressById,
            today = date.toStudyCalendar() ?: return null,
        )
        val baseAvailableMinutes = todayBaseAvailableMinutes(project, data, date)
        val availableMinutes = activeSession.availableMinutes ?: baseAvailableMinutes
        val todayPlan = TodaySessionPlanner().plan(
            data = data,
            date = date,
            availableMinutes = availableMinutes,
            session = activeSession,
            hasAvailabilityOverride = activeSession.availableMinutes != null,
        )
        val sourceTasksById = project.plan.blocks.associateBy { it.id }
        val progress = project.taskProgressById.toMutableMap()
        var completedCount = 0
        var removedCount = 0

        todayPlan.doneTodayTasks
            .filter { it.id in activeSession.doneTodayTaskIds }
            .forEach { task ->
                if (progress.addTaskProgress(task, sourceTasksById, completedMinutes = task.durationMinutes)) {
                    completedCount += 1
                }
            }

        todayPlan.removedFromPlanTasks.forEach { task ->
            if (progress.addTaskProgress(task, sourceTasksById, removedMinutes = task.durationMinutes)) {
                removedCount += 1
            }
        }

        val updatedProject = project.copy(
            taskProgressById = progress,
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

private fun MutableMap<String, StudyTaskProgress>.addTaskProgress(
    task: GeneratedStudyBlock,
    sourceTasksById: Map<String, GeneratedStudyBlock>,
    completedMinutes: Int = 0,
    removedMinutes: Int = 0,
): Boolean {
    val sourceTaskId = task.sourceTaskId(sourceTasksById) ?: return false
    val sourceTask = sourceTasksById[sourceTaskId] ?: return false
    val totalMinutes = sourceTask.durationMinutes.coerceAtLeast(1)
    val current = this[sourceTaskId] ?: StudyTaskProgress()
    val completed = (current.completedMinutes + completedMinutes.coerceAtLeast(0))
        .coerceIn(0, totalMinutes)
    val removed = (current.removedMinutes + removedMinutes.coerceAtLeast(0))
        .coerceIn(0, totalMinutes - completed)
    val updated = StudyTaskProgress(
        completedMinutes = completed,
        removedMinutes = removed,
    )
    if (updated.isEmpty) {
        remove(sourceTaskId)
    } else {
        this[sourceTaskId] = updated
    }
    return updated != current
}

private fun GeneratedStudyBlock.sourceTaskId(
    sourceTasksById: Map<String, GeneratedStudyBlock>,
): String? {
    if (id in sourceTasksById) return id
    val markerIndex = id.lastIndexOf(LocalSplitIdMarker)
    if (markerIndex <= 0) return null
    return id.substring(0, markerIndex).takeIf { it in sourceTasksById }
}
