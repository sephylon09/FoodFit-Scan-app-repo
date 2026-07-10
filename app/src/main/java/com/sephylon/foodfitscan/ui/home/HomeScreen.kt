package com.sephylon.foodfitscan.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sephylon.foodfitscan.ads.AdConfig
import com.sephylon.foodfitscan.ads.AdaptiveBannerAd
import com.sephylon.foodfitscan.ads.NativeAdCard
import com.sephylon.foodfitscan.ads.NativeAdSlotState
import com.sephylon.foodfitscan.ads.SearchAdPlacement
import com.sephylon.foodfitscan.ads.SearchRow
import com.sephylon.foodfitscan.ads.rememberNativeAdLoader
import com.sephylon.foodfitscan.domain.model.ProductSearchItem
import com.sephylon.foodfitscan.domain.model.ProductSearchResult
import com.sephylon.foodfitscan.domain.model.SearchCountry
import com.sephylon.foodfitscan.ui.components.AppLogo
import com.sephylon.foodfitscan.ui.theme.FoodFitScanTheme

@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val showPreferencesCard by viewModel.showPreferencesCard.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitSearch: () -> Unit = {
        keyboardController?.hide()
        when (val action = viewModel.onSearchSubmit()) {
            is HomeSearchAction.NavigateToProduct -> onOpenProduct(action.barcode)
            HomeSearchAction.Handled -> Unit // Reflected in searchState.
        }
    }

    Scaffold(
        floatingActionButton = { SettingsFab(onClick = onSettingsClick) },
        // Anchored adaptive banner at the very bottom, well away from the search bar at the
        // top. It renders with zero height until an ad loads, and hides itself on failure.
        bottomBar = {
            AdaptiveBannerAd(
                adUnitId = AdConfig.bannerAdUnitId,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .imePadding(),
        ) {
            // ── Top: logo/app identity, then country filter + About shortcut ───
            Spacer(Modifier.height(16.dp))
            BrandHeader(
                selectedCountry = selectedCountry,
                onCountrySelect = viewModel::onCountrySelected,
                onAboutClick = onAboutClick,
            )

            Spacer(Modifier.height(18.dp))

            // ── Search bar: scanner icon (left) + search action (right) ────────
            HomeSearchBar(
                query = query,
                onQueryChange = viewModel::onSearchQueryChange,
                onScanClick = onScanClick,
                onSearchClick = submitSearch,
            )

            // Inline validation message (blank / too short) stays under the search bar.
            (searchState as? ProductSearchResult.ValidationError)?.let { validation ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = validation.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Main content area: intro, loading, results, empty, or error ────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Crossfade(
                    targetState = searchState,
                    animationSpec = tween(durationMillis = 220),
                    label = "searchStateCrossfade",
                ) { state ->
                    when (state) {
                        is ProductSearchResult.Success ->
                            SearchResults(items = state.items, onOpenProduct = onOpenProduct)

                        ProductSearchResult.Loading ->
                            LoadingSkeleton()

                        ProductSearchResult.Empty ->
                            StatusMessage(
                                icon = Icons.Rounded.SearchOff,
                                title = "No results found",
                                message = emptyResultMessage(query, selectedCountry),
                            )

                        is ProductSearchResult.NetworkError,
                        is ProductSearchResult.UnknownError ->
                            StatusMessage(
                                icon = Icons.Rounded.CloudOff,
                                title = "Something went wrong",
                                message = "We couldn't reach product search. " +
                                    "Check your connection and try again.",
                                onRetry = viewModel::retryLastSearch,
                            )

                        ProductSearchResult.Idle,
                        is ProductSearchResult.ValidationError ->
                            IntroContent(
                                showPreferencesCard = showPreferencesCard,
                                onSettingsClick = onSettingsClick,
                            )
                    }
                }
            }
        }
    }
}

private fun emptyResultMessage(query: String, country: SearchCountry): String {
    val shown = query.trim()
    val hint = if (country == SearchCountry.ALL) {
        "Try another name, or scan the barcode instead."
    } else {
        "Try another name, switch the country to All, or scan the barcode instead."
    }
    if (shown.isEmpty()) return hint

    val scope = if (country == SearchCountry.ALL) "" else " in ${country.displayName}"
    return "We couldn't find “$shown”$scope. $hint"
}

// ── Brand header ────────────────────────────────────────────────────────────

/**
 * Logo and app name on the left; the country filter and the About shortcut on the right,
 * in that order — the info icon always stays on the far edge.
 *
 * The app name gives up space before the country pill does, so a long country label
 * ("United Kingdom") shortens "FoodFit Scan" rather than pushing the info icon off-screen.
 */
@Composable
private fun BrandHeader(
    selectedCountry: SearchCountry,
    onCountrySelect: (SearchCountry) -> Unit,
    onAboutClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "FoodFit Scan",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CountryFilter(selected = selectedCountry, onSelect = onCountrySelect)
            IconButton(onClick = onAboutClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "About FoodFit Scan",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Country filter ──────────────────────────────────────────────────────────

/**
 * Compact, borderless country pill that scopes product-name search results. It lives in the
 * header beside the info icon, so the label stays short ("Singapore", "All"). The starting
 * value comes from the device locale's region (see [HomeViewModel]); picking a country here
 * refreshes any results already on screen.
 */
@Composable
private fun CountryFilter(
    selected: SearchCountry,
    onSelect: (SearchCountry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.semantics {
                contentDescription = "Country filter, ${selected.displayName}"
            },
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
        ) {
            SearchCountry.OPTIONS.forEach { country ->
                DropdownMenuItem(
                    text = { Text(country.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(country)
                    },
                    trailingIcon = {
                        if (country == selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

// ── Search bar ──────────────────────────────────────────────────────────────

@Composable
private fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onScanClick) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = "Scan barcode",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search food or barcode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // Keyboard "Search" action triggers the same submit as the search button.
                    keyboardActions = KeyboardActions(onSearch = { onSearchClick() }),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search text",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledIconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── Idle / intro state ──────────────────────────────────────────────────────

@Composable
private fun IntroContent(
    showPreferencesCard: Boolean,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(size = 88.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "FoodFit Scan",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Search or scan packaged food to check how it fits your nutrition goals.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (showPreferencesCard) {
            Spacer(Modifier.height(32.dp))
            PreferencesHelperCard(onSettingsClick = onSettingsClick)
        }
    }
}

@Composable
private fun PreferencesHelperCard(onSettingsClick: () -> Unit) {
    Surface(
        onClick = onSettingsClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set your preferences",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Allergens and limits personalize every result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Results ─────────────────────────────────────────────────────────────────

@Composable
private fun SearchResults(
    items: List<ProductSearchItem>,
    onOpenProduct: (String) -> Unit,
) {
    val nativeAdLoader = rememberNativeAdLoader(AdConfig.nativeAdUnitId)
    val rows = remember(items) { SearchAdPlacement.buildRows(items) }

    // Start loading every planned ad slot (max 3) as soon as results arrive.
    LaunchedEffect(rows) {
        rows.filterIsInstance<SearchRow.Ad>().forEach { nativeAdLoader.ensureLoaded(it.slot) }
    }

    // An ad row only becomes visible once its ad is actually loaded, so loading/failed slots
    // never leave gaps or empty placeholders between product results.
    val visibleRows = rows.filter { row ->
        row !is SearchRow.Ad || nativeAdLoader.stateFor(row.slot) is NativeAdSlotState.Loaded
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // Extra bottom padding keeps the last card clear of the settings FAB.
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
    ) {
        item(key = "resultCount") {
            Text(
                text = if (items.size == 1) "1 result" else "${items.size} results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        items(
            items = visibleRows,
            key = { row ->
                when (row) {
                    is SearchRow.Product -> row.item.barcode
                    is SearchRow.Ad -> "nativeAd:${row.slot}"
                }
            },
            contentType = { row -> if (row is SearchRow.Ad) "nativeAd" else "product" },
        ) { row ->
            when (row) {
                is SearchRow.Product ->
                    ProductResultCard(
                        item = row.item,
                        onClick = { onOpenProduct(row.item.barcode) },
                    )

                is SearchRow.Ad -> {
                    val state = nativeAdLoader.stateFor(row.slot)
                    if (state is NativeAdSlotState.Loaded) {
                        NativeAdCard(nativeAd = state.ad)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductResultCard(item: ProductSearchItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResultThumbnail(imageUrl = item.imageUrl, productName = item.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.brand.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultThumbnail(imageUrl: String?, productName: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Image of $productName",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Subtle neutral fallback — a small quiet glyph instead of a loud placeholder.
            Icon(
                imageVector = Icons.Outlined.Fastfood,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ── Loading skeleton ────────────────────────────────────────────────────────

@Composable
private fun LoadingSkeleton(rowCount: Int = 6) {
    val pulse = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp)
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rowCount) {
            SkeletonRow()
        }
    }
}

@Composable
private fun SkeletonRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

// ── Empty / error states ────────────────────────────────────────────────────

@Composable
private fun StatusMessage(
    icon: ImageVector,
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}

// ── Settings FAB ────────────────────────────────────────────────────────────

@Composable
private fun SettingsFab(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Open settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    FoodFitScanTheme {
        HomeScreen(
            onScanClick = {},
            onOpenProduct = {},
            onSettingsClick = {},
            onAboutClick = {},
        )
    }
}
