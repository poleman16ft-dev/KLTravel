package com.kl.travel

import com.kl.travel.data.Trip
import com.kl.travel.data.TripSpan
import com.kl.travel.data.Trips
import com.kl.travel.ui.spanText
import org.junit.Assert.*
import org.junit.Test

class TripsTest {
    @Test fun jsonRoundTrip() {
        val l = listOf(Trip(1, "Philippines 2027", "https://docs.google.com/spreadsheets/d/abc/edit", 123L), Trip(2, "Japan", "", 0L))
        assertEquals(l, Trips.decode(Trips.encode(l)))
    }

    @Test fun idsNeverCollideAndStartAfterLegacy() {
        assertEquals(1L, Trips.nextId(emptyList()))
        assertEquals(6L, Trips.nextId(listOf(Trip(2, "a", ""), Trip(5, "b", ""))))
    }

    @Test fun legacyTripKeepsOldRowIds() {
        assertEquals("e1a2b", Trips.scopedId(Trips.LEGACY_ID, "e1a2b"))
        assertEquals("t2_e1a2b", Trips.scopedId(2, "e1a2b"))
        assertNotEquals(Trips.scopedId(2, "x"), Trips.scopedId(3, "x"))     // same row content in two trips stays distinct
    }

    @Test fun oldSingleSheetBecomesMyTrip() {
        val t = Trips.legacy(" https://docs.google.com/spreadsheets/d/abc/edit ", 55L).single()
        assertEquals(Trips.LEGACY_ID, t.id); assertEquals("My trip", t.name); assertEquals(55L, t.lastSync)
        assertTrue(Trips.legacy("  ", 0).isEmpty())
    }

    @Test fun namesAreCleaned() {
        val existing = listOf(Trip(1, "A", ""))
        assertEquals("Trip 2", Trips.cleanName("   ", existing))
        assertEquals("Paris", Trips.cleanName(" Paris ", existing))
        assertEquals(40, Trips.cleanName("x".repeat(80), existing).length)
    }

    @Test fun spanText() {
        assertEquals("Feb 21, 2027 to Mar 21, 2027 · 113 events", spanText(TripSpan("2027-02-21", "2027-03-21", 113), true))
        assertEquals("Feb 21, 2027 · 1 event", spanText(TripSpan("2027-02-21", "2027-02-21", 1), true))
        assertEquals("Not synced yet", spanText(TripSpan(null, null, 0), true))
        assertEquals("No sheet link yet", spanText(null, false))
    }
}
