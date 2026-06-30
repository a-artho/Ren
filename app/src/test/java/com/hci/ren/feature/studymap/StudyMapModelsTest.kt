package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.effectiveCognitivePoints
import com.hci.ren.feature.plangeneration.effectiveReservedMinutes
import com.hci.ren.feature.plangeneration.reservedStudyMinutes
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMapModelsTest {
    private val monday = GregorianCalendar(2026, Calendar.JUNE, 22)

    @Test fun realisticPlanIsOnTrack() {
        val result = PlanRealismCalculator().calculate(
            listOf(task("one", 60)),
            submission(120, StudyDeadline.InThreeDays),
            monday,
        )

        assertEquals(PlanRealismStatus.OnTrack, result.status)
        assertEquals(360, result.availableMinutes)
    }

    @Test fun slightlyOverCapacityPlanIsTight() {
        val tasks = listOf(task("one", 60).copy(effortMaxMinutes = 68))
        val preferences = submission(60, StudyDeadline.ChooseDate, "2026-06-23")
        val schedule = StudyScheduleCalculator().calculate(tasks, preferences, monday)
        val result = PlanRealismCalculator().calculate(
            tasks,
            preferences,
            monday,
            schedule = schedule,
        )

        assertEquals(ScheduleFitMode.LikelyFallback, schedule.fitMode)
        assertEquals(PlanRealismStatus.Tight, result.status)
        assertEquals(2, result.shortageMinutes)
        assertEquals(0, result.likelyShortageMinutes)
        assertEquals(2, result.reservedShortageMinutes)
    }

    @Test fun farOverCapacityPlanIsOverloadedWhenItCannotBeScheduled() {
        val result = PlanRealismCalculator().calculate(
            listOf(task("one", 150)),
            submission(60, StudyDeadline.ChooseDate, "2026-06-23"),
            monday,
        )

        assertEquals(PlanRealismStatus.Overloaded, result.status)
    }

    @Test fun suggestedDeadlineIsFutureAndProvidesEnoughCapacity() {
        val preferences = submission(60, StudyDeadline.ChooseDate, "2026-06-23")
        val suggestion = PlanAdjustmentService().suggestedDeadline(
            listOf(task("one", 180)),
            preferences,
            monday,
        )

        assertNotNull(suggestion)
        val suggestedDate = suggestion!!.toStudyCalendar()!!
        assertFalse(suggestedDate.before(monday))
        val updated = preferences.copy(deadline = StudyDeadline.ChooseDate, deadlineDate = suggestion)
        val available = PlanRealismCalculator().calculate(listOf(task("one", 180)), updated, monday).availableMinutes
        assertTrue(available!! >= 180)
    }

    @Test fun increasingDailyTimeIncreasesAvailabilityAndScheduleCapacity() {
        val preferences = submission(60, StudyDeadline.ChooseDate, "2026-06-23")
        val tasks = listOf(task("one", 45), task("two", 45))
        val before = PlanRealismCalculator().calculate(tasks, preferences, monday)
        val after = PlanRealismCalculator().calculate(tasks, preferences, monday, dailyMinutesOverride = 120)
        val schedule = StudyScheduleCalculator().calculate(tasks, preferences, monday, dailyMinutesOverride = 120)

        assertTrue(after.availableMinutes > before.availableMinutes)
        assertEquals(90, schedule.days.single().totalScheduledMinutes)
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun chosenTopicScopeStateExcludesOtherTopicsFromRequiredTimeAndTopicCounts() {
        val tasks = listOf(
            task("kept", 60, topic = "a"),
            task("removed", 60, topic = "b"),
        )
        val data = buildStudyMapData(
            plan(tasks),
            submission(60, StudyDeadline.InThreeDays),
            taskStateById = mapOf("removed" to StudyTaskState(status = StudyTaskStatus.ExcludedByUser)),
            today = monday,
        )

        assertEquals(60, data.totalReservedMinutes)
        assertEquals(StudyTaskStatus.NotStarted, tasks.single { it.id == "removed" }.status)
        assertEquals(StudyTaskStatus.ExcludedByUser, data.plan.blocks.single { it.id == "removed" }.status)
        assertEquals(0 to 0, TaskProgressCalculator().topicProgress(data.plan.blocks, "b"))
    }

    @Test fun overflowTasksRemainVisibleAndDailyTotalsMatchVisibleDurations() {
        val tasks = listOf(task("one", 45), task("two", 45), task("three", 45))
        val schedule = StudyScheduleCalculator().calculate(tasks, submission(60, StudyDeadline.ChooseDate, "2026-06-23"), monday)

        assertEquals(schedule.days.single().tasks.sumOf { it.reservedStudyMinutes }, schedule.days.single().totalScheduledMinutes)
        assertEquals(2, schedule.unscheduledTasks.size)
        assertTrue(schedule.unscheduledTasks.all { it.status == StudyTaskStatus.Unscheduled })
    }

    @Test fun scheduleSpreadsWorkAcrossAvailableDeadlineWindow() {
        val tasks = (1..4).map { index -> task("task$index", 30).copy(order = index) }
        val schedule = StudyScheduleCalculator().calculate(tasks, submission(120, StudyDeadline.InOneWeek), monday)

        assertEquals(
            listOf("2026-06-22", "2026-06-23", "2026-06-24", "2026-06-25"),
            schedule.days.map { it.date },
        )
        assertEquals(listOf(1, 2, 3, 4), schedule.days.map { it.dayIndex })
        assertTrue(schedule.days.all { it.totalScheduledMinutes <= 120 })
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun fallbackScheduleBalancesOrderedWorkInsteadOfDumpingLastDay() {
        val friday = GregorianCalendar(2026, Calendar.JUNE, 26)
        val tasks = listOf(45, 60, 45, 30, 60).mapIndexed { index, minutes ->
            task("task${index + 1}", minutes).copy(order = index + 1)
        }

        val schedule = StudyScheduleCalculator().calculate(tasks, submission(180, StudyDeadline.InThreeDays), friday)

        assertEquals(listOf("2026-06-26", "2026-06-27", "2026-06-28"), schedule.days.map { it.date })
        assertTrue(schedule.days.last().plannedMinutes <= schedule.days.first().plannedMinutes)
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun fallbackScheduleDoesNotSkipAheadAfterOversizedOrderedTask() {
        val tasks = listOf(
            task("first", 30).copy(order = 1),
            task("too-big", 100).copy(order = 2),
            task("later", 30).copy(order = 3),
        )

        val schedule = StudyScheduleCalculator().calculate(tasks, submission(60, StudyDeadline.InOneWeek), monday)

        assertEquals(listOf("first"), schedule.days.flatMap { it.tasks }.map { it.id })
        assertEquals(
            listOf(StudyTaskStatus.OverCapacity, StudyTaskStatus.Unscheduled),
            schedule.unscheduledTasks.map { it.status },
        )
    }

    @Test fun fallbackScheduleDefersSuffixWhenTotalFitsButOrderedPartitionDoesNot() {
        val tasks = listOf(60, 60, 60).mapIndexed { index, minutes ->
            task("task${index + 1}", minutes).copy(order = index + 1)
        }

        val schedule = StudyScheduleCalculator().calculate(
            tasks,
            submission(90, StudyDeadline.ChooseDate, "2026-06-24"),
            monday,
        )

        assertEquals(listOf("task1", "task2"), schedule.days.flatMap { it.tasks }.map { it.id })
        assertEquals(listOf("2026-06-22", "2026-06-23"), schedule.days.map { it.date })
        assertEquals(listOf("task3"), schedule.unscheduledTasks.map { it.id })
        assertEquals(StudyTaskStatus.Unscheduled, schedule.unscheduledTasks.single().status)
    }

    @Test fun scheduleReportsLikelyFallbackWhenReservedDoesNotFit() {
        val tasks = listOf(
            task("first", 50).copy(order = 1, effortMaxMinutes = 90),
            task("second", 50).copy(order = 2, effortMaxMinutes = 90),
        )

        val schedule = StudyScheduleCalculator().calculate(
            tasks,
            submission(100, StudyDeadline.ChooseDate, "2026-06-23"),
            monday,
        )
        val day = schedule.days.single()

        assertEquals(ScheduleFitMode.LikelyFallback, schedule.fitMode)
        assertEquals(100, day.fittedMinutes)
        assertEquals(120, day.reservedMinutes)
        assertTrue(day.isRisky)
        assertFalse(day.isOverCapacity)
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun likelyFallbackSubtractsFixedTasksUsingLikelyMinutes() {
        val tasks = listOf(
            task("locked", 50).copy(
                order = 1,
                effortMaxMinutes = 90,
                status = StudyTaskStatus.Locked,
                scheduledDate = "2026-06-22",
            ),
            task("auto", 50).copy(order = 2, effortMaxMinutes = 90),
        )

        val schedule = StudyScheduleCalculator().calculate(
            tasks,
            submission(100, StudyDeadline.ChooseDate, "2026-06-23"),
            monday,
        )
        val day = schedule.days.single()

        assertEquals(ScheduleFitMode.LikelyFallback, schedule.fitMode)
        assertEquals(listOf("locked", "auto"), day.tasks.map { it.id })
        assertEquals(100, day.fittedMinutes)
        assertEquals(120, day.reservedMinutes)
        assertTrue(day.isRisky)
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun likelyFallbackHandlesFixedOnlyDayWhenLikelyFits() {
        val tasks = listOf(
            task("locked", 50).copy(
                order = 1,
                effortMaxMinutes = 94,
                status = StudyTaskStatus.Locked,
                scheduledDate = "2026-06-22",
            ),
        )

        val data = buildStudyMapData(
            plan(tasks),
            submission(60, StudyDeadline.ChooseDate, "2026-06-23"),
            today = monday,
        )
        val day = data.schedule.days.single()

        assertEquals(ScheduleFitMode.LikelyFallback, data.schedule.fitMode)
        assertEquals(50, day.fittedMinutes)
        assertEquals(61, day.reservedMinutes)
        assertTrue(day.isRisky)
        assertFalse(day.isOverCapacity)
        assertEquals(PlanRealismStatus.Tight, data.realism.status)
    }

    @Test fun scheduleAllowsDependenciesAfterEarlierBlocksAreScheduled() {
        val tasks = listOf(
            task("first", 30).copy(order = 1),
            task("second", 30).copy(order = 2, dependencies = listOf("first")),
        )
        val schedule = StudyScheduleCalculator().calculate(tasks, submission(120, StudyDeadline.InThreeDays), monday)

        assertTrue(schedule.unscheduledTasks.isEmpty())
        assertEquals(listOf("first", "second"), schedule.days.flatMap { it.tasks }.map { it.id })
    }

    @Test fun completedTasksKeepStatusAndNextTaskAdvances() {
        val tasks = listOf(
            task("done", 30).copy(status = StudyTaskStatus.Completed, scheduledDate = "2026-06-22"),
            task("next", 30),
        )
        val data = buildStudyMapData(plan(tasks), submission(60, StudyDeadline.ChooseDate, "2026-06-23"), today = monday)

        assertEquals(StudyTaskStatus.Completed, data.schedule.days.single().tasks.first().status)
        assertEquals("next", data.nextTask?.id)
        assertEquals(1, data.completedTasks)
    }

    @Test fun completedPastTasksDoNotConsumeCurrentScheduleCapacity() {
        val tasks = listOf(
            task("done", 60).copy(status = StudyTaskStatus.Completed, scheduledDate = "2026-06-21"),
            task("today", 60).copy(order = 2),
        )

        val schedule = StudyScheduleCalculator().calculate(
            tasks,
            submission(60, StudyDeadline.Tomorrow),
            today = monday,
        )

        assertEquals(listOf("today"), schedule.days.single().tasks.map { it.id })
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun lockedTaskKeepsManualScheduleAndConsumesCapacity() {
        val tasks = listOf(
            task("locked", 45).copy(
                order = 1,
                status = StudyTaskStatus.Locked,
                scheduledDate = "2026-06-22",
            ),
            task("auto", 30).copy(order = 2),
        )

        val schedule = StudyScheduleCalculator().calculate(
            tasks,
            submission(60, StudyDeadline.InThreeDays),
            today = monday,
        )

        assertEquals(listOf("locked"), schedule.days.first { it.date == "2026-06-22" }.tasks.map { it.id })
        assertEquals(listOf("auto"), schedule.days.flatMap { it.tasks }.filterNot { it.id == "locked" }.map { it.id })
        assertEquals("2026-06-23", schedule.days.flatMap { it.tasks }.single { it.id == "auto" }.scheduledDate)
    }

    @Test fun lockedOverCapacityDayMakesPlanCrammed() {
        val tasks = listOf(
            task("locked", 90).copy(
                order = 1,
                status = StudyTaskStatus.Locked,
                scheduledDate = "2026-06-22",
            ),
        )

        val data = buildStudyMapData(
            plan(tasks),
            submission(60, StudyDeadline.ChooseDate, "2026-06-23"),
            today = monday,
        )

        assertTrue(data.schedule.days.single().isOverCapacity)
        assertEquals(PlanRealismStatus.Crammed, data.realism.status)
    }

    @Test fun activeStateMarksTaskCompletedWithoutChangingSourceEffort() {
        val canonical = task("chapter", 100).copy(order = 1)
        val data = buildStudyMapData(
            plan(listOf(canonical)),
            submission(60, StudyDeadline.InThreeDays),
            taskStateById = mapOf("chapter" to StudyTaskState(status = StudyTaskStatus.Completed)),
            today = monday,
        )

        assertEquals(listOf("chapter"), data.plan.blocks.map { it.id })
        assertEquals(listOf(100), data.plan.blocks.map { it.effortLikelyMinutes })
        assertEquals(StudyTaskStatus.Completed, data.plan.blocks.single().status)
        assertTrue(data.schedule.days.isEmpty())
        assertEquals(1, data.completedTasks)
    }

    @Test fun excludedActiveStateRemovesTaskWithoutChangingSourceEffort() {
        val canonical = task("chapter", 100).copy(order = 1)
        val data = buildStudyMapData(
            plan(listOf(canonical)),
            submission(60, StudyDeadline.InThreeDays),
            taskStateById = mapOf("chapter" to StudyTaskState(status = StudyTaskStatus.ExcludedByUser)),
            today = monday,
        )

        val task = data.plan.blocks.single()
        assertEquals(100, task.effortLikelyMinutes)
        assertEquals(StudyTaskStatus.ExcludedByUser, task.status)
        assertTrue(data.schedule.days.isEmpty())
    }

    @Test fun inProgressActiveStateKeepsFullSourceEffortScheduled() {
        val canonical = task("chapter", 100).copy(order = 1)
        val data = buildStudyMapData(
            plan(listOf(canonical)),
            submission(60, StudyDeadline.InThreeDays),
            taskStateById = mapOf("chapter" to StudyTaskState(status = StudyTaskStatus.InProgress)),
            today = monday,
        )

        assertEquals(100, data.plan.blocks.single().effortLikelyMinutes)
        assertEquals(StudyTaskStatus.InProgress, data.plan.blocks.single().status)
        assertTrue(data.schedule.days.isEmpty())
        assertEquals(listOf("chapter"), data.schedule.unscheduledTasks.map { it.id })
    }

    @Test fun dailyAvailableOverrideChangesOnlyThatStudyDateCapacity() {
        val tasks = listOf(
            task("one", 30).copy(order = 1),
            task("two", 30).copy(order = 2),
        )
        val data = buildStudyMapData(
            plan(tasks),
            submission(60, StudyDeadline.InThreeDays),
            dailyAvailableMinutesByDate = mapOf("2026-06-22" to 0),
            today = monday,
        )

        assertFalse("2026-06-22" in data.schedule.days.map { it.date })
        assertEquals(listOf("one", "two"), data.schedule.days.flatMap { it.tasks }.map { it.id })
        assertEquals(0, data.realism.shortageMinutes)
    }

    @Test fun overCapacityUsesPerDayAvailableOverride() {
        val schedule = StudyScheduleCalculator().calculate(
            tasks = listOf(task("too-large", 45).copy(order = 1)),
            preferences = submission(60, StudyDeadline.InThreeDays),
            today = monday,
            dailyAvailableMinutesByDate = mapOf(
                "2026-06-22" to 30,
                "2026-06-23" to 30,
                "2026-06-24" to 30,
            ),
        )

        assertEquals(StudyTaskStatus.OverCapacity, schedule.unscheduledTasks.single().status)
    }

    @Test fun deferredTasksDoNotCountInvisibleWorkload() {
        val tasks = listOf(
            task("active", 30),
            task("deferred", 120).copy(status = StudyTaskStatus.DeferredByUser),
        )
        val data = buildStudyMapData(
            plan(tasks),
            submission(60, StudyDeadline.ChooseDate, "2026-06-23"),
            today = monday,
        )

        assertEquals(StudyTaskStatus.DeferredByUser, data.plan.blocks.single { it.id == "deferred" }.status)
        assertEquals(listOf("active"), data.schedule.days.flatMap { it.tasks }.map { it.id })
        assertTrue(data.schedule.unscheduledTasks.isEmpty())
        assertEquals(30, data.totalLikelyMinutes)
        assertEquals(30, data.totalReservedMinutes)
        assertEquals(0f, data.progress)
        assertEquals(0 to 1, TaskProgressCalculator().projectProgress(data.plan.blocks))
        assertEquals(30, data.realism.remainingMinutes)
    }

    @Test fun workloadEngineUsesEffortRangeWithoutChangingSourceEstimate() {
        val updated = task("edited", 50).copy(
            effortMinMinutes = 35,
            effortLikelyMinutes = 50,
            effortMaxMinutes = 80,
        )

        assertEquals(50, updated.effortLikelyMinutes)
        assertEquals(58, updated.effectiveReservedMinutes())
        assertEquals(71, updated.effectiveCognitivePoints())
    }

    @Test fun lockedTaskIsNotSelectedAsNextTask() {
        val tasks = listOf(
            task("locked", 30).copy(status = StudyTaskStatus.Locked, dependencies = listOf("missing")),
            task("available", 30),
        )
        val data = buildStudyMapData(plan(tasks), submission(60, StudyDeadline.Tomorrow), today = monday)

        assertEquals("available", data.nextTask?.id)
    }

    @Test fun emptyTasksProduceEmptyScheduleAndZeroProgress() {
        val data = buildStudyMapData(plan(emptyList()), submission(60, StudyDeadline.Tomorrow), today = monday)

        assertTrue(data.schedule.days.isEmpty())
        assertTrue(data.schedule.unscheduledTasks.isEmpty())
        assertEquals(0f, data.progress)
        assertNull(data.nextTask)
    }

    @Test fun availableStudyDatesExcludeDeadlineDate() {
        val dates = availableStudyDates(
            submission(60, StudyDeadline.ChooseDate, "2026-06-24"),
            monday,
        ).map { it.toStudyDate() }

        assertEquals(listOf("2026-06-22", "2026-06-23"), dates)
    }

    @Test fun sameDayDeadlineHasNoStudyWindow() {
        val dates = availableStudyDates(
            submission(60, StudyDeadline.ChooseDate, "2026-06-22"),
            monday,
        )

        assertTrue(dates.isEmpty())
    }

    @Test fun extendingDeadlineReturnsDayAfterLastStudyDay() {
        val deadline = deadlineAfterSelectedStudyDays(
            days = 2,
            selectedDays = setOf(StudyDay.Monday, StudyDay.Wednesday),
            today = monday,
        )

        assertEquals("2026-06-25", deadline)
    }

    private fun task(
        id: String,
        minutes: Int,
        topic: String = "topic",
    ) = GeneratedStudyBlock(
        id = id,
        title = id,
        order = id.hashCode(),
        effortMinMinutes = minutes,
        effortLikelyMinutes = minutes,
        effortMaxMinutes = minutes,
        instructions = "Study $id",
        topicIds = listOf(topic),
        taskType = StudyTaskType.Concept,
    )

    private fun plan(tasks: List<GeneratedStudyBlock>) = GeneratedStudyPlan(
        id = "plan",
        topics = emptyList(),
        blocks = tasks,
        projectName = "Calculus",
    )

    private fun submission(minutes: Int, deadline: StudyDeadline, deadlineDate: String? = null) = PlanSetupSubmission(
        documentUris = listOf("content://test"),
        goal = StudyGoal.PrepareForExam,
        deadline = deadline,
        deadlineDate = deadlineDate,
        dailyStudyMinutes = minutes,
        studyDays = StudyDay.entries.toSet(),
    )
}
