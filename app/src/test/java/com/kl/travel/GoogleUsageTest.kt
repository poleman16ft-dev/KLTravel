package com.kl.travel

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kl.travel.data.*
import com.kl.travel.net.*
import com.kl.travel.ui.rememberMapLoadAllowed
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth

class RouteThrottleTest {
    @Test fun farOffEventsWaitLonger() {
        assertEquals(60, RouteThrottle.intervalMin(240)); assertEquals(60, RouteThrottle.intervalMin(181))
        assertEquals(30, RouteThrottle.intervalMin(180)); assertEquals(30, RouteThrottle.intervalMin(91))
        assertEquals(15, RouteThrottle.intervalMin(90)); assertEquals(15, RouteThrottle.intervalMin(-10))
    }
    @Test fun dueOnlyAfterTheIntervalWithOneMinuteSlack() {
        val min = 60_000L; val now = 10_000_000L
        assertTrue(RouteThrottle.due(240, 0, now))                       // never checked
        assertFalse(RouteThrottle.due(240, now - 45 * min, now))          // 45 min ago, needs ~60
        assertTrue(RouteThrottle.due(240, now - 59 * min, now))           // worker fired a minute early: still fine
        assertFalse(RouteThrottle.due(60, now - 10 * min, now))           // close in: every 15
        assertTrue(RouteThrottle.due(60, now - 15 * min, now))
    }
    @Test fun fourHourWindowNeedsAboutAThirdFewerCalls() {
        // simulate the 15-minute worker over the 4 hours before an event
        var last = 0L; var calls = 0; var t = 0L
        for (mins in 240 downTo 0 step 15) {
            t += 15 * 60_000L
            if (RouteThrottle.due(mins.toLong(), last, t)) { calls++; last = t }
        }
        assertTrue("calls=$calls", calls in 9..11)          // was 17 (every 15 minutes)
    }
}

class RouteCacheTest {
    private val r = RouteResult(600, 500, 5000, emptyList(), null, null)
    private val a = Place.Address("DFW Airport"); private val b = Place.Address("Hotel  ")
    @Before fun clear() = RouteCache.clear()

    @Test fun reusesForTenMinutesThenExpires() {
        val k = RouteCache.key(a, b, TravelMode.DRIVE, true)
        RouteCache.put(k, r, now = 1000)
        assertSame(r, RouteCache.get(k, now = 1000 + RouteCache.TTL_MS - 1))
        assertNull(RouteCache.get(k, now = 1000 + RouteCache.TTL_MS))
    }
    @Test fun keyDiffersByModeAndTrafficButNotByCaseOrNearbyGps() {
        assertNotEquals(RouteCache.key(a, b, TravelMode.DRIVE, true), RouteCache.key(a, b, TravelMode.WALK, true))
        assertNotEquals(RouteCache.key(a, b, TravelMode.DRIVE, true), RouteCache.key(a, b, TravelMode.DRIVE, false))
        // rideshare is the same drive route, so it shares the entry
        assertEquals(RouteCache.key(a, b, TravelMode.DRIVE, true), RouteCache.key(a, b, TravelMode.RIDESHARE, true))
        assertEquals(RouteCache.key(Place.Address("DFW airport"), b, TravelMode.DRIVE, true), RouteCache.key(a, b, TravelMode.DRIVE, true))
        assertEquals(RouteCache.key(Place.Coord(32.89961, -97.04038), b, TravelMode.DRIVE, true), RouteCache.key(Place.Coord(32.89990, -97.04001), b, TravelMode.DRIVE, true))
        assertNotEquals(RouteCache.key(Place.Coord(32.9, -97.0), b, TravelMode.DRIVE, true), RouteCache.key(Place.Coord(32.95, -97.0), b, TravelMode.DRIVE, true))
    }
    @Test fun staysSmall() {
        for (i in 0 until 100) RouteCache.put("k$i", r, now = 5)
        assertNull(RouteCache.get("k0", now = 6)); assertNotNull(RouteCache.get("k99", now = 6))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GoogleUsageTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var p: Prefs
    private var scenario: ActivityScenario<ComponentActivity>? = null
    @After fun closeActivity() { scenario?.close(); scenario = null }
    @Before fun setup() {
        shadowOf(ctx.packageManager).addActivityIfNotPresent(ComponentName(ctx, ComponentActivity::class.java))
        p = Prefs(ctx); RouteCache.clear()
        ctx.getSharedPreferences("kltravel", 0).edit().clear().commit()
    }

    @Test fun countsUntilTheCapThenStops() {
        GoogleUsage.setCap(p, GoogleApi.PLACES, 3)
        assertTrue(GoogleUsage.tryUse(p, GoogleApi.PLACES)); assertTrue(GoogleUsage.tryUse(p, GoogleApi.PLACES)); assertTrue(GoogleUsage.tryUse(p, GoogleApi.PLACES))
        assertFalse(GoogleUsage.tryUse(p, GoogleApi.PLACES))
        assertEquals(3, GoogleUsage.used(p, GoogleApi.PLACES))            // the refused call was not counted
        assertEquals(0, GoogleUsage.used(p, GoogleApi.ROUTES))            // other APIs are separate
        val e = assertThrows(GoogleUsage.LimitReached::class.java) { GoogleUsage.require(p, GoogleApi.PLACES) }
        assertTrue(e.message!!.contains("limit reached (3)")); assertTrue(e.message!!.contains("Settings > Google usage"))
    }

    @Test fun defaultsAreWellInsideGooglesFreeAllowance() {
        assertEquals(3000, GoogleUsage.cap(p, GoogleApi.ROUTES)); assertEquals(1000, GoogleUsage.cap(p, GoogleApi.PLACES)); assertEquals(3000, GoogleUsage.cap(p, GoogleApi.MAPS))
        assertTrue(GoogleApi.ROUTES.defaultCap < 5000 && GoogleApi.PLACES.defaultCap < 5000 && GoogleApi.MAPS.defaultCap < 10000)
    }

    @Test fun startsFreshEachMonth() {
        GoogleUsage.setCap(p, GoogleApi.ROUTES, 1)
        val aug = YearMonth.of(2027, 2); val sep = YearMonth.of(2027, 3)
        assertTrue(GoogleUsage.tryUse(p, GoogleApi.ROUTES, aug)); assertFalse(GoogleUsage.tryUse(p, GoogleApi.ROUTES, aug))
        assertEquals(0, GoogleUsage.used(p, GoogleApi.ROUTES, sep))
        assertTrue(GoogleUsage.tryUse(p, GoogleApi.ROUTES, sep))
    }

    @Test fun capOfZeroTurnsTheFeatureOff() {
        GoogleUsage.setCap(p, GoogleApi.MAPS, 0)
        assertFalse(GoogleUsage.tryUse(p, GoogleApi.MAPS))
    }

    @Test fun routeAtTheLimitSendsNothingToGoogle() = runBlocking {
        p.mapsKey = "AIzaSyTESTKEYTESTKEYTESTKEYTESTKEY12345"
        GoogleUsage.setCap(p, GoogleApi.ROUTES, 0)
        val r = RoutesClient.compute(ctx, Place.Address("A st"), Place.Address("B st"), TravelMode.DRIVE, null)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("limit reached"))
        assertEquals(0, GoogleUsage.used(p, GoogleApi.ROUTES))
    }

    @Test fun aRecentRouteIsServedFromMemoryEvenAtTheLimit() = runBlocking {
        p.mapsKey = "AIzaSyTESTKEYTESTKEYTESTKEYTESTKEY12345"
        GoogleUsage.setCap(p, GoogleApi.ROUTES, 0)
        val a = Place.Address("A st"); val b = Place.Address("B st")
        val route = RouteResult(900, 800, 9000, emptyList(), null, null)
        RouteCache.put(RouteCache.key(a, b, TravelMode.DRIVE, p.liveTraffic), route)
        val r = RoutesClient.compute(ctx, a, b, TravelMode.DRIVE, null)
        assertSame(route, r.getOrThrow())
        assertEquals(0, GoogleUsage.used(p, GoogleApi.ROUTES))
    }

    @Test fun loungeSearchAtTheLimitFailsBeforeAnyRequest() = runBlocking {
        p.mapsKey = "AIzaSyTESTKEYTESTKEYTESTKEYTESTKEY12345"
        GoogleUsage.setCap(p, GoogleApi.PLACES, 0)
        val r = LoungeClient.load(ctx, "TPE", "")
        assertTrue(r.exceptionOrNull()!!.message!!.contains("limit reached"))
        val h = HotelPhotoClient.fetch(ctx, "Caesar Park", "Taipei")
        assertTrue(h.exceptionOrNull()!!.message!!.contains("limit reached"))
        assertEquals(0, GoogleUsage.used(p, GoogleApi.PLACES))
    }

    @Test fun liveTrafficDefaultsOnAndIsBackedUp() {
        assertTrue(p.liveTraffic)
        p.liveTraffic = false; assertFalse(p.liveTraffic)
        val text = Backup.encode(mapOf("liveTraffic" to false, "gu_routes_cap" to 500, "gu_routes_n" to 42), emptyList(), "1.23.0")
        val b = Backup.decode(text)
        assertEquals(false, b.prefs["liveTraffic"]); assertEquals(500, b.prefs["gu_routes_cap"])
        assertFalse(b.prefs.containsKey("gu_routes_n"))     // your limits come back after a restore, the month's count does not
    }

    @Test fun mapLoadIsCountedOncePerPageAndRefusedAtTheLimit() {
        GoogleUsage.setCap(p, GoogleApi.MAPS, 1)
        var html by mutableStateOf("page1")
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { a -> a.setContent { Text(if (rememberMapLoadAllowed(html)) "MAP OK" else "MAP BLOCKED") } }
        rule.onNodeWithText("MAP OK").assertIsDisplayed()
        rule.runOnIdle { html = "page1" }; rule.waitForIdle()
        assertEquals(1, GoogleUsage.used(p, GoogleApi.MAPS))               // recomposing is not a second load
        rule.runOnIdle { html = "page2" }; rule.waitForIdle()              // a different page is a new load, and the cap is 1
        rule.onNodeWithText("MAP BLOCKED").assertIsDisplayed()
        assertEquals(1, GoogleUsage.used(p, GoogleApi.MAPS))
    }
}
