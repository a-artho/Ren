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

        val fixedTasksByDate = linkedMapOf<String, MutableList<GeneratedStudyBlock>>()
        val unscheduled = mutableListOf<GeneratedStudyBlock>()
        val fixedTaskIds = mutableSetOf<String>()

        tasks.filter(::isFixedScheduledTask)
            .sortedBy { it.order }
            .forEach { task ->
                val date = task.scheduledDate?.takeIf { it in capacityByDate }
                if (date == null) {
                    if (task.status == StudyTaskStatus.Locked) {
                        unscheduled += task.copy(scheduledDate = null)
                    }
                    fixedTaskIds += task.id
                    return@forEach
                }
                fixedTasksByDate.getOrPut(date, ::mutableListOf).add(task.copy(scheduledDate = date))
                fixedTaskIds += task.id
            }

        val orderedTasks = tasks.filter(::isSchedulable)
            .filterNot { it.id in fixedTaskIds }
            .sortedBy { it.order }
        val reservedAttempt = scheduleAttemptForMode(
            tasks = orderedTasks,
            eligibleDates = eligibleDateKeys,
            capacityByDate = capacityByDate,
            fixedTasksByDate = fixedTasksByDate,
            fitMode = ScheduleFitMode.Reserved,
        )
        val likelyAttempt = if (reservedAttempt.scheduledCount < orderedTasks.size || reservedAttempt.fixedOverCapacity) {
            scheduleAttemptForMode(
                tasks = orderedTasks,
                eligibleDates = eligibleDateKeys,
                capacityByDate = capacityByDate,
                fixedTasksByDate = fixedTasksByDate,
                fitMode = ScheduleFitMode.LikelyFallback,
            )
        } else {
            null
        }
        val attempt = likelyAttempt
            ?.takeIf {
                it.scheduledCount > reservedAttempt.scheduledCount ||
                    (
                        it.scheduledCount == reservedAttempt.scheduledCount &&
                            reservedAttempt.fixedOverCapacity &&
                            !it.fixedOverCapacity
                    )
            }
            ?: reservedAttempt
        val candidateTasks = attempt.candidateTasks
        val deferredTasks = attempt.deferredTasks
        deferredTasks.forEach { task ->
            unscheduled += task.copy(scheduledDate = null, status = StudyTaskStatus.Unscheduled)
        }

        val feasibleCount = attempt.feasibleCount
        val assignedDates = attempt.assignedDates
        val schedulableTasks = candidateTasks.take(feasibleCount)
        candidateTasks.drop(feasibleCount).forEachIndexed { index, task ->
            val blockingOversizedTask = index == 0 && attempt.fitMinutes(task).coerceAtLeast(1) > maxTaskCapacity
            unscheduled += task.copy(
                scheduledDate = null,
                status = if (blockingOversizedTask) StudyTaskStatus.OverCapacity else StudyTaskStatus.Unscheduled,
            )
        }

        val safeAssignedDates = assignedDates ?: orderedScheduleAssignments(
            tasks = schedulableTasks,
            eligibleDates = eligibleDateKeys,
            remainingCapacity = attempt.remainingCapacity,
            existingDayTasks = fixedTasksByDate,
            fitMinutes = attempt.fitMinutes,
        )

        val dayTasks = linkedMapOf<String, MutableList<GeneratedStudyBlock>>().apply {
            fixedTasksByDate.forEach { (date, tasks) -> put(date, tasks.toMutableList()) }
        }
        val remainingCapacity = attempt.remainingCapacity.toMutableMap()
        if (safeAssignedDates == null) {
            schedulableTasks.forEach { task ->
                val oversized = attempt.fitMinutes(task).coerceAtLeast(1) > maxTaskCapacity
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
                    attempt.fitMinutes(task).coerceAtLeast(1)
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
                        fitMode = attempt.fitMode,
                        load = dayLoad(dayLoadIndex(it, capacityByDate[key] ?: capacity)),
                    )
                }
            },
            unscheduledTasks = unscheduled.distinctBy { it.id },
            fitMode = attempt.fitMode,
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

private data class ScheduleAttempt(
    val candidateTasks: List<GeneratedStudyBlock>,
    val deferredTasks: List<GeneratedStudyBlock>,
    val feasibleCount: Int,
    val assignedDates: List<String>?,
    val fitMode: ScheduleFitMode,
    val fitMinutes: (GeneratedStudyBlock) -> Int,
    val remainingCapacity: Map<String, Int>,
    val fixedOverCapacity: Boolean,
) {
    val scheduledCount: Int get() = feasibleCount
}

private fun scheduleAttemptForMode(
    tasks: List<GeneratedStudyBlock>,
    eligibleDates: List<String>,
    capacityByDate: Map<String, Int>,
    fixedTasksByDate: Map<String, List<GeneratedStudyBlock>>,
    fitMode: ScheduleFitMode,
): ScheduleAttempt {
    val remainingCapacity = capacityAfterFixedTasks(
        eligibleDates = eligibleDates,
        capacityByDate = capacityByDate,
        fixedTasksByDate = fixedTasksByDate,
        mode = fitMode,
    )
    val fixedOverCapacity = remainingCapacity.values.any { it < 0 }
    return scheduleAttempt(
        tasks = tasks,
        eligibleDates = eligibleDates,
        remainingCapacity = remainingCapacity,
        existingDayTasks = fixedTasksByDate,
        fitMode = fitMode,
        fitMinutes = { task -> fitMode.fitMinutes(task) },
        fixedOverCapacity = fixedOverCapacity,
    )
}

private fun capacityAfterFixedTasks(
    eligibleDates: List<String>,
    capacityByDate: Map<String, Int>,
    fixedTasksByDate: Map<String, List<GeneratedStudyBlock>>,
    mode: ScheduleFitMode,
): Map<String, Int> = eligibleDates.associateWith { date ->
    val fixedMinutes = fixedTasksByDate[date].orEmpty().sumOf { task -> mode.fitMinutes(task) }
    (capacityByDate[date] ?: 0) - fixedMinutes
}

private fun scheduleAttempt(
    tasks: List<GeneratedStudyBlock>,
    eligibleDates: List<String>,
    remainingCapacity: Map<String, Int>,
    existingDayTasks: Map<String, List<GeneratedStudyBlock>>,
    fitMode: ScheduleFitMode,
    fitMinutes: (GeneratedStudyBlock) -> Int,
    fixedOverCapacity: Boolean,
): ScheduleAttempt {
    val (candidateTasks, deferredTasks) = trimSuffixToAvailableCapacity(
        tasks = tasks,
        availableMinutes = remainingCapacity.values.sumOf { it.coerceAtLeast(0) },
        fitMinutes = fitMinutes,
    )
    val (feasibleCount, assignedDates) = largestFeasiblePrefixAssignments(
        tasks = candidateTasks,
        eligibleDates = eligibleDates,
        remainingCapacity = remainingCapacity,
        existingDayTasks = existingDayTasks,
        fitMinutes = fitMinutes,
    )
    return ScheduleAttempt(
        candidateTasks = candidateTasks,
        deferredTasks = deferredTasks,
        feasibleCount = feasibleCount,
        assignedDates = assignedDates,
        fitMode = fitMode,
        fitMinutes = fitMinutes,
        remainingCapacity = remainingCapacity,
        fixedOverCapacity = fixedOverCapacity,
    )
}

private fun largestFeasiblePrefixAssignments(
    tasks: List<GeneratedStudyBlock>,
    eligibleDates: List<String>,
    remainingCapacity: Map<String, Int>,
    existingDayTasks: Map<String, List<GeneratedStudyBlock>>,
    fitMinutes: (GeneratedStudyBlock) -> Int,
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
            fitMinutes = fitMinutes,
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
    fitMinutes: (GeneratedStudyBlock) -> Int,
): Pair<List<GeneratedStudyBlock>, List<GeneratedStudyBlock>> {
    var remaining = tasks.sumOf { fitMinutes(it).coerceAtLeast(1) }
    var keepCount = tasks.size
    while (keepCount > 0 && remaining > availableMinutes) {
        keepCount -= 1
        remaining -= fitMinutes(tasks[keepCount]).coerceAtLeast(1)
    }
    return tasks.take(keepCount) to tasks.drop(keepCount)
}

private fun orderedScheduleAssignments(
    tasks: List<GeneratedStudyBlock>,
    eligibleDates: List<String>,
    remainingCapacity: Map<String, Int>,
    existingDayTasks: Map<String, List<GeneratedStudyBlock>>,
    fitMinutes: (GeneratedStudyBlock) -> Int,
): List<String>? {
    if (tasks.isEmpty()) return emptyList()
    if (eligibleDates.isEmpty()) return null

    val taskCount = tasks.size
    val dayCount = eligibleDates.size
    val fitPrefix = prefixSums(tasks.map { fitMinutes(it).coerceAtLeast(1) })
    val reservedPrefix = prefixSums(tasks.map { it.reservedScheduleMinutes })
    val cognitivePrefix = prefixSums(tasks.map { it.cognitiveStudyPoints })
    val targetReserved = ceil(tasks.sumOf { it.reservedScheduleMinutes }.toDouble() / dayCount)
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
        val existingReserved = existingTasks.sumOf { it.reservedScheduleMinutes }
        val existingCognitive = existingTasks.sumOf { it.cognitiveStudyPoints }
        for (end in 0..taskCount) {
            for (start in 0..end) {
                val priorCost = dp[day - 1][start]
                if (priorCost.isInfinite()) continue
                val fit = fitPrefix[end] - fitPrefix[start]
                if (fit > (remainingCapacity[date] ?: 0)) continue
                val blockCount = end - start
                val reserved = reservedPrefix[end] - reservedPrefix[start]
                val cognitive = cognitivePrefix[end] - cognitivePrefix[start]
                var cost = dayScheduleCost(
                    dayIndex = day - 1,
                    blockCount = blockCount,
                    reserved = reserved + existingReserved,
                    cognitive = cognitive + existingCognitive,
                    segmentReserved = reserved,
                    targetReserved = targetReserved,
                    targetCognitive = targetCognitive,
                )
                if (start > 0 && end > start && sameKeepTogetherGroup(tasks[start - 1], tasks[start])) {
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
    reserved: Int,
    cognitive: Int,
    segmentReserved: Int,
    targetReserved: Int,
    targetCognitive: Int,
): Double {
    if (blockCount == 0) return 0.0
    val reservedDeviation = reserved - targetReserved
    val cognitiveDeviation = cognitive - targetCognitive
    val balance = reservedDeviation * reservedDeviation + cognitiveDeviation * cognitiveDeviation
    val lateRisk = dayIndex * segmentReserved * 2
    val contextSwitch = maxOf(0, blockCount - 1) * 20
    return (balance + lateRisk + contextSwitch).toDouble()
}

private fun sameKeepTogetherGroup(previous: GeneratedStudyBlock, current: GeneratedStudyBlock): Boolean =
    !previous.keepTogetherGroup.isNullOrBlank() && previous.keepTogetherGroup == current.keepTogetherGroup

internal val GeneratedStudyBlock.reservedScheduleMinutes: Int
    get() = requiredBlockMinutes()

internal val GeneratedStudyBlock.cognitiveStudyPoints: Int
    get() = maxOf(effectiveCognitivePoints(), reservedScheduleMinutes, 1)

internal fun targetMinutesForCapacity(capacityMinutes: Int): Int =
    (capacityMinutes * 0.85).toInt().coerceAtLeast(1)

internal fun dayLoadIndex(tasks: List<GeneratedStudyBlock>, capacityMinutes: Int): Double {
    if (tasks.isEmpty() || capacityMinutes <= 0) return 0.0
    val target = targetMinutesForCapacity(capacityMinutes)
    val reservedRatio = tasks.sumOf { it.reservedScheduleMinutes }.toDouble() / target
    val cognitiveRatio = tasks.sumOf { it.cognitiveStudyPoints }.toDouble() / target
    val contextSwitchPenalty = maxOf(0, tasks.size - 3) * 0.03
    val densePenalty = if (tasks.count { (it.difficultyScore ?: 3) >= 4 } >= 2) 0.05 else 0.0
    return maxOf(reservedRatio, cognitiveRatio) + contextSwitchPenalty + densePenalty
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
        if (tasks.sumOf { it.reservedScheduleMinutes } >= target * 0.9) add("TIME_NEAR_CAPACITY")
        if (tasks.sumOf { it.cognitiveStudyPoints } >= target * 0.9) add("COGNITIVE_LOAD_NEAR_CAPACITY")
        if (tasks.count { (it.difficultyScore ?: 3) >= 4 } >= 2) add("MULTIPLE_DENSE_BLOCKS")
        if (tasks.any { it.estimateConfidence == EstimateConfidence.Low }) add("LOW_CONFIDENCE_ESTIMATE")
        if (tasks.any { !it.keepTogetherGroup.isNullOrBlank() }) add("KEEP_TOGETHER")
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

internal fun currentStudyCalendar(preferences: PlanSetupSubmission, nowMillis: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = nowMillis }.asStudyCalendar(preferences)

internal fun currentStudyCalendar(resetOffsetHours: Int): Calendar =
    Calendar.getInstance().asStudyCalendar(resetOffsetHours)

internal fun currentStudyCalendar(resetOffsetHours: Int, nowMillis: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = nowMillis }.asStudyCalendar(resetOffsetHours)

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
