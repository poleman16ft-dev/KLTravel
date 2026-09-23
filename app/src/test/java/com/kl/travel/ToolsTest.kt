package com.kl.travel

import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import com.kl.travel.net.DayWeather
import com.kl.travel.net.WeatherClient
import com.kl.travel.ui.convertAmount
import com.kl.travel.widget.NextUpWidget
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ToolsTest {
    private fun item(date: String, time: String?, kind: ItemKind = ItemKind.EVENT, loc: String = "", title: String = "T") = TripItem(
        id = "$date$time$title", kind = kind, type = "Event", title = title, location = loc, address = "",
        date = LocalDate.parse(date), start = time?.let { LocalDate.parse(date).atTime(java.time.LocalTime.parse(it)) }, end = null,
        flight = "", confirmation = "", phone = "", cost = null, transport = "", notes = "",
    )

    @Test fun convertsAmounts() {
        assertEquals(5612.0, convertAmount("100", 56.12)!!, 1e-9)
        assertEquals(1120.0, convertAmount("1,000", 1.12)!!, 1e-9)
        assertNull(convertAmount("abc", 2.0))
        assertNull(convertAmount("10", null))
    }

    @Test fun describesWeatherCodes() {
        assertEquals("Clear", WeatherClient.describe(0))
        assertEquals("Rain showers", WeatherClient.describe(81))
        assertEquals("Thunderstorm", WeatherClient.describe(95))
        assertEquals("Mixed", WeatherClient.describe(-1))
    }

    @Test fun planDaysPicksMostCommonPlaceAndSkipsFlightOnlyDays() {
        val items = listOf(
            item("2027-03-01", "09:00", loc = "Angeles City"),
            item("2027-03-01", "10:00", loc = "Coron"),
            item("2027-03-01", "11:00", loc = "Coron"),
            item("2027-03-02", null),                                   // nothing located: borrows Coron
            item("2027-03-03", "18:00", ItemKind.FLIGHT),               // flight-only day: no guess
            item("2027-03-04", "08:00", loc = "El Nido"),
        )
        val plan = WeatherClient.planDays(items)
        assertEquals(listOf("2027-03-01" to "Coron", "2027-03-02" to "Coron", "2027-03-04" to "El Nido"),
            plan.map { it.first.toString() to it.second })
    }

    @Test fun weatherJsonRoundTrips() {
        val w = DayWeather(LocalDate.parse("2027-03-05"), "El Nido", 32.0, 26.0, 61, 70, null, "forecast")
        val back = DayWeather.fromJson(w.toJson())
        assertEquals(w, back)
        assertEquals("90°/79°F · rain 70%", w.short())
    }

    @Test fun widgetLinesShowLeaveByThenFlightStatus() {
        val now = LocalDateTime.parse("2027-03-09T10:00:00")
        val ev = item("2027-03-09", "13:35", loc = "El Nido Airport", title = "Cebgo DG6887")
        val withLeave = NextUpWidget.lines(ev, now, LocalDateTime.parse("2027-03-09T11:10:00"), 25, "On time")
        assertEquals("Cebgo DG6887", withLeave.title)
        assertTrue(withLeave.whenLine.startsWith("in 3h 35m"))
        assertEquals("Leave by 11:10 AM · 25 min", withLeave.extra)
        assertEquals("On time", NextUpWidget.lines(ev, now, null, -1, "On time").extra)
        assertEquals("Nothing coming up", NextUpWidget.lines(null, now, null, -1, null).title)
    }
}
