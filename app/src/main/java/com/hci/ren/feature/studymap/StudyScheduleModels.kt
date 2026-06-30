package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty
import com.hci.ren.feature.plangeneration.likelyStudyMinutes
import com.hci.ren.feature.plangeneration.reservedStudyMinutes

enum class ScheduleFitMode { Reserved, LikelyFallback }

data class StudyScheduleDay(
    val date: String,
    val tasks: List<GeneratedStudyBlock>,
    val capacityMinutes: Int,
    val dayIndex: Int = 1,
    val fitMode: ScheduleFitMode = ScheduleFitMode.Reserved,
    val likelyMinutes: Int = tasks.sumOf { it.likelyStudyMinutes.coerceAtLeast(0) },
    val reservedMinutes: Int = tasks.sumOf { it.reservedStudyMinutes.coerceAtLeast(0) },
    val fittedMinutes: Int = when (fitMode) {
        ScheduleFitMode.Reserved -> reservedMinutes
        ScheduleFitMode.LikelyFallback -> likelyMinutes
    },
    val plannedMinutes: Int = fittedMinutes,
    val cognitivePoints: Int = tasks.sumOf { it.cognitiveStudyPoints },
    val targetMinutes: Int = targetMinutesForCapacity(capacityMinutes),
    val loadIndex: Double = dayLoadIndex(tasks, capacityMinutes),
    val load: StudyBlockDifficulty = StudyBlockDifficulty.Standard,
    val reasonCodes: List<String> = dayReasonCodes(tasks, capacityMinutes),
) {
    val totalScheduledMinutes: Int get() = fittedMinutes
    val isRisky: Boolean get() = reservedMinutes > capacityMinutes && fittedMinutes <= capacityMinutes
    val isOverCapacity: Boolean get() = fittedMinutes > capacityMinutes
}

data class StudySchedule(
    val days: List<StudyScheduleDay>,
    val unscheduledTasks: List<GeneratedStudyBlock>,
    val fitMode: ScheduleFitMode = ScheduleFitMode.Reserved,
) {
    val visibleTasks: List<GeneratedStudyBlock> get() = days.flatMap { it.tasks } + unscheduledTasks
}

internal fun ScheduleFitMode.fitMinutes(task: GeneratedStudyBlock): Int = when (this) {
    ScheduleFitMode.Reserved -> task.reservedStudyMinutes
    ScheduleFitMode.LikelyFallback -> task.likelyStudyMinutes
}
