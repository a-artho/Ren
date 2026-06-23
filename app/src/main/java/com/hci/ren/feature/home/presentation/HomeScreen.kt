package com.hci.ren.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.hci.ren.feature.home.presentation.components.ActiveHomeContent
import com.hci.ren.feature.home.presentation.components.AddMaterialSheet
import com.hci.ren.feature.home.presentation.components.EmptyHomeContent
import com.hci.ren.ui.theme.RenTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(hostState = it) }
        },
        topBar = {
            HomeHeader(
                state = state,
                onAction = onAction,
            )
        },
        bottomBar = {
            HomeNavigationBar(onAction = onAction)
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { onAction(HomeAction.AddMaterialClicked) },
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Add study material"
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        },
    ) { padding ->
        HomeBody(
            state = state,
            onAction = onAction,
            contentPadding = padding,
        )
    }

    if (state.isAddMaterialSheetVisible) {
        AddMaterialSheet(
            onUploadPdf = { onAction(HomeAction.UploadPdfClicked) },
            onUseSampleMaterial = { onAction(HomeAction.UseSampleMaterialClicked) },
            onDismiss = { onAction(HomeAction.AddMaterialDismissed) },
        )
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${state.greeting}, ${state.userName}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        when (state.content) {
            HomeContent.Empty -> HeaderAction(
                icon = Icons.Default.Person,
                contentDescription = "Open profile",
                onClick = { onAction(HomeAction.ProfileClicked) },
            )

            is HomeContent.Active -> HeaderAction(
                icon = Icons.Default.Notifications,
                contentDescription = "Open notifications",
                onClick = { onAction(HomeAction.NotificationsClicked) },
            )
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
        )
    }
}

@Composable
private fun HomeBody(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (state.content) {
            HomeContent.Empty -> EmptyHomeContent(
                onUploadPdf = { onAction(HomeAction.UploadPdfClicked) },
                onUseSampleMaterial = { onAction(HomeAction.UseSampleMaterialClicked) },
            )

            is HomeContent.Active -> ActiveHomeContent(
                content = state.content,
                onStartFocus = { onAction(HomeAction.StartFocusClicked) },
                onMaterialClick = { materialId ->
                    onAction(HomeAction.MaterialClicked(materialId))
                },
                onAddMaterial = { onAction(HomeAction.AddMaterialClicked) },
            )
        }
    }
}

@Composable
private fun HomeNavigationBar(
    onAction: (HomeAction) -> Unit,
) {
    NavigationBar {
        listOf(HomeDestination.Home, HomeDestination.StudyMap, HomeDestination.Insights).forEach { destination ->
            NavigationBarItem(
                selected = destination == HomeDestination.Home,
                onClick = {
                    onAction(HomeAction.NavigationItemClicked(destination))
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(destination.label)
                },
            )
        }
    }
}

private val HomeDestination.label: String
    get() = when (this) {
        HomeDestination.Home -> "Home"
        HomeDestination.StudyMap -> "Study Map"
        HomeDestination.Focus -> "Focus"
        HomeDestination.Insights -> "Insights"
    }

private val HomeDestination.icon: ImageVector
    get() = when (this) {
        HomeDestination.Home -> Icons.Default.Home
        HomeDestination.StudyMap -> Icons.Default.Map
        HomeDestination.Focus -> Icons.Default.Timer
        HomeDestination.Insights -> Icons.Default.Insights
    }

@Preview(name = "Empty home", showBackground = true, showSystemUi = true)
@Composable
private fun EmptyHomePreview() {
    RenTheme(dynamicColor = false) {
        HomeScreen(
            state = HomePreviewData.empty,
            onAction = {},
        )
    }
}

@Preview(name = "Active home", showBackground = true, showSystemUi = true)
@Composable
private fun ActiveHomePreview() {
    RenTheme(dynamicColor = false) {
        HomeScreen(
            state = HomePreviewData.active,
            onAction = {},
        )
    }
}

@Preview(name = "Active without next session", showBackground = true)
@Composable
private fun ActiveHomeWithoutNextSessionPreview() {
    val activeContent = HomePreviewData.active.content as HomeContent.Active
    RenTheme(dynamicColor = false) {
        HomeScreen(
            state = HomePreviewData.active.copy(
                content = activeContent.copy(nextSession = null),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "Active without materials", showBackground = true)
@Composable
private fun ActiveHomeWithoutMaterialsPreview() {
    val activeContent = HomePreviewData.active.content as HomeContent.Active
    RenTheme(dynamicColor = false) {
        HomeScreen(
            state = HomePreviewData.active.copy(
                content = activeContent.copy(materials = emptyList()),
            ),
            onAction = {},
        )
    }
}
