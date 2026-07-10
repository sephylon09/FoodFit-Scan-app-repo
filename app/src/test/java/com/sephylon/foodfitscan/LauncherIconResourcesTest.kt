package com.sephylon.foodfitscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the launcher icon wiring. `assembleDebug` catches a *missing* drawable reference,
 * but not a half-migrated icon: a stale `ic_launcher.webp` sitting next to the new PNG, an
 * adaptive layer that only exists at one density, or a manifest that still points somewhere
 * else. Those all build, install, and then show the wrong icon on the home screen.
 */
class LauncherIconResourcesTest {

    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    /** Launcher icon size in px per density bucket (48dp baseline). */
    private val legacySizes = mapOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192,
    )

    /** Adaptive layer size in px per density bucket (108dp canvas). */
    private val adaptiveLayerSizes = mapOf(
        "mdpi" to 108,
        "hdpi" to 162,
        "xhdpi" to 216,
        "xxhdpi" to 324,
        "xxxhdpi" to 432,
    )

    @Test
    fun `every density provides both legacy launcher bitmaps`() {
        densities.forEach { density ->
            assertTrue(
                "mipmap-$density/ic_launcher.png is missing",
                res("mipmap-$density/ic_launcher.png").isFile,
            )
            assertTrue(
                "mipmap-$density/ic_launcher_round.png is missing",
                res("mipmap-$density/ic_launcher_round.png").isFile,
            )
        }
    }

    @Test
    fun `every density provides the adaptive foreground and monochrome layers`() {
        densities.forEach { density ->
            assertTrue(
                "mipmap-$density/ic_launcher_foreground.png is missing",
                res("mipmap-$density/ic_launcher_foreground.png").isFile,
            )
            assertTrue(
                "mipmap-$density/ic_launcher_monochrome.png is missing",
                res("mipmap-$density/ic_launcher_monochrome.png").isFile,
            )
        }
    }

    @Test
    fun `the default android launcher bitmaps were removed`() {
        densities.forEach { density ->
            assertTrue(
                "Stale mipmap-$density/ic_launcher.webp still shadows the new PNG",
                !res("mipmap-$density/ic_launcher.webp").exists(),
            )
            assertTrue(
                "Stale mipmap-$density/ic_launcher_round.webp still shadows the new PNG",
                !res("mipmap-$density/ic_launcher_round.webp").exists(),
            )
        }
    }

    @Test
    fun `legacy launcher bitmaps are square and sized for their density`() {
        densities.forEach { density ->
            val expected = legacySizes.getValue(density)
            listOf("ic_launcher.png", "ic_launcher_round.png").forEach { name ->
                val (width, height) = pngSize(res("mipmap-$density/$name"))
                assertEquals("mipmap-$density/$name width", expected, width)
                assertEquals("mipmap-$density/$name height", expected, height)
            }
        }
    }

    @Test
    fun `adaptive layers are square 108dp canvases, so the logo is never stretched`() {
        densities.forEach { density ->
            val expected = adaptiveLayerSizes.getValue(density)
            listOf("ic_launcher_foreground.png", "ic_launcher_monochrome.png").forEach { name ->
                val (width, height) = pngSize(res("mipmap-$density/$name"))
                assertEquals("mipmap-$density/$name width", expected, width)
                assertEquals("mipmap-$density/$name height", expected, height)
            }
        }
    }

    @Test
    fun `both launcher icons are adaptive icons with a background and a foreground`() {
        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { name ->
            val xml = res("mipmap-anydpi-v26/$name").readText()

            assertTrue("$name is not an adaptive-icon", xml.contains("<adaptive-icon"))
            assertTrue(
                "$name does not use the white launcher background",
                xml.contains("""<background android:drawable="@drawable/ic_launcher_background""""),
            )
            assertTrue(
                "$name does not use the app-logo foreground",
                xml.contains("""<foreground android:drawable="@mipmap/ic_launcher_foreground""""),
            )
            assertTrue(
                "$name does not declare a monochrome layer for themed icons",
                xml.contains("""<monochrome android:drawable="@mipmap/ic_launcher_monochrome""""),
            )
        }
    }

    @Test
    fun `the adaptive background is a plain white plate`() {
        val xml = res("drawable/ic_launcher_background.xml").readText()

        assertTrue("Background should be a vector", xml.contains("<vector"))
        assertTrue(
            "Background should fill the canvas with white",
            xml.contains("""android:fillColor="#FFFFFFFF""""),
        )
        assertTrue(
            "Background still carries the default Android green",
            !xml.contains("#3DDC84"),
        )
    }

    @Test
    fun `the manifest points icon and roundIcon at the launcher mipmaps`() {
        val manifest = File(moduleDir, "src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher_round""""))
    }

    /**
     * The adaptive XML lives behind the `-v26` qualifier and the legacy bitmaps sit in the
     * plain density folders. Merging the two into `mipmap-anydpi` would hand the
     * `<adaptive-icon>` XML to pre-26 devices if minSdk is ever lowered.
     */
    @Test
    fun `adaptive icon xml is qualified for api 26 and does not shadow the bitmaps`() {
        assertTrue(
            "mipmap-anydpi-v26 is missing",
            File(moduleDir, "src/main/res/mipmap-anydpi-v26").isDirectory,
        )
        assertTrue(
            "mipmap-anydpi still exists; its unqualified adaptive-icon XML would shadow the PNGs",
            !File(moduleDir, "src/main/res/mipmap-anydpi").exists(),
        )
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun res(relativePath: String) = File(moduleDir, "src/main/res/$relativePath")

    /**
     * Gradle runs unit tests with the module directory as the working directory, but IDE
     * run configurations sometimes use the project root. Resolve whichever holds the module.
     */
    private val moduleDir: File by lazy {
        val here = File("").absoluteFile
        listOf(here, File(here, "app"))
            .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
            ?: error("Could not locate the app module from ${here.absolutePath}")
    }

    /** Reads width/height out of the PNG IHDR chunk (bytes 16..23, big-endian). */
    private fun pngSize(file: File): Pair<Int, Int> {
        val header = file.inputStream().use { stream ->
            val bytes = ByteArray(24)
            val read = stream.read(bytes)
            require(read == bytes.size) { "${file.name} is too short to be a PNG" }
            bytes
        }

        fun intAt(offset: Int): Int =
            (header[offset].toInt() and 0xFF shl 24) or
                (header[offset + 1].toInt() and 0xFF shl 16) or
                (header[offset + 2].toInt() and 0xFF shl 8) or
                (header[offset + 3].toInt() and 0xFF)

        require(intAt(12) == 0x49484452) { "${file.name} is not a PNG (no IHDR)" }
        return intAt(16) to intAt(20)
    }
}
