package com.kl.travel

import com.kl.travel.data.*
import com.kl.travel.net.TravelMode
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SheetParserTest {
    private fun res(name: String) = Csv.parse(javaClass.classLoader!!.getResource(name)!!.readText())

    private fun check(events: String, hotels: String) {
        val warn = mutableListOf<String>()
        val ev = SheetParser.parseEvents(res(events), warn)
        val ht = SheetParser.parseHotels(res(hotels), warn)
        assertEquals(emptyList<String>(), warn)
        assertEquals(108, ev.size)
        assertEquals(8, ht.size)
        assertEquals(3711.60, ev.sumOf { it.cost ?: 0.0 }, 0.01)
        assertEquals(200.12, ht.sumOf { it.cost ?: 0.0 }, 0.01)
        assertEquals(ev.size, ev.map { it.id }.toSet().size)               // unique ids
        // flights
        val flights = ev.filter { it.flight.isNotBlank() }
        assertEquals(listOf("PR2678", "DG6887", "PR2359", "PR2044"), flights.map { it.flight })
        assertEquals("OJHWVM", flights[1].confirmation)
        assertEquals("15:20", flights[0].endTime)
        assertEquals("14:15", flights[0].startTime)
        // first event and first hotel
        assertEquals("2027-02-25", ev[0].date); assertEquals("14:00", ev[0].startTime)
        assertEquals("2027-03-01", ht[0].checkOutDate); assertEquals("12:00", ht[0].checkOutTime)
        assertNull(ht[2].checkInTime)                                        // El Nido check-in time not in sheet
        assertNull(ht[2].cost)

        // timeline: hotel check-in/out items are suppressed when an Events row that day already says so
        val items = TripBuilder.build(ev, ht)
        assertEquals(items.sortedBy { it.sortKey }, items)
        val autoIn = items.filter { it.kind == ItemKind.HOTEL_IN }.map { it.location }
        val autoOut = items.filter { it.kind == ItemKind.HOTEL_OUT }.map { it.location }
        assertEquals(listOf("Sweet Home Boutique", "Holiday Inn Cebu City", "AC Hotel by Marriott Manila"), autoIn)
        assertEquals(emptyList<String>(), autoOut)
        assertEquals(108 + 3, items.size)
    }

    @Test fun parsesIsoStyleSheet() = check("events_iso.csv", "hotels_iso.csv")
    @Test fun parsesUsStyleSheet() = check("events_us.csv", "hotels_us.csv")

    @Test fun dateAndTimeFormats() {
        assertEquals(LocalDate.of(2027, 2, 25), SheetParser.parseDate("Feb 25, 2027"))
        assertEquals(LocalDate.of(2027, 2, 25), SheetParser.parseDate("2/25/2027"))
        assertEquals(LocalDate.of(2027, 2, 25), SheetParser.parseDate("2027-02-25"))
        assertNull(SheetParser.parseDate("soon"))
        assertEquals(LocalTime.of(14, 0), SheetParser.parseTime("2:00 PM"))
        assertEquals(LocalTime.of(14, 0), SheetParser.parseTime("2:00 pm"))
        assertEquals(LocalTime.of(14, 0), SheetParser.parseTime("14:00:00"))
        assertEquals(LocalTime.of(9, 0), SheetParser.parseTime("9 AM"))
        assertEquals(LocalTime.of(0, 5), SheetParser.parseTime("12:05 AM"))
        assertNull(SheetParser.parseTime(""))
    }

    @Test fun csvQuotesAndNewlines() {
        val rows = Csv.parse("a,b\r\n\"x, y\",\"he said \"\"hi\"\"\nline2\"\r\n")
        assertEquals(listOf("x, y", "he said \"hi\"\nline2"), rows[1])
    }

    @Test fun transportHints() {
        assertEquals(TravelMode.RIDESHARE, TravelMode.hint("Grab/taxi"))
        assertEquals(TravelMode.WALK, TravelMode.hint("Walk"))
        assertEquals(TravelMode.DRIVE, TravelMode.hint("Van"))
        assertNull(TravelMode.hint("Boat"))
        assertNull(TravelMode.hint(""))
    }

    @Test fun polylineDecode() {
        // Google's documented example: _p~iF~ps|U_ulLnnqC_mqNvxq`@
        val pts = com.kl.travel.net.RoutesClient.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].latitude, 1e-5); assertEquals(-120.2, pts[0].longitude, 1e-5)
        assertEquals(43.252, pts[2].latitude, 1e-5); assertEquals(-126.453, pts[2].longitude, 1e-5)
    }

    /** The sheet after the trip emails were merged in: real confirmations, international legs, Tokyo and Taipei hotels. */
    @Test fun parsesSheetWithEmailDetails() {
        val warn = mutableListOf<String>()
        val ev = SheetParser.parseEvents(res("events_v2.csv"), warn)
        val ht = SheetParser.parseHotels(res("hotels_v2.csv"), warn)
        assertEquals(emptyList<String>(), warn)
        assertEquals(113, ev.size)
        assertEquals(10, ht.size)
        val flights = ev.filter { it.flight.isNotBlank() }.associate { it.flight to it.confirmation }
        assertEquals("Y8P6AV", flights["PR2678"]); assertEquals("PLBJ3D", flights["DG6559"])
        assertEquals("OJHWVM", flights["DG6887"]); assertEquals("Y9MIKV", flights["PR2359"])
        assertEquals("Y9WH9F", flights["PR2046"]); assertEquals("C2PWL6", flights["PR431"])
        assertNull(flights["PR2044"])                                        // replaced by the real flight number
        assertEquals("BR49", ev.first().flight); assertEquals("2027-02-21", ev.first().date)
        assertEquals("12599SG298237", ht[ht.size - 2].confirmation); assertEquals("K2E7Y1", ht.last().confirmation); assertEquals("+886223115151", ht.last().phone.filter { it.isDigit() || it == '+' })
        assertEquals(981.38, ht.first().cost!!, 0.001)
        val items = TripBuilder.build(ev, ht)
        assertEquals(items.sortedBy { it.sortKey }, items)
        assertTrue(items.any { it.kind == ItemKind.HOTEL_IN && it.location.startsWith("The Strings") })   // Tokyo check-in appears
    }

    @Test fun parsesTabGidsFromEditPage() {
        val html = """x,[21350203,"[1,0,\"759455535\",[{\"1\":[[0,0,\"Hotels\"],[{}]]}]]"],[21350203,"[1,0,\"0\",[{\"1\":[[0,0,\"Events\"],[{}]]}]]"]"""
        assertEquals(mapOf("Hotels" to "759455535", "Events" to "0"), com.kl.travel.data.SheetRepository.parseGids(html))
        assertEquals(emptyMap<String, String>(), com.kl.travel.data.SheetRepository.parseGids("<html>no sheets here</html>"))
    }
}
