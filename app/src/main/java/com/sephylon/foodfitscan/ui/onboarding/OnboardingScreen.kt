package com.sephylon.foodfitscan.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sephylon.foodfitscan.domain.model.AllergenOption
import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.ui.components.AppLogo
import com.sephylon.foodfitscan.ui.components.ChipOption
import com.sephylon.foodfitscan.ui.components.SelectionChipGroup
import com.sephylon.foodfitscan.ui.theme.GreenAccentLight

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isLastPage = state.currentPage == OnboardingViewModel.PAGE_COUNT - 1

    BackHandler(enabled = state.currentPage > 0) {
        viewModel.previousPage()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Top bar: back, gradient progress, skip.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (state.currentPage > 0) {
                        IconButton(onClick = viewModel::previousPage) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OnboardingProgress(
                    page = state.currentPage,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    if (!isLastPage) {
                        TextButton(
                            onClick = {
                                viewModel.skipOnboarding()
                                onComplete()
                            },
                        ) {
                            Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = state.currentPage,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "onboarding_page",
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (page) {
                        0 -> IntroPage()
                        1 -> AllergenPage(
                            selectedAllergens = state.selectedAllergens,
                            onToggle = viewModel::toggleAllergen,
                        )
                        2 -> NutritionFieldsPage(
                            selectedFields = state.selectedNutritionFields,
                            onToggle = viewModel::toggleNutritionField,
                        )
                        3 -> UltraProcessedPage(
                            avoidUltraProcessed = state.avoidUltraProcessed,
                            onSelect = viewModel::setAvoidUltraProcessed,
                        )
                        else -> CreditPage()
                    }
                }
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.completeOnboarding()
                        onComplete()
                    } else {
                        viewModel.nextPage()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(
                    text = if (isLastPage) "Get started" else "Next",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingProgress(page: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = (page + 1) / OnboardingViewModel.PAGE_COUNT.toFloat(),
        label = "onboarding_progress",
    )
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, GreenAccentLight),
                    ),
                ),
        )
    }
}

// ── Page 1: intro ───────────────────────────────────────────────────────────

@Composable
private fun IntroPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        AppLogo(size = 108.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "FoodFit Scan",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Scan or search packaged food to see how it fits your needs.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Gradient tile used by the Open Food Facts credit page — not the app's own brand mark. */
@Composable
private fun BrandMark(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Shared page header ──────────────────────────────────────────────────────

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
}

// ── Page 2: allergens ───────────────────────────────────────────────────────

@Composable
private fun AllergenPage(
    selectedAllergens: Set<String>,
    onToggle: (String) -> Unit,
) {
    PageHeader(
        title = "Anything to avoid?",
        subtitle = "Tap the allergens you want flagged. You can change these anytime in Settings.",
    )
    SelectionChipGroup(
        options = AllergenOption.ALL.map { ChipOption(it.key, it.displayName) },
        selectedKeys = selectedAllergens,
        onToggle = onToggle,
    )
}

// ── Page 3: nutrition fields ────────────────────────────────────────────────

@Composable
private fun NutritionFieldsPage(
    selectedFields: Set<String>,
    onToggle: (String) -> Unit,
) {
    PageHeader(
        title = "Nutrition that matters to you",
        subtitle = "Choose which values appear on product pages.",
    )
    SelectionChipGroup(
        options = NutritionDisplayOption.ALL.map { ChipOption(it.key, it.displayName) },
        selectedKeys = selectedFields,
        onToggle = onToggle,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = "If a product has no data for a nutrition field, that field will be hidden.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Page 4: ultra-processed warning ─────────────────────────────────────────

@Composable
private fun UltraProcessedPage(
    avoidUltraProcessed: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    PageHeader(
        title = "Want warnings for ultra-processed foods?",
        subtitle = "This warns when a product is classified as NOVA group 4.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            title = "Yes, warn me",
            selected = avoidUltraProcessed,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
        ChoiceCard(
            title = "No thanks",
            selected = !avoidUltraProcessed,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Page 5: Open Food Facts credit ──────────────────────────────────────────

@Composable
private fun CreditPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        BrandMark(icon = Icons.Default.Public)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Powered by Open Food Facts",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Product data comes from Open Food Facts, a free community database.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Data can be incomplete — always double-check the packaging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
