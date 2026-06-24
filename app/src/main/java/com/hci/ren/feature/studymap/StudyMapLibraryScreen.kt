package com.hci.ren.feature.studymap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

@Composable
fun StudyMapLibraryScreen(
    state: StudyMapLibraryUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (StudyMapFilter) -> Unit,
    onSortChange: (StudyMapSort) -> Unit,
    onClearSearchAndFilter: () -> Unit,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit,
    onConsumeMessage: () -> Unit,
    onHome: () -> Unit,
    onCreateProject: () -> Unit,
    onInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<StudyProjectSummary?>(null) }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_study_map_title)) },
            text = { Text(stringResource(R.string.delete_study_map_message)) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteProject(project.id) }) {
                    Text(stringResource(R.string.delete_study_map))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                StudyMapLibraryTitle()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.errorMessage != null -> StudyMapLibraryError(padding, onRetry)
            state.allProjectsCount == 0 -> StudyMapLibraryEmpty(padding, onCreateProject)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    StudyMapLibraryTitle()
                }
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.study_maps_search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            { IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, stringResource(R.string.clear_search))
                            } }
                        } else null,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StudyMapFilter.entries) { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filterLabel(filter)) },
                            )
                        }
                    }
                }
                item { StudyMapSortControl(state.sort, onSortChange) }
                if (state.projects.isEmpty()) {
                    item { NoMatchingStudyMaps(onClearSearchAndFilter) }
                } else {
                    items(state.projects, key = { it.id }) { project ->
                        StudyProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onDelete = { pendingDelete = project },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyMapSortControl(selected: StudyMapSort, onSelected: (StudyMapSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null)
            Spacer(Modifier.width(8.dp))
            Text(sortLabel(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StudyMapSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sortLabel(sort)) },
                    onClick = { expanded = false; onSelected(sort) },
                )
            }
        }
    }
}

@Composable
private fun StudyProjectCard(project: StudyProjectSummary, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = androidx.compose.animation.core.spring(),
        label = "card-press",
    )
    Card(
        modifier = modifier.fillMaxWidth().scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        project.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusBadge(project.status)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.study_map_actions))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_study_map)) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
            val percent = (project.progress * 100).toInt().coerceIn(0, 100)
            val progressLabel = stringResource(R.string.percent_complete_format, percent)
            Text(progressLabel, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { project.progress },
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = progressLabel
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    pluralStringResource(R.plurals.tasks_done_format, project.totalTasks, project.completedTasks, project.totalTasks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                project.deadlineAtMillis?.let {
                    Text(
                        stringResource(R.string.due_date_format, formatProjectDate(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MiniMap(project.progress, progressLabel)
            Text(
                stringResource(R.string.updated_date_format, formatProjectDate(project.updatedAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.open_study_map))
            }
        }
    }
}

@Composable
private fun MiniMap(progress: Float, progressLabel: String) {
    val filled = if (progress >= 1f) 5 else floor(progress.coerceIn(0f, 1f) * 5).toInt()
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = progressLabel
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            Box(
                Modifier.size(10.dp).clip(CircleShape).then(
                    if (index < filled) Modifier else Modifier
                ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (index < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    content = {},
                )
            }
            if (index < 4) HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun StatusBadge(status: StudyProjectStatus) {
    val error = status in setOf(StudyProjectStatus.Behind, StudyProjectStatus.PlanIssue)
    Surface(
        shape = RoundedCornerShape(50),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            statusLabel(status),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StudyMapLibraryEmpty(padding: PaddingValues, onCreateProject: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        StudyMapLibraryTitle()
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            StudyMapEmptyContent(
                title = stringResource(R.string.no_study_maps_title),
                message = stringResource(R.string.no_study_maps_message),
                actionLabel = stringResource(R.string.create_project),
                onAction = onCreateProject,
            )
        }
    }
}

@Composable
internal fun StudyMapEmptyContent(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Map, null, modifier = Modifier.size(32.dp)) }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun NoMatchingStudyMaps(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.no_matching_study_maps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.no_matching_study_maps_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClear) { Text(stringResource(R.string.clear_search_filters)) }
    }
}

@Composable
private fun StudyMapLibraryError(padding: PaddingValues, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        StudyMapLibraryTitle()
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.study_maps_load_error), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun StudyMapLibraryTitle() {
    Text(
        text = stringResource(R.string.study_map),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.statusBarsPadding().padding(top = 18.dp).semantics { heading() },
    )
}

@Composable private fun filterLabel(filter: StudyMapFilter) = when (filter) {
    StudyMapFilter.All -> stringResource(R.string.filter_all)
    StudyMapFilter.Active -> stringResource(R.string.filter_active)
    StudyMapFilter.Behind -> stringResource(R.string.filter_behind)
    StudyMapFilter.Completed -> stringResource(R.string.filter_completed)
    StudyMapFilter.PlanIssue -> stringResource(R.string.filter_plan_issue)
}

@Composable private fun sortLabel(sort: StudyMapSort) = when (sort) {
    StudyMapSort.RecentlyUpdated -> stringResource(R.string.sort_recently_updated)
    StudyMapSort.DeadlineSoon -> stringResource(R.string.sort_deadline_soon)
    StudyMapSort.Progress -> stringResource(R.string.sort_progress)
    StudyMapSort.Alphabetical -> stringResource(R.string.sort_alphabetical)
    StudyMapSort.NewestCreated -> stringResource(R.string.sort_newest_created)
}

@Composable private fun statusLabel(status: StudyProjectStatus) = when (status) {
    StudyProjectStatus.Active -> stringResource(R.string.filter_active)
    StudyProjectStatus.Behind -> stringResource(R.string.filter_behind)
    StudyProjectStatus.Completed -> stringResource(R.string.filter_completed)
    StudyProjectStatus.PlanIssue -> stringResource(R.string.filter_plan_issue)
}

private fun formatProjectDate(millis: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
