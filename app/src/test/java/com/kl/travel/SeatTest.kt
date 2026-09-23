package com.kl.travel

import com.kl.travel.data.SheetParser
import com.kl.travel.net.Airlines
import org.junit.Assert.*
import org.junit.Test

class SeatTest {
    @Test fun seatsListEveryTravelerWithName() {
        val two = com.kl.travel.data.Seats.parse("Luis 3C / Sabrina 3D")
        assertEquals(listOf(com.kl.travel.data.SeatEntry("Luis", "3C"), com.kl.travel.data.SeatEntry("Sabrina", "3D")), two)
        assertEquals(listOf("3C", "3D"), com.kl.travel.data.Seats.parse("3C, 3D").map { it.seat })
        assertEquals(com.kl.travel.data.SeatEntry("Sabrina", "12B"), com.kl.travel.data.Seats.parse("Luis: 12a; Sabrina - 12B")[1])
        assertEquals(com.kl.travel.data.SeatEntry("Sabrina", "5F"), com.kl.travel.data.Seats.parse("5F Sabrina").single())
        assertEquals(listOf(com.kl.travel.data.SeatEntry("", "6K")), com.kl.travel.data.Seats.parse("6K"))
        assertEquals(3, com.kl.travel.data.Seats.parse("Luis 1A and Sabrina 1B & Kid 1C").size)
        assertTrue(com.kl.travel.data.Seats.parse("").isEmpty())
        assertEquals("Luis 3C, Sabrina 3D", com.kl.travel.data.Seats.summary("Luis 3C / Sabrina 3D"))
    }

    @Test fun parsesSeatAndAircraftColumns() {
        val rows = listOf(
            listOf("Date", "Title", "Flight #", "Seat", "Aircraft"),
            listOf("2027-02-21", "EVA BR49", "BR49", "Luis 32A, Sabrina 32B", "Boeing 777-300ER"),
            listOf("2027-02-22", "Dinner", "", "", ""),
        )
        val ev = SheetParser.parseEvents(rows, mutableListOf())
        assertEquals("Luis 32A, Sabrina 32B", ev[0].seat); assertEquals("Boeing 777-300ER", ev[0].aircraft)
        assertEquals("", ev[1].seat)
    }

    @Test fun oldSheetsWithoutColumnsStillParse() {
        val ev = SheetParser.parseEvents(listOf(listOf("Date", "Title"), listOf("2027-02-21", "X")), mutableListOf())
        assertEquals("", ev[0].seat); assertEquals("", ev[0].aircraft)
    }

    @Test fun airlineLinks() {
        assertEquals("BR", Airlines.code("BR49")); assertEquals("5J", Airlines.code("5J521")); assertEquals("PR", Airlines.code("PR2678"))
        assertTrue(Airlines.forFlight("BR49").url.contains("evaair.com"))
        assertTrue(Airlines.forFlight("PR431").url.contains("philippineairlines.com"))
        assertTrue(Airlines.forFlight("DG6559").url.contains("cebupacificair.com"))
        assertTrue(Airlines.forFlight("XX12").url.startsWith("https://www.google.com/search"))
    }

    @Test fun seatMapsLinksGoStraightToSeatMaps() {
        val h = "https://seatmaps.com/airlines/"
        assertEquals(h + "br-eva-air/boeing-787-9/", Airlines.seatMapsUrl("BR49", "Boeing 787-9"))
        assertEquals(h + "br-eva-air/boeing-787-10/", Airlines.seatMapsUrl("BR184", "Boeing 787-10"))
        assertEquals(h + "br-eva-air/airbus-a330-300/", Airlines.seatMapsUrl("BR278", "Airbus A330-300"))
        assertEquals(h + "pr-pal/airbus-a321/", Airlines.seatMapsUrl("PR431", "Airbus A321"))
        assertEquals(h + "pr-pal/airbus-a321neo/", Airlines.seatMapsUrl("PR431", "Airbus A321neo"))
        assertEquals(h + "pr-pal/de-havilland-dash-8-q400/", Airlines.seatMapsUrl("PR2678", "De Havilland Dash 8"))
        assertEquals(h + "5j-cebu-pacific-air/airbus-a320/", Airlines.seatMapsUrl("5J521", "Airbus A320-200"))
        assertEquals(h + "dg-cebgo/atr-72-600/", Airlines.seatMapsUrl("DG6559", "ATR 72-600"))
        // unknown plane -> the airline's seatmaps.com page, never a search
        assertEquals(h + "br-eva-air/", Airlines.seatMapsUrl("BR49", ""))
        assertEquals(h + "dg-cebgo/", Airlines.seatMapsUrl("DG6559", ""))
        assertEquals("https://seatmaps.com/", Airlines.seatMapsUrl("XX12", "Boeing 737"))
        for (u in listOf(Airlines.seatMapsUrl("BR49", "Boeing 787-9"), Airlines.seatMapsUrl("DG6559", ""), Airlines.seatMapsUrl("XX1", "")))
            assertFalse(u.contains("google.com") || u.contains("seatguru"))
    }

    @Test fun firstReference() {
        assertEquals("DQZUUS", Airlines.firstReference("DQZUUS (Luis) / C2YF3G (Sabrina)"))
        assertEquals("C2PWL6", Airlines.firstReference("C2PWL6"))
        assertNull(Airlines.firstReference(""))
    }
}

class FlightKeyLinkTest {
    @org.junit.Test
    fun keyButtonFollowsProvider() {
        org.junit.Assert.assertTrue(com.kl.travel.ui.flightKeyUrl("aerodatabox").contains("rapidapi.com"))
        org.junit.Assert.assertTrue(com.kl.travel.ui.flightKeyUrl("aviationstack").contains("aviationstack.com"))
    }
}
