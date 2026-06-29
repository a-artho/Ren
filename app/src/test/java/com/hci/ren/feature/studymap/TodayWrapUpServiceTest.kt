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

class TodayWrapUpServiceTest {
    private val monday = GregorianCalendar(2026, Calendar.JUNE, 22)

    @Test fun wrapUpSummarizesUnfinishedWorkMovingForward() {
        val project = project(task("today", 30))

        val result = TodayWrapUpService().wrapUp(project, "2026-06-22", null)!!

        assertEquals(1, result.summary.movedForwardTasks)
        assertEquals(30, result.summary.movedForwardMinutes)
    }

    private fun project(vararg tasks: GeneratedStudyBlock) = StudyProject(
        id = "project",
        title = "Plan",
        createdAtMillis = monday.timeInMillis,
        updatedAtMillis = monday.timeInMillis,
        deadlineAtMillis = null,
        plan = GeneratedStudyPlan(
            id = "plan",
            topics = emptyList(),
            blocks = tasks.toList(),
            totalEstimatedMinutes = tasks.sumOf { it.durationMinutes },
            projectName = "Plan",
        ),
        preferences = PlanSetupSubmission(
            documentUris = emptyList(),
            goal = StudyGoal.PrepareForExam,
            deadline = StudyDeadline.InThreeDays,
            deadlineDate = null,
            dailyStudyMinutes = 60,
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
