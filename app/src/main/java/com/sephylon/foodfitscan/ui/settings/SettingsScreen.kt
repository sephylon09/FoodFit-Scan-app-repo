package com.sephylon.foodfitscan.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sephylon.foodfitscan.domain.model.AdditiveOption
import com.sephylon.foodfitscan.domain.model.AllergenOption

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
            title = { Text("Reset preferences?") },
            text = { Text("All allergen, additive, and nutrition preferences will be cleared.") },
            confirmButton = {
                TextButton(onClick = viewModel::resetPreferences) { Text("Reset") }
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = "Preferences are used for informational scoring only. Food data from Open Food Facts may be incomplete — always check the product packaging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel("Allergens to Avoid")
            PreferenceCard {
                state.allergenOptions.forEachIndexed { index, option ->
                    CheckboxRow(
                        label = option.displayName,
                        checked = option.key in state.selectedAllergens,
                        onCheckedChange = { viewModel.toggleAllergen(option.key) },
                    )
                    if (index < state.allergenOptions.lastIndex) HorizontalDivider()
                }
            }
            HelperText("Allergen data may be incomplete. Always check packaging.")

            Spacer(Modifier.height(16.dp))

            SectionLabel("Additives to Avoid")
            PreferenceCard {
                state.additiveOptions.forEachIndexed { index, option ->
                    CheckboxRow(
                        label = option.displayName,
                        checked = option.key in state.selectedAdditives,
                        onCheckedChange = { viewModel.toggleAdditive(option.key) },
                    )
                    if (index < state.additiveOptions.lastIndex) HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionLabel("Ultra-Processed Foods")
            PreferenceCard {
                SwitchRow(
                    label = "Avoid NOVA 4 ultra-processed foods",
                    checked = state.avoidUltraProcessed,
                    onCheckedChange = { viewModel.toggleAvoidUltraProcessed() },
                )
            }
            HelperText("Warns when a scanned product is NOVA group 4 (ultra-processed).")

            Spacer(Modifier.height(16.dp))

            SectionLabel("Nutrition Caps (per 100g)")
            PreferenceCard {
                NutritionCapField(
                    label = "Max sugar (g)",
                    value = state.maxSugarInput,
                    isError = state.sugarInputError,
                    onValueChange = viewModel::onSugarInputChanged,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                NutritionCapField(
                    label = "Max salt (g)",
                    value = state.maxSaltInput,
                    isError = state.saltInputError,
                    onValueChange = viewModel::onSaltInputChanged,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                NutritionCapField(
                    label = "Max saturated fat (g)",
                    value = state.maxSaturatedFatInput,
                    isError = state.saturatedFatInputError,
                    onValueChange = viewModel::onSaturatedFatInputChanged,
                )
            }
            HelperText("Leave empty for no limit. Values are per 100 g. Based on Open Food Facts data, which may be incomplete.")

            Spacer(Modifier.height(16.dp))

            SectionLabel("Nutrition fields to show")
            PreferenceCard {
                state.nutritionFieldOptions.forEachIndexed { index, option ->
                    CheckboxRow(
                        label = option.displayName,
                        checked = option.key in state.selectedNutritionFields,
                        onCheckedChange = { viewModel.toggleNutritionField(option.key) },
                    )
                    if (index < state.nutritionFieldOptions.lastIndex) HorizontalDivider()
                }
            }
            HelperText("Choose which nutrition values appear on product result screens.")
            if (state.showLastFieldWarning) {
                Text(
                    text = "Select at least one nutrition field.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            TextButton(
                onClick = viewModel::resetNutritionFields,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text("Reset to default")
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::showResetDialog,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Reset all preferences")
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onReviewOnboarding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review onboarding")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun PreferenceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

@Composable
private fun NutritionCapField(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            supportingText = if (isError) {
                { Text("Enter a valid positive number") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
