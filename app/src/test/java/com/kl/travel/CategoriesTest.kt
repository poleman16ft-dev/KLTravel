package com.kl.travel

import com.kl.travel.data.Categories
import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CategoriesTest {
    private fun item(type: String, kind: ItemKind = ItemKind.EVENT, flight: String = "") = TripItem(
        id = "x", kind = kind, type = type, title = "t", location = "", address = "", date = LocalDate.of(2027, 3, 1),
        start = null, end = null, flight = flight, confirmation = "", phone = "", cost = null, transport = "", notes = "",
    )

    @Test fun transportTypes() {
        assertTrue(Categories.isTransport(item("Flight", ItemKind.FLIGHT, "BR49")))
        for (t in listOf("Transport", "Boat", "Ferry", "Train", "Bus", "Drive", "Taxi", "Rideshare", "Van transfer", "Car rental"))
            assertTrue(t, Categories.isTransport(item(t)))
        assertFalse(Categories.isTransport(item("Activity")))
        assertFalse(Categories.isTransport(item("Meal")))
        assertFalse(Categories.isTransport(item("Carnival")))
    }

    @Test fun activityTypes() {
        for (t in listOf("Activity", "Meal", "Wedding", "Tour", "Meeting", "Event", "")) assertTrue(t, Categories.isActivity(item(t)))
        for (t in listOf("Hotel", "Lodging", "Transport", "Flight")) assertFalse(t, Categories.isActivity(item(t)))
        assertFalse(Categories.isActivity(item("Lodging", ItemKind.HOTEL_IN)))
        assertFalse(Categories.isActivity(item("Flight", ItemKind.FLIGHT, "BR49")))
    }

    @Test fun eachItemLandsInAtMostOneTab() {
        for (t in listOf("Flight", "Activity", "Meal", "Transport", "Wedding", "Hotel", "Boat", ""))
            assertFalse(t, Categories.isTransport(item(t)) && Categories.isActivity(item(t)))
    }
}
