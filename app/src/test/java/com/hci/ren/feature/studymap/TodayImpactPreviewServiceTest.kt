package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskType
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayImpactPreviewServiceTest {
    private val monday = GregorianCalendar(2026, Calendar.JUNE, 22)

    @Test fun previewReportsPlanStillFitsAfterDoneWork() {
        val project = project(
            tasks = listOf(task("today", 30).copy(order = 1)),
            dailyMinutes = 60,
            deadline = StudyDeadline.InThreeDays,
        )
        val session = TodaySessionState(
            date = "2026-06-22",
            doneTodayTaskIds = setOf("today"),
        )

        val preview = TodayImpactPreviewService().preview(project, "2026-06-22", session)

        assertEquals(TodayImpactStatus.Fits, preview?.status)
    }

    @Test fun previewReportsWorkMovesForwardWhenMovedLaterStillFits() {
        val project = project(
            tasks = listOf(task("today", 30).copy(order = 1)),
            dailyMinutes = 60,
            deadline = StudyDeadline.InThreeDays,
        )
        val session = TodaySessionState(date = "2026-06-22")
            .applyTaskAction("today", TodaySessionTaskAction.MoveLater)

        val preview = TodayImpactPreviewService().preview(project, "2026-06-22", session)

        assertEquals(TodayImpactStatus.WorkMovesForward, preview?.status)
    }

    @Test fun previewReportsWorkMovesForwardWhenUnfinishedTodayCloses() {
        val project = project(
            tasks = listOf(task("today", 30).copy(order = 1)),
            dailyMinutes = 60,
            deadline = StudyDeadline.InThreeDays,
        )

        val preview = TodayImpactPreviewService().preview(project, "2026-06-22", null)

        assertEquals(TodayImpactStatus.WorkMovesForward, preview?.status)
    }

    @Test fun previewReportsTightProjectedPlan() {
        val project = project(
            tasks = listOf(task("today", 60).copy(order = 1)),
            dailyMinutes = 60,
            deadline = StudyDeadline.ChooseDate,
            deadlineDate = "2026-06-24",
        )
        val session = TodaySessionState(date = "2026-06-22")
            .applyTaskAction("today", TodaySessionTaskAction.MoveLater)

        val preview = TodayImpactPreviewService().preview(project, "2026-06-22", session)

        assertEquals(TodayImpactStatus.Tight, preview?.status)
    }

    @Test fun previewReportsProjectedPlanThatDoesNotFit() {
        val project = project(
            tasks = listOf(
                task("today", 60).copy(order = 1),
                task("tomorrow", 60).copy(order = 2),
            ),
            dailyMinutes = 60,
            deadline = StudyDeadline.ChooseDate,
            deadlineDate = "2026-06-24",
        )
        val session = TodaySessionState(date = "2026-06-22")
            .applyTaskAction("today", TodaySessionTaskAction.MoveLater)

        val preview = TodayImpactPreviewService().preview(project, "2026-06-22", session)

        assertEquals(TodayImpactStatus.DoesNotFit, preview?.status)
    }

    private fun project(
        tasks: List<GeneratedStudyBlock>,
        dailyMinutes: Int,
        deadline: StudyDeadline,
        deadlineDate: String? = null,
    ) = StudyProject(
        id = "project",
        title = "Plan",
        createdAtMillis = monday.timeInMillis,
        updatedAtMillis = monday.timeInMillis,
        deadlineAtMillis = null,
        plan = GeneratedStudyPlan(
            id = "plan",
            topics = emptyList(),
            blocks = tasks,
            totalEstimatedMinutes = tasks.sumOf { it.durationMinutes },
            projectName = "Plan",
        ),
        preferences = PlanSetupSubmission(
            documentUris = emptyList(),
            goal = StudyGoal.PrepareForExam,
            deadline = deadline,
            deadlineDate = deadlineDate,
            dailyStudyMinutes = dailyMinutes,
            studyDays = StudyDay.entries.toSet(),
        ),
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
}
