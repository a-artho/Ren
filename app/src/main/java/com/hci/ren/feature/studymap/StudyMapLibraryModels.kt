package com.hci.ren.feature.studymap

import com.hci.ren.feature.plangeneration.StudyTaskStatus
import java.text.Collator
import java.util.Calendar
import java.util.Locale

enum class StudyMapFilter { All, Active, Behind, Completed, PlanIssue }
enum class StudyMapSort { RecentlyUpdated, DeadlineSoon, Progress, Alphabetical, NewestCreated }
enum class StudyProjectStatus { Active, Behind, Completed, PlanIssue }

data class StudyProjectSummary(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deadlineAtMillis: Long?,
    val completedTasks: Int,
    val totalTasks: Int,
    val progress: Float,
    val status: StudyProjectStatus,
)

data class StudyMapLibraryUiState(
    val isLoading: Boolean = true,
    val allProjectsCount: Int = 0,
    val projects: List<StudyProjectSummary> = emptyList(),
    val query: String = "",
    val filter: StudyMapFilter = StudyMapFilter.All,
    val sort: StudyMapSort = StudyMapSort.RecentlyUpdated,
    val errorMessage: String? = null,
    val userMessage: String? = null,
)

internal fun StudyProject.toSummary(today: Calendar = Calendar.getInstance()): StudyProjectSummary {
    val active = plan.blocks.filterNot { it.isExcluded || it.status == StudyTaskStatus.Excluded }
    val completed = active.count { it.status == StudyTaskStatus.Completed }
    val progress = if (active.isEmpty()) 0f else (completed.toFloat() / active.size).coerceIn(0f, 1f)
    val startOfToday = dayOnly(today).timeInMillis
    val overdue = active.filterNot { it.status == StudyTaskStatus.Completed }.any { task ->
        task.status == StudyTaskStatus.Overdue ||
            task.scheduledDate?.toStudyCalendar()?.timeInMillis?.let { it < startOfToday } == true
    }
    val pastDeadline = deadlineAtMillis?.let { it < startOfToday } == true && completed < active.size
    val realism = if (active.isEmpty()) null else PlanRealismCalculator().calculate(
        active,
        preferences,
        today,
        dailyMinutesOverride,
    )
    val hasPlanIssue = active.isEmpty() ||
        realism?.status == PlanRealismStatus.Unrealistic ||
        active.any { it.status in setOf(StudyTaskStatus.Unscheduled, StudyTaskStatus.OverCapacity) }
    val status = when {
        active.isNotEmpty() && completed == active.size -> StudyProjectStatus.Completed
        overdue || pastDeadline -> StudyProjectStatus.Behind
        hasPlanIssue -> StudyProjectStatus.PlanIssue
        else -> StudyProjectStatus.Active
    }
    return StudyProjectSummary(
        id = id,
        title = title.safeStudyProjectTitle(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        deadlineAtMillis = deadlineAtMillis,
        completedTasks = completed,
        totalTasks = active.size,
        progress = progress,
        status = status,
    )
}

internal fun filterAndSortStudyProjects(
    projects: List<StudyProjectSummary>,
    query: String,
    filter: StudyMapFilter,
    sort: StudyMapSort,
    locale: Locale = Locale.getDefault(),
): List<StudyProjectSummary> {
    val normalizedQuery = query.normalizedSearchText()
    val filtered = projects.filter { project ->
        (normalizedQuery.isBlank() || project.title.normalizedSearchText().contains(normalizedQuery)) &&
            (filter == StudyMapFilter.All || project.status.name == filter.name)
    }
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val comparator = when (sort) {
        StudyMapSort.RecentlyUpdated -> compareByDescending<StudyProjectSummary> { it.updatedAtMillis }
        StudyMapSort.DeadlineSoon -> compareBy<StudyProjectSummary> { it.deadlineAtMillis == null }
            .thenBy { it.deadlineAtMillis ?: Long.MAX_VALUE }
        StudyMapSort.Progress -> compareByDescending<StudyProjectSummary> { it.progress }.thenBy { it.title }
        StudyMapSort.Alphabetical -> Comparator { left, right -> collator.compare(left.title, right.title) }
        StudyMapSort.NewestCreated -> compareByDescending { it.createdAtMillis }
    }
    return filtered.sortedWith(comparator)
}

internal fun String.normalizedSearchText(): String = trim().replace(Regex("\\s+"), " ").lowercase()
