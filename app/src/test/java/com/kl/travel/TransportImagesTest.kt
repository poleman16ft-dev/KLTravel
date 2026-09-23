package com.kl.travel

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kl.travel.data.Backup
import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import com.kl.travel.net.TransportImages
import com.kl.travel.net.TransportImages.Source
import com.kl.travel.net.TransportPhotoClient
import com.kl.travel.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

private fun tFlight(no: String, aircraft: String = "", title: String = "$no - DFW > TPE") =
    TripItem("f$no", ItemKind.FLIGHT, "Flight", title, "", "", LocalDate.parse("2027-02-20"),
        LocalDateTime.parse("2027-02-20T10:00"), null, no, "", "", null, "", "", aircraft = aircraft)

private fun tEvent(type: String, title: String, transport: String = "") =
    TripItem("e1", ItemKind.EVENT, type, title, "", "", LocalDate.parse("2027-02-20"), null, null, "", "", "", null, transport, "")

private fun steps(s: Source) = (s as Source.Search).plan.steps

class TransportImagesLogicTest {
    @Test fun evaFlightSearchesForTheExactPlane() {
        val s = steps(TransportImages.forItem(tFlight("BR49", "Boeing 787-9")))
        assertEquals("EVA Air Boeing 787-9", s[0].query)
        assertEquals(listOf("evaair", "7879"), s[0].must)
        assertTrue(s.any { it.query == "EVA Air 787" })
        assertEquals("EVA Air aircraft (example)", s.last().label)
    }

    @Test fun cebgoWithNoPlaneFallsBackToTheAirline() {
        val s = steps(TransportImages.forItem(tFlight("DG6559")))
        assertEquals("Cebgo aircraft", s.single().query)
    }

    @Test fun livePlaneIsUsedWhenTheSheetHasNone() {
        val s = steps(TransportImages.forItem(tFlight("DG6559"), livePlane = "ATR 72-600"))
        assertEquals("Cebgo ATR 72-600", s[0].query)
        assertTrue(s[0].must.contains("atr72600"))
    }

    @Test fun sheetPlaneBeatsLivePlane() {
        val s = steps(TransportImages.forItem(tFlight("BR49", "Boeing 787-9"), livePlane = "Airbus A330-300"))
        assertTrue(s[0].query.contains("787"))
    }

    @Test fun philippineAirlinesDash8() {
        val s = steps(TransportImages.forItem(tFlight("PR2001", "De Havilland Dash 8")))
        assertEquals(listOf("philippineairlines", "dash8"), s[0].must)
    }

    @Test fun ferryUsesTheOperatorFromTheRow() {
        val it = tEvent("Transportation", "Tagbilaran Port → Cebu Pier 1, OceanJet", "Fast ferry")
        val s = steps(TransportImages.forItem(it))
        assertEquals("OceanJet ferry", s[0].query)
        assertEquals(listOf("oceanjet"), s[0].must)
        assertEquals("ferry boat", s.last().query)
    }

    @Test fun ferryWithNoOperatorGetsAGenericFerry() {
        val s = steps(TransportImages.forItem(tEvent("Ferry", "Port to island", "")))
        assertEquals(1, s.size); assertEquals("ferry boat", s[0].query)
    }

    @Test fun drivingShowsYourOwnVehicleAndTaxisShowNothing() {
        assertEquals(Source.MyVehicle, TransportImages.forItem(tEvent("Drive", "Drive to Fort Worth")))
        assertEquals(Source.MyVehicle, TransportImages.forItem(tEvent("Transportation", "Airport", "Rental car")))
        assertEquals(Source.None, TransportImages.forItem(tEvent("Transportation", "Airport to hotel", "Grab/taxi")))
        assertEquals(Source.None, TransportImages.forItem(tEvent("Transportation", "Airport to hotel", "Van")))
    }

    @Test fun activitiesAndFlightsWithNoClueShowNothing() {
        assertEquals(Source.None, TransportImages.forItem(tEvent("Dinner", "Dinner at Jollibee")))
        assertEquals(Source.None, TransportImages.forItem(tFlight("ZZ1", title = "x")))
    }

    @Test fun trainBusAndTricycle() {
        assertEquals("Shinkansen", steps(TransportImages.forItem(tEvent("Train", "Tokyo to Kyoto Shinkansen")))[0].query.replaceFirstChar { it.uppercase() })
        assertEquals("intercity bus", steps(TransportImages.forItem(tEvent("Bus", "Manila to Baguio")))[0].query)
        assertEquals(listOf("tricycle"), steps(TransportImages.forItem(tEvent("Transportation", "To beach", "Tricycle")))[0].must)
    }

    @Test fun vehicleTextPlan() {
        val p = TransportImages.vehiclePlan("2019 Toyota RAV4 XLE")!!
        assertEquals("2019 Toyota RAV4 XLE", p.steps[0].query)
        assertEquals("toyota rav4", p.steps[1].query)
        assertNull(TransportImages.vehiclePlan("   "))
    }

    @Test fun planeKeys() {
        assertEquals("7879", TransportImages.planeKey("Boeing 787-9"))
        assertEquals("a330300", TransportImages.planeKey("Airbus A330-300"))
        assertEquals("dash8", TransportImages.planeKey("De Havilland Dash 8"))
        assertEquals("787", TransportImages.planeFamily("Boeing 787-9"))
        assertEquals("a330", TransportImages.planeFamily("Airbus A330-300"))
    }

    // Shape copied from a real Commons response for "EVA Air Boeing 787-9" (titles are real; numbers trimmed).
    private val json = """{"query":{"pages":{
      "1":{"pageid":1,"title":"File:EVA Air Boeing 787-9 B-17887 economy interior May 2025.jpg","index":1,"imageinfo":[{"thumburl":"https://upload.wikimedia.org/t/1.jpg","width":4032,"height":3024,"mime":"image/jpeg","descriptionurl":"https://commons.wikimedia.org/wiki/File:a","extmetadata":{"Artist":{"value":"A"},"LicenseShortName":{"value":"CC BY 4.0"}}}]},
      "2":{"pageid":2,"title":"File:EVA Air Boeing 787-9 B-17887 at Munich May 2025.jpg","index":2,"imageinfo":[{"thumburl":"https://upload.wikimedia.org/t/2.jpg","width":8234,"height":5490,"mime":"image/jpeg","descriptionurl":"https://commons.wikimedia.org/wiki/File:b","extmetadata":{"Artist":{"value":"<a href=\"//x\">Jane Doe</a>"},"LicenseShortName":{"value":"CC BY 4.0"}}}]},
      "3":{"pageid":3,"title":"File:EVA Air 787-9 brochure.pdf","index":3,"imageinfo":[{"thumburl":"https://upload.wikimedia.org/t/3.jpg","width":2000,"height":2000,"mime":"application/pdf"}]},
      "4":{"pageid":4,"title":"File:EVA Air Boeing 787-9 tiny.jpg","index":4,"imageinfo":[{"thumburl":"https://upload.wikimedia.org/t/4.jpg","width":320,"height":200,"mime":"image/jpeg"}]},
      "5":{"pageid":5,"title":"File:EVA Air Boeing 787-9 B-17885 departing Taoyuan.jpg","index":5,"imageinfo":[{"thumburl":"http://insecure/5.jpg","width":4857,"height":2732,"mime":"image/jpeg"}]},
      "6":{"pageid":6,"title":"File:EVA Air Airbus A330 at Seattle.jpg","index":6,"imageinfo":[{"thumburl":"https://upload.wikimedia.org/t/6.jpg","width":3000,"height":2000,"mime":"image/jpeg"}]},
      "7":{"pageid":7,"title":"File:No imageinfo.jpg","index":7}
    }}}"""

    @Test fun parsesAndSortsAndStripsHtml() {
        val c = TransportImages.parseResults(json)
        assertEquals(listOf(1, 2, 3, 4, 6), c.map { it.index })     // http thumb and missing imageinfo are dropped
        assertEquals("Jane Doe", c[1].artist); assertEquals("CC BY 4.0", c[1].license)
        assertEquals("EVA Air Boeing 787-9 B-17887 at Munich May 2025.jpg", c[1].title)
        assertEquals("Jane Doe · CC BY 4.0 · Wikimedia Commons", TransportImages.credit(c[1]))
    }

    @Test fun pickSkipsInteriorsPdfsTinyFilesAndWrongPlanes() {
        val c = TransportImages.parseResults(json)
        val step = TransportImages.Step("EVA Air Boeing 787-9", listOf("evaair", "7879"), "EVA Air Boeing 787-9")
        assertEquals(2, TransportImages.pick(c, step)!!.index)
        // the A330 shot is named for a different plane, so a 787 search never picks it
        assertNull(TransportImages.pick(c, TransportImages.Step("x", listOf("evaair", "787", "b17999"), "x")))
        // ...but the A330 search does find it
        assertEquals(6, TransportImages.pick(c, TransportImages.Step("x", listOf("evaair", "a330"), "x"))!!.index)
    }

    @Test fun negativeWordsMatchWholeWordsOnly() {
        // "Seattle" contains "seat"; it must not be thrown out
        assertNotNull(TransportImages.pick(TransportImages.parseResults(json), TransportImages.Step("x", listOf("seattle"), "x")))
    }

    @Test fun emptyOrOddResponsesGiveNothing() {
        assertTrue(TransportImages.parseResults("""{"batchcomplete":""}""").isEmpty())
    }

    @Test fun searchUrlIsEncodedAndAsksForPhotosOnly() {
        val u = TransportPhotoClient.searchUrl("EVA Air Boeing 787-9")
        assertTrue(u.startsWith("https://commons.wikimedia.org/w/api.php?"))
        assertTrue(u.contains("gsrsearch=EVA+Air+Boeing+787-9+filetype%3Abitmap"))
        assertFalse(u.contains(" "))
    }

    @Test fun cacheNameIsStableAndCaseBlind() {
        assertEquals(TransportPhotoClient.cacheName("air|eva air|7879"), TransportPhotoClient.cacheName(" AIR|Eva Air|7879 "))
        assertNotEquals(TransportPhotoClient.cacheName("air|a"), TransportPhotoClient.cacheName("air|b"))
        assertEquals(24, TransportPhotoClient.cacheName("x").length)
    }

    @Test fun myVehicleIsBackedUp() {
        val b = Backup.decode(Backup.encode(mapOf("myVehicle" to "2019 RAV4"), emptyList(), "1.22.0"))
        assertEquals("2019 RAV4", b.prefs["myVehicle"])
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TransportPhotoViewTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<ComponentActivity>? = null
    @After fun closeActivity() { scenario?.close(); scenario = null }
    @Before fun register() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(ctx.packageManager).addActivityIfNotPresent(ComponentName(ctx, ComponentActivity::class.java))
    }
    private fun show(shot: TransportShot, pick: (() -> Unit)? = {}, reset: (() -> Unit)? = null) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { a -> a.setContent { TransportPhotoView(shot, pick, reset) } }
    }
    private val bmp get() = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).asImageBitmap()

    @Test fun showsPhotoWithExampleNoteAndCredit() {
        show(TransportShot.Ready(bmp, "EVA Air Boeing 787-9", "Jane Doe · CC BY 4.0 · Wikimedia Commons", mine = false))
        rule.onNodeWithTag(TP_IMAGE).assertIsDisplayed()
        rule.onNodeWithText("EVA Air Boeing 787-9").assertIsDisplayed()
        rule.onNodeWithText("Example photo", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Jane Doe", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(TP_RESET).assertDoesNotExist()
        rule.onNodeWithText("Use a different photo").assertIsDisplayed()
    }

    @Test fun yourOwnPhotoIsLabelledAndCanBeReset() {
        var reset = 0
        show(TransportShot.Ready(bmp, "Your photo", null, mine = true), reset = { reset++ })
        rule.onNodeWithText("Your own photo").assertIsDisplayed()
        rule.onNodeWithTag(TP_RESET).performClick()
        assertEquals(1, reset)
    }

    @Test fun driveWithNoVehicleTellsYouWhereToAddOne() {
        show(TransportShot.Hint("Driving yourself? Add your vehicle in Settings > My vehicle to see it here."))
        rule.onNodeWithTag(TP_HINT).assertIsDisplayed()
        rule.onNodeWithTag(TP_PICK).assertIsDisplayed()
    }

    @Test fun emptyOffersOnlyAddMyOwnPhoto() {
        var picked = 0
        show(TransportShot.Empty, pick = { picked++ })
        rule.onNodeWithTag(TP_IMAGE).assertDoesNotExist()
        rule.onNodeWithText("Add my own photo").performClick()
        assertEquals(1, picked)
    }

    @Test fun loadingShowsNothingYet() {
        show(TransportShot.Loading)
        rule.onNodeWithTag(TP_PICK).assertDoesNotExist()
    }
}
