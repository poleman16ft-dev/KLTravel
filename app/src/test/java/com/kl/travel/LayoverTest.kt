package com.kl.travel

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.ComponentName
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kl.travel.data.*
import com.kl.travel.ui.LayoverRow
import com.kl.travel.ui.Row
import com.kl.travel.ui.buildRows
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

private fun flight(id: String, title: String, date: String, start: String, end: String?, no: String = "BR1", loc: String = ""): TripItem {
    val d = LocalDate.parse(date)
    return TripItem(id, ItemKind.FLIGHT, "Flight", title, loc, "", d,
        LocalDateTime.parse("${date}T$start"), end?.let { LocalDateTime.parse("${date}T$it") },
        no, "", "", null, "", "")
}

class LayoverLogicTest {
    @Test fun routeReadsArrowsDashesAndGreaterThan() {
        assertEquals("DFW" to "TPE", Layovers.route("EVA BR49 - DFW > TPE"))
        assertEquals("TPE" to "NRT", Layovers.route("EVA BR184 — TPE → NRT"))
        assertEquals("MNL" to "CEB", Layovers.route("5J 123 MNL to CEB"))
        assertNull(Layovers.route("Dinner with family"))
    }

    @Test fun sameDayConnectionGivesMinutes() {
        val a = flight("a", "BR49 - DFW > TPE", "2027-02-20", "10:00", "13:15", "BR49")
        val b = flight("b", "BR184 - TPE > NRT", "2027-02-20", "15:30", "20:00", "BR184")
        val l = Layovers.find(listOf(a, b)).single()
        assertEquals("TPE", l.airport); assertEquals(135L, l.minutes); assertEquals("2h 15m", l.durationText())
        assertEquals(LayoverLevel.OK, l.level)
    }

    @Test fun longHaulArrivingNextCalendarDay() {
        // dep 23:00 Feb 20, land 05:30 Feb 21 (End Time before Start Time), connect 08:15 Feb 21
        val a = flight("a", "BR1 - LAX > TPE", "2027-02-20", "23:00", "05:30")
        val b = flight("b", "BR2 - TPE > MNL", "2027-02-21", "08:15", "10:45")
        assertEquals(165L, Layovers.find(listOf(a, b)).single().minutes)
    }

    @Test fun eastboundArrivalEarlierOnSameDate() {
        // dep 23:35 Feb 20 from TPE, land 20:10 the same date in LAX (crossing the date line), connect 07:00 Feb 21
        val a = flight("a", "BR1 - TPE > LAX", "2027-02-20", "23:35", "20:10")
        val b = flight("b", "BR2 - LAX > DFW", "2027-02-21", "07:00", "12:30")
        assertEquals(650L, Layovers.find(listOf(a, b)).single().minutes)
    }

    @Test fun sixToTwelveHoursIsLongButNotAStay() {
        val a = flight("a", "BR1 - MNL > TPE", "2027-02-20", "08:00", "10:30")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-20", "19:00", "23:00")
        assertEquals(LayoverLevel.LONG, Layovers.find(listOf(a, b)).single().level)
    }

    @Test fun overnightLayoverAndLongLevel() {
        val a = flight("a", "BR1 - MNL > TPE", "2027-02-20", "08:00", "10:30")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-21", "07:00", "11:00")
        val l = Layovers.find(listOf(a, b)).single()
        assertEquals(1230L, l.minutes); assertEquals(LayoverLevel.STAY, l.level)
    }

    @Test fun shortConnectionIsFlagged() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-20", "14:00", "18:00")
        assertEquals(LayoverLevel.SHORT, Layovers.find(listOf(a, b)).single().level)
    }

    @Test fun differentAirportsOrNoEndTimeOrBigGapAreNotLayovers() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15")
        assertTrue(Layovers.find(listOf(a, flight("b", "BR2 - NRT > ICN", "2027-02-20", "16:00", "18:00"))).isEmpty())
        assertEquals(LayoverLevel.UNKNOWN, Layovers.find(listOf(flight("a2", "BR1 - DFW > TPE", "2027-02-20", "10:00", null), flight("b2", "BR2 - TPE > NRT", "2027-02-20", "16:00", "18:00"))).single().level)
        assertTrue(Layovers.find(listOf(a, flight("c", "BR9 - TPE > NRT", "2027-02-23", "16:00", "18:00"))).isEmpty())
    }

    @Test fun departureFallsBackToLocationCode() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15")
        val b = flight("b", "Flight to Tokyo", "2027-02-20", "16:00", "20:00", loc = "Taipei Taoyuan (TPE) T2")
        assertEquals(1, Layovers.find(listOf(a, b)).size)
    }

    @Test fun lateFlightMovesTheTimesAndCanMeanMissed() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-20", "15:00", "19:00")
        val cache = mapOf("a" to "Delayed · +150 min\nNow departs 12:30 (was 10:00) · Arrives 15:45")
        val l = Layovers.find(listOf(a, b)) { cache[it] }.single()
        assertTrue(l.live); assertEquals(-45L, l.minutes); assertEquals(LayoverLevel.MISSED, l.level)
    }

    @Test fun lateArrivalPastMidnightRollsToNextDay() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "22:30")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-21", "01:30", "05:00")
        val l = Layovers.find(listOf(a, b)) { if (it == "a") "Delayed\nArrives 00:10" else null }.single()
        assertEquals(80L, l.minutes)
    }

    @Test fun arrivalCanComeFromNotes() {
        val d = LocalDate.parse("2027-02-21")
        assertEquals(java.time.LocalTime.of(5, 25) to LocalDate.parse("2027-02-23"), Layovers.arrivalFromNotes("Business; arrives Taipei 5:25 AM on Feb 23; Chase Trip 1029128324", d))
        assertEquals(java.time.LocalTime.of(13, 45) to null, Layovers.arrivalFromNotes("CONFIRMED; depart 1:35 PM, arrive 1:45 PM", d))
        assertEquals(java.time.LocalTime.of(15, 45) to null, Layovers.arrivalFromNotes("arrives NAIA Terminal 2 at 3:45 PM", d))
        assertEquals(java.time.LocalTime.of(15, 20) to null, Layovers.arrivalFromNotes("Nonstop; arrive 3:20 PM; total shown for 2", d))
        assertNull(Layovers.arrivalFromNotes("Business; 2 travelers", d))
    }

    @Test fun missingArrivalAsksForItInsteadOfStayingSilent() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", null)
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-21", "08:00", "12:00")
        val l = Layovers.find(listOf(a, b)).single()
        assertEquals(LayoverLevel.UNKNOWN, l.level); assertNull(l.arrives)
        // flights days apart at the same airport are separate visits, not a connection
        assertTrue(Layovers.find(listOf(a, flight("c", "BR9 - TPE > NRT", "2027-02-25", "08:00", "12:00"))).isEmpty())
    }

    @Test fun sameClockThreeDaysApartIsNotAConnection() {
        val a = flight("a", "BR1 - MNL > CEB", "2027-03-01", "07:00", "08:30")
        val b = flight("b", "BR2 - CEB > NRT", "2027-03-04", "07:30", "12:00")
        assertTrue(Layovers.find(listOf(a, b)).isEmpty())
    }

    /** The real Philippines 2027 sheet: flights and lodging rows exactly as written there. */
    @Test fun philippinesSheetGivesTheTwoTaipeiLayovers() {
        val events = """
Date,Start Time,End Time,Type,Title,Location,Address,Flight #,Confirmation #,Cost,Transport,Notes,Seat,Aircraft,Cabin
2027-02-21,11:05 PM,,Flight,EVA BR49 — DFW → TPE,Dallas/Fort Worth (DFW),"Dallas Fort Worth International Airport, Texas, USA",BR49,DQZUUS (Luis) / C2YF3G (Sabrina),,Flight,Business; arrives Taipei 5:25 AM on Feb 23; Chase Trip 1029128324,6K,Boeing 787-9,Business
2027-02-23,7:55 AM,12:00 PM,Flight,EVA BR184 — TPE → NRT,Taipei Taoyuan (TPE) T2,"Taiwan Taoyuan International Airport Terminal 2, Taiwan",BR184,DQZUUS (Luis) / C2YF3G (Sabrina),,Flight,Business; arrives Tokyo Narita 12:00 PM,3K,Boeing 787-10,Business
2027-02-25,9:20 AM,1:40 PM,Flight,PAL PR431 — NRT → MNL,Tokyo Narita (NRT),"Narita International Airport, Japan",PR431,C2PWL6,,Flight,Business; 2 travelers; arrives Manila 1:40 PM,,Airbus A321,Business
2027-03-01,2:15 PM,3:20 PM,Flight,PAL PR2678 — CRK → USU,Clark Intl (CRK),"Clark International Airport, Angeles City, Pampanga, Philippines",PR2678,Y8P6AV,${'$'}138.60,Flight,Nonstop; arrive 3:20 PM,Luis 3C / Sabrina 3D,De Havilland Dash 8,Economy
2027-03-05,1:05 PM,1:45 PM,Flight,Cebgo DG6559 — USU → ENI,Busuanga (USU),"Francisco B. Reyes Airport, Busuanga, Palawan, Philippines",DG6559,PLBJ3D,,Flight,CONFIRMED; arrives El Nido 1:45 PM,Luis 1C / Sabrina 1D,,Economy
2027-03-09,1:35 PM,3:20 PM,Flight,Cebgo DG6887 — ENI → TAG,El Nido Airport (ENI),"El Nido Airport, El Nido, Palawan, Philippines",DG6887,OJHWVM,${'$'}80.00,Flight,depart 1:35 PM; arrive 3:20 PM,Luis 2A / Sabrina 2B,,Economy
2027-03-15,11:00 AM,12:00 PM,Flight,PAL PR2359 — CEB → MPH,Mactan-Cebu Intl (CEB),"Mactan-Cebu International Airport, Lapu-Lapu City, Cebu, Philippines",PR2359,Y9MIKV,${'$'}100.00,Flight,departs 11:00 AM; arrives 12:00 PM,Luis 31A / Sabrina 31B,Airbus A320,Economy
2027-03-17,2:40 PM,3:45 PM,Flight,PAL PR2046 — MPH → MNL,Caticlan (MPH),"Godofredo P. Ramos Airport, Caticlan, Malay, Aklan, Philippines",PR2046,Y9WH9F,${'$'}70.00,Flight,arrives NAIA Terminal 2 at 3:45 PM,Luis 31B / Sabrina 31A,Airbus A320,Economy
2027-03-20,6:50 PM,9:20 PM,Flight,EVA BR278 — MNL → TPE,Manila NAIA (MNL),"Ninoy Aquino International Airport, Manila, Philippines",BR278,DQZUUS (Luis) / C2YF3G (Sabrina),,Flight,Business; arrives Taipei 9:20 PM; overnight at Caesar Park Taipei,10K,Airbus A330-300,Business
2027-03-21,7:45 PM,8:15 PM,Flight,EVA BR50 — TPE → DFW,Taipei Taoyuan (TPE) T2,"Taiwan Taoyuan International Airport Terminal 2, Taiwan",BR50,DQZUUS (Luis) / C2YF3G (Sabrina),,Flight,Business; arrives Dallas 8:15 PM,2K,Boeing 787-9,Business
""".trim()
        val hotels = """
Lodging Name,Address,Check-In Date,Check-In Time,Check-Out Date,Check-Out Time,Confirmation #,Phone,Cost,Notes
Caesar Park Taipei,"Caesar Park Taipei, Taipei City 100, Taiwan",2027-03-20,,2027-03-21,,K2E7Y1,,,Guests: 2; 1 night
""".trim()
        val warn = mutableListOf<String>()
        val items = TripBuilder.build(SheetParser.parseEvents(Csv.parse(events), warn), SheetParser.parseHotels(Csv.parse(hotels), warn))
        val found = Layovers.find(items)
        assertEquals(listOf("BR49>BR184", "BR278>BR50"), found.map { it.from.flight + ">" + it.to.flight })
        // Feb 23: lands 5:25 AM (from the note, since End Time is blank), next flight 7:55 AM
        assertEquals(150L, found[0].minutes); assertEquals(LayoverLevel.OK, found[0].level)
        assertEquals("2h 30m", found[0].durationText())
        // Mar 20-21: lands 9:20 PM, leaves 7:45 PM next day, with the hotel in between
        assertEquals(1345L, found[1].minutes); assertEquals(LayoverLevel.STAY, found[1].level); assertTrue(found[1].hotel)
    }

    @Test fun rowsPutTheLayoverJustBeforeTheNextFlight() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-20", "15:30", "20:00")
        val rows = buildRows(listOf(a, b), Layovers.find(listOf(a, b)))
        assertEquals(listOf("Header", "Entry", "Connection", "Entry"), rows.map { it::class.simpleName })
        // a layover is only shown when both flights are on the screen
        assertTrue(buildRows(listOf(b), Layovers.find(listOf(a, b))).none { it is Row.Connection })
    }

    @Test fun collapsedDayKeepsItsHeaderButDropsItsEntriesAndConnections() {
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-20", "15:30", "20:00")
        val c = flight("c", "BR3 - NRT > MNL", "2027-02-21", "08:00", "11:00")
        val layovers = Layovers.find(listOf(a, b))
        val open = buildRows(listOf(a, b, c), layovers)
        assertEquals(listOf("Header", "Entry", "Connection", "Entry", "Header", "Entry"), open.map { it::class.simpleName })

        // Collapsing Feb 20 keeps its Header (so the day is still visible) but drops its entries and the layover.
        val collapsed = buildRows(listOf(a, b, c), layovers, setOf(LocalDate.parse("2027-02-20")))
        assertEquals(listOf("Header", "Header", "Entry"), collapsed.map { it::class.simpleName })
        assertEquals(LocalDate.parse("2027-02-20"), (collapsed[0] as Row.Header).date)
        assertEquals(LocalDate.parse("2027-02-21"), (collapsed[1] as Row.Header).date)
        assertEquals("c", (collapsed[2] as Row.Entry).item.id)

        // Collapsing every day leaves only the day headers.
        val allCollapsed = buildRows(listOf(a, b, c), layovers, setOf(LocalDate.parse("2027-02-20"), LocalDate.parse("2027-02-21")))
        assertEquals(listOf("Header", "Header"), allCollapsed.map { it::class.simpleName })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LayoverRowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<ComponentActivity>? = null
    @org.junit.After fun closeActivity() { scenario?.close(); scenario = null }

    @Test fun cardShowsTheAirportTimeAndWarningAndIsTappable() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(ctx.packageManager).addActivityIfNotPresent(ComponentName(ctx, ComponentActivity::class.java))
        val a = flight("a", "BR1 - DFW > TPE", "2027-02-20", "10:00", "13:15", "BR49")
        val b = flight("b", "BR2 - TPE > NRT", "2027-02-20", "14:00", "18:00", "BR184")
        val l = Layovers.find(listOf(a, b)).single()
        var tapped = false
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { act ->
            act.setContent { com.kl.travel.ui.KLTheme { LayoverRow(l) { tapped = true } } }
        }
        rule.onNodeWithText("Layover in TPE: 45m").assertIsDisplayed()
        rule.onNodeWithText("Short connection", substring = true).assertIsDisplayed()
        rule.onNodeWithText("BR184 leaves 2:00 PM", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Tap for airport lounges").performClick()
        assertTrue(tapped)
    }

    @Test fun stopoverAndMissingArrivalCardsSayWhatTheyMean() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(ctx.packageManager).addActivityIfNotPresent(ComponentName(ctx, ComponentActivity::class.java))
        val a = flight("a", "BR278 - MNL > TPE", "2027-03-20", "18:50", "21:20", "BR278")
        val b = flight("b", "BR50 - TPE > DFW", "2027-03-21", "19:45", "20:15", "BR50")
        val stay = Layovers.find(listOf(a, b)).single()
        val a2 = flight("a2", "BR49 - DFW > TPE", "2027-02-21", "23:05", null, "BR49")
        val b2 = flight("b2", "BR184 - TPE > NRT", "2027-02-23", "07:55", "12:00", "BR184")
        val unknown = Layovers.find(listOf(a2, b2)).single()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { act -> act.setContent { com.kl.travel.ui.KLTheme { androidx.compose.foundation.layout.Column {
            LayoverRow(stay) {}; LayoverRow(unknown) {} } } } }
        rule.onNodeWithText("Stopover in TPE: 22h 25m").assertIsDisplayed()
        rule.onNodeWithText("Connection at TPE: arrival time missing").assertIsDisplayed()
        rule.onNodeWithText("Add the arrival time for BR49", substring = true).assertIsDisplayed()
    }
}
