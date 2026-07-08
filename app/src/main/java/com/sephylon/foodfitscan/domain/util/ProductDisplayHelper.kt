package com.sephylon.foodfitscan.domain.util

import com.sephylon.foodfitscan.domain.model.NutritionDisplayOption
import com.sephylon.foodfitscan.domain.model.NutritionFacts

object ProductDisplayHelper {

    /** A single nutrition row ready for display: a label and a formatted value. */
    data class NutritionDisplayRow(
        val key: String,
        val label: String,
        val value: String,
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
            NutritionDisplayRow(
                key = option.key,
                label = option.displayName,
                value = formatNutrient(nutrition?.let { option.valueFrom(it) }, option.unit),
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
