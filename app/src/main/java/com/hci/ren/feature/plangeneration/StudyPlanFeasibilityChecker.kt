package com.hci.ren.feature.plangeneration

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class FeasibilityStatus { Realistic, Intensive, Unrealistic }
enum class RealityCheckAction { PrioritizeMostImportant, ExtendDeadline, ReduceGoal, ContinueAnyway }

data class FeasibilityResult(
    val status: FeasibilityStatus,
    val totalRequiredMinutes: Int,
    val availableMinutes: Int,
    val shortageMinutes: Int,
    val workloadRatio: Double,
    val estimatedCoveragePercent: Int,
    val recommendedDaysBalanced: Int,
    val recommendedDaysIntensive: Int,
    val availableMinutesPerStudyDay: Int,
    val hasDeadline: Boolean,
    val suggestedActions: List<RealityCheckAction>,
)

class StudyPlanFeasibilityChecker {
    fun check(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = Calendar.getInstance(),
        dailyMinutesOverride: Int? = null,
    ): FeasibilityResult {
        val baseMinutes = tasks.sumOf { it.durationMinutes.coerceAtLeast(it.minimumUsefulMinutes) }
        val required = (baseMinutes * (100 + preferences.goal.bufferPercent) + 99) / 100
        val hasDeadline = preferences.deadline != StudyDeadline.NoFixedDeadline
        val availableStudyDays = if (hasDeadline) countAvailableDays(preferences, today) else 0
        val dailyMinutes = dailyMinutesOverride ?: preferences.dailyStudyMinutes
        val available = if (hasDeadline) availableStudyDays * dailyMinutes else required
        val availableMinutesPerStudyDay = if (availableStudyDays > 0) {
            available / availableStudyDays
        } else {
            dailyMinutes
        }
        val ratio = if (available == 0) Double.POSITIVE_INFINITY else required.toDouble() / available
        val coverage = when {
            required == 0 -> 100
            available == 0 -> 0
            else -> ((available.toDouble() / required) * 100).roundToInt().coerceAtMost(100)
        }
        val status = when {
            !hasDeadline -> FeasibilityStatus.Realistic
            required <= available * 0.9 -> FeasibilityStatus.Realistic
            required <= available * 1.15 -> FeasibilityStatus.Intensive
            else -> FeasibilityStatus.Unrealistic
        }
        return FeasibilityResult(status, required, available, (required - available).coerceAtLeast(0), ratio, coverage,
            daysNeeded(required, availableMinutesPerStudyDay),
            daysNeeded(required, (availableMinutesPerStudyDay * 1.5).roundToInt()),
            availableMinutesPerStudyDay,
            hasDeadline, if (status == FeasibilityStatus.Unrealistic) RealityCheckAction.entries else emptyList())
    }

    private fun countAvailableDays(preferences: PlanSetupSubmission, today: Calendar): Int {
        val end = deadlineDate(preferences, today) ?: return 0
        val cursor = dayOnly(today)
        if (end.before(cursor)) return 0
        var count = 0
        while (!cursor.after(end)) {
            if (cursor.studyDay in preferences.studyDays) count++
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return count
    }

    private fun deadlineDate(preferences: PlanSetupSubmission, today: Calendar): Calendar? = when (preferences.deadline) {
        StudyDeadline.Today -> dayOnly(today)
        StudyDeadline.InThreeDays -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 2) }
        StudyDeadline.InOneWeek -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 6) }
        StudyDeadline.ChooseDate -> preferences.deadlineDate?.toCalendarDate()
        StudyDeadline.NoFixedDeadline -> null
    }

    private fun daysNeeded(minutes: Int, dailyMinutes: Int) = if (minutes == 0) 0 else ceil(minutes.toDouble() / dailyMinutes.coerceAtLeast(1)).toInt()
}

private val StudyGoal.bufferPercent: Int get() = when (this) {
    StudyGoal.LearnThoroughly -> 20
    StudyGoal.PrepareForExam -> 15
    StudyGoal.ReviseKnown -> 10
    StudyGoal.FinishQuickly -> 5
    StudyGoal.OngoingStudy -> 10
}

private val Calendar.studyDay: StudyDay get() = when (get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> StudyDay.Monday
    Calendar.TUESDAY -> StudyDay.Tuesday
    Calendar.WEDNESDAY -> StudyDay.Wednesday
    Calendar.THURSDAY -> StudyDay.Thursday
    Calendar.FRIDAY -> StudyDay.Friday
    Calendar.SATURDAY -> StudyDay.Saturday
    else -> StudyDay.Sunday
}

private fun dayOnly(value: Calendar): Calendar = (value.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun String.toCalendarDate(): Calendar? = runCatching {
    val parts = split('-').map(String::toInt); require(parts.size == 3)
    GregorianCalendar(parts[0], parts[1] - 1, parts[2]).apply { isLenient = false; timeInMillis }
}.getOrNull()

class StudyPlanAdapter {
    fun fit(
        tasks: List<GeneratedStudyBlock>,
        availableMinutes: Int,
        prioritised: Boolean,
        preservePractice: Boolean = false,
    ): List<GeneratedStudyBlock> {
        var remaining = availableMinutes.coerceAtLeast(0)
        val ordered = if (prioritised) {
            tasks.sortedWith(
                compareBy<GeneratedStudyBlock> { it.priority.ordinal }
                    .thenBy { if (preservePractice && it.taskType == StudyTaskType.Practice) 0 else 1 }
                    .thenBy { it.order },
            )
        } else tasks.sortedBy { it.order }
        var optionalAssigned = false
        return ordered.map { task ->
            val fullDuration = task.durationMinutes.coerceAtLeast(task.minimumUsefulMinutes)
            when {
                fullDuration <= remaining -> { remaining -= fullDuration; task.copy(disposition = TaskDisposition.MustComplete) }
                task.minimumUsefulMinutes <= remaining -> {
                    val usefulDuration = remaining; remaining = 0
                    task.copy(durationMinutes = usefulDuration, disposition = TaskDisposition.MustComplete)
                }
                !optionalAssigned && task.isSkippable -> {
                    optionalAssigned = true
                    task.copy(disposition = TaskDisposition.IfTimeRemains)
                }
                else -> task.copy(disposition = TaskDisposition.Postponed)
            }
        }
    }
}

class StudyPlanScopeAdjuster {
    fun applyGoalStrategy(
        tasks: List<GeneratedStudyBlock>,
        goal: StudyScopeGoal,
        selectedTopics: Set<String> = emptySet(),
    ): List<GeneratedStudyBlock> = when (goal) {
        StudyScopeGoal.PassExam -> passExam(tasks)
        StudyScopeGoal.ReviseOnly -> tasks.map(::asReviewTask)
        StudyScopeGoal.Fundamentals -> tasks.filter { it.priority == TaskPriority.High || !it.isSkippable || it.taskType == StudyTaskType.Learn }
            .filterNot { it.priority == TaskPriority.Low && it.isSkippable }
        StudyScopeGoal.SkimEverything -> tasks.map(::asSkimTask)
        StudyScopeGoal.SelectedTopics -> tasks.mapNotNull { task ->
            val matching = task.topicIds.filter { it in selectedTopics }
            task.takeIf { matching.isNotEmpty() }?.copy(topicIds = matching)
        }
        StudyScopeGoal.CompleteEverything -> tasks
    }.map { it.copy(disposition = TaskDisposition.MustComplete) }

    private fun passExam(tasks: List<GeneratedStudyBlock>): List<GeneratedStudyBlock> {
        val selected = tasks.filter {
            it.priority == TaskPriority.High || !it.isSkippable ||
                it.taskType in setOf(StudyTaskType.Practice, StudyTaskType.Quiz, StudyTaskType.MockExam)
        }
        val practice = tasks.firstOrNull { it.taskType == StudyTaskType.Practice }
        return if (practice != null && selected.none { it.id == practice.id }) selected + practice else selected
    }

    private fun asReviewTask(task: GeneratedStudyBlock): GeneratedStudyBlock {
        if (task.taskType != StudyTaskType.Learn) return task
        val minimum = maxOf(task.minimumUsefulMinutes, StudyTaskType.Review.defaultMinimumMinutes)
        return task.copy(
            durationMinutes = maxOf(minimum, (task.durationMinutes * 0.6).roundToInt()),
            minimumUsefulMinutes = minimum,
            taskType = StudyTaskType.Review,
            instructions = "Review the key ideas, formulas, and a representative example. ${task.instructions}",
        )
    }

    private fun asSkimTask(task: GeneratedStudyBlock): GeneratedStudyBlock {
        val skimMinutes = task.durationMinutes.coerceIn(StudyTaskType.Skim.defaultMinimumMinutes, 10)
        return task.copy(
            durationMinutes = skimMinutes,
            minimumUsefulMinutes = StudyTaskType.Skim.defaultMinimumMinutes,
            taskType = StudyTaskType.Skim,
            instructions = "Skim for broad awareness; this does not provide mastery. ${task.instructions}",
        )
    }
}
