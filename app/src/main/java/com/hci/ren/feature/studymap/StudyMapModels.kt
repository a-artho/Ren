package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.TaskDisposition
import com.hci.ren.feature.plangeneration.TaskPriority
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.ceil

enum class StudyMapView { Schedule, Topics }

enum class PlanRealismStatus { OnTrack, Tight, Unrealistic, NoDeadline }

data class PlanRealism(
    val status: PlanRealismStatus,
    val remainingMinutes: Int,
    val availableMinutes: Int?,
    val shortageMinutes: Int,
)

data class StudyScheduleDay(
    val date: String,
    val tasks: List<GeneratedStudyBlock>,
    val capacityMinutes: Int,
) {
    val totalScheduledMinutes: Int get() = tasks.sumOf { it.durationMinutes.coerceAtLeast(0) }
    val isOverCapacity: Boolean get() = totalScheduledMinutes > capacityMinutes
}

data class StudySchedule(
    val days: List<StudyScheduleDay>,
    val unscheduledTasks: List<GeneratedStudyBlock>,
) {
    val visibleTasks: List<GeneratedStudyBlock> get() = days.flatMap { it.tasks } + unscheduledTasks
}

data class StudyMapData(
    val plan: GeneratedStudyPlan,
    val preferences: PlanSetupSubmission,
    val realism: PlanRealism,
    val schedule: StudySchedule,
    val dailyMinutes: Int,
) {
    val activeTasks: List<GeneratedStudyBlock>
        get() = plan.blocks.filterNot { it.isExcluded || it.status == StudyTaskStatus.Excluded }
    val requiredTasks: List<GeneratedStudyBlock>
        get() = activeTasks.filterNot { it.isOptional || it.disposition != TaskDisposition.MustComplete }
    val totalEstimatedMinutes: Int get() = requiredTasks.sumOf { it.durationMinutes.coerceAtLeast(0) }
    val completedTasks: Int get() = activeTasks.count { it.status == StudyTaskStatus.Completed }
    val progress: Float get() = if (activeTasks.isEmpty()) 0f else completedTasks.toFloat() / activeTasks.size
    val nextTask: GeneratedStudyBlock?
        get() {
            val completedIds = activeTasks.filter { it.status == StudyTaskStatus.Completed }.mapTo(mutableSetOf()) { it.id }
            return schedule.days.asSequence().flatMap { it.tasks.asSequence() }.firstOrNull {
                it.status !in setOf(StudyTaskStatus.Completed, StudyTaskStatus.Skipped, StudyTaskStatus.Excluded, StudyTaskStatus.Locked) &&
                    it.dependencies.all { dependency -> dependency in completedIds || activeTasks.none { task -> task.id == dependency } }
            }
        }
}

class PlanRealismCalculator {
    fun calculate(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = Calendar.getInstance(),
        dailyMinutesOverride: Int? = null,
    ): PlanRealism {
        val remaining = tasks.filter(::countsTowardRequiredTime)
            .filterNot { it.status == StudyTaskStatus.Completed }
            .sumOf { it.durationMinutes.coerceAtLeast(0) }
        if (preferences.deadline == StudyDeadline.NoFixedDeadline) {
            return PlanRealism(PlanRealismStatus.NoDeadline, remaining, null, 0)
        }
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val days = availableStudyDates(preferences, today)
        val available = days.size * dailyMinutes
        val status = when {
            remaining <= available * ON_TRACK_RATIO -> PlanRealismStatus.OnTrack
            remaining <= available * TIGHT_RATIO -> PlanRealismStatus.Tight
            else -> PlanRealismStatus.Unrealistic
        }
        return PlanRealism(status, remaining, available, (remaining - available).coerceAtLeast(0))
    }

    companion object {
        const val ON_TRACK_RATIO = 0.9
        const val TIGHT_RATIO = 1.15
    }
}

class StudyScheduleCalculator {
    fun calculate(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = Calendar.getInstance(),
        dailyMinutesOverride: Int? = null,
    ): StudySchedule {
        val capacity = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val eligibleDates = if (preferences.deadline == StudyDeadline.NoFixedDeadline) {
            upcomingStudyDates(preferences.studyDays, today, tasks.size.coerceAtLeast(1))
        } else {
            availableStudyDates(preferences, today)
        }
        if (capacity == 0 || eligibleDates.isEmpty()) {
            return StudySchedule(emptyList(), tasks.filter(::isSchedulable).map { it.copy(scheduledDate = null, status = StudyTaskStatus.Unscheduled) })
        }

        val dayTasks = linkedMapOf<String, MutableList<GeneratedStudyBlock>>()
        val remainingCapacity = eligibleDates.associate { it.toStudyDate() to capacity }.toMutableMap()
        val unscheduled = mutableListOf<GeneratedStudyBlock>()
        val completed = tasks.filter { it.status == StudyTaskStatus.Completed }
        val completedIds = completed.mapTo(mutableSetOf()) { it.id }

        completed.forEach { task ->
            val date = task.scheduledDate?.takeIf(remainingCapacity::containsKey) ?: eligibleDates.first().toStudyDate()
            dayTasks.getOrPut(date, ::mutableListOf).add(task.copy(scheduledDate = date))
            remainingCapacity[date] = (remainingCapacity[date] ?: capacity) - task.durationMinutes.coerceAtLeast(0)
        }

        tasks.filter(::isSchedulable)
            .filterNot { it.status == StudyTaskStatus.Completed }
            .sortedWith(compareBy<GeneratedStudyBlock> { it.order }.thenBy { it.priority.ordinal })
            .forEach { task ->
                val duration = task.durationMinutes.coerceAtLeast(1)
                val preferredDate = task.scheduledDate?.takeIf { date ->
                    remainingCapacity.containsKey(date) && (remainingCapacity[date] ?: 0) >= duration
                }
                val date = preferredDate ?: eligibleDates.firstOrNull {
                    val key = it.toStudyDate()
                    (remainingCapacity[key] ?: 0) >= duration && task.dependencies.all { dependency ->
                        dependency in completedIds || tasks.none { candidate -> candidate.id == dependency }
                    }
                }?.toStudyDate()
                if (date == null) {
                    val oversized = duration > capacity
                    unscheduled += task.copy(
                        scheduledDate = null,
                        status = if (oversized) StudyTaskStatus.OverCapacity else StudyTaskStatus.Unscheduled,
                    )
                } else {
                    dayTasks.getOrPut(date, ::mutableListOf).add(task.copy(scheduledDate = date))
                    remainingCapacity[date] = (remainingCapacity[date] ?: capacity) - duration
                }
            }

        tasks.filter { it.isOptional || it.disposition == TaskDisposition.IfTimeRemains }
            .filterNot { it.isExcluded || it.status == StudyTaskStatus.Completed }
            .forEach { unscheduled += it.copy(scheduledDate = null, status = StudyTaskStatus.Optional) }

        return StudySchedule(
            days = eligibleDates.mapNotNull { date ->
                val key = date.toStudyDate()
                dayTasks[key]?.takeIf(List<*>::isNotEmpty)?.let { StudyScheduleDay(key, it, capacity) }
            },
            unscheduledTasks = unscheduled.distinctBy { it.id },
        )
    }
}

enum class ScopeReduction { HighPriorityOnly, RemoveOptionalReviews, ReducePractice, ChooseTopics }

data class ScopeReductionPreview(val strategy: ScopeReduction, val savedMinutes: Int)

class PlanAdjustmentService {
    fun suggestedDeadline(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = Calendar.getInstance(),
        dailyMinutesOverride: Int? = null,
    ): String? {
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        if (dailyMinutes == 0 || preferences.studyDays.isEmpty()) return null
        val remaining = tasks.filter(::countsTowardRequiredTime)
            .filterNot { it.status == StudyTaskStatus.Completed }
            .sumOf { it.durationMinutes.coerceAtLeast(0) }
        val target = ceil(remaining * DEADLINE_BUFFER).toInt()
        val cursor = dayOnly(today)
        var available = 0
        repeat(MAX_DEADLINE_SEARCH_DAYS) {
            if (cursor.studyDay in preferences.studyDays) available += dailyMinutes
            if (available >= target) return cursor.toStudyDate()
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    fun scopePreviews(tasks: List<GeneratedStudyBlock>): List<ScopeReductionPreview> = listOf(
        ScopeReductionPreview(
            ScopeReduction.HighPriorityOnly,
            tasks.filter(::countsTowardRequiredTime).filter { it.priority != TaskPriority.High && it.isSkippable }.sumOf { it.durationMinutes },
        ),
        ScopeReductionPreview(
            ScopeReduction.RemoveOptionalReviews,
            tasks.filter(::countsTowardRequiredTime).filter { it.taskType.isReviewType && it.isSkippable }.sumOf { it.durationMinutes },
        ),
        ScopeReductionPreview(
            ScopeReduction.ReducePractice,
            tasks.filter(::countsTowardRequiredTime).filter { it.taskType == StudyTaskType.Practice }.sumOf { it.durationMinutes / 2 },
        ),
        ScopeReductionPreview(ScopeReduction.ChooseTopics, 0),
    )

    fun applyScope(
        tasks: List<GeneratedStudyBlock>,
        strategy: ScopeReduction,
        selectedTopicIds: Set<String> = emptySet(),
    ): List<GeneratedStudyBlock> = tasks.map { task ->
        when (strategy) {
            ScopeReduction.HighPriorityOnly -> if (task.priority != TaskPriority.High && task.isSkippable) task.excluded() else task
            ScopeReduction.RemoveOptionalReviews -> if (task.taskType.isReviewType && task.isSkippable) task.excluded() else task
            ScopeReduction.ReducePractice -> if (task.taskType == StudyTaskType.Practice) {
                task.copy(durationMinutes = maxOf(task.minimumUsefulMinutes, task.durationMinutes / 2))
            } else task
            ScopeReduction.ChooseTopics -> if (selectedTopicIds.isNotEmpty() && task.topicIds.none(selectedTopicIds::contains)) task.excluded() else task
        }
    }

    private fun GeneratedStudyBlock.excluded() = copy(
        isOptional = true,
        isExcluded = false,
        disposition = TaskDisposition.IfTimeRemains,
        status = StudyTaskStatus.Optional,
        scheduledDate = null,
    )

    companion object {
        const val DEADLINE_BUFFER = 1.1
        const val MAX_DEADLINE_SEARCH_DAYS = 366 * 3
    }
}

class TaskProgressCalculator {
    fun projectProgress(tasks: List<GeneratedStudyBlock>): Pair<Int, Int> {
        val included = tasks.filterNot { it.isExcluded || it.status == StudyTaskStatus.Excluded }
        return included.count { it.status == StudyTaskStatus.Completed } to included.size
    }

    fun topicProgress(tasks: List<GeneratedStudyBlock>, topicId: String): Pair<Int, Int> =
        projectProgress(tasks.filter { topicId in it.topicIds })
}

internal fun buildStudyMapData(
    plan: GeneratedStudyPlan,
    preferences: PlanSetupSubmission,
    dailyMinutesOverride: Int? = null,
    today: Calendar = Calendar.getInstance(),
): StudyMapData {
    val realism = PlanRealismCalculator().calculate(plan.blocks, preferences, today, dailyMinutesOverride)
    val schedule = StudyScheduleCalculator().calculate(plan.blocks, preferences, today, dailyMinutesOverride)
    return StudyMapData(plan, preferences, realism, schedule, dailyMinutesOverride ?: preferences.dailyStudyMinutes)
}

internal fun countsTowardRequiredTime(task: GeneratedStudyBlock): Boolean =
    !task.isExcluded && task.status != StudyTaskStatus.Excluded && !task.isOptional && task.disposition == TaskDisposition.MustComplete

private fun isSchedulable(task: GeneratedStudyBlock): Boolean =
    countsTowardRequiredTime(task) && task.status != StudyTaskStatus.Skipped

internal val StudyTaskType.isReviewType: Boolean
    get() = this in setOf(StudyTaskType.Review, StudyTaskType.Summary, StudyTaskType.MistakeReview)

internal fun availableStudyDates(preferences: PlanSetupSubmission, today: Calendar): List<Calendar> {
    val end = deadlineDate(preferences, today) ?: return emptyList()
    val cursor = dayOnly(today)
    if (end.before(cursor)) return emptyList()
    return buildList {
        while (!cursor.after(end)) {
            if (cursor.studyDay in preferences.studyDays) add(cursor.clone() as Calendar)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

private fun upcomingStudyDates(days: Set<StudyDay>, today: Calendar, taskCount: Int): List<Calendar> {
    if (days.isEmpty()) return emptyList()
    val cursor = dayOnly(today)
    val target = taskCount.coerceAtMost(365)
    return buildList {
        repeat(366) {
            if (cursor.studyDay in days) add(cursor.clone() as Calendar)
            if (size >= target) return@buildList
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

internal fun deadlineDate(preferences: PlanSetupSubmission, today: Calendar): Calendar? = when (preferences.deadline) {
    StudyDeadline.Today -> dayOnly(today)
    StudyDeadline.InThreeDays -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 2) }
    StudyDeadline.InOneWeek -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 6) }
    StudyDeadline.ChooseDate -> preferences.deadlineDate?.toStudyCalendar()
    StudyDeadline.NoFixedDeadline -> null
}

internal fun String.toStudyCalendar(): Calendar? = runCatching {
    val parts = split('-').map(String::toInt)
    require(parts.size == 3)
    GregorianCalendar(parts[0], parts[1] - 1, parts[2]).apply { isLenient = false; timeInMillis }
}.getOrNull()

internal fun Calendar.toStudyDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(time)

internal fun dayOnly(value: Calendar): Calendar = (value.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

internal val Calendar.studyDay: StudyDay get() = when (get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> StudyDay.Monday
    Calendar.TUESDAY -> StudyDay.Tuesday
    Calendar.WEDNESDAY -> StudyDay.Wednesday
    Calendar.THURSDAY -> StudyDay.Thursday
    Calendar.FRIDAY -> StudyDay.Friday
    Calendar.SATURDAY -> StudyDay.Saturday
    else -> StudyDay.Sunday
}
