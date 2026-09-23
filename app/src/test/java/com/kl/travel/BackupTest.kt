package com.kl.travel

import com.kl.travel.data.Backup
import com.kl.travel.data.ExpenseEntity
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTripKeepsWantedSettingsAndExpenses() {
        val all = mapOf<String, Any?>(
            "mapsKey" to "AIzaTEST", "loungePrograms" to "AMEX,CHASE", "flightCap" to 150, "activeTrip" to 2L, "alertsEnabled" to true,
            "trips_v1" to "[{\"id\":1}]", "mode_e1" to "TRANSIT",
            "lounges_DFW" to "big cache", "fcache_e1" to "cache", "fx_USD_PHP" to "56.1",
        )
        val ex = listOf(ExpenseEntity(id = 7, date = "2027-03-01", category = "Food", amount = 12.5, note = "lunch", tripId = 2))
        val text = Backup.encode(all, ex, "1.14.0")
        val b = Backup.decode(text)
        assertEquals("AIzaTEST", b.prefs["mapsKey"]); assertEquals(150, b.prefs["flightCap"]); assertEquals(2L, b.prefs["activeTrip"])
        assertEquals(true, b.prefs["alertsEnabled"]); assertEquals("TRANSIT", b.prefs["mode_e1"])
        assertFalse(b.prefs.containsKey("lounges_DFW")); assertFalse(b.prefs.containsKey("fcache_e1")); assertFalse(b.prefs.containsKey("fx_USD_PHP"))
        assertEquals(1, b.expenses.size); assertEquals(12.5, b.expenses[0].amount, 0.0); assertEquals(2L, b.expenses[0].tripId)
    }

    @Test fun rejectsFilesThatAreNotBackups() {
        for (bad in listOf("", "not json", "{\"app\":\"Other\"}", "{\"app\":\"KLTravel\",\"format\":99}"))
            assertThrows(IllegalArgumentException::class.java) { Backup.decode(bad) }
    }
}
