package com.kl.travel.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val date: String,            // ISO yyyy-MM-dd
    val startTime: String?,      // ISO HH:mm or null
    val endTime: String?,
    val type: String,
    val title: String,
    val location: String,
    val address: String,
    val flight: String,
    val confirmation: String,
    val cost: Double?,
    val transport: String,
    val notes: String,
    @ColumnInfo(defaultValue = "") val seat: String = "",
    @ColumnInfo(defaultValue = "") val aircraft: String = "",
    @ColumnInfo(defaultValue = "") val cabin: String = "",
    @ColumnInfo(defaultValue = "1") val tripId: Long = 1L,
)

@Entity(tableName = "hotels")
data class HotelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val checkInDate: String,
    val checkInTime: String?,
    val checkOutDate: String,
    val checkOutTime: String?,
    val confirmation: String,
    val phone: String,
    val cost: Double?,
    val notes: String,
    @ColumnInfo(defaultValue = "1") val tripId: Long = 1L,
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val category: String,
    val amount: Double,
    val note: String,
    @ColumnInfo(defaultValue = "1") val tripId: Long = 1L,
    /** Receipt photo kept inside the app, or "". */
    @ColumnInfo(defaultValue = "") val receiptPath: String = "",
    /** What the receipt was actually in (amount above is always USD). */
    @ColumnInfo(defaultValue = "'USD'") val currency: String = "USD",
    /** Amount in [currency]; 0 when the expense was typed in as USD. */
    @ColumnInfo(defaultValue = "0") val originalAmount: Double = 0.0,
)

enum class ItemKind { EVENT, FLIGHT, HOTEL_IN, HOTEL_OUT }

/** One row on the timeline: a sheet event, or a hotel check-in / check-out. */
data class TripItem(
    val id: String,
    val kind: ItemKind,
    val type: String,
    val title: String,
    val location: String,
    val address: String,
    val date: LocalDate,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val flight: String,
    val confirmation: String,
    val phone: String,
    val cost: Double?,
    val transport: String,
    val notes: String,
    val seat: String = "",
    val aircraft: String = "",
    val cabin: String = "",
) {
    val sortKey: LocalDateTime get() = start ?: date.atTime(LocalTime.MIN)
    val isFlight get() = kind == ItemKind.FLIGHT
    val needsTravel get() = kind != ItemKind.HOTEL_OUT && address.isNotBlank()
}

object TripBuilder {
    private fun mentions(events: List<EventEntity>, vararg words: String): Set<String> =
        events.filter { e -> words.any { e.title.contains(it, ignoreCase = true) } }.map { it.date }.toSet()

    fun build(events: List<EventEntity>, hotels: List<HotelEntity>): List<TripItem> {
        val out = mutableListOf<TripItem>()
        for (e in events) {
            val d = LocalDate.parse(e.date)
            val st = e.startTime?.let { d.atTime(LocalTime.parse(it)) }
            val en = e.endTime?.let { d.atTime(LocalTime.parse(it)) }
            val isFlight = e.type.equals("Flight", true) || e.flight.isNotBlank()
            out += TripItem(
                id = e.id,
                kind = if (isFlight) ItemKind.FLIGHT else ItemKind.EVENT,
                type = if (isFlight) "Flight" else e.type.ifBlank { "Event" },
                title = e.title, location = e.location, address = e.address,
                date = d, start = st, end = en, flight = e.flight,
                confirmation = e.confirmation, phone = "", cost = e.cost,
                transport = e.transport, notes = e.notes,
                seat = e.seat, aircraft = e.aircraft, cabin = e.cabin,
            )
        }
        // Lodging check-in / check-out become timeline items unless an Events row that day already covers them.
        val inDays = mentions(events, "check-in", "check in")
        val outDays = mentions(events, "check-out", "check out")
        for (h in hotels) {
            val inD = LocalDate.parse(h.checkInDate)
            val outD = LocalDate.parse(h.checkOutDate)
            if (h.checkInDate !in inDays) out += TripItem(
                id = h.id + "_in", kind = ItemKind.HOTEL_IN, type = "Lodging",
                title = "Check in: ${h.name}", location = h.name, address = h.address,
                date = inD, start = inD.atTime(LocalTime.parse(h.checkInTime ?: "15:00")), end = null,
                flight = "", confirmation = h.confirmation, phone = h.phone, cost = null,
                transport = "", notes = h.notes,
            )
            if (h.checkOutDate !in outDays) out += TripItem(
                id = h.id + "_out", kind = ItemKind.HOTEL_OUT, type = "Lodging",
                title = "Check out: ${h.name}", location = h.name, address = h.address,
                date = outD, start = outD.atTime(LocalTime.parse(h.checkOutTime ?: "12:00")), end = null,
                flight = "", confirmation = h.confirmation, phone = h.phone, cost = null,
                transport = "", notes = h.notes,
            )
        }
        return out.sortedBy { it.sortKey }
    }
}

/** Which tab a timeline item belongs on: Transportation (flights, boats, drives, trains...) or Activities. */
object Categories {
    private val TRANSPORT = setOf(
        "flight", "transport", "transportation", "travel", "boat", "ferry", "train", "bus", "drive", "driving", "car", "taxi",
        "ride", "rideshare", "grab", "uber", "shuttle", "transfer", "van", "tricycle", "cruise", "metro", "subway", "rail", "jeepney",
    )
    private val NOT_ACTIVITY = setOf("hotel", "lodging", "stay")

    private fun words(type: String): List<String> = type.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }

    fun isTransport(i: TripItem): Boolean =
        i.kind == ItemKind.FLIGHT || (i.kind == ItemKind.EVENT && words(i.type).any { it in TRANSPORT })

    /** Sheet events that are neither transport nor lodging: activities, meals, tours, wedding events, meetings, etc. */
    fun isActivity(i: TripItem): Boolean =
        i.kind == ItemKind.EVENT && !isTransport(i) && words(i.type).none { it in NOT_ACTIVITY }
}
