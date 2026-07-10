package com.sephylon.foodfitscan.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs once per build variant (`./gradlew test` executes debug, release, AND closedTesting
 * unit tests), so these assertions verify the real BuildConfig wiring of every variant:
 * debug/closedTesting must resolve Google sample ad units, release the real ones.
 */
class AdConfigTest {

    private val allAdUnitIds = listOf(
        AdConfig.bannerAdUnitId,
        AdConfig.interstitialAdUnitId,
        AdConfig.nativeAdUnitId,
    )

    @Test
    fun `ad unit ids are well formed`() {
        val pattern = Regex("""ca-app-pub-\d{16}/\d+""")
        allAdUnitIds.forEach { id ->
            assertTrue("malformed ad unit id: $id", pattern.matches(id))
        }
    }

    @Test
    fun `test-ad builds use only google sample ad units`() {
        if (!AdConfig.useTestAds) return // release variant — covered by the test below
        assertEquals("ca-app-pub-3940256099942544/6300978111", AdConfig.bannerAdUnitId)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdConfig.interstitialAdUnitId)
        assertEquals("ca-app-pub-3940256099942544/2247696110", AdConfig.nativeAdUnitId)
    }

    @Test
    fun `release build uses only the real foodfit ad units`() {
        if (AdConfig.useTestAds) return // debug/closedTesting variants — covered above
        assertEquals("ca-app-pub-6675028272966387/5612169301", AdConfig.bannerAdUnitId)
        assertEquals("ca-app-pub-6675028272966387/8889069336", AdConfig.interstitialAdUnitId)
        assertEquals("ca-app-pub-6675028272966387/4561564676", AdConfig.nativeAdUnitId)
    }

    @Test
    fun `test flag and publisher account always agree`() {
        val expectedPublisher =
            if (AdConfig.useTestAds) AdConfig.GOOGLE_SAMPLE_PUBLISHER else AdConfig.FOODFIT_PUBLISHER
        allAdUnitIds.forEach { id ->
            assertTrue(
                "ad unit $id does not belong to $expectedPublisher (useTestAds=${AdConfig.useTestAds})",
                id.startsWith("$expectedPublisher/"),
            )
        }
    }
}
