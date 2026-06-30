package com.hci.ren.feature.studymap

enum class TodayImpactStatus {
    Fits,
    WorkMovesForward,
    Tight,
    DoesNotFit,
}

data class TodayImpactPreview(
    val status: TodayImpactStatus,
)

class TodayImpactPreviewService(
    private val wrapUpService: TodayWrapUpService = TodayWrapUpService(),
) {
    fun preview(
        project: StudyProject,
        date: String,
        session: TodaySessionState?,
    ): TodayImpactPreview? {
        val today = date.toStudyCalendar() ?: return null
        val activeSession = session?.takeIf { it.date == date } ?: TodaySessionState(date = date)
        val currentData = buildStudyMapData(
            plan = project.plan,
            preferences = project.preferences,
            dailyMinutesOverride = project.dailyMinutesOverride,
            dailyAvailableMinutesByDate = project.dailyAvailableMinutesByDate,
            taskStateById = project.taskStateById,
            today = today,
        )
        val todayPlan = TodaySessionPlanner().plan(
            data = currentData,
            date = date,
            availableMinutes = activeSession.availableMinutes
                ?: todayBaseAvailableMinutes(project, currentData, date),
            session = activeSession,
            hasAvailabilityOverride = activeSession.availableMinutes != null,
        )
        val projected = wrapUpService.wrapUp(project, date, activeSession)?.project ?: return null
        val projectedData = buildStudyMapData(
            plan = projected.plan,
            preferences = projected.preferences,
            dailyMinutesOverride = projected.dailyMinutesOverride,
            dailyAvailableMinutesByDate = projected.dailyAvailableMinutesByDate,
            taskStateById = projected.taskStateById,
            today = today,
        )
        val status = when {
            projectedData.realism.status == PlanRealismStatus.Unrealistic ||
                projectedData.schedule.unscheduledTasks.isNotEmpty() -> TodayImpactStatus.DoesNotFit
            projectedData.realism.status == PlanRealismStatus.Tight -> TodayImpactStatus.Tight
            todayPlan.unfinishedWorkForwardMinutes > 0 -> TodayImpactStatus.WorkMovesForward
            else -> TodayImpactStatus.Fits
        }
        return TodayImpactPreview(status)
    }
}
