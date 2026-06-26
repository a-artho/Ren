package com.hci.ren.feature.studymap

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StudyProjectDatabaseTest {
    private lateinit var database: StudyProjectDatabase
    private lateinit var repository: StudyProjectRepository

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StudyProjectDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = StudyProjectRepository(database.studyProjectDao())
    }

    @After @Throws(IOException::class) fun closeDatabase() = database.close()

    @Test fun activeProjectReplacesPreviousProject() = runBlocking {
        val first = project("first", "Calculus")
        val second = project("second", "Physics")
        repository.upsert(first)
        assertEquals("first", repository.getActive()?.id)

        repository.upsert(second)
        assertEquals("second", repository.getActive()?.id)

        repository.upsert(second.copy(title = "Updated", updatedAtMillis = 3))
        assertEquals("Updated", repository.getActive()?.title)
        assertEquals(1, repository.getActive()?.plan?.blocks?.size)
    }

    private fun project(id: String, title: String) = StudyProject(
        id = id,
        title = title,
        createdAtMillis = 1,
        updatedAtMillis = 2,
        deadlineAtMillis = null,
        plan = GeneratedStudyPlan(
            id = id,
            topics = emptyList(),
            blocks = listOf(
                GeneratedStudyBlock(
                    id = "task",
                    title = "Task",
                    order = 1,
                    durationMinutes = 30,
                    instructions = "Study",
                    topicIds = emptyList(),
                ),
            ),
            totalEstimatedMinutes = 30,
            projectName = title,
        ),
        preferences = PlanSetupSubmission(
            documentUris = emptyList(),
            goal = StudyGoal.PrepareForExam,
            deadline = StudyDeadline.InOneWeek,
            deadlineDate = null,
            dailyStudyMinutes = 30,
            studyDays = StudyDay.entries.toSet(),
        ),
    )
}

