package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.plangeneration.EstimateConfidence
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.effectiveCognitivePoints
import com.hci.ren.feature.plangeneration.requiredBlockMinutes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.ceil

class StudyScheduleCalculator {
    fun calculate(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = currentStudyCalendar(preferences),
        dailyMinutesOverride: Int? = null,
        dailyAvailableMinutesByDate: Map<String, Int> = emptyMap(),
    ): StudySchedule {
        val capacity = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val eligibleDates = availableStudyDates(preferences, today)
        val eligibleDateKeys = eligibleDates.map { it.toStudyDate() }
        val capacityByDate = eligibleDateKeys.associateWith { date ->
            dailyAvailableMinutesByDate[date]?.coerceIn(0, 1_440) ?: capacity
        }
        val maxTaskCapacity = capacityByDate.values.maxOrNull() ?: 0
        if (eligibleDates.isEmpty() || maxTaskCapacity <= 0) {
            return StudySchedule(
                days = emptyList(),
                unscheduledTasks = tasks
                    .filter(::shouldSurfaceAsUnscheduledWithoutCapacity)
                    .map { task ->
                        if (task.status == StudyTaskStatus.Locked) {
                            task.copy(scheduledDate = null)
                        } else {
                            task.copy(scheduledDate = null, status = StudyTaskStatus.Unscheduled)
                        }
                    },
            )
        }

        val dayTasks = linkedMapOf<String, MutableList<GeneratedStudyBlock>>()
        val remainingCapacity = capacityByDate.toMutableMap()
        val unscheduled = mutableListOf<GeneratedStudyBlock>()
        val fixedTaskIds = mutableSetOf<String>()

        tasks.filter(::isFixedScheduledTask)
            .sortedBy { it.order }
            .forEach { task ->
                val date = task.scheduledDate?.takeIf { it in remainingCapacity }
                if (date == null) {
                    if (task.status == StudyTaskStatus.Locked) {
                        unscheduled += task.copy(scheduledDate = null)
                    }
                    fixedTaskIds += task.id
                    return@forEach
                }
                dayTasks.getOrPut(date, ::mutableListOf).add(task.copy(scheduledDate = date))
                remainingCapacity[date] = (remainingCapacity[date] ?: capacityByDate[date].orZero()) -
                    task.durationMinutes.coerceAtLeast(0)
                fixedTaskIds += task.id
            }

        val orderedTasks = tasks.filter(::isSchedulable)
            .filterNot { it.id in fixedTaskIds }
            .sortedBy { it.order }
        val (candidateTasks, deferredTasks) = trimSuffixToAvailableCapacity(
            tasks = orderedTasks,
            availableMinutes = remainingCapacity.values.sumOf { it.coerceAtLeast(0) },
        )
        deferredTasks.forEach { task ->
            unscheduled += task.copy(scheduledDate = null, status = StudyTaskStatus.Unscheduled)
        }

        val (feasibleCount, assignedDates) = largestFeasiblePrefixAssignments(
            tasks = candidateTasks,
            eligibleDates = eligibleDateKeys,
            remainingCapacity = remainingCapacity,
            existingDayTasks = dayTasks,
        )
        val schedulableTasks = candidateTasks.take(feasibleCount)
        candidateTasks.drop(feasibleCount).forEachIndexed { index, task ->
            val blockingOversizedTask = index == 0 && task.durationMinutes.coerceAtLeast(1) > maxTaskCapacity
            unscheduled += task.copy(
                scheduledDate = null,
                status = if (blockingOversizedTask) StudyTaskStatus.OverCapacity else StudyTaskStatus.Unscheduled,
            )
        }

        val safeAssignedDates = assignedDates ?: orderedScheduleAssignments(
            tasks = schedulableTasks,
            eligibleDates = eligibleDateKeys,
            remainingCapacity = remainingCapacity,
            existingDayTasks = dayTasks,
        )

        if (safeAssignedDates == null) {
            schedulableTasks.forEach { task ->
                val oversized = task.durationMinutes.coerceAtLeast(1) > maxTaskCapacity
                unscheduled += task.copy(
                    scheduledDate = null,
                    status = if (oversized) StudyTaskStatus.OverCapacity else StudyTaskStatus.Unscheduled,
                )
            }
        } else {
            schedulableTasks.zip(safeAssignedDates).forEach { (task, date) ->
                val scheduledTask = task.copy(scheduledDate = date)
                dayTasks.getOrPut(date, ::mutableListOf).add(scheduledTask)
                remainingCapacity[date] = (remainingCapacity[date] ?: capacityByDate[date].orZero()) -
                    task.durationMinutes.coerceAtLeast(1)
            }
        }

        return StudySchedule(
            days = eligibleDates.mapIndexedNotNull { index, date ->
                val key = date.toStudyDate()
                dayTasks[key]?.takeIf(List<*>::isNotEmpty)?.let {
                    StudyScheduleDay(
                        date = key,
                        tasks = it.sortedBy { task -> task.order },
                        capacityMinutes = capacityByDate[key] ?: capacity,
                        dayIndex = index + 1,
                        load = dayLoad(dayLoadIndex(it, capacityByDate[key] ?: capacity)),
                    )
                }
            },
            unscheduledTasks = unscheduled.distinctBy { it.id },
        )
    }
}

private fun isFixedScheduledTask(task: GeneratedStudyBlock): Boolean =
    task.status == StudyTaskStatus.Completed ||
        (task.status == StudyTaskStatus.Locked && task.scheduledDate != null)

private fun shouldSurfaceAsUnscheduledWithoutCapacity(task: GeneratedStudyBlock): Boolean =
    countsTowardRequiredTime(task) && task.status != StudyTaskStatus.Completed

private fun isSchedulable(task: GeneratedStudyBlock): Boolean =
    countsTowardRequiredTime(task) &&
        task.status !in setOf(
            StudyTaskStatus.Completed,
            StudyTaskStatus.DeferredByUser,
            StudyTaskStatus.Locked,
        )

private fun largestFeasiblePrefixAssignments(
    tasks: List<GeneratedStudyBlock>,
    eligibleDates: List<String>,
    remainingCapacity: Map<String, Int>,
    existingDayTasks: Map<String, List<GeneratedStudyBlock>>,
): Pair<Int, List<String>?> {
    if (tasks.isEmpty()) return 0 to emptyList()
    if (eligibleDates.isEmpty()) return 0 to null

    var low = 0
    var high = tasks.size
    var bestCount = 0
    var bestAssignments: List<String>? = emptyList()
    while (low <= high) {
        val mid = (low + high) / 2
        val assignments = orderedScheduleAssignments(
            tasks = tasks.take(mid),
            eligibleDates = eligibleDates,
            remainingCapacity = remainingCapacity,
            existingDayTasks = existingDayTasks,
        )
        if (assignments != null) {
            bestCount = mid
            bestAssignments = assignments
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return bestCount to bestAssignments
}

private fun trimSuffixToAvailableCapacity(
    tasks: List<GeneratedStudyBlock>,
    availableMinutes: Int,
): Pair<List<GeneratedStudyBlock>, List<GeneratedStudyBlock>> {
    var remaining = tasks.sumOf { it.durationMinutes.coerceAtLeast(1) }
    var keepCount = tasks.size
    while (keepCount > 0 && remaining > availableMinutes) {
        keepCount -= 1
        remaining -= tasks[keepCount].durationMinutes.coerceAtLeast(1)
    }
    return tasks.take(keepCount) to tasks.drop(keepCount)
}

private fun orderedScheduleAssignments(
    tasks: List<GeneratedStudyBlock>,
    eligibleDates: List<String>,
    remainingCapacity: Map<String, Int>,
    existingDayTasks: Map<String, List<GeneratedStudyBlock>>,
): List<String>? {
    if (tasks.isEmpty()) return emptyList()
    if (eligibleDates.isEmpty()) return null

    val taskCount = tasks.size
    val dayCount = eligibleDates.size
    val durationPrefix = prefixSums(tasks.map { it.durationMinutes.coerceAtLeast(1) })
    val robustPrefix = prefixSums(tasks.map { it.robustStudyMinutes })
    val cognitivePrefix = prefixSums(tasks.map { it.cognitiveStudyPoints })
    val targetRobust = ceil(tasks.sumOf { it.robustStudyMinutes }.toDouble() / dayCount)
        .toInt()
        .coerceAtLeast(1)
    val targetCognitive = ceil(tasks.sumOf { it.cognitiveStudyPoints }.toDouble() / dayCount)
        .toInt()
        .coerceAtLeast(1)

    val impossible = Double.POSITIVE_INFINITY
    val dp = Array(dayCount + 1) { DoubleArray(taskCount + 1) { impossible } }
    val previous = Array(dayCount + 1) { arrayOfNulls<Int>(taskCount + 1) }
    dp[0][0] = 0.0

    for (day in 1..dayCount) {
        val date = eligibleDates[day - 1]
        val existingTasks = existingDayTasks[date].orEmpty()
        val existingRobust = existingTasks.sumOf { it.robustStudyMinutes }
        val existingCognitive = existingTasks.sumOf { it.cognitiveStudyPoints }
        for (end in 0..taskCount) {
            for (start in 0..end) {
                val priorCost = dp[day - 1][start]
                if (priorCost.isInfinite()) continue
                val duration = durationPrefix[end] - durationPrefix[start]
                if (duration > (remainingCapacity[date] ?: 0)) continue
                val blockCount = end - start
                val robust = robustPrefix[end] - robustPrefix[start]
                val cognitive = cognitivePrefix[end] - cognitivePrefix[start]
                var cost = dayScheduleCost(
                    dayIndex = day - 1,
                    blockCount = blockCount,
                    robust = robust + existingRobust,
                    cognitive = cognitive + existingCognitive,
                    segmentRobust = robust,
                    targetRobust = targetRobust,
                    targetCognitive = targetCognitive,
                )
                if (start > 0 && end > start && sameContinuityGroup(tasks[start - 1], tasks[start])) {
                    cost += 250.0
                }
                val candidate = priorCost + cost
                if (candidate < dp[day][end]) {
                    dp[day][end] = candidate
                    previous[day][end] = start
                }
            }
        }
    }

    if (dp[dayCount][taskCount].isInfinite()) return null

    val result = MutableList(taskCount) { eligibleDates.first() }
    var end = taskCount
    for (day in dayCount downTo 1) {
        val start = previous[day][end] ?: return null
        for (index in start until end) {
            result[index] = eligibleDates[day - 1]
        }
        end = start
    }
    return result
}

private fun prefixSums(values: List<Int>): List<Int> = buildList {
    add(0)
    values.forEach { add(last() + it) }
}

private fun dayScheduleCost(
    dayIndex: Int,
    blockCount: Int,
    robust: Int,
    cognitive: Int,
    segmentRobust: Int,
    targetRobust: Int,
    targetCognitive: Int,
): Double {
    if (blockCount == 0) return 0.0
    val robustDeviation = robust - targetRobust
    val cognitiveDeviation = cognitive - targetCognitive
    val balance = robustDeviation * robustDeviation + cognitiveDeviation * cognitiveDeviation
    val lateRisk = dayIndex * segmentRobust * 2
    val contextSwitch = maxOf(0, blockCount - 1) * 20
    return (balance + lateRisk + contextSwitch).toDouble()
}

private fun sameContinuityGroup(previous: GeneratedStudyBlock, current: GeneratedStudyBlock): Boolean =
    !previous.continuityGroup.isNullOrBlank() && previous.continuityGroup == current.continuityGroup

internal val GeneratedStudyBlock.robustStudyMinutes: Int
    get() = requiredBlockMinutes()

internal val GeneratedStudyBlock.cognitiveStudyPoints: Int
    get() = maxOf(effectiveCognitivePoints(), robustStudyMinutes, 1)

internal fun targetMinutesForCapacity(capacityMinutes: Int): Int =
    (capacityMinutes * 0.85).toInt().coerceAtLeast(1)

internal fun dayLoadIndex(tasks: List<GeneratedStudyBlock>, capacityMinutes: Int): Double {
    if (tasks.isEmpty() || capacityMinutes <= 0) return 0.0
    val target = targetMinutesForCapacity(capacityMinutes)
    val robustRatio = tasks.sumOf { it.robustStudyMinutes }.toDouble() / target
    val cognitiveRatio = tasks.sumOf { it.cognitiveStudyPoints }.toDouble() / target
    val contextSwitchPenalty = maxOf(0, tasks.size - 3) * 0.03
    val densePenalty = if (tasks.count { (it.difficultyScore ?: 3) >= 4 || it.difficulty == StudyBlockDifficulty.Heavy } >= 2) 0.05 else 0.0
    return maxOf(robustRatio, cognitiveRatio) + contextSwitchPenalty + densePenalty
}

private fun dayLoad(loadIndex: Double): StudyBlockDifficulty = when {
    loadIndex > 1.05 -> StudyBlockDifficulty.Heavy
    loadIndex >= 0.65 -> StudyBlockDifficulty.Standard
    else -> StudyBlockDifficulty.Light
}

internal fun dayReasonCodes(tasks: List<GeneratedStudyBlock>, capacityMinutes: Int): List<String> {
    if (tasks.isEmpty() || capacityMinutes <= 0) return emptyList()
    val target = targetMinutesForCapacity(capacityMinutes)
    return buildList {
        if (tasks.sumOf { it.robustStudyMinutes } >= target * 0.9) add("TIME_NEAR_CAPACITY")
        if (tasks.sumOf { it.cognitiveStudyPoints } >= target * 0.9) add("COGNITIVE_LOAD_NEAR_CAPACITY")
        if (tasks.count { (it.difficultyScore ?: 3) >= 4 || it.difficulty == StudyBlockDifficulty.Heavy } >= 2) add("MULTIPLE_DENSE_BLOCKS")
        if (tasks.any { it.estimateConfidence == EstimateConfidence.Low }) add("LOW_CONFIDENCE_ESTIMATE")
        if (tasks.any { !it.continuityGroup.isNullOrBlank() }) add("ORDER_CONTINUITY")
    }
}

internal fun availableStudyDates(preferences: PlanSetupSubmission, today: Calendar): List<Calendar> {
    val end = deadlineDate(preferences, today) ?: return emptyList()
    val cursor = dayOnly(today)
    if (!end.after(cursor)) return emptyList()
    return buildList {
        while (cursor.before(end)) {
            if (cursor.studyDay in preferences.studyDays) add(cursor.clone() as Calendar)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

internal fun deadlineDate(preferences: PlanSetupSubmission, today: Calendar): Calendar? = when (preferences.deadline) {
    StudyDeadline.Tomorrow -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 1) }
    StudyDeadline.InThreeDays -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 3) }
    StudyDeadline.InOneWeek -> dayOnly(today).apply { add(Calendar.DAY_OF_MONTH, 7) }
    StudyDeadline.ChooseDate -> preferences.deadlineDate?.toStudyCalendar()
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

internal fun currentStudyCalendar(preferences: PlanSetupSubmission): Calendar =
    Calendar.getInstance().asStudyCalendar(preferences)

internal fun currentStudyCalendar(resetOffsetHours: Int): Calendar =
    Calendar.getInstance().asStudyCalendar(resetOffsetHours)

internal fun Calendar.asStudyCalendar(preferences: PlanSetupSubmission): Calendar =
    asStudyCalendar(preferences.studyDayResetOffsetHours)

internal fun Calendar.asStudyCalendar(resetOffsetHours: Int): Calendar =
    (clone() as Calendar).apply {
        add(Calendar.HOUR_OF_DAY, -resetOffsetHours.coerceIn(0, 23))
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
