package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.requiredStudyMinutes
import java.util.Calendar

enum class StudyMapView { Schedule, Topics }

data class StudyMapData(
    val plan: GeneratedStudyPlan,
    val preferences: PlanSetupSubmission,
    val realism: PlanRealism,
    val schedule: StudySchedule,
    val dailyMinutes: Int,
) {
    val activeTasks: List<GeneratedStudyBlock>
        get() = plan.blocks.filterNot { it.status == StudyTaskStatus.ExcludedByUser }
    val requiredTasks: List<GeneratedStudyBlock>
        get() = plan.blocks.filter(::countsTowardRequiredTime)
    val totalLikelyMinutes: Int
        get() = requiredTasks
            .sumOf { it.effortLikelyMinutes.coerceAtLeast(1) }
    val remainingLikelyMinutes: Int
        get() = requiredTasks
            .filter { it.status != StudyTaskStatus.Completed }
            .sumOf { it.effortLikelyMinutes.coerceAtLeast(1) }
    val totalReservedMinutes: Int get() = requiredStudyMinutes(requiredTasks, includeCompleted = true)
    val remainingReservedMinutes: Int get() = requiredStudyMinutes(requiredTasks)
    val completedTasks: Int get() = requiredTasks.count { it.status == StudyTaskStatus.Completed }
    val progress: Float get() = if (requiredTasks.isEmpty()) 0f else completedTasks.toFloat() / requiredTasks.size
    val nextTask: GeneratedStudyBlock?
        get() {
            val completedIds = activeTasks.filter { it.status == StudyTaskStatus.Completed }.mapTo(mutableSetOf()) { it.id }
            return schedule.days.asSequence().flatMap { it.tasks.asSequence() }.firstOrNull {
                it.status !in setOf(
                    StudyTaskStatus.Completed,
                    StudyTaskStatus.DeferredByUser,
                    StudyTaskStatus.ExcludedByUser,
                    StudyTaskStatus.Locked,
                ) &&
                    it.dependencies.all { dependency -> dependency in completedIds || activeTasks.none { task -> task.id == dependency } }
            }
        }
}

enum class ScopeReduction { ChooseTopics }

data class ScopeReductionPreview(val strategy: ScopeReduction, val savedMinutes: Int)

class PlanAdjustmentService {
    fun suggestedDeadline(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = currentStudyCalendar(preferences),
        dailyMinutesOverride: Int? = null,
    ): String? {
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        if (dailyMinutes == 0 || preferences.studyDays.isEmpty()) return null
        val target = requiredStudyMinutes(tasks)
        val cursor = dayOnly(today)
        var available = 0
        repeat(MAX_DEADLINE_SEARCH_DAYS) {
            if (cursor.studyDay in preferences.studyDays) available += dailyMinutes
            if (available >= target) {
                return (cursor.clone() as Calendar)
                    .apply { add(Calendar.DAY_OF_MONTH, 1) }
                    .toStudyDate()
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    fun scopePreviews(tasks: List<GeneratedStudyBlock>): List<ScopeReductionPreview> = listOf(
        ScopeReductionPreview(ScopeReduction.ChooseTopics, 0),
    )

    companion object {
        const val MAX_DEADLINE_SEARCH_DAYS = 366 * 3
    }
}

class TaskProgressCalculator {
    fun projectProgress(tasks: List<GeneratedStudyBlock>): Pair<Int, Int> {
        val included = tasks.filter(::countsTowardRequiredTime)
        return included.count { it.status == StudyTaskStatus.Completed } to included.size
    }

    fun topicProgress(tasks: List<GeneratedStudyBlock>, topicId: String): Pair<Int, Int> =
        projectProgress(tasks.filter { topicId in it.topicIds })
}

internal fun buildStudyMapData(
    plan: GeneratedStudyPlan,
    preferences: PlanSetupSubmission,
    dailyMinutesOverride: Int? = null,
    dailyAvailableMinutesByDate: Map<String, Int> = emptyMap(),
    taskStateById: Map<String, StudyTaskState> = emptyMap(),
    today: Calendar = currentStudyCalendar(preferences),
): StudyMapData {
    val dailyMinutes = dailyMinutesOverride ?: preferences.dailyStudyMinutes
    val schedulingPreferences = preferences.copy(dailyStudyMinutes = dailyMinutes)
    val schedulingPlan = plan.applyTaskState(taskStateById)
    val schedule = StudyScheduleCalculator().calculate(
        tasks = schedulingPlan.blocks,
        preferences = schedulingPreferences,
        today = today,
        dailyAvailableMinutesByDate = dailyAvailableMinutesByDate,
    )
    val realism = PlanRealismCalculator().calculate(
        tasks = schedulingPlan.blocks,
        preferences = schedulingPreferences,
        today = today,
        schedule = schedule,
        unscheduledTasks = schedule.unscheduledTasks,
        dailyAvailableMinutesByDate = dailyAvailableMinutesByDate,
    )
    return StudyMapData(schedulingPlan, schedulingPreferences, realism, schedule, dailyMinutes)
}

internal fun countsTowardRequiredTime(task: GeneratedStudyBlock): Boolean =
    task.status !in setOf(StudyTaskStatus.ExcludedByUser, StudyTaskStatus.DeferredByUser)

internal val StudyTaskType.isReviewType: Boolean
    get() = this in setOf(StudyTaskType.Review, StudyTaskType.Summary, StudyTaskType.MistakeReview)

private fun GeneratedStudyPlan.applyTaskState(
    stateById: Map<String, StudyTaskState>,
): GeneratedStudyPlan {
    if (stateById.isEmpty()) return this
    val updatedBlocks = blocks.map { block ->
        val state = stateById[block.id] ?: return@map block
        block.copy(status = state.status, scheduledDate = state.scheduledDate)
    }
    return copy(blocks = updatedBlocks)
}
