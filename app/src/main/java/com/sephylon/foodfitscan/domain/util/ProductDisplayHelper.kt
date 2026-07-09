package com.sephylon.foodfitscan.domain.util

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.NutritionFacts

object ProductDisplayHelper {

    /**
     * A single nutrition row ready for display: a label, a formatted value, and — for
     * the animated level bar — the raw per-100g amount, its unit, and the optional
     * [NutritionLevelGuide] limit ([guideLimit] null means "no bar for this field").
     */
    data class NutritionDisplayRow(
        val key: String,
        val label: String,
        val value: String,
        val rawValue: Double? = null,
        val unit: String = "g",
        val guideLimit: Double? = null,
    )

    fun formatNutrient(value: Double?, unit: String = "g"): String =
        if (value != null) "%.1f $unit".format(value) else "Not available"

    /**
     * Builds the ordered list of nutrition rows to display based on the user's
     * selected fields. Only selected fields are returned (canonical order), and a
     * missing value renders as "Not available". Falls back to the default selection
     * when [selectedKeys] is null or empty. Display-only — never used for scoring.
     */
    fun selectedNutritionRows(
        nutrition: NutritionFacts?,
        selectedKeys: Set<String>?,
    ): List<NutritionDisplayRow> =
        NutritionDisplayOption.resolveSelected(selectedKeys).map { option ->
            val value = nutrition?.let { option.valueFrom(it) }
            NutritionDisplayRow(
                key = option.key,
                label = option.displayName,
                value = formatNutrient(value, option.unit),
                rawValue = value,
                unit = option.unit,
                guideLimit = NutritionLevelGuide.guideLimitFor(option),
            )
        }

    /**
     * Like [selectedNutritionRows] but omits fields the product has no data for,
     * supporting the hide-when-missing display rule. May be empty when the product
     * has no nutrition data at all.
     */
    fun availableNutritionRows(
        nutrition: NutritionFacts?,
        selectedKeys: Set<String>?,
    ): List<NutritionDisplayRow> =
        NutritionDisplayOption.resolveSelected(selectedKeys).mapNotNull { option ->
            val value = nutrition?.let { option.valueFrom(it) } ?: return@mapNotNull null
            NutritionDisplayRow(
                key = option.key,
                label = option.displayName,
                value = formatNutrient(value, option.unit),
                rawValue = value,
                unit = option.unit,
                guideLimit = NutritionLevelGuide.guideLimitFor(option),
            )
        }

    fun orNotAvailable(value: String?): String =
        if (!value.isNullOrBlank()) value else "Not available"

    fun orUnknown(value: String?): String =
        if (!value.isNullOrBlank()) value else "Unknown"

    fun formatTagList(tags: List<String>): String =
        if (tags.isEmpty()) "None listed"
        else tags.joinToString(", ") { tag ->
            val stripped = if (tag.contains(':')) tag.substringAfter(':') else tag
            stripped.replace('-', ' ')
        }
}
