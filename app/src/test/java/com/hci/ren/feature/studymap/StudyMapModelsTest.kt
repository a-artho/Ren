package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.TaskPriority
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
        val result = PlanRealismCalculator().calculate(
            listOf(task("one", 65)),
            submission(60, StudyDeadline.Today),
            monday,
        )

        assertEquals(PlanRealismStatus.Tight, result.status)
        assertEquals(5, result.shortageMinutes)
    }

    @Test fun farOverCapacityPlanIsUnrealistic() {
        val result = PlanRealismCalculator().calculate(
            listOf(task("one", 150)),
            submission(60, StudyDeadline.Today),
            monday,
        )

        assertEquals(PlanRealismStatus.Unrealistic, result.status)
    }

    @Test fun suggestedDeadlineIsFutureAndProvidesBufferedCapacity() {
        val preferences = submission(60, StudyDeadline.Today)
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
        val preferences = submission(60, StudyDeadline.Today)
        val tasks = listOf(task("one", 45), task("two", 45))
        val before = PlanRealismCalculator().calculate(tasks, preferences, monday)
        val after = PlanRealismCalculator().calculate(tasks, preferences, monday, dailyMinutesOverride = 120)
        val schedule = StudyScheduleCalculator().calculate(tasks, preferences, monday, dailyMinutesOverride = 120)

        assertTrue(after.availableMinutes!! > before.availableMinutes!!)
        assertEquals(90, schedule.days.single().totalScheduledMinutes)
        assertTrue(schedule.unscheduledTasks.isEmpty())
    }

    @Test fun scopeReductionExcludesTasksFromRequiredTimeAndTopicCounts() {
        val tasks = listOf(
            task("high", 60, priority = TaskPriority.High, topic = "a"),
            task("low", 60, priority = TaskPriority.Low, topic = "b"),
        )
        val reduced = PlanAdjustmentService().applyScope(tasks, ScopeReduction.HighPriorityOnly)
        val data = buildStudyMapData(plan(reduced), submission(60, StudyDeadline.InThreeDays), today = monday)

        assertEquals(60, data.totalEstimatedMinutes)
        assertTrue(reduced.single { it.id == "low" }.isOptional)
        assertEquals(0 to 1, TaskProgressCalculator().topicProgress(reduced, "b"))
    }

    @Test fun overflowTasksRemainVisibleAndDailyTotalsMatchVisibleDurations() {
        val tasks = listOf(task("one", 45), task("two", 45), task("three", 45))
        val schedule = StudyScheduleCalculator().calculate(tasks, submission(60, StudyDeadline.Today), monday)

        assertEquals(schedule.days.single().tasks.sumOf { it.durationMinutes }, schedule.days.single().totalScheduledMinutes)
        assertEquals(2, schedule.unscheduledTasks.size)
        assertTrue(schedule.unscheduledTasks.all { it.status == StudyTaskStatus.Unscheduled })
    }

    @Test fun completedTasksKeepStatusAndNextTaskAdvances() {
        val tasks = listOf(
            task("done", 30).copy(status = StudyTaskStatus.Completed, scheduledDate = "2026-06-22"),
            task("next", 30),
        )
        val data = buildStudyMapData(plan(tasks), submission(60, StudyDeadline.Today), today = monday)

        assertEquals(StudyTaskStatus.Completed, data.schedule.days.single().tasks.first().status)
        assertEquals("next", data.nextTask?.id)
        assertEquals(1, data.completedTasks)
    }

    @Test fun noDeadlineProducesNeutralStatusWithoutCrash() {
        val data = buildStudyMapData(
            plan(listOf(task("one", 30))),
            submission(60, StudyDeadline.NoFixedDeadline),
            today = monday,
        )

        assertEquals(PlanRealismStatus.NoDeadline, data.realism.status)
        assertNull(data.realism.availableMinutes)
        assertTrue(data.schedule.days.isNotEmpty())
    }

    @Test fun lockedTaskIsNotSelectedAsNextTask() {
        val tasks = listOf(
            task("locked", 30).copy(status = StudyTaskStatus.Locked, dependencies = listOf("missing")),
            task("available", 30),
        )
        val data = buildStudyMapData(plan(tasks), submission(60, StudyDeadline.Today), today = monday)

        assertEquals("available", data.nextTask?.id)
    }

    @Test fun emptyTasksProduceEmptyScheduleAndZeroProgress() {
        val data = buildStudyMapData(plan(emptyList()), submission(60, StudyDeadline.Today), today = monday)

        assertTrue(data.schedule.days.isEmpty())
        assertTrue(data.schedule.unscheduledTasks.isEmpty())
        assertEquals(0f, data.progress)
        assertNull(data.nextTask)
    }

    private fun task(
        id: String,
        minutes: Int,
        priority: TaskPriority = TaskPriority.Medium,
        topic: String = "topic",
    ) = GeneratedStudyBlock(
        id = id,
        title = id,
        order = id.hashCode(),
        durationMinutes = minutes,
        instructions = "Study $id",
        topicIds = listOf(topic),
        minimumUsefulMinutes = 10,
        priority = priority,
        taskType = StudyTaskType.Concept,
    )

    private fun plan(tasks: List<GeneratedStudyBlock>) = GeneratedStudyPlan(
        id = "plan",
        topics = emptyList(),
        blocks = tasks,
        totalEstimatedMinutes = tasks.sumOf { it.durationMinutes },
        projectName = "Calculus",
    )

    private fun submission(minutes: Int, deadline: StudyDeadline) = PlanSetupSubmission(
        documentUris = listOf("content://test"),
        goal = StudyGoal.PrepareForExam,
        deadline = deadline,
        deadlineDate = null,
        dailyStudyMinutes = minutes,
        studyDays = StudyDay.entries.toSet(),
    )
}
