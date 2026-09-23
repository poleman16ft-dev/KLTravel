package com.kl.travel

import com.kl.travel.data.ReceiptParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    @Test fun amounts() {
        assertEquals(1250.0, ReceiptParser.toAmount("1,250.00")!!, 0.0)
        assertEquals(1250.5, ReceiptParser.toAmount("1.250,50")!!, 0.0)
        assertEquals(3480.0, ReceiptParser.toAmount("3,480")!!, 0.0)
        assertEquals(12.5, ReceiptParser.toAmount("12.50")!!, 0.0)
        assertEquals(45.0, ReceiptParser.toAmount("45")!!, 0.0)
    }

    @Test fun philippineRestaurant() {
        val r = ReceiptParser.parse("""
            JOLLIBEE ANGELES
            SM CITY CLARK
            TEL 045-123-4567
            03/01/2027 12:41 PM
            1 Chickenjoy 99.00
            1 Peach Mango Pie 45.00
            SUBTOTAL 144.00
            VAT 12% 15.43
            TOTAL ₱ 144.00
            CASH 200.00
            CHANGE 56.00
        """.trimIndent())
        assertEquals("Jollibee Angeles", r.merchant)
        assertEquals(144.0, r.total!!, 0.0)
        assertEquals("PHP", r.currency)
        assertEquals("Food", r.category)
        assertEquals(LocalDate.of(2027, 1, 3), r.date)   // dd/mm for non-USD receipts
    }

    @Test fun usdReceiptMonthFirst() {
        val r = ReceiptParser.parse("Blue Bottle Coffee\n3/5/2027\nLatte 5.50\nSubtotal 5.50\nTax 0.45\nTotal Due\n$5.95\nVisa 5.95")
        assertEquals(5.95, r.total!!, 0.0)
        assertEquals("USD", r.currency)
        assertEquals(LocalDate.of(2027, 3, 5), r.date)
        assertEquals("Food", r.category)
    }

    @Test fun japaneseYenNoDecimals() {
        val r = ReceiptParser.parse("ファミリーマート\nFamilyMart Shinjuku\n2027-02-26\nTOTAL ¥3,480\n")
        assertEquals(3480.0, r.total!!, 0.0)
        assertEquals("JPY", r.currency)
        assertEquals(LocalDate.of(2027, 2, 26), r.date)
    }

    @Test fun namedMonthDatesAndHotel() {
        val r = ReceiptParser.parse("VALENTINE HOTEL\nAngeles City\nCheck-out 28 Feb 2027\nRoom charge 2,100.00\nGrand Total PHP 2,352.00")
        assertEquals("Valentine Hotel", r.merchant)
        assertEquals(LocalDate.of(2027, 2, 28), r.date)
        assertEquals(2352.0, r.total!!, 0.0)
        assertEquals("Lodging", r.category)
        assertEquals("PHP", r.currency)
        assertEquals(LocalDate.of(2027, 3, 5), ReceiptParser.parse("March 5, 2027").date)
    }

    @Test fun unreadableGivesEmptyResult() {
        val r = ReceiptParser.parse("")
        assertEquals("", r.merchant); assertNull(r.total); assertNull(r.date); assertEquals("", r.currency); assertEquals("Other", r.category)
    }
}
