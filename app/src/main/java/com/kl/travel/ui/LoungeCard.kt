package com.kl.travel.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kl.travel.data.LoungeProgram
import com.kl.travel.data.Prefs
import com.kl.travel.net.AirportLounges
import com.kl.travel.net.Lounge
import com.kl.travel.net.LoungeClient
import com.kl.travel.net.MapsKey
import org.json.JSONArray
import org.json.JSONObject

/** Lounges at the airports of a flight: map, list, "your access" badges and walking directions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoungeCard(item: com.kl.travel.data.TripItem, codes: List<String>, addressHint: String, prefs: Prefs) {
    if (codes.isEmpty()) return
    val ctx = LocalContext.current
    var code by remember(codes) { mutableStateOf(codes.first()) }
    var shown by remember(codes) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember(code) { mutableStateOf<AirportLounges?>(LoungeClient.cached(prefs, code)) }
    var showAll by remember { mutableStateOf(false) }   // default: only lounges your memberships match
    val mine = prefs.loungePrograms
    val ownWords = com.kl.travel.net.Airlines.ownLoungeWords(item.flight)
    val key = MapsKey.effective(ctx)

    LaunchedEffect(shown, code) {
        if (!shown || key == null) return@LaunchedEffect
        loading = true; error = null
        LoungeClient.load(ctx, code, if (code == codes.first()) addressHint else "").fold(
            onSuccess = { data = it }, onFailure = { error = it.message ?: "Couldn't load lounges." })
        loading = false
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Airport lounges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (codes.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                codes.forEach { c -> FilterChip(selected = code == c, onClick = { code = c }, label = { Text(c) }) }
            }
            if (key == null) {
                Text("Add your Google Maps key in Settings to find lounges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (!shown) {
                Button(onClick = { shown = true }) { Text("Show lounges at $code") }
                return@Column
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text("$it (Lounges need Places API (New) and Maps JavaScript API on your key.)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val d = data
            if (d != null) {
                val rows = d.lounges.map { it to LoungeProgram.matches(it.name, mine) }
                    .let { r -> if (ownWords.isNotEmpty()) r.sortedByDescending { (l, _) -> ownWords.any { w -> l.name.contains(w, ignoreCase = true) } } else r }
                val filtering = mine.isNotEmpty() && !showAll
                val list = if (filtering) rows.filter { it.second.isNotEmpty() } else rows
                if (mine.isNotEmpty()) {
                    FilterChip(selected = showAll, onClick = { showAll = !showAll }, label = { Text("All lounges") })
                    Text(if (showAll) "Showing all ${rows.size} lounges." else "Showing ${list.size} of ${rows.size}: only the lounges your memberships should get you into.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Showing every lounge. Tick your memberships in Settings > Lounge access to see only the ones you can get into.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showAll || mine.isEmpty()) Text(
                    "Warning: access may not be given at lounges that don't say \"You may get in with\". Check the lounge's rules first.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (d.lounges.isEmpty()) Text("No lounges found near $code on Google Maps.", style = MaterialTheme.typography.bodyMedium)
                else {
                    if (list.isNotEmpty()) LoungeMap(key, d.centerLat, d.centerLng, list.map { it.first })
                    list.forEachIndexed { i, (l, ok) ->
                        LoungeRow(i + 1, l, ok, mine.isEmpty(), ownWords.any { w -> l.name.contains(w, ignoreCase = true) })
                    }
                    if (list.isEmpty()) Text("None of these match the memberships you set. Tap All lounges to see every lounge (access may not be given).", style = MaterialTheme.typography.bodySmall)
                }
                if (prefs.loungeNotes.isNotBlank()) Text("Your notes: ${prefs.loungeNotes}", style = MaterialTheme.typography.bodySmall)
                Text("Positions come from Google Maps and may be approximate inside the terminal. Access rules, guest limits and hours change, so confirm before you go. Set your memberships in Settings > Lounge access.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoungeRow(n: Int, l: Lounge, matches: List<LoungeProgram>, noPrograms: Boolean, yours: Boolean) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$n. ${l.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (l.address.isNotBlank()) Text(l.address, style = MaterialTheme.typography.bodySmall)
        when {
            matches.isNotEmpty() -> Text((if (yours) "Your airline's lounge. " else "") + "You may get in with: " + matches.joinToString { it.label }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            LoungeProgram.isAirlineLounge(l.name) -> Text("Airline lounge: usually business/first class or status.", style = MaterialTheme.typography.labelSmall)
            noPrograms -> Unit
            else -> Text("Not matched to your memberships. Check access.", style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(walkingUrl(l)))) }) { Text("Navigate") }
            l.mapsUri?.let { uri -> OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }) { Text("Details") } }
        }
    }
}

internal fun walkingUrl(l: Lounge): String =
    "https://www.google.com/maps/dir/?api=1&destination=${l.lat},${l.lng}" +
        (if (l.id.isNotBlank()) "&destination_place_id=${Uri.encode(l.id)}" else "") + "&travelmode=walking"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoungeMap(key: String, lat: Double, lng: Double, lounges: List<Lounge>) {
    val html = loungeHtml(key, lat, lng, lounges)
    if (!rememberMapLoadAllowed(html)) { MapLimitNote(); return }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
        factory = { c -> WebView(c).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true } },
        update = { w -> if (w.tag != html) { w.tag = html; w.loadDataWithBaseURL("https://maps.googleapis.com/", html, "text/html", "utf-8", null) } },
    )
}

/** Safe to drop inside a <script> block. */
private fun jsData(json: String) = json.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

internal fun loungeHtml(key: String, lat: Double, lng: Double, lounges: List<Lounge>): String {
    require(Regex("^[A-Za-z0-9_-]+$").matches(key)) { "bad key" }
    val arr = JSONArray().apply { lounges.forEach { put(JSONObject().put("n", it.name).put("a", it.lat).put("o", it.lng)) } }
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body,#m{height:100%;margin:0;font-family:sans-serif}#e{padding:16px;color:#b3261e}</style></head>
<body><div id="m"></div><script>
window.gm_authFailure=function(){document.body.innerHTML='<div id="e">Map could not load. Check the key, and that Maps JavaScript API is enabled and billing is on.</div>'};
function init(){
 var L=${jsData(arr.toString())};
 var map=new google.maps.Map(document.getElementById('m'),{center:{lat:$lat,lng:$lng},zoom:16,gestureHandling:'cooperative',mapTypeControl:false,streetViewControl:false,fullscreenControl:false});
 var b=new google.maps.LatLngBounds(); var iw=new google.maps.InfoWindow();
 L.forEach(function(x,i){
  var p={lat:x.a,lng:x.o}; b.extend(p);
  var m=new google.maps.Marker({position:p,map:map,label:String(i+1),title:x.n});
  m.addListener('click',function(){iw.setContent((i+1)+'. '+x.n.replace(/[<>&]/g,''));iw.open(map,m)});
 });
 if(L.length>1) map.fitBounds(b,40);
}
</script><script async src="https://maps.googleapis.com/maps/api/js?key=$key&callback=init"></script></body></html>"""
}
