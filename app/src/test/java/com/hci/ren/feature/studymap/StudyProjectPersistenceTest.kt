package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudySourceRef
import com.hci.ren.feature.plangeneration.StudyTopic
import java.util.Calendar
import java.util.GregorianCalendar
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

        assertEquals("Untitled Plan", generated.title)
        assertEquals(StudyDeadline.ChooseDate, generated.preferences.deadline)
        assertEquals("2026-06-30", generated.preferences.deadlineDate)
        assertEquals(emptyList<String>(), generated.preferences.documentUris)
    }

    @Test fun generatedProjectUsesUserProvidedTitleBeforeAiTitle() {
        val generated = newStudyProject(
            plan(emptyList()).copy(projectName = "AI title"),
            preferences().copy(planTitle = "HCI final"),
            nowMillis = 100,
        )

        assertEquals("HCI Final", generated.title)
        assertEquals("HCI Final", generated.plan.projectName)
    }

    @Test fun generatedProjectPersistsRelativeDeadlineFromCreationTime() {
        val now = GregorianCalendar(2026, Calendar.JUNE, 25, 12, 0).timeInMillis
        val generated = newStudyProject(
            plan(emptyList()),
            preferences().copy(deadline = StudyDeadline.InOneWeek, deadlineDate = null),
            nowMillis = now,
        )

        assertEquals(StudyDeadline.ChooseDate, generated.preferences.deadline)
        assertEquals("2026-07-02", generated.preferences.deadlineDate)
    }

    @Test fun planJsonPreservesRawStudyMetadataWithoutPersistedSchedule() {
        val project = newStudyProject(
            plan(
                listOf(
                    GeneratedStudyBlock(
                        id = "b1",
                        title = "Dense bit",
                        order = 1,
                        durationMinutes = 40,
                        effortMinMinutes = 30,
                        effortLikelyMinutes = 40,
                        effortMaxMinutes = 70,
                        instructions = "Study",
                        topicIds = listOf("topic"),
                        difficultyScore = 5,
                        densityScore = 4,
                        productionDemandScore = 3,
                        completionCriteria = listOf("Explain it without notes"),
                        continuityGroup = "chapter-1",
                        sourceRefs = listOf(
                            StudySourceRef(
                                documentId = "doc1",
                                startPage = 2,
                                endPage = 4,
                                sectionTitle = "Atomic bit",
                                materialGroupTitle = "Broad chapter",
                            ),
                        ),
                    ),
                ),
            ),
            preferences(),
            nowMillis = 100,
        )

        val decoded = StudyProjectJsonCodec.decode(StudyProjectJsonCodec.encode(project))
        val block = decoded.plan.blocks.single()

        assertEquals(listOf("Explain it without notes"), block.completionCriteria)
        assertEquals("chapter-1", block.continuityGroup)
        assertEquals("Atomic bit", block.sourceRefs.single().sectionTitle)
        assertEquals("Broad chapter", block.sourceRefs.single().materialGroupTitle)
    }

    @Test fun dailyAvailableTimeOverridesPersistSeparatelyFromPlanTime() {
        val project = newStudyProject(
            plan(emptyList()),
            preferences().copy(dailyStudyMinutes = 60),
            nowMillis = 100,
        ).copy(
            dailyAvailableMinutesByDate = mapOf("2026-06-22" to 15),
        )

        val decoded = StudyProjectJsonCodec.decode(StudyProjectJsonCodec.encode(project))

        assertEquals(60, decoded.preferences.dailyStudyMinutes)
        assertEquals(mapOf("2026-06-22" to 15), decoded.dailyAvailableMinutesByDate)
    }

    @Test fun taskProgressPersistsSeparatelyFromSourcePlan() {
        val project = newStudyProject(
            plan(listOf(GeneratedStudyBlock(
                id = "chapter",
                title = "Chapter",
                order = 1,
                durationMinutes = 100,
                instructions = "Study",
                topicIds = listOf("topic"),
            ))),
            preferences(),
            nowMillis = 100,
        ).copy(
            taskProgressById = mapOf("chapter" to StudyTaskProgress(completedMinutes = 50)),
        )

        val decoded = StudyProjectJsonCodec.decode(StudyProjectJsonCodec.encode(project))

        assertEquals(100, decoded.plan.blocks.single().durationMinutes)
        assertEquals(StudyTaskProgress(completedMinutes = 50), decoded.taskProgressById["chapter"])
    }

    @Test fun generatedProjectKeepsCanonicalBlocksBeforePersistence() {
        val generated = newStudyProject(
            plan(
                listOf(
                    GeneratedStudyBlock(
                        id = "b1",
                        title = "Dense chapter",
                        order = 1,
                        durationMinutes = 100,
                        effortMinMinutes = 70,
                        effortLikelyMinutes = 100,
                        effortMaxMinutes = 140,
                        instructions = "Study",
                        topicIds = listOf("topic"),
                        minimumUsefulMinutes = 20,
                        splitAllowed = true,
                    ),
                    GeneratedStudyBlock(
                        id = "b2",
                        title = "After",
                        order = 2,
                        durationMinutes = 30,
                        instructions = "Study",
                        topicIds = listOf("topic"),
                        dependencies = listOf("b1"),
                    ),
                ),
            ),
            preferences().copy(dailyStudyMinutes = 60),
            nowMillis = 100,
        )

        assertEquals(listOf("b1", "b2"), generated.plan.blocks.map { it.id })
        assertEquals(listOf(100, 30), generated.plan.blocks.map { it.durationMinutes })
        assertTrue(generated.plan.blocks.first().splitAllowed)
        assertEquals(listOf("b1"), generated.plan.blocks[1].dependencies)
    }

    private fun plan(blocks: List<GeneratedStudyBlock>) = GeneratedStudyPlan(
        id = "plan",
        topics = listOf(StudyTopic("topic", "Topic", 1)),
        blocks = blocks,
        totalEstimatedMinutes = blocks.sumOf { it.durationMinutes },
        projectName = "Plan",
    )

    private fun preferences() = PlanSetupSubmission(
        documentUris = listOf("content://raw.pdf"),
        goal = StudyGoal.PrepareForExam,
        deadline = StudyDeadline.ChooseDate,
        deadlineDate = "2099-06-30",
        dailyStudyMinutes = 60,
        studyDays = StudyDay.entries.toSet(),
    )
}
