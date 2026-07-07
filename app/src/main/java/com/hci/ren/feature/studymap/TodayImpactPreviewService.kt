package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.StudyBlockDifficulty

enum class TodayImpactStatus {
    Fits,
    WorkMovesForward,
    Tight,
    Crammed,
    Overloaded,
}

data class TodayImpactPreview(
    val status: TodayImpactStatus,
    val carriedTaskCount: Int = 0,
    val carriedMinutes: Int = 0,
    val firstCarriedDate: String? = null,
    val affectedFutureDayCount: Int = 0,
    val unscheduledTaskCount: Int = 0,
    val unscheduledMinutes: Int = 0,
    val unscheduledCarriedTaskCount: Int = 0,
    val unscheduledCarriedMinutes: Int = 0,
    val firstCarriedDayLoad: StudyBlockDifficulty? = null,
    val firstCarriedDayIsRisky: Boolean = false,
    val firstCarriedDayPlannedMinutes: Int = 0,
    val firstCarriedDayCapacityMinutes: Int = 0,
)

class TodayImpactPreviewService(
    private val wrapUpService: TodayWrapUpService = TodayWrapUpService(),
) {
    fun preview(
        project: StudyProject,
        date: String,
        session: TodaySessionState?,
        nowMillis: Long = System.currentTimeMillis(),
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
        val baseAvailableMinutes = effectiveAvailableMinutesForStudyDate(
            date = date,
            requestedMinutes = todayBaseAvailableMinutes(project, currentData, date),
            resetOffsetHours = project.preferences.studyDayResetOffsetHours,
            nowMillis = nowMillis,
        )
        val availableMinutes = effectiveAvailableMinutesForStudyDate(
            date = date,
            requestedMinutes = activeSession.availableMinutes ?: baseAvailableMinutes,
            resetOffsetHours = project.preferences.studyDayResetOffsetHours,
            nowMillis = nowMillis,
        )
        val todayPlan = TodaySessionPlanner().plan(
            data = currentData,
            date = date,
            availableMinutes = availableMinutes,
            session = activeSession,
            hasAvailabilityOverride = activeSession.availableMinutes != null && availableMinutes != baseAvailableMinutes,
        )
        val projected = wrapUpService.wrapUp(project, date, activeSession, nowMillis)?.project ?: return null
        val projectedData = buildStudyMapData(
            plan = projected.plan,
            preferences = projected.preferences,
            dailyMinutesOverride = projected.dailyMinutesOverride,
            dailyAvailableMinutesByDate = projected.dailyAvailableMinutesByDate,
            taskStateById = projected.taskStateById,
            today = today,
        )
        val status = when {
            projectedData.realism.status == PlanRealismStatus.Overloaded ||
                projectedData.schedule.unscheduledTasks.isNotEmpty() -> TodayImpactStatus.Overloaded
            projectedData.realism.status == PlanRealismStatus.Crammed -> TodayImpactStatus.Crammed
            projectedData.realism.status == PlanRealismStatus.Tight -> TodayImpactStatus.Tight
            todayPlan.unfinishedWorkForwardMinutes > 0 -> TodayImpactStatus.WorkMovesForward
            else -> TodayImpactStatus.Fits
        }
        val carriedTaskIds = todayPlan.unfinishedWorkForwardTasks.mapTo(mutableSetOf()) { it.id }
        val carriedScheduleDays = projectedData.schedule.days
            .filter { day -> day.tasks.any { task -> task.id in carriedTaskIds } }
            .map { it.date }
        val firstCarriedDate = carriedScheduleDays.minOrNull()
        val firstCarriedDay = projectedData.schedule.days.firstOrNull { it.date == firstCarriedDate }
        val unscheduledCarriedTasks = projectedData.schedule.unscheduledTasks
            .filter { it.id in carriedTaskIds }
        return TodayImpactPreview(
            status = status,
            carriedTaskCount = todayPlan.unfinishedWorkForwardTasks.size,
            carriedMinutes = todayPlan.unfinishedWorkForwardMinutes,
            firstCarriedDate = firstCarriedDate,
            affectedFutureDayCount = carriedScheduleDays.distinct().size,
            unscheduledTaskCount = projectedData.schedule.unscheduledTasks.size,
            unscheduledMinutes = projectedData.schedule.unscheduledTasks.sumOf {
                projectedData.schedule.fitMode.fitMinutes(it)
            },
            unscheduledCarriedTaskCount = unscheduledCarriedTasks.size,
            unscheduledCarriedMinutes = unscheduledCarriedTasks.sumOf { projectedData.schedule.fitMode.fitMinutes(it) },
            firstCarriedDayLoad = firstCarriedDay?.load,
            firstCarriedDayIsRisky = firstCarriedDay?.isRisky == true,
            firstCarriedDayPlannedMinutes = firstCarriedDay?.plannedMinutes ?: 0,
            firstCarriedDayCapacityMinutes = firstCarriedDay?.capacityMinutes ?: 0,
        )
    }
}
