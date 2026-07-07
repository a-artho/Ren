package com.hci.ren.feature.progress.presentation

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.hci.ren.MainActivity
import com.hci.ren.R
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test

class ProgressScreenshotSimulationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openFilledProgressPageForManualScreenshot() {
        composeRule.activity.setContent {
            RenTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0.dp),
                    bottomBar = { ScreenshotNavigationBar() },
                ) { scaffoldPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        ProgressScreen(
                            project = ProgressScreenFixtures.screenshotProject(),
                            today = ProgressScreenFixtures.ScreenshotToday,
                            modifier = Modifier.padding(scaffoldPadding),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Weekly focus").assertIsDisplayed()
        Thread.sleep(ScreenshotHoldMillis)
    }
}

@Composable
private fun ScreenshotNavigationBar() {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        ScreenshotTabItems.forEachIndexed { index, tab ->
            val selected = index == ProgressTabIndex
            NavigationBarItem(
                selected = selected,
                enabled = true,
                onClick = {},
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                ),
            )
        }
    }
}

private data class ScreenshotTab(val labelRes: Int, val icon: ImageVector)

private val ScreenshotTabItems = listOf(
    ScreenshotTab(R.string.study_plan_tab, Icons.Default.Route),
    ScreenshotTab(R.string.today, Icons.Default.Timer),
    ScreenshotTab(R.string.progress, Icons.Default.BarChart),
)

private const val ProgressTabIndex = 2
private const val ScreenshotHoldMillis = 10_000L
