package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.requiredStudyMinutes
import java.util.Calendar
import kotlin.math.roundToInt

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
        get() = activeTasks
    val totalEstimatedMinutes: Int get() = requiredStudyMinutes(requiredTasks)
    val completedTasks: Int get() = activeTasks.count { it.status == StudyTaskStatus.Completed }
    val progress: Float get() = if (activeTasks.isEmpty()) 0f else completedTasks.toFloat() / activeTasks.size
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

    fun applyScope(
        tasks: List<GeneratedStudyBlock>,
        strategy: ScopeReduction,
        selectedTopicIds: Set<String> = emptySet(),
    ): List<GeneratedStudyBlock> = tasks.map { task ->
        when (strategy) {
            ScopeReduction.ChooseTopics -> if (selectedTopicIds.isNotEmpty() && task.topicIds.none(selectedTopicIds::contains)) task.excluded() else task
        }
    }

    private fun GeneratedStudyBlock.excluded() = copy(
        status = StudyTaskStatus.ExcludedByUser,
        scheduledDate = null,
    )

    companion object {
        const val MAX_DEADLINE_SEARCH_DAYS = 366 * 3
    }
}

class TaskProgressCalculator {
    fun projectProgress(tasks: List<GeneratedStudyBlock>): Pair<Int, Int> {
        val included = tasks.filterNot { it.status == StudyTaskStatus.ExcludedByUser }
        return included.count { it.status == StudyTaskStatus.Completed } to included.size
    }

    fun topicProgress(tasks: List<GeneratedStudyBlock>, topicId: String): Pair<Int, Int> =
        projectProgress(tasks.filter { topicId in it.topicIds })
}

internal fun buildStudyMapData(
    plan: GeneratedStudyPlan,
    preferences: PlanSetupSubmission,
    dailyMinutesOverride: Int? = null,
    today: Calendar = currentStudyCalendar(preferences),
): StudyMapData {
    val dailyMinutes = dailyMinutesOverride ?: preferences.dailyStudyMinutes
    val schedulingPreferences = preferences.copy(dailyStudyMinutes = dailyMinutes)
    val schedulingPlan = plan.prepareForLocalScheduling(schedulingPreferences)
    val schedule = StudyScheduleCalculator().calculate(schedulingPlan.blocks, schedulingPreferences, today)
    val realism = PlanRealismCalculator().calculate(
        tasks = schedulingPlan.blocks,
        preferences = schedulingPreferences,
        today = today,
        unscheduledTasks = schedule.unscheduledTasks,
    )
    return StudyMapData(schedulingPlan, schedulingPreferences, realism, schedule, dailyMinutes)
}

internal fun countsTowardRequiredTime(task: GeneratedStudyBlock): Boolean =
    task.status !in setOf(StudyTaskStatus.ExcludedByUser, StudyTaskStatus.DeferredByUser)

internal val StudyTaskType.isReviewType: Boolean
    get() = this in setOf(StudyTaskType.Review, StudyTaskType.Summary, StudyTaskType.MistakeReview)

internal fun GeneratedStudyBlock.withLocalDuration(minutes: Int): GeneratedStudyBlock {
    val coerced = minutes.coerceIn(1, 1_440)
    val newMinimum = minOf(minimumUsefulMinutes, coerced).coerceAtLeast(1)
    val newEffortMin = minOf(coerced, maxOf(1, newMinimum, (coerced * 0.7).roundToInt()))
    val newEffortMax = maxOf(coerced, (coerced * 1.35).roundToInt())
    return copy(
        durationMinutes = coerced,
        estimatedMinutes = coerced,
        minimumUsefulMinutes = newMinimum,
        effortMinMinutes = newEffortMin,
        effortLikelyMinutes = coerced,
        effortMaxMinutes = newEffortMax.coerceAtMost(1_440),
    )
}
