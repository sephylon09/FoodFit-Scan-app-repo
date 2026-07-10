package com.sephylon.foodfitscan.data.device

import android.content.Context
import java.util.Locale

/**
 * Supplies the device's ISO 3166-1 alpha-2 region (e.g. `"SG"`), used to pick the initial
 * search-country filter.
 *
 * This reads the locale/region the user configured in system settings. It never requests a
 * location permission, never touches GPS or network location, and stores nothing.
 */
interface DeviceRegionProvider {
    /** The device region, or null when the locale carries no region. */
    fun regionCode(): String?
}

internal class LocaleDeviceRegionProvider(
    private val context: Context,
) : DeviceRegionProvider {

    override fun regionCode(): String? {
        val locales = context.resources.configuration.locales
        val fromConfiguration = (0 until locales.size())
            .asSequence()
            .map { locales[it].country }
            .firstOrNull { it.isNotBlank() }

        return fromConfiguration ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
    }
}
