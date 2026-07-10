package com.sephylon.foodfitscan.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sephylon.foodfitscan.R
import com.sephylon.foodfitscan.ui.theme.FoodFitScanTheme

/**
 * The FoodFit Scan app mark, used anywhere the app identifies itself (home header, home
 * intro hero, About hero, onboarding intro).
 *
 * The artwork is drawn on white, so it is clipped to a rounded tile with a white backing:
 * on the app's white-first surfaces the tile disappears into the page, and in dark mode it
 * reads as a deliberate rounded logo tile instead of a hard square. [ContentScale.Fit] on a
 * square source in a square box keeps the aspect ratio exact.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    cornerRadius: Dp = size * LOGO_CORNER_FRACTION,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_app_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White),
    )
}

/** Keeps the tile's corner rounding visually constant across every logo size. */
private const val LOGO_CORNER_FRACTION = 0.24f

@Preview(showBackground = true)
@Composable
private fun AppLogoPreview() {
    FoodFitScanTheme {
        AppLogo(size = 104.dp)
    }
}
