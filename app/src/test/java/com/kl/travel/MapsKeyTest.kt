package com.kl.travel

import com.google.android.gms.maps.model.LatLng
import com.kl.travel.net.MapsKey
import com.kl.travel.ui.routeHtml
import org.junit.Assert.*
import org.junit.Test

class MapsKeyTest {
    @Test fun keyFormat() {
        assertTrue(MapsKey.isWellFormed("AIzaSyA-1234567890_abcdefghijklmnopqrs"))
        assertFalse(MapsKey.isWellFormed("PASTE_YOUR_KEY"))
        assertFalse(MapsKey.isWellFormed("short"))
        assertFalse(MapsKey.isWellFormed("AIza\"><script>alert(1)</script>0123456789"))
    }

    @Test fun routeHtmlEmbedsKeyAndPath() {
        val h = routeHtml("AIzaSyA-1234567890_abcdefghijklmnopqrs", listOf(LatLng(14.5, 121.0), LatLng(14.6, 121.1)), LatLng(14.5, 121.0), null)
        assertTrue(h.contains("key=AIzaSyA-1234567890_abcdefghijklmnopqrs&callback=init"))
        assertTrue(h.contains("[[14.50000,121.00000],[14.60000,121.10000]]"))
        assertTrue(h.contains("E=null"))
    }

    @Test fun routeHtmlRejectsBadKey() {
        assertThrows(IllegalArgumentException::class.java) { routeHtml("a\"b", emptyList(), null, null) }
    }
}
