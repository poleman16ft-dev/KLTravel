package com.kl.travel.net

import android.content.Context
import com.kl.travel.BuildConfig
import com.kl.travel.data.Prefs

/**
 * Which Google Maps key to use. A key typed into Settings wins; otherwise the one baked in
 * at build time (local.properties). The Routes API and the in-app map both use it, so a
 * second person can install the same APK and bring their own key.
 */
object MapsKey {
    private val FORMAT = Regex("^[A-Za-z0-9_-]{20,}$")

    fun isWellFormed(k: String) = FORMAT.matches(k.trim())

    fun buildKey(): String? = BuildConfig.MAPS_API_KEY.takeIf { it.isNotBlank() && !it.startsWith("PASTE") }

    fun userKey(ctx: Context): String? = Prefs(ctx).mapsKey.takeIf { it.isNotBlank() }

    /** The key to use for web requests (Routes API, JS map), or null if none is set. */
    fun effective(ctx: Context): String? = userKey(ctx) ?: buildKey()
}
