package com.kl.travel

import com.kl.travel.data.Csv
import com.kl.travel.data.SheetParser
import com.kl.travel.data.SheetRepository
import org.junit.Assert.*
import org.junit.Test

class TemplateTest {
    private fun res(n: String) = Csv.parse(javaClass.classLoader!!.getResource(n)!!.readText())

    @Test fun blankTemplateParsesWithNoWarnings() {
        val w = mutableListOf<String>()
        assertEquals(0, SheetParser.parseEvents(res("template_events.csv"), w).size)
        assertEquals(0, SheetParser.parseHotels(res("template_lodging.csv"), w).size)
        assertEquals(emptyList<String>(), w)
    }

    @Test fun templateHeadersFilledWithExamplesParse() {
        val ev = mutableListOf(res("template_events.csv").first(), emptyList<String>())
        ev[1] = listOf("2027-02-23", "12:10 PM", "4:00 PM", "Flight", "EVA BR184 - TPE > NRT", "Taipei Taoyuan (TPE) T2", "Taiwan Taoyuan International Airport Terminal 2",
            "BR184", "ABC123", "420", "Rideshare", "", "32A", "Boeing 777-300ER", "Business")
        val lo = mutableListOf(res("template_lodging.csv").first(), emptyList<String>())
        lo[1] = listOf("The Strings by InterContinental Tokyo", "2-16-1 Konan, Minato-ku, Tokyo", "2027-02-23", "3:00 PM", "2027-02-25", "12:00 PM", "86582339", "", "981.38", "2 nights")
        val w = mutableListOf<String>()
        val e = SheetParser.parseEvents(ev, w).single(); val h = SheetParser.parseHotels(lo, w).single()
        assertEquals("Business", e.cabin); assertEquals("32A", e.seat); assertEquals("Boeing 777-300ER", e.aircraft); assertEquals("BR184", e.flight)
        assertEquals("The Strings by InterContinental Tokyo", h.name); assertEquals("86582339", h.confirmation)
        assertTrue(w.isEmpty())
    }

    @Test fun lodgingHeaderAndOldHotelHeaderBothWork() {
        val newer = listOf(listOf("Lodging Name", "Check-In Date", "Check-Out Date"), listOf("Inn", "2027-03-01", "2027-03-02"))
        val older = listOf(listOf("Hotel Name", "Check-In Date", "Check-Out Date"), listOf("Inn", "2027-03-01", "2027-03-02"))
        assertEquals("Inn", SheetParser.parseHotels(newer, mutableListOf()).single().name)
        assertEquals("Inn", SheetParser.parseHotels(older, mutableListOf()).single().name)
    }

    @Test fun picksLodgingTabBeforeHotels() {
        assertEquals("Lodging", SheetRepository.pickLodgingTab(mapOf("Events" to "1", "Hotels" to "2", "Lodging" to "3")))
        assertEquals("Hotels", SheetRepository.pickLodgingTab(mapOf("Events" to "1", "Hotels" to "2")))
        assertEquals("lodging", SheetRepository.pickLodgingTab(mapOf("lodging" to "3")))
        assertNull(SheetRepository.pickLodgingTab(mapOf("Events" to "1")))
    }
}
