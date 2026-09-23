package com.kl.travel

import com.kl.travel.net.HotelPhotoClient
import org.junit.Assert.*
import org.junit.Test

class HotelPhotoTest {
    @Test fun parsesFirstPhotoAndCredit() {
        val json = """{"places":[{"photos":[{"name":"places/ABC/photos/XYZ","widthPx":4000,"authorAttributions":[{"displayName":"Jane D","uri":"x"}]}]}]}"""
        val f = HotelPhotoClient.parseSearch(json)!!
        assertEquals("places/ABC/photos/XYZ", f.photoName); assertEquals("Jane D", f.credit)
    }

    @Test fun skipsPlacesWithoutPhotos() {
        assertNull(HotelPhotoClient.parseSearch("{}"))
        assertNull(HotelPhotoClient.parseSearch("""{"places":[{"photos":[]},{}]}"""))
        val f = HotelPhotoClient.parseSearch("""{"places":[{},{"photos":[{"name":"places/B/photos/2"}]}]}""")!!
        assertEquals("places/B/photos/2", f.photoName); assertNull(f.credit)
    }

    @Test fun photoUriMustBeHttps() {
        assertEquals("https://lh3.googleusercontent.com/a", HotelPhotoClient.parsePhotoUri("""{"photoUri":"https://lh3.googleusercontent.com/a"}"""))
        assertNull(HotelPhotoClient.parsePhotoUri("""{"photoUri":"http://x"}"""))
        assertNull(HotelPhotoClient.parsePhotoUri("{}"))
    }

    @Test fun cacheNameIsStableAndCaseInsensitive() {
        assertEquals(HotelPhotoClient.cacheName("The Strings", " Tokyo "), HotelPhotoClient.cacheName("the strings", "tokyo"))
        assertNotEquals(HotelPhotoClient.cacheName("A", "x"), HotelPhotoClient.cacheName("B", "x"))
    }
}
