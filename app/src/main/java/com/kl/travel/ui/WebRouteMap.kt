package com.kl.travel.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * Route map that uses the Maps JavaScript API with a key supplied at run time (Settings),
 * because the native Maps SDK can only read its key from the manifest at build time.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebRouteMap(key: String, path: List<LatLng>, start: LatLng?, end: LatLng?) {
    val html = routeHtml(key, path, start, end)
    if (!rememberMapLoadAllowed(html)) { MapLimitNote(); return }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)),
        factory = { c -> WebView(c).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true } },
        update = { w ->
            if (w.tag != html) {
                w.tag = html
                w.loadDataWithBaseURL("https://maps.googleapis.com/", html, "text/html", "utf-8", null)
            }
        },
    )
}

private fun num(d: Double) = String.format(Locale.US, "%.5f", d)

/** Builds the page. [key] must already be validated ([com.kl.travel.net.MapsKey.isWellFormed]). */
internal fun routeHtml(key: String, path: List<LatLng>, start: LatLng?, end: LatLng?): String {
    require(Regex("^[A-Za-z0-9_-]+$").matches(key)) { "bad key" }
    val pts = path.joinToString(",", "[", "]") { "[${num(it.latitude)},${num(it.longitude)}]" }
    fun pt(p: LatLng?) = if (p == null) "null" else "[${num(p.latitude)},${num(p.longitude)}]"
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body,#m{height:100%;margin:0;font-family:sans-serif}#e{padding:16px;color:#b3261e}</style></head>
<body><div id="m"></div><script>
window.gm_authFailure=function(){document.body.innerHTML='<div id="e">Map could not load. Check the key, and that Maps JavaScript API is enabled and billing is on.</div>'};
function init(){
 var P=$pts,S=${pt(start)},E=${pt(end)};
 var map=new google.maps.Map(document.getElementById('m'),{center:{lat:20,lng:0},zoom:2,gestureHandling:'cooperative',mapTypeControl:false,streetViewControl:false,fullscreenControl:false});
 function ll(a){return {lat:a[0],lng:a[1]}}
 if(P.length){
  var line=P.map(ll);
  new google.maps.Polyline({path:line,strokeColor:'#1a73e8',strokeWeight:5,map:map});
  var b=new google.maps.LatLngBounds(); line.forEach(function(p){b.extend(p)}); map.fitBounds(b,40);
 }
 if(S) new google.maps.Marker({position:ll(S),map:map,title:'Start'});
 if(E) new google.maps.Marker({position:ll(E),map:map,title:'Destination'});
}
</script><script async src="https://maps.googleapis.com/maps/api/js?key=$key&callback=init"></script></body></html>"""
}

/** Each new map page is one billed "map load". Counts it once per page and says no when the monthly limit is used up. */
@Composable
internal fun rememberMapLoadAllowed(html: String): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(html) { com.kl.travel.data.GoogleUsage.tryUse(com.kl.travel.data.Prefs(ctx), com.kl.travel.data.GoogleApi.MAPS) }
}

@Composable
internal fun MapLimitNote() {
    androidx.compose.material3.Text(
        "Map hidden: this month's map-load limit is used up. Raise it in Settings > Google usage. Times and directions still work.",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
    )
}
