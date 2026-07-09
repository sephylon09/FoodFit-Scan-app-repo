package com.sephylon.foodfitscan.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sephylon.foodfitscan.ui.components.ChipOption
import com.sephylon.foodfitscan.ui.components.SelectionChipGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReviewOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showResetDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResetDialog,
            title = { Text("Reset all preferences?") },
            text = { Text("This clears your allergens, additives, and nutrition settings.") },
            confirmButton = {
                TextButton(onClick = viewModel::resetPreferences) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissResetDialog) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))

            InfoBanner("Preferences personalize your results. Always check the packaging.")

            SectionSpacer()

            SectionHeader("Allergens to avoid")
            SelectionChipGroup(
                options = state.allergenOptions.map { ChipOption(it.key, it.displayName) },
                selectedKeys = state.selectedAllergens,
                onToggle = viewModel::toggleAllergen,
            )

            SectionSpacer()

            SectionHeader("Additives to avoid")
            SelectionChipGroup(
                options = state.additiveOptions.map { ChipOption(it.key, it.displayName) },
                selectedKeys = state.selectedAdditives,
                onToggle = viewModel::toggleAdditive,
            )

            SectionSpacer()

            SectionHeader("Ultra-processed foods")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Warn me about ultra-processed foods",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Flags products in NOVA group 4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.avoidUltraProcessed,
                        onCheckedChange = { viewModel.toggleAvoidUltraProcessed() },
                    )
                }
            }

            SectionSpacer()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nutrition on product pages",
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = viewModel::resetNutritionFields) {
                    Text("Reset")
                }
            }
            SelectionChipGroup(
                options = state.nutritionFieldOptions.map { ChipOption(it.key, it.displayName) },
                selectedKeys = state.selectedNutritionFields,
                onToggle = viewModel::toggleNutritionField,
            )
            if (state.showLastFieldWarning) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Keep at least one field selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Fields without data are hidden on product pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(36.dp))

            OutlinedButton(
                onClick = onReviewOnboarding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review onboarding")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = viewModel::showResetDialog,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset all preferences", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(28.dp))
}
