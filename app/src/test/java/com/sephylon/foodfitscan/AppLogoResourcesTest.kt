package com.sephylon.foodfitscan

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the app-logo drawable that [com.sephylon.foodfitscan.ui.components.AppLogo] resolves
 * via `R.drawable.ic_app_logo`. A missing density bucket still compiles (Android falls back to
 * the nearest one) but silently degrades the logo, so assert every bucket ships.
 */
class AppLogoResourcesTest {

    @Test
    fun `app logo drawable exists in every density bucket`() {
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

        densities.forEach { density ->
            val file = resFile("drawable-$density/ic_app_logo.webp")
            assertTrue("Missing logo asset: ${file.path}", file.isFile)
            assertTrue("Empty logo asset: ${file.path}", file.length() > 0)
        }
    }

    /** Unit tests run with the module directory as the working directory. */
    private fun resFile(relativePath: String): File {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val fromModule = File(moduleRoot, "src/main/res/$relativePath")
        return if (fromModule.exists()) fromModule else File(moduleRoot, "app/src/main/res/$relativePath")
    }
}
