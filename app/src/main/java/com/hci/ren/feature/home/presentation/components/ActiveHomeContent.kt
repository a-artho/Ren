package com.hci.ren.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hci.ren.feature.home.presentation.HomeContent

@Composable
fun ActiveHomeContent(
    content: HomeContent.Active,
    onStartFocus: () -> Unit,
    onMaterialClick: (String) -> Unit,
    onAddMaterial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ContinueStudyingCard(
            nextSession = content.nextSession,
            onStartFocus = onStartFocus,
        )
        DailyProgressSection(progress = content.progress)
        MaterialList(
            materials = content.materials,
            onMaterialClick = onMaterialClick,
            onAddMaterial = onAddMaterial,
        )
        Spacer(Modifier.height(72.dp))
    }
}
