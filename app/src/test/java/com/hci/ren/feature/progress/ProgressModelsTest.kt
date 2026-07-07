package com.hci.ren.feature.progress

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.studymap.FocusSessionOutcome
import com.hci.ren.feature.studymap.FocusSessionRecord
import com.hci.ren.feature.studymap.StudyProject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressModelsTest {
    @Test fun weeklyFocusSummaryAggregatesCurrentStudyWeekOnly() {
        val project = project(
            dailyMinutes = 240,
            focusHistory = mapOf(
                "2026-07-05" to listOf(focusRecord(focusSeconds = 7_200)),
                "2026-07-06" to listOf(
                    focusRecord(focusSeconds = 3_600),
                    focusRecord(focusSeconds = 1_800),
                ),
                "2026-07-08" to listOf(focusRecord(focusSeconds = 7_200)),
                "2026-07-12" to listOf(focusRecord(focusSeconds = 14_400)),
                "2026-07-13" to listOf(focusRecord(focusSeconds = 3_600)),
            ),
        )

        val summary = buildWeeklyFocusSummary(project, today = "2026-07-09")

        assertEquals(
            listOf(
                WeeklyFocusDay("2026-07-06", 90),
                WeeklyFocusDay("2026-07-07", 0),
                WeeklyFocusDay("2026-07-08", 120),
                WeeklyFocusDay("2026-07-09", 0),
                WeeklyFocusDay("2026-07-10", 0),
                WeeklyFocusDay("2026-07-11", 0),
                WeeklyFocusDay("2026-07-12", 240),
            ),
            summary.days,
        )
        assertEquals(450, summary.totalFocusMinutes)
        assertEquals(3, summary.studyDays)
    }

    @Test fun weeklyFocusSummaryRoundsChartMaximumToTwoHourIntervalsWithGoalHeadroom() {
        val summary = buildWeeklyFocusSummary(
            project(
                dailyMinutes = 240,
                focusHistory = mapOf(
                    "2026-07-06" to listOf(focusRecord(focusSeconds = 30_300)),
                ),
            ),
            today = "2026-07-06",
        )

        assertEquals(600, summary.maxChartMinutes)
    }

    @Test fun weeklyFocusSummaryRoundsPartialSecondsUpToVisibleMinutes() {
        val summary = buildWeeklyFocusSummary(
            project(
                dailyMinutes = 120,
                focusHistory = mapOf(
                    "2026-07-06" to listOf(focusRecord(focusSeconds = 61)),
                ),
            ),
            today = "2026-07-06",
        )

        assertEquals(2, summary.days.first().focusMinutes)
        assertEquals(2, summary.totalFocusMinutes)
    }

    @Test fun studyConsistencySummaryBuildsFourWeekHeatmapFromFocusHistory() {
        val summary = buildStudyConsistencySummary(
            project(
                dailyMinutes = 120,
                focusHistory = mapOf(
                    "2026-07-07" to listOf(focusRecord(focusSeconds = 3_600)),
                    "2026-07-08" to listOf(focusRecord(focusSeconds = 3_600)),
                    "2026-07-09" to listOf(focusRecord(focusSeconds = 7_200)),
                    "2026-06-30" to listOf(focusRecord(focusSeconds = 1_800)),
                    "2026-07-01" to listOf(focusRecord(focusSeconds = 1_800)),
                    "2026-07-03" to listOf(focusRecord(focusSeconds = 1_800)),
                    "2026-07-05" to listOf(focusRecord(focusSeconds = 1_800)),
                    "2026-06-23" to listOf(focusRecord(focusSeconds = 3_600)),
                ),
            ),
            today = "2026-07-09",
        )

        assertEquals(4, summary.weeks.size)
        assertEquals("2026-07-06", summary.weeks[0].days.first().date)
        assertEquals("2026-06-29", summary.weeks[1].days.first().date)
        assertEquals(3, summary.weeks[0].activeDays)
        assertEquals(4, summary.weeks[1].activeDays)
        assertEquals(8, summary.activeDays)
        assertEquals(3, summary.currentStreakDays)
        assertEquals(1, summary.mostConsistentWeeksAgo)
        assertEquals(120, summary.weeks[0].days[3].focusMinutes)
        assertEquals(1f, summary.weeks[0].days[3].completionRatio, 0.001f)
        assertEquals(0f, summary.weeks[0].days[0].completionRatio, 0.001f)
    }

    @Test fun studyConsistencySummaryUsesTodayForCurrentStreak() {
        val summary = buildStudyConsistencySummary(
            project(
                dailyMinutes = 120,
                focusHistory = mapOf(
                    "2026-07-07" to listOf(focusRecord(focusSeconds = 3_600)),
                    "2026-07-08" to listOf(focusRecord(focusSeconds = 3_600)),
                ),
            ),
            today = "2026-07-09",
        )

        assertEquals(0, summary.currentStreakDays)
        assertEquals(0, summary.mostConsistentWeeksAgo)
    }

    @Test fun studyConsistencySummaryHasNoMostConsistentWeekWithoutFocusData() {
        val summary = buildStudyConsistencySummary(
            project(
                dailyMinutes = 120,
                focusHistory = emptyMap(),
            ),
            today = "2026-07-09",
        )

        assertEquals(0, summary.currentStreakDays)
        assertEquals(0, summary.activeDays)
        assertEquals(null, summary.mostConsistentWeeksAgo)
    }

    @Test fun bestRhythmSummaryGroupsFocusHistoryByPlannedRoundLength() {
        val summary = buildBestRhythmSummary(
            project(
                dailyMinutes = 120,
                focusHistory = mapOf(
                    "2026-07-07" to listOf(
                        focusRecord(plannedFocusMinutes = 10, focusSeconds = 600),
                        focusRecord(
                            plannedFocusMinutes = 10,
                            focusSeconds = 420,
                            outcome = FocusSessionOutcome.FocusStopped,
                        ),
                    ),
                    "2026-07-08" to listOf(
                        focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
                        focusRecord(plannedFocusMinutes = 15, focusSeconds = 960, flowOvertimeSeconds = 60),
                        focusRecord(plannedFocusMinutes = 15, focusSeconds = 900, interruptionCount = 1),
                    ),
                    "2026-07-09" to listOf(
                        focusRecord(
                            plannedFocusMinutes = 20,
                            focusSeconds = 0,
                            outcome = FocusSessionOutcome.BreakEnded,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf(10, 15), summary.buckets.map { it.plannedFocusMinutes })
        assertEquals(2, summary.buckets[0].attemptedRounds)
        assertEquals(1, summary.buckets[0].cleanRounds)
        assertEquals(50, summary.buckets[0].cleanRatePercent)
        assertEquals(3, summary.buckets[1].attemptedRounds)
        assertEquals(2, summary.buckets[1].cleanRounds)
        assertEquals(67, summary.buckets[1].cleanRatePercent)
        assertEquals(15, summary.bestBucket?.plannedFocusMinutes)
    }

    @Test fun bestRhythmSummaryHasNoBestBucketWithoutFocusAttempts() {
        val summary = buildBestRhythmSummary(
            project(
                dailyMinutes = 120,
                focusHistory = mapOf(
                    "2026-07-09" to listOf(
                        focusRecord(
                            plannedFocusMinutes = 0,
                            focusSeconds = 0,
                            outcome = FocusSessionOutcome.BreakEnded,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(emptyList<BestRhythmBucket>(), summary.buckets)
        assertEquals(null, summary.bestBucket)
        assertEquals(false, summary.hasData)
    }

    private fun focusRecord(
        focusSeconds: Int,
        plannedFocusMinutes: Int = 60,
        flowOvertimeSeconds: Int = 0,
        interruptionCount: Int = 0,
        outcome: FocusSessionOutcome = FocusSessionOutcome.FocusRoundEnded,
    ) = FocusSessionRecord(
        taskId = "task",
        plannedFocusMinutes = plannedFocusMinutes,
        plannedFocusSeconds = plannedFocusMinutes * 60,
        plannedBreakMinutes = 10,
        focusSeconds = focusSeconds,
        flowOvertimeSeconds = flowOvertimeSeconds,
        breakSeconds = 0,
        awaySeconds = 0,
        interruptionCount = interruptionCount,
        outcome = outcome,
        endedAtMillis = 1L,
    )

    private fun project(
        dailyMinutes: Int,
        focusHistory: Map<String, List<FocusSessionRecord>>,
    ) = StudyProject(
        id = "plan",
        title = "Plan",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
        deadlineAtMillis = null,
        plan = GeneratedStudyPlan(
            id = "plan",
            topics = emptyList(),
            blocks = emptyList(),
            projectName = "Plan",
        ),
        preferences = PlanSetupSubmission(
            documentUris = emptyList(),
            goal = StudyGoal.PrepareForExam,
            deadline = StudyDeadline.ChooseDate,
            deadlineDate = "2026-07-30",
            dailyStudyMinutes = dailyMinutes,
            studyDays = StudyDay.entries.toSet(),
        ),
        focusSessionHistoryByDate = focusHistory,
    )
}
