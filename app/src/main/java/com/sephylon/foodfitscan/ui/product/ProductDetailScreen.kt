package com.sephylon.foodfitscan.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sephylon.foodfitscan.domain.model.AlternativeProduct
import com.sephylon.foodfitscan.domain.model.AlternativesResult
import com.sephylon.foodfitscan.domain.model.NutritionFacts
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.SuitabilityLevel
import com.sephylon.foodfitscan.domain.model.SuitabilityResult
import com.sephylon.foodfitscan.domain.util.ProductDisplayHelper

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
                title = { Text("Product Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is ProductDetailUiState.Loading ->
                LoadingContent(barcode = barcode, modifier = Modifier.padding(innerPadding))
            is ProductDetailUiState.ProductFound ->
                ProductFoundContent(
                    product = state.product,
                    isFromStaleCache = state.isFromStaleCache,
                    suitabilityResult = state.suitabilityResult,
                    selectedNutritionKeys = state.selectedNutritionKeys,
                    alternativesState = alternativesState,
                    onNavigateToSettings = onNavigateToSettings,
                    onLoadAlternatives = viewModel::loadAlternatives,
                    onNavigateToProduct = onNavigateToProduct,
                    modifier = Modifier.padding(innerPadding),
                )
            is ProductDetailUiState.NotFound ->
                NotFoundContent(
                    barcode = barcode,
                    onRetry = viewModel::retry,
                    onScanAnother = onScanAnother,
                    onBack = onBack,
                    modifier = Modifier.padding(innerPadding),
                )
            is ProductDetailUiState.NetworkError ->
                ErrorContent(
                    message = "Could not connect to Open Food Facts.\nCheck your internet connection.",
                    onRetry = viewModel::retry,
                    onBack = onBack,
                    modifier = Modifier.padding(innerPadding),
                )
            is ProductDetailUiState.UnknownError ->
                ErrorContent(
                    message = state.message,
                    onRetry = viewModel::retry,
                    onBack = onBack,
                    modifier = Modifier.padding(innerPadding),
                )
        }
    }
}

@Composable
private fun LoadingContent(barcode: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Looking up barcode\n$barcode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProductFoundContent(
    product: ProductDetails,
    isFromStaleCache: Boolean = false,
    suitabilityResult: SuitabilityResult,
    selectedNutritionKeys: Set<String>,
    alternativesState: AlternativesResult,
    onNavigateToSettings: () -> Unit,
    onLoadAlternatives: () -> Unit,
    onNavigateToProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        if (isFromStaleCache) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    text = "Showing saved product data. Some details may be outdated — check packaging for the latest information.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        SuitabilityCard(result = suitabilityResult, onSetPreferences = onNavigateToSettings)

        ProductImageSection(
            imageFrontUrl = product.imageFrontUrl,
            productName = product.name,
        )

        // Header card: name, brand, quantity, barcode, badges
        DetailCard(title = "Product") {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = ProductDisplayHelper.orUnknown(product.brand),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow("Quantity", ProductDisplayHelper.orUnknown(product.quantity))
            InfoRow("Barcode", product.barcode)

            if (product.nutriScore != null || product.novaGroup != null) {
                Spacer(Modifier.height(8.dp))
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

        // Nutrition card (always shown, filtered to the user's selected fields)
        NutritionCard(
            nutrition = product.nutrition,
            selectedNutritionKeys = selectedNutritionKeys,
            onEditFields = onNavigateToSettings,
        )

        // Ingredients card (always shown)
        DetailCard(title = "Ingredients") {
            val ingredients = product.ingredientsText?.takeIf { it.isNotBlank() }
            Text(
                text = ingredients ?: "Ingredients not available for this product.",
                style = MaterialTheme.typography.bodySmall,
                color = if (ingredients != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Allergens & Additives (always shown)
        AllergenAdditiveCard(
            allergensTags = product.allergensTags,
            tracesTags = product.tracesTags,
            additivesTags = product.additivesTags,
        )

        // Alternatives section
        AlternativesSection(
            suitabilityLevel = suitabilityResult.level,
            hasCategoryData = !product.categoriesTags.isNullOrEmpty(),
            alternativesState = alternativesState,
            onFindAlternatives = onLoadAlternatives,
            onNavigateToProduct = onNavigateToProduct,
        )

        // Attribution
        Text(
            text = "Product data provided by Open Food Facts. Always check the product packaging, especially for allergens.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProductImageSection(imageFrontUrl: String?, productName: String) {
    if (imageFrontUrl != null) {
        AsyncImage(
            model = imageFrontUrl,
            contentDescription = "Product image: $productName",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = "No product image available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AllergenAdditiveCard(
    allergensTags: List<String>,
    tracesTags: List<String>,
    additivesTags: List<String>,
) {
    DetailCard(title = "Allergens & Additives") {
        TagsRow(label = "Allergens", tags = allergensTags)
        if (tracesTags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TagsRow(label = "Traces", tags = tracesTags)
        }
        Spacer(Modifier.height(4.dp))
        TagsRow(label = "Additives", tags = additivesTags)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Always check the packaging, especially for allergens. Data may be incomplete.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuitabilityCard(result: SuitabilityResult, onSetPreferences: () -> Unit) {
    val containerColor: Color
    val contentColor: Color
    val titleText: String

    when (result.level) {
        SuitabilityLevel.GOOD_MATCH -> {
            containerColor = Color(0xFFD4EDDA)
            contentColor = Color(0xFF155724)
            titleText = "Good match"
        }
        SuitabilityLevel.CAUTION -> {
            containerColor = Color(0xFFFFF3CD)
            contentColor = Color(0xFF7B4F00)
            titleText = "Caution"
        }
        SuitabilityLevel.AVOID -> {
            containerColor = Color(0xFFF8D7DA)
            contentColor = Color(0xFF721C24)
            titleText = "Avoid"
        }
        SuitabilityLevel.UNKNOWN -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            titleText = "Unknown suitability"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )

            val displayReasons = result.reasons.take(3)
            displayReasons.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
            if (result.reasons.size > 3) {
                Text(
                    text = "+${result.reasons.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }

            if (result.noPreferencesConfigured) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onSetPreferences) {
                    Text("Set preferences")
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = "Always check the packaging, especially for allergens.",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun NutritionCard(
    nutrition: NutritionFacts?,
    selectedNutritionKeys: Set<String>,
    onEditFields: () -> Unit,
) {
    val rows = ProductDisplayHelper.selectedNutritionRows(nutrition, selectedNutritionKeys)
    DetailCard(title = "Nutrition per 100 g") {
        Text(
            text = "Showing your selected nutrition fields.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { index, row ->
            NutritionRow(row.label, row.value)
            if (index < rows.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onEditFields, contentPadding = PaddingValues(0.dp)) {
            Text("Edit nutrition fields")
        }
    }
}

@Composable
private fun NotFoundContent(
    barcode: String,
    onRetry: () -> Unit,
    onScanAnother: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Product not found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Barcode: $barcode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "This product wasn't found in Open Food Facts. The database is community-maintained " +
                "and may not include every product.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Try again")
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onScanAnother, modifier = Modifier.fillMaxWidth()) {
            Text("Scan another product")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun NutriScoreBadge(grade: String) {
    val (bg, fg) = when (grade.lowercase()) {
        "a" -> Color(0xFF1E8449) to Color.White
        "b" -> Color(0xFF58D68D) to Color.Black
        "c" -> Color(0xFFF7DC6F) to Color.Black
        "d" -> Color(0xFFE67E22) to Color.White
        "e" -> Color(0xFFE74C3C) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Nutri-Score ${grade.uppercase()}",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NovaGroupBadge(group: Int) {
    val (bg, fg) = when (group) {
        1 -> Color(0xFF1E8449) to Color.White
        2 -> Color(0xFFF7DC6F) to Color.Black
        3 -> Color(0xFFE67E22) to Color.White
        4 -> Color(0xFFE74C3C) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "NOVA $group",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun NutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TagsRow(label: String, tags: List<String>) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = ProductDisplayHelper.formatTagList(tags),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AlternativesSection(
    suitabilityLevel: SuitabilityLevel,
    hasCategoryData: Boolean,
    alternativesState: AlternativesResult,
    onFindAlternatives: () -> Unit,
    onNavigateToProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (suitabilityLevel) {
        SuitabilityLevel.GOOD_MATCH -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alternatives",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This already looks like a good match for your preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SuitabilityLevel.UNKNOWN -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Alternatives",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Set food preferences to get personalised alternatives.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SuitabilityLevel.CAUTION, SuitabilityLevel.AVOID -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Alternatives",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    when (alternativesState) {
                        is AlternativesResult.Idle -> {
                            if (!hasCategoryData) {
                                Text(
                                    text = "No product category available to find similar options.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                FilledTonalButton(
                                    onClick = onFindAlternatives,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Find better options")
                                }
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
                                    text = "Searching for alternatives…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is AlternativesResult.Success -> {
                            Text(
                                text = "Potential better options based on available Open Food Facts data.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            alternativesState.alternatives.forEach { alternative ->
                                AlternativeProductCard(
                                    alternative = alternative,
                                    onClick = { onNavigateToProduct(alternative.barcode) },
                                )
                            }
                        }
                        is AlternativesResult.Empty -> {
                            Text(
                                text = "No better options found in this category yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is AlternativesResult.NoCategory -> {
                            Text(
                                text = "No category data available to find similar options.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is AlternativesResult.NetworkError -> {
                            Text(
                                text = "Could not load alternatives. Check your internet connection.",
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
    }
}

@Composable
private fun AlternativeProductCard(
    alternative: AlternativeProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suitabilityColor = when (alternative.suitabilityLevel) {
        SuitabilityLevel.GOOD_MATCH -> Color(0xFF1E8449)
        SuitabilityLevel.CAUTION -> Color(0xFF7B4F00)
        SuitabilityLevel.AVOID -> MaterialTheme.colorScheme.error
        SuitabilityLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val suitabilityLabel = when (alternative.suitabilityLevel) {
        SuitabilityLevel.GOOD_MATCH -> "Good match"
        SuitabilityLevel.CAUTION -> "Caution"
        SuitabilityLevel.AVOID -> "Avoid"
        SuitabilityLevel.UNKNOWN -> "Unknown"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alternative.imageFrontUrl != null) {
                AsyncImage(
                    model = alternative.imageFrontUrl,
                    contentDescription = alternative.productName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = alternative.productName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                if (alternative.brand != null) {
                    Text(
                        text = alternative.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (alternative.nutriScore != null) {
                        NutriScoreBadge(grade = alternative.nutriScore)
                    }
                    if (alternative.novaGroup != null) {
                        NovaGroupBadge(group = alternative.novaGroup)
                    }
                }
                Text(
                    text = suitabilityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = suitabilityColor,
                    fontWeight = FontWeight.Medium,
                )
                if (alternative.shortReasons.isNotEmpty()) {
                    Text(
                        text = alternative.shortReasons.first(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
