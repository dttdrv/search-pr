package app.pane.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserSettingsTest {
    private val json = SettingsJson.json

    private fun decode(raw: String): BrowserSettings = checkNotNull(SettingsJson.decode(raw))

    @Test fun defaultsBlockAdsAndUpgradeToHttpsButLeaveCookiesAlone() {
        val d = BrowserSettings()
        assertTrue(d.blockAds && d.stripTrackingParams)
        // Blocking third-party cookies breaks everyday sites, so it is something a person turns on.
        assertFalse(d.blockThirdPartyCookies)
        assertEquals(HttpsMode.First, d.httpsMode)
        assertEquals(BrowserSettings.DEFAULTS_VERSION, d.defaultsVersion)
    }

    @Test fun filterListsDefaultToTheStandardOnesAndSurviveOldSettings() {
        val d = BrowserSettings()
        assertEquals(BrowserSettings.DEFAULT_FILTER_LISTS, d.enabledFilterLists)
        assertTrue("ublock-filters" in d.enabledFilterLists && "easylist" in d.enabledFilterLists)
        // Settings saved before the lists existed get all of them.
        assertEquals(BrowserSettings.DEFAULT_FILTER_LISTS, decode("""{"blockAds":true,"theme":"Dark"}""").enabledFilterLists)
        // A person's choice is kept exactly, ids this version doesn't know included.
        val chosen = decode("""{"enabledFilterLists":["easylist","future-list"]}""")
        assertEquals(listOf("easylist", "future-list"), chosen.enabledFilterLists)
        assertEquals(chosen, decode(SettingsJson.encode(chosen)))
        assertEquals(emptyList(), decode("""{"enabledFilterLists":[]}""").enabledFilterLists)
    }

    @Test fun defaultsAreNeverStrict() {
        val d = BrowserSettings()
        // Normal browser behaviour out of the box: GPC and Safe Browsing on, pages left as they are.
        assertTrue(d.globalPrivacyControl && d.safeBrowsing)
        assertFalse(d.darkPages)
        assertEquals(HttpsMode.First, d.httpsMode)
    }

    /** Settings saved by the earlier versions carry keys that no longer exist; they must still load. */
    @Test fun decodesOldJsonAndKeepsWhatStillExists() {
        val old = """
            {"searchEngineId":"brave","trackingProtection":"Strict","cookiePolicy":"BlockAll","httpsMode":"HttpsOnly",
             "dnsOverHttps":"Max","dohProvider":"Mullvad","globalPrivacyControl":false,"fingerprintingProtection":true,
             "safeBrowsing":false,"toolbarPosition":"Top","clearOnExit":true,"textScale":1.3,"theme":"Dark",
             "onboardingDone":true,"defaultsVersion":2}
        """.trimIndent()
        val s = decode(old)
        assertEquals("brave", s.searchEngineId)
        assertTrue(s.clearOnExit)
        assertTrue(s.onboardingDone)
        assertEquals(ThemeMode.Dark, s.theme)
        assertEquals(1.3f, s.textScale)
        // Keys that kept their name and type carry over, so a person who turned them off keeps them off.
        assertFalse(s.globalPrivacyControl)
        assertFalse(s.safeBrowsing)
        // The first Pane's "HttpsOnly" is the new Only; what it called Strict has no counterpart and is dropped.
        assertEquals(HttpsMode.Only, s.httpsMode)
        assertTrue(s.blockAds)
        // Whatever an older build stored, cookies are allowed again from this version on.
        assertFalse(s.blockThirdPartyCookies)
    }

    @Test fun cookieBlockingStoredByAnEarlierDefaultIsReset() {
        assertFalse(decode("""{"blockThirdPartyCookies":true,"defaultsVersion":4}""").blockThirdPartyCookies)
        // A choice made on this version is kept.
        assertTrue(decode("""{"blockThirdPartyCookies":true,"defaultsVersion":5}""").blockThirdPartyCookies)
    }

    @Test fun httpsSwitchFromTheLastVersionBecomesTheMode() {
        assertEquals(HttpsMode.First, decode("""{"httpsUpgrade":true}""").httpsMode)
        assertEquals(HttpsMode.Off, decode("""{"httpsUpgrade":false,"theme":"Dark"}""").httpsMode)
        // Nothing stored: the default, which is the same as the old switch's default.
        assertEquals(HttpsMode.First, decode("""{"searchEngineId":"brave"}""").httpsMode)
        // The switch isn't kept beside the mode once translated.
        assertFalse("httpsUpgrade" in SettingsJson.encode(decode("""{"httpsUpgrade":false}""")))
    }

    @Test fun httpsModeNamesFromEarlierVersionsAreTranslated() {
        assertEquals(HttpsMode.Only, decode("""{"httpsMode":"HttpsOnly"}""").httpsMode)
        assertEquals(HttpsMode.First, decode("""{"httpsMode":"HttpsFirst"}""").httpsMode)
        assertEquals(HttpsMode.Off, decode("""{"httpsMode":"Off"}""").httpsMode)
        assertEquals(HttpsMode.Only, decode("""{"httpsMode":"Only"}""").httpsMode)
    }

    @Test fun anExplicitModeWinsOverTheOldSwitch() {
        assertEquals(HttpsMode.Only, decode("""{"httpsMode":"Only","httpsUpgrade":false}""").httpsMode)
    }

    @Test fun anUnknownHttpsChoiceFallsBackToTheDefault() {
        assertEquals(HttpsMode.First, decode("""{"httpsMode":"Sometimes","textScale":1.2}""").httpsMode)
        assertEquals(HttpsMode.First, decode("""{"httpsMode":3}""").httpsMode)
        assertEquals(1.2f, decode("""{"httpsMode":"Sometimes","textScale":1.2}""").textScale)
    }

    @Test fun newPrivacyAndPageSwitchesRoundTrip() {
        val s = BrowserSettings(httpsMode = HttpsMode.Only, globalPrivacyControl = false, safeBrowsing = false, darkPages = true)
        assertEquals(s, decode(SettingsJson.encode(s)))
    }

    @Test fun garbageIsNotSettings() {
        assertNull(SettingsJson.decode("not json"))
        assertNull(SettingsJson.decode("[1,2]"))
    }

    @Test fun roundTrips() {
        val s = BrowserSettings(blockAds = false, httpsMode = HttpsMode.Off, textScale = 0.9f)
        assertEquals(s, json.decodeFromString(BrowserSettings.serializer(), json.encodeToString(BrowserSettings.serializer(), s)))
    }

    @Test fun startPageDefaultsAreCalmAndCentred() {
        val d = BrowserSettings()
        assertEquals(StartTitle.Name, d.startTitle)
        assertEquals("", d.startTitleText)
        assertTrue(d.startShowSearch && d.showHomeFavorites)
        assertEquals(8, d.startFavoritesCount)
        assertEquals(StartFavorites.Frequent, d.startFavoritesSource)
    }

    @Test fun startPageSettingsRoundTripAndOldJsonKeepsDefaults() {
        val s = BrowserSettings(
            startTitle = StartTitle.Clock,
            startTitleText = "Hi",
            startShowSearch = false,
            startFavoritesCount = 12,
            startFavoritesSource = StartFavorites.Bookmarks,
        )
        assertEquals(s, json.decodeFromString(BrowserSettings.serializer(), json.encodeToString(BrowserSettings.serializer(), s)))
        // Settings saved before the start page was customisable still load, with the new fields at their defaults.
        val old = json.decodeFromString(BrowserSettings.serializer(), """{"searchEngineId":"brave","showHomeFavorites":false}""")
        assertEquals(StartTitle.Name, old.startTitle)
        assertEquals(8, old.startFavoritesCount)
        assertEquals(false, old.showHomeFavorites)
        // An unknown stored choice falls back instead of failing the whole load.
        val odd = json.decodeFromString(BrowserSettings.serializer(), """{"startTitle":"Weather","startFavoritesSource":"Nope"}""")
        assertEquals(StartTitle.Name, odd.startTitle)
        assertEquals(StartFavorites.Frequent, odd.startFavoritesSource)
    }

    @Test fun startFavoritesCountSnapsToTheGrid() {
        assertEquals(4, BrowserSettings(startFavoritesCount = 4).startFavoritesLimit)
        assertEquals(8, BrowserSettings(startFavoritesCount = 7).startFavoritesLimit)
        assertEquals(12, BrowserSettings(startFavoritesCount = 99).startFavoritesLimit)
        assertEquals(4, BrowserSettings(startFavoritesCount = -3).startFavoritesLimit)
    }

    @Test fun customStartTitleIsTrimmedAndCapped() {
        assertEquals("Hello", BrowserSettings(startTitleText = "  Hello  ").startTitleCustom)
        assertEquals(BrowserSettings.START_TITLE_MAX, BrowserSettings(startTitleText = "x".repeat(60)).startTitleCustom.length)
    }
}
