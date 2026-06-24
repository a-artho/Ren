package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTopic
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyProjectPersistenceTest {
    @Test fun generatedProjectNormalizesBlankTitleAndFixedDeadline() {
        val generated = newStudyProject(
            plan(emptyList()).copy(projectName = "  "),
            preferences().copy(deadline = StudyDeadline.ChooseDate, deadlineDate = "2026-06-30"),
            nowMillis = 100,
        )

        assertEquals("Untitled Study Map", generated.title)
        assertEquals(StudyDeadline.ChooseDate, generated.preferences.deadline)
        assertEquals("2026-06-30", generated.preferences.deadlineDate)
        assertEquals(emptyList<String>(), generated.preferences.documentUris)
    }

    @Test fun searchIsForgivingAndSortsMissingDeadlinesLast() {
        val first = summary("a", "Computer   Architecture", deadline = null, updated = 1)
        val second = summary("b", "Physics Exam", deadline = 200, updated = 2)

        assertEquals(
            listOf("a"),
            filterAndSortStudyProjects(listOf(first, second), "  computer architecture ", StudyMapFilter.All, StudyMapSort.Alphabetical, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("b", "a"),
            filterAndSortStudyProjects(listOf(first, second), "", StudyMapFilter.All, StudyMapSort.DeadlineSoon, Locale.US).map { it.id },
        )
    }

    @Test fun statusPrecedenceUsesRealTaskAndDeadlineData() {
        val today = GregorianCalendar(2026, Calendar.JUNE, 24)
        val completed = project("Done", listOf(block("one", StudyTaskStatus.Completed))).toSummary(today)
        val behind = project("Late", listOf(block("one", StudyTaskStatus.NotStarted))).copy(deadlineAtMillis = GregorianCalendar(2026, Calendar.JUNE, 23).timeInMillis).toSummary(today)
        val issue = project("Broken", emptyList()).toSummary(today)
        val active = project("Active", listOf(block("one", StudyTaskStatus.NotStarted))).copy(deadlineAtMillis = null, preferences = preferences().copy(deadline = StudyDeadline.NoFixedDeadline, deadlineDate = null)).toSummary(today)

        assertEquals(StudyProjectStatus.Completed, completed.status)
        assertEquals(StudyProjectStatus.Behind, behind.status)
        assertEquals(StudyProjectStatus.PlanIssue, issue.status)
        assertEquals(StudyProjectStatus.Active, active.status)
        assertTrue(completed.progress in 0f..1f)
    }

    private fun project(title: String, blocks: List<GeneratedStudyBlock>) = StudyProject(
        id = title,
        title = title,
        createdAtMillis = 1,
        updatedAtMillis = 2,
        deadlineAtMillis = GregorianCalendar(2099, Calendar.JUNE, 30).timeInMillis,
        plan = plan(blocks).copy(id = title, projectName = title),
        preferences = preferences(),
    )

    private fun plan(blocks: List<GeneratedStudyBlock>) = GeneratedStudyPlan(
        id = "plan",
        topics = listOf(StudyTopic("topic", "Topic", 1)),
        blocks = blocks,
        totalEstimatedMinutes = blocks.sumOf { it.durationMinutes },
        projectName = "Plan",
    )

    private fun block(id: String, status: StudyTaskStatus) = GeneratedStudyBlock(
        id = id,
        title = id,
        order = 1,
        durationMinutes = 30,
        instructions = "Study",
        topicIds = listOf("topic"),
        status = status,
    )

    private fun preferences() = PlanSetupSubmission(
        documentUris = listOf("content://raw.pdf"),
        goal = StudyGoal.PrepareForExam,
        deadline = StudyDeadline.ChooseDate,
        deadlineDate = "2099-06-30",
        dailyStudyMinutes = 60,
        studyDays = StudyDay.entries.toSet(),
    )

    private fun summary(id: String, title: String, deadline: Long?, updated: Long) = StudyProjectSummary(
        id, title, 0, updated, deadline, 0, 1, 0f, StudyProjectStatus.Active,
    )
}
