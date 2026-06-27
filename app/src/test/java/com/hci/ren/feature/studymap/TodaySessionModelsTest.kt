package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySessionModelsTest {
    @Test fun reducedTodayAvailabilityMovesOrderedSuffixToWontFitToday() {
        val tasks = listOf(
            task("first", 30).copy(order = 1),
            task("second", 30).copy(order = 2),
            task("third", 30).copy(order = 3),
        )
        val data = dataFor(todayTasks = tasks, dailyMinutes = 90)

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 60,
            hasAvailabilityOverride = true,
        )

        assertEquals(listOf("first", "second"), session.doTodayTasks.map { it.id })
        assertEquals(listOf("third"), session.wontFitTodayTasks.map { it.id })
        assertTrue(session.hasPendingChanges)
    }

    @Test fun tasksThatDoNotFitAreNotPendingChangesWithoutAnOverride() {
        val tasks = listOf(task("long", 60).copy(order = 1))
        val data = dataFor(todayTasks = tasks, dailyMinutes = 30)

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 30,
        )

        assertEquals(emptyList<String>(), session.doTodayTasks.map { it.id })
        assertEquals(listOf("long"), session.wontFitTodayTasks.map { it.id })
        assertFalse(session.hasPendingChanges)
    }

    @Test fun availabilityOverrideCreatesPendingChanges() {
        val tasks = listOf(task("first", 30).copy(order = 1))
        val data = dataFor(todayTasks = tasks, dailyMinutes = 30)

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 15,
            hasAvailabilityOverride = true,
        )

        assertTrue(session.hasPendingChanges)
    }

    @Test fun completedTodayTasksStayInTodayWhenAvailabilityShrinks() {
        val tasks = listOf(
            task("done", 45).copy(order = 1, status = StudyTaskStatus.Completed),
            task("later", 30).copy(order = 2),
        )
        val data = dataFor(todayTasks = tasks, dailyMinutes = 75)

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 15,
            hasAvailabilityOverride = true,
        )

        assertEquals(listOf("done"), session.doTodayTasks.map { it.id })
        assertEquals(listOf("later"), session.wontFitTodayTasks.map { it.id })
        assertEquals(45, session.completedMinutes)
    }

    @Test fun availableMinutesAreNormalizedBeforePlanningPullAheadCapacity() {
        val today = task("today", 30).copy(order = 1)
        val future = (1..4).map { index -> task("future$index", 600).copy(order = index + 1) }
        val data = dataFor(
            todayTasks = listOf(today),
            futureTasks = future,
            dailyMinutes = 30,
        )

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = MaxTodaySessionMinutes + 600,
            hasAvailabilityOverride = true,
        )

        assertEquals(MaxTodaySessionMinutes, session.availableMinutes)
        assertEquals(listOf("future1", "future2"), session.pullInCandidates.map { it.id })
    }

    @Test fun extraTimeSuggestsFutureTasksWithoutAddingThemToToday() {
        val today = task("today", 30).copy(order = 1)
        val future = task("future", 30).copy(order = 2)
        val data = dataFor(
            todayTasks = listOf(today),
            futureTasks = listOf(future),
            dailyMinutes = 30,
        )

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 60,
        )

        assertEquals(listOf("today"), session.doTodayTasks.map { it.id })
        assertEquals(listOf("future"), session.pullInCandidates.map { it.id })
        assertEquals(30, session.plannedMinutes)
    }

    @Test fun pullAheadSuggestionsDoNotSkipOrderedTasksThatDoNotFit() {
        val today = task("today", 30).copy(order = 1)
        val firstFuture = task("first-future", 90).copy(order = 2)
        val secondFuture = task("second-future", 30).copy(order = 3)
        val data = dataFor(
            todayTasks = listOf(today),
            futureTasks = listOf(firstFuture, secondFuture),
            dailyMinutes = 30,
        )

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 60,
        )

        assertEquals(listOf("first-future"), session.pullInCandidates.map { it.id })
    }

    @Test fun pullAheadDoesNotSuggestTasksWithUnfinishedDependencies() {
        val prerequisite = task("prerequisite", 30).copy(order = 1)
        val dependent = task("dependent", 30).copy(
            order = 2,
            dependencies = listOf("prerequisite"),
        )
        val data = dataFor(
            todayTasks = listOf(prerequisite),
            futureTasks = listOf(dependent),
            dailyMinutes = 30,
        )

        val session = TodaySessionPlanner().plan(
            data = data,
            date = "2026-06-22",
            availableMinutes = 60,
        )

        assertEquals(emptyList<String>(), session.pullInCandidates.map { it.id })
    }

    private fun dataFor(
        todayTasks: List<GeneratedStudyBlock>,
        futureTasks: List<GeneratedStudyBlock> = emptyList(),
        dailyMinutes: Int,
    ) = StudyMapData(
        plan = plan(todayTasks + futureTasks),
        preferences = submission(dailyMinutes),
        realism = PlanRealism(
            status = PlanRealismStatus.OnTrack,
            remainingMinutes = todayTasks.sumOf { it.durationMinutes } + futureTasks.sumOf { it.durationMinutes },
            availableMinutes = dailyMinutes * 3,
            shortageMinutes = 0,
        ),
        schedule = StudySchedule(
            days = buildList {
                add(StudyScheduleDay("2026-06-22", todayTasks, dailyMinutes))
                if (futureTasks.isNotEmpty()) {
                    add(StudyScheduleDay("2026-06-23", futureTasks, dailyMinutes))
                }
            },
            unscheduledTasks = emptyList(),
        ),
        dailyMinutes = dailyMinutes,
    )

    private fun task(id: String, minutes: Int) = GeneratedStudyBlock(
        id = id,
        title = id,
        order = 1,
        durationMinutes = minutes,
        instructions = "Study $id",
        topicIds = listOf("topic"),
        minimumUsefulMinutes = 10,
        taskType = StudyTaskType.Concept,
    )

    private fun plan(tasks: List<GeneratedStudyBlock>) = GeneratedStudyPlan(
        id = "plan",
        topics = emptyList(),
        blocks = tasks,
        totalEstimatedMinutes = tasks.sumOf { it.durationMinutes },
        projectName = "Plan",
    )

    private fun submission(dailyMinutes: Int) = PlanSetupSubmission(
        documentUris = emptyList(),
        goal = StudyGoal.PrepareForExam,
        deadline = StudyDeadline.InThreeDays,
        deadlineDate = null,
        dailyStudyMinutes = dailyMinutes,
        studyDays = StudyDay.entries.toSet(),
    )
}
