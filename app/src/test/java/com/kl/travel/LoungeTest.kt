package com.kl.travel

import com.kl.travel.data.LoungeProgram
import com.kl.travel.net.*
import com.kl.travel.ui.loungeHtml
import org.junit.Assert.*
import org.junit.Test

class LoungeTest {
    @Test fun airportCodesFromRow() {
        assertEquals(listOf("TPE", "NRT"), LoungeClient.airportCodes("Taipei Taoyuan (TPE) T2", "EVA BR184 — TPE → NRT"))
        assertEquals(listOf("DFW", "TPE"), LoungeClient.airportCodes("Dallas/Fort Worth (DFW)", "EVA BR49 — DFW → TPE"))
        assertEquals(listOf("MNL"), LoungeClient.airportCodes("Manila NAIA (MNL)", "no arrow"))
        assertEquals(emptyList<String>(), LoungeClient.airportCodes("", ""))
    }

    @Test fun parsesAndFiltersPlaces() {
        val json = """{"places":[
          {"id":"a","displayName":{"text":"Plaza Premium Lounge"},"formattedAddress":"T2","location":{"latitude":25.078,"longitude":121.233},"googleMapsUri":"https://maps.google.com/?cid=1"},
          {"id":"b","displayName":{"text":"Airport Hotel"},"location":{"latitude":25.079,"longitude":121.234}},
          {"id":"c","displayName":{"text":"Far Away Lounge"},"location":{"latitude":26.5,"longitude":122.5}},
          {"id":"a","displayName":{"text":"Plaza Premium Lounge"},"location":{"latitude":25.078,"longitude":121.233}},
          {"displayName":{"text":"No location"}}]}"""
        val all = LoungeClient.parsePlaces(json)
        assertEquals(4, all.size)
        val kept = LoungeClient.filterLounges(all, 25.077, 121.232)
        assertEquals(listOf("Plaza Premium Lounge"), kept.map { it.name })
        assertEquals("https://maps.google.com/?cid=1", kept[0].mapsUri)
    }

    @Test fun cacheRoundTrip() {
        val a = AirportLounges("TPE", 25.0, 121.0, listOf(Lounge("x", "The Infinity Lounge", "T2", 25.1, 121.1, null)), 123L)
        val b = AirportLounges.fromJson(a.toJson())
        assertEquals(a, b)
    }

    @Test fun accessMatching() {
        val mine = setOf(LoungeProgram.PRIORITY_PASS, LoungeProgram.AIRLINE_STATUS)
        assertEquals(listOf(LoungeProgram.PRIORITY_PASS), LoungeProgram.matches("Plaza Premium Lounge (T1)", mine))
        assertEquals(listOf(LoungeProgram.AIRLINE_STATUS), LoungeProgram.matches("EVA Air Infinity Lounge", mine))
        assertEquals(emptyList<LoungeProgram>(), LoungeProgram.matches("Centurion Lounge", mine))
        assertEquals(listOf(LoungeProgram.AMEX), LoungeProgram.matches("The Centurion Lounge", setOf(LoungeProgram.AMEX)))
        assertEquals(setOf(LoungeProgram.AMEX, LoungeProgram.CHASE), LoungeProgram.decode(LoungeProgram.encode(setOf(LoungeProgram.AMEX, LoungeProgram.CHASE))))
    }

    @Test fun mapHtmlEscapesNames() {
        val h = loungeHtml("AIzaSyA-1234567890_abcdefghijklmnopqrs", 25.0, 121.0, listOf(Lounge("1", "Bad </script><b>\"Lounge\"", "", 25.0, 121.0, null)))
        assertFalse(h.contains("</script><b>"))
        assertTrue(h.contains("key=AIzaSyA-1234567890_abcdefghijklmnopqrs&callback=init"))
    }

    @Test fun businessCabinDetection() {
        assertTrue(LoungeProgram.isBusinessCabin("Business")); assertTrue(LoungeProgram.isBusinessCabin("first class"))
        assertTrue(LoungeProgram.isBusinessCabin("Royal Laurel Class"))
        assertFalse(LoungeProgram.isBusinessCabin("Economy")); assertFalse(LoungeProgram.isBusinessCabin("Premium Economy"))
        assertFalse(LoungeProgram.isBusinessCabin(""))
    }

    @Test fun businessTicketMatchesAirlineLoungesOnly() {
        val biz = setOf(LoungeProgram.BUSINESS_TICKET)
        assertEquals(listOf(LoungeProgram.BUSINESS_TICKET), LoungeProgram.matches("EVA Air Infinity Lounge", biz))
        assertEquals(emptyList<LoungeProgram>(), LoungeProgram.matches("Plaza Premium Lounge", biz))
        assertEquals(listOf("eva", "infinity", "the star"), Airlines.ownLoungeWords("BR49"))
        assertEquals(emptyList<String>(), Airlines.ownLoungeWords("XX1"))
    }

    @Test fun businessTicketIsNeverSavedAndOldAirlineChipMigrates() {
        assertEquals("AMEX", LoungeProgram.encode(setOf(LoungeProgram.AMEX, LoungeProgram.BUSINESS_TICKET)))
        assertEquals(setOf(LoungeProgram.AIRLINE_STATUS), LoungeProgram.decode("AIRLINE"))
        assertEquals(emptySet<LoungeProgram>(), LoungeProgram.decode("BUSINESS_TICKET"))
    }

    @Test fun cabinColumnParses() {
        val ev = com.kl.travel.data.SheetParser.parseEvents(listOf(listOf("Date", "Title", "Class"), listOf("2027-02-21", "BR49", "Business")), mutableListOf())
        assertEquals("Business", ev[0].cabin)
    }
}
