package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty

data class StudyScheduleDay(
    val date: String,
    val tasks: List<GeneratedStudyBlock>,
    val capacityMinutes: Int,
    val dayIndex: Int = 1,
    val plannedMinutes: Int = tasks.sumOf { it.durationMinutes.coerceAtLeast(0) },
    val robustMinutes: Int = tasks.sumOf { it.robustStudyMinutes },
    val cognitivePoints: Int = tasks.sumOf { it.cognitiveStudyPoints },
    val targetMinutes: Int = targetMinutesForCapacity(capacityMinutes),
    val loadIndex: Double = dayLoadIndex(tasks, capacityMinutes),
    val load: StudyBlockDifficulty = StudyBlockDifficulty.Standard,
    val reasonCodes: List<String> = dayReasonCodes(tasks, capacityMinutes),
) {
    val totalScheduledMinutes: Int get() = tasks.sumOf { it.durationMinutes.coerceAtLeast(0) }
    val isOverCapacity: Boolean get() = totalScheduledMinutes > capacityMinutes
}

data class StudySchedule(
    val days: List<StudyScheduleDay>,
    val unscheduledTasks: List<GeneratedStudyBlock>,
) {
    val visibleTasks: List<GeneratedStudyBlock> get() = days.flatMap { it.tasks } + unscheduledTasks
}
