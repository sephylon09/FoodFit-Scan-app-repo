package com.sephylon.foodfitscan.ui.product

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sephylon.foodfitscan.domain.model.AlternativeProduct
import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.util.IngredientHighlighter
import com.sephylon.foodfitscan.domain.util.ProductDisplayHelper
import com.sephylon.foodfitscan.ui.theme.SuitabilityAvoidContainer
import com.sephylon.foodfitscan.ui.theme.SuitabilityAvoidRed
import com.sephylon.foodfitscan.ui.theme.SuitabilityCautionContainer
import com.sephylon.foodfitscan.ui.theme.SuitabilityCautionOrange
import com.sephylon.foodfitscan.ui.theme.SuitabilityGoodContainer
import com.sephylon.foodfitscan.ui.theme.SuitabilityGoodGreen
import com.sephylon.foodfitscan.ui.theme.SuitabilityOnAvoid
import com.sephylon.foodfitscan.ui.theme.SuitabilityOnCaution
import com.sephylon.foodfitscan.ui.theme.SuitabilityOnGood
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    barcode: String,
    onBack: () -> Unit,
    onScanAnother: () -> Unit = onBack,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProduct: (String) -> Unit = {},
    viewModel: ProductDetailViewModel = viewModel(factory = ProductDetailViewModel.factory(barcode)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val alternativesState by viewModel.alternativesState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Product details",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        // Crossfade between state *types* only, so live preference updates (which produce
        // a new ProductFound instance) never reset scroll or section-expansion state.
        Crossfade(
            targetState = uiState::class,
            animationSpec = tween(durationMillis = 220),
            label = "productDetailState",
        ) { stateClass ->
            // Render the live uiState only while it still matches the crossfaded type, so the
            // brief fade-out of a replaced state never shows the new state's content.
            val state = uiState.takeIf { it::class == stateClass } ?: return@Crossfade
            when (state) {
                is ProductDetailUiState.Loading ->
                    LoadingContent(modifier = Modifier.padding(innerPadding))
                is ProductDetailUiState.ProductFound ->
                    ProductFoundContent(
                        product = state.product,
                        isFromStaleCache = state.isFromStaleCache,
                        suitabilityResult = state.suitabilityResult,
                        selectedNutritionKeys = state.selectedNutritionKeys,
                        avoidedAllergenKeys = state.avoidedAllergenKeys,
                        alternativesState = alternativesState,
                        onNavigateToSettings = onNavigateToSettings,
                        onLoadAlternatives = viewModel::loadAlternatives,
                        onNavigateToProduct = onNavigateToProduct,
                        modifier = Modifier.padding(innerPadding),
                    )
                is ProductDetailUiState.NotFound ->
                    StatusContent(
                        icon = Icons.Rounded.SearchOff,
                        title = "Product not found",
                        message = "Barcode $barcode isn't in Open Food Facts yet.",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        Button(onClick = onScanAnother, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan another product")
                        }
                        FilledTonalButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("Try again")
                        }
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back")
                        }
                    }
                is ProductDetailUiState.NetworkError ->
                    StatusContent(
                        icon = Icons.Rounded.CloudOff,
                        title = "Connection problem",
                        message = "Could not load this product. Check your internet connection and try again.",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("Retry")
                        }
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back")
                        }
                    }
                is ProductDetailUiState.UnknownError ->
                    StatusContent(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Something went wrong",
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("Retry")
                        }
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back")
                        }
                    }
            }
        }
    }
}

// ── Loading skeleton ────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "detailSkeletonPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "detailSkeletonAlpha",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(height = 220.dp)
        SkeletonBlock(height = 96.dp)
        SkeletonBlock(height = 180.dp)
        SkeletonBlock(height = 54.dp)
        SkeletonBlock(height = 54.dp)
    }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

// ── Found content ───────────────────────────────────────────────────────────

@Composable
private fun ProductFoundContent(
    product: ProductDetails,
    isFromStaleCache: Boolean = false,
    suitabilityResult: SuitabilityResult,
    selectedNutritionKeys: Set<String>,
    avoidedAllergenKeys: Set<String>,
    alternativesState: AlternativesResult,
    onNavigateToSettings: () -> Unit,
    onLoadAlternatives: () -> Unit,
    onNavigateToProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAboutDataDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(0.dp))

        if (isFromStaleCache) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = "Showing saved data — details may be outdated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        HeroCard(product = product)

        SuitabilityCard(result = suitabilityResult, onSetPreferences = onNavigateToSettings)

        NutritionCard(
            product = product,
            selectedNutritionKeys = selectedNutritionKeys,
            onEditFields = onNavigateToSettings,
        )

        IngredientsSection(
            ingredientsText = product.ingredientsText,
            avoidedAllergenKeys = avoidedAllergenKeys,
        )

        AllergenAdditiveSection(
            product = product,
            avoidedAllergenKeys = avoidedAllergenKeys,
        )

        AlternativesSection(
            suitabilityLevel = suitabilityResult.level,
            hasCategoryData = !product.categoriesTags.isNullOrEmpty(),
            alternativesState = alternativesState,
            onFindAlternatives = onLoadAlternatives,
            onNavigateToProduct = onNavigateToProduct,
        )

        // Compact data-source note; details live behind the info affordance.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { showAboutDataDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "About this data",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showAboutDataDialog) {
        AboutDataDialog(onDismiss = { showAboutDataDialog = false })
    }
}

// ── Hero: image + identity ──────────────────────────────────────────────────

@Composable
private fun HeroCard(product: ProductDetails) {
    DetailCard {
        // No image -> no placeholder block; the card simply starts with the name.
        if (!product.imageFrontUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = product.imageFrontUrl,
                    contentDescription = "Product image: ${product.name}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = product.name,
            style = MaterialTheme.typography.titleMedium,
        )
        if (!product.brand.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = product.brand,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val meta = listOfNotNull(
            product.quantity?.takeIf { it.isNotBlank() },
            product.barcode.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (product.nutriScore != null || product.novaGroup != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (product.nutriScore != null) {
                    NutriScoreBadge(grade = product.nutriScore)
                }
                if (product.novaGroup != null) {
                    NovaGroupBadge(group = product.novaGroup)
                }
            }
        }
    }
}

// ── Suitability ─────────────────────────────────────────────────────────────

private data class SuitabilityStyle(
    val container: Color,
    val content: Color,
    val title: String,
    val icon: ImageVector,
)

@Composable
private fun SuitabilityCard(result: SuitabilityResult, onSetPreferences: () -> Unit) {
    val style = when (result.level) {
        SuitabilityLevel.GOOD_MATCH -> SuitabilityStyle(
            SuitabilityGoodContainer, SuitabilityOnGood, "Good match", Icons.Rounded.CheckCircle,
        )
        SuitabilityLevel.CAUTION -> SuitabilityStyle(
            SuitabilityCautionContainer, SuitabilityOnCaution, "Caution", Icons.Rounded.ErrorOutline,
        )
        SuitabilityLevel.AVOID -> SuitabilityStyle(
            SuitabilityAvoidContainer, SuitabilityOnAvoid, "Avoid", Icons.Rounded.Block,
        )
        SuitabilityLevel.UNKNOWN -> SuitabilityStyle(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Unknown suitability",
            Icons.AutoMirrored.Rounded.HelpOutline,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = style.container,
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(style.content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = style.content,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = style.content,
                )
                val displayReasons = result.reasons.take(3)
                if (displayReasons.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        displayReasons.forEach { reason ->
                            Text(
                                text = "•  $reason",
                                style = MaterialTheme.typography.bodySmall,
                                color = style.content.copy(alpha = 0.95f),
                            )
                        }
                    }
                }
                if (result.reasons.size > 3) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "+${result.reasons.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = style.content.copy(alpha = 0.8f),
                    )
                }
                if (result.noPreferencesConfigured) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        onClick = onSetPreferences,
                        shape = CircleShape,
                        color = style.content.copy(alpha = 0.13f),
                    ) {
                        Text(
                            text = "Set preferences",
                            style = MaterialTheme.typography.labelLarge,
                            color = style.content,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Nutrition with animated level bars ──────────────────────────────────────

@Composable
private fun NutritionCard(
    product: ProductDetails,
    selectedNutritionKeys: Set<String>,
    onEditFields: () -> Unit,
) {
    val rows = ProductDisplayHelper.availableNutritionRows(product.nutrition, selectedNutritionKeys)
    if (rows.isEmpty()) return

    DetailCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Nutrition per 100 g",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Edit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onEditFields)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, row ->
            NutritionMeterRow(row = row)
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun NutritionMeterRow(row: ProductDisplayHelper.NutritionDisplayRow) {
    val rawValue = row.rawValue
    val guideLimit = row.guideLimit
    val hasBar = rawValue != null && guideLimit != null && guideLimit > 0.0
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "nutritionChevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasBar) Modifier.clickable { expanded = !expanded } else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (hasBar) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide level bar" else "Show level bar",
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (hasBar) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    NutritionLevelBar(value = rawValue!!, limit = guideLimit!!, unit = row.unit)
                }
            }
        }
    }
}

@Composable
private fun NutritionLevelBar(value: Double, limit: Double, unit: String) {
    val ratio = (value / limit).toFloat().coerceAtLeast(0f)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "nutritionLevelFill",
    )
    val currentRatio = ratio * progress
    val fillFraction = currentRatio.coerceIn(0f, 1f)
    val barColor = nutritionLevelColor(currentRatio)

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (fillFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Guide: up to ${formatGuideAmount(limit, unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ratio > 1f) {
                Text(
                    text = "Over guide",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SuitabilityAvoidRed,
                )
            } else {
                Text(
                    text = "${(ratio * 100).roundToInt()}% of guide",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Green while comfortably under the guide, blending to orange, then red at/over it. */
private fun nutritionLevelColor(ratio: Float): Color = when {
    ratio <= 0.5f -> SuitabilityGoodGreen
    ratio <= 0.8f -> lerp(SuitabilityGoodGreen, SuitabilityCautionOrange, (ratio - 0.5f) / 0.3f)
    ratio <= 1.0f -> lerp(SuitabilityCautionOrange, SuitabilityAvoidRed, (ratio - 0.8f) / 0.2f)
    else -> SuitabilityAvoidRed
}

private fun formatGuideAmount(limit: Double, unit: String): String =
    if (limit % 1.0 == 0.0) "${limit.toInt()} $unit" else "$limit $unit"

// ── Ingredients (collapsible, highlighted) ──────────────────────────────────

@Composable
private fun IngredientsSection(
    ingredientsText: String?,
    avoidedAllergenKeys: Set<String>,
) {
    val text = ingredientsText?.trim()?.takeIf { it.isNotBlank() } ?: return
    val spans = remember(text, avoidedAllergenKeys) {
        IngredientHighlighter.findMatchSpans(text, avoidedAllergenKeys)
    }
    val hasWarning = spans.isNotEmpty()

    CollapsibleCard(
        title = "Ingredients",
        subtitle = if (hasWarning) "Contains ingredients you avoid" else null,
        warning = hasWarning,
        initiallyExpanded = hasWarning,
    ) {
        val annotated = remember(text, spans) {
            buildAnnotatedString {
                append(text)
                spans.forEach { span ->
                    addStyle(
                        style = SpanStyle(
                            color = SuitabilityAvoidRed,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                        ),
                        start = span.start,
                        end = span.endExclusive,
                    )
                }
            }
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Allergens & additives (collapsible) ─────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergenAdditiveSection(
    product: ProductDetails,
    avoidedAllergenKeys: Set<String>,
) {
    data class TagGroup(val plural: String, val singular: String, val tags: List<String>, val flaggable: Boolean)

    val groups = listOf(
        TagGroup("allergens", "allergen", product.allergensTags, flaggable = true),
        TagGroup("traces", "trace", product.tracesTags, flaggable = true),
        TagGroup("additives", "additive", product.additivesTags, flaggable = false),
    ).filter { it.tags.isNotEmpty() }
    if (groups.isEmpty()) return

    val avoidedLower = remember(avoidedAllergenKeys) {
        avoidedAllergenKeys.map { it.lowercase() }.toSet()
    }
    fun isFlagged(tag: String) = tag.lowercase() in avoidedLower

    val hasWarning = groups
        .filter { it.flaggable }
        .any { group -> group.tags.any { isFlagged(it) } }
    val subtitle =
        if (hasWarning) "Includes allergens you avoid"
        else groups.joinToString("  ·  ") { group ->
            "${group.tags.size} ${if (group.tags.size == 1) group.singular else group.plural}"
        }

    CollapsibleCard(
        title = "Allergens & additives",
        subtitle = subtitle,
        warning = hasWarning,
        initiallyExpanded = hasWarning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.forEach { group ->
                Column {
                    Text(
                        text = group.plural.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.tags.forEach { tag ->
                            TagPill(
                                text = ProductDisplayHelper.formatTagList(listOf(tag)),
                                flagged = group.flaggable && isFlagged(tag),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(text: String, flagged: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (flagged) SuitabilityAvoidContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (flagged) SuitabilityAvoidRed.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (flagged) FontWeight.SemiBold else FontWeight.Medium,
            color = if (flagged) SuitabilityOnAvoid else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ── Alternatives (collapsible) ──────────────────────────────────────────────

@Composable
private fun AlternativesSection(
    suitabilityLevel: SuitabilityLevel,
    hasCategoryData: Boolean,
    alternativesState: AlternativesResult,
    onFindAlternatives: () -> Unit,
    onNavigateToProduct: (String) -> Unit,
) {
    // Only offer alternatives when the product is a poor fit; hide the section
    // entirely when there is nothing actionable to show.
    if (suitabilityLevel != SuitabilityLevel.CAUTION && suitabilityLevel != SuitabilityLevel.AVOID) {
        return
    }
    if (alternativesState is AlternativesResult.Idle && !hasCategoryData) return

    CollapsibleCard(
        title = "Better options",
        subtitle = "Similar products, healthier first",
        initiallyExpanded = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (alternativesState) {
                is AlternativesResult.Idle -> {
                    FilledTonalButton(
                        onClick = onFindAlternatives,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Find better options")
                    }
                }
                is AlternativesResult.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Finding similar products…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is AlternativesResult.Success -> {
                    alternativesState.alternatives.forEach { alternative ->
                        AlternativeProductCard(
                            alternative = alternative,
                            onClick = { onNavigateToProduct(alternative.barcode) },
                        )
                    }
                }
                is AlternativesResult.Empty -> {
                    Text(
                        text = "No clearly better options of this type yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is AlternativesResult.NoCategory -> {
                    Text(
                        text = "Not enough category data to find similar products.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is AlternativesResult.NetworkError,
                is AlternativesResult.UnknownError -> {
                    Text(
                        text = "Could not load alternatives.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FilledTonalButton(
                        onClick = onFindAlternatives,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlternativeProductCard(
    alternative: AlternativeProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suitabilityColor = when (alternative.suitabilityLevel) {
        SuitabilityLevel.GOOD_MATCH -> SuitabilityOnGood
        SuitabilityLevel.CAUTION -> SuitabilityOnCaution
        SuitabilityLevel.AVOID -> MaterialTheme.colorScheme.error
        SuitabilityLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val suitabilityLabel = when (alternative.suitabilityLevel) {
        SuitabilityLevel.GOOD_MATCH -> "Good match"
        SuitabilityLevel.CAUTION -> "Caution"
        SuitabilityLevel.AVOID -> "Avoid"
        SuitabilityLevel.UNKNOWN -> "Unknown fit"
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (!alternative.imageFrontUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = alternative.imageFrontUrl,
                        contentDescription = alternative.productName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Fastfood,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = alternative.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!alternative.brand.isNullOrBlank()) {
                    Text(
                        text = alternative.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alternative.nutriScore != null) {
                        NutriScoreBadge(grade = alternative.nutriScore, compact = true)
                    }
                    if (alternative.novaGroup != null) {
                        NovaGroupBadge(group = alternative.novaGroup, compact = true)
                    }
                    Text(
                        text = suitabilityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = suitabilityColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── About-this-data dialog ──────────────────────────────────────────────────

@Composable
private fun AboutDataDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    text = "About this data",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(14.dp))
                AboutDataLine("Product data comes from Open Food Facts, a community database.")
                Spacer(Modifier.height(8.dp))
                AboutDataLine("Data may be incomplete or out of date.")
                Spacer(Modifier.height(8.dp))
                AboutDataLine("Always check the packaging, especially for allergens.")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Got it")
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutDataLine(text: String) {
    Row {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Not found / error ───────────────────────────────────────────────────────

@Composable
private fun StatusContent(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            buttons()
        }
    }
}

// ── Shared building blocks ──────────────────────────────────────────────────

/** White bordered card used by all non-collapsible detail sections. */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/**
 * Accordion card with a clickable header. [warning] switches the border and subtitle to
 * the warning accent. [initiallyExpanded] is re-applied when its value changes (e.g. the
 * user edits preferences and a warning appears), otherwise the user's toggle wins.
 */
@Composable
private fun CollapsibleCard(
    title: String,
    subtitle: String? = null,
    warning: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title, initiallyExpanded) {
        mutableStateOf(initiallyExpanded)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "collapsibleChevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (warning) SuitabilityAvoidRed.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (warning) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SuitabilityAvoidRed,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (warning) SuitabilityAvoidRed
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

// ── Badges ──────────────────────────────────────────────────────────────────

@Composable
private fun NutriScoreBadge(grade: String, compact: Boolean = false) {
    val (bg, fg) = when (grade.lowercase()) {
        "a" -> Color(0xFF1E8449) to Color.White
        "b" -> Color(0xFF58D68D) to Color.Black
        "c" -> Color(0xFFF7DC6F) to Color.Black
        "d" -> Color(0xFFE67E22) to Color.White
        "e" -> Color(0xFFE74C3C) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ScoreBadge(
        text = if (compact) "Nutri ${grade.uppercase()}" else "Nutri-Score ${grade.uppercase()}",
        background = bg,
        foreground = fg,
        compact = compact,
    )
}

@Composable
private fun NovaGroupBadge(group: Int, compact: Boolean = false) {
    val (bg, fg) = when (group) {
        1 -> Color(0xFF1E8449) to Color.White
        2 -> Color(0xFFF7DC6F) to Color.Black
        3 -> Color(0xFFE67E22) to Color.White
        4 -> Color(0xFFE74C3C) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ScoreBadge(text = "NOVA $group", background = bg, foreground = fg, compact = compact)
}

@Composable
private fun ScoreBadge(text: String, background: Color, foreground: Color, compact: Boolean) {
    Box(
        modifier = Modifier
            .background(background, CircleShape)
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 2.dp else 5.dp,
            ),
    ) {
        Text(
            text = text,
            color = foreground,
            style = if (compact) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
