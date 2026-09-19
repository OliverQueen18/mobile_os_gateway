package com.osgateway.distributor.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Adresse + carte OpenStreetMap (Leaflet dans WebView).
 * Lat/lng uniquement via carte ou géocodage — pas de champs numériques.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun AddressMapField(
    address: String,
    onAddressChange: (String) -> Unit,
    latitude: Double?,
    longitude: Double?,
    onCoordinatesChange: (lat: Double?, lng: Double?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    autoGeocode: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapHeightPx = with(density) { 280.dp.roundToPx() }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var geoHint by remember { mutableStateOf<String?>(null) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }
    var lastGeocodedQuery by remember { mutableStateOf("") }

    fun pushMarker(lat: Double, lng: Double, fly: Boolean) {
        val wv = webView ?: return
        if (!mapReady) return
        wv.post {
            wv.evaluateJavascript(
                "window.setMarker && window.setMarker($lat,$lng,${if (fly) "true" else "false"});",
                null,
            )
        }
    }

    fun invalidateMap() {
        webView?.post {
            webView?.evaluateJavascript(
                "window.map && window.map.invalidateSize(true);",
                null,
            )
        }
    }

    fun geocodeAddress(query: String, silent: Boolean = false) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            if (!silent) geoHint = null
            val q = query.trim()
            if (q.length < 5) {
                if (!silent) geoHint = "Adresse trop courte"
                return@launch
            }
            if (!silent) geoHint = "Recherche…"
            val hit = withContext(Dispatchers.IO) { nominatimSearch(q) }
            if (hit == null) {
                if (!silent) geoHint = "Aucun résultat"
            } else {
                lastGeocodedQuery = hit.displayName.trim()
                onAddressChange(hit.displayName)
                onCoordinatesChange(hit.lat, hit.lng)
                pushMarker(hit.lat, hit.lng, true)
                geoHint = "Position trouvée"
            }
        }
    }

    LaunchedEffect(latitude, longitude, mapReady) {
        val lat = latitude
        val lng = longitude
        if (lat != null && lng != null && mapReady) {
            pushMarker(lat, lng, true)
            delay(80)
            invalidateMap()
        }
    }

    LaunchedEffect(address, autoGeocode, enabled) {
        if (!autoGeocode || !enabled) return@LaunchedEffect
        val q = address.trim()
        if (q.length < 8 || q == lastGeocodedQuery) return@LaunchedEffect
        delay(900)
        val current = address.trim()
        if (current.length < 8 || current != q || current == lastGeocodedQuery) return@LaunchedEffect
        geocodeAddress(current, silent = true)
    }

    DisposableEffect(Unit) {
        onDispose {
            geocodeJob?.cancel()
            webView?.stopLoading()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OsTextField(
            value = address,
            onValueChange = onAddressChange,
            label = "Adresse du point de vente",
            enabled = enabled,
            singleLine = false,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (enabled) {
            OsSecondaryButton("Localiser sur la carte") {
                geocodeAddress(address, silent = false)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            "Touchez la carte pour placer le point de vente.",
            style = MaterialTheme.typography.bodySmall,
            color = OsMuted,
        )
        if (latitude != null && longitude != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Position GPS enregistrée",
                style = MaterialTheme.typography.labelMedium,
                color = OsNavy,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, OsBorder, RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFE8EEF3)),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            mapHeightPx,
                        )
                        setBackgroundColor(Color.parseColor("#E8EEF3"))
                        setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                        isFocusable = true
                        isFocusableInTouchMode = true

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowContentAccess = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = false
                        }

                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE,
                                -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL,
                                -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.postDelayed({
                                    view.evaluateJavascript(
                                        """
                                        (function(){
                                          if (window.map) {
                                            window.map.invalidateSize(true);
                                          } else if (window.__mapInitError) {
                                            window.AndroidBridge && AndroidBridge.onMapError &&
                                              AndroidBridge.onMapError(String(window.__mapInitError));
                                          }
                                        })();
                                        """.trimIndent(),
                                        null,
                                    )
                                }, 200)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    mapError = "Impossible de charger la carte"
                                }
                            }
                        }

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onMapReady() {
                                    post {
                                        mapReady = true
                                        mapError = null
                                        invalidateMap()
                                        val lat = latitude
                                        val lng = longitude
                                        if (lat != null && lng != null) {
                                            pushMarker(lat, lng, true)
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onMapError(message: String?) {
                                    post {
                                        mapError = message?.ifBlank { null } ?: "Carte indisponible"
                                    }
                                }

                                @JavascriptInterface
                                fun onMapClick(lat: Double, lng: Double) {
                                    if (!enabled) return
                                    post {
                                        onCoordinatesChange(lat, lng)
                                        scope.launch {
                                            val name = withContext(Dispatchers.IO) {
                                                nominatimReverse(lat, lng)
                                            }
                                            if (!name.isNullOrBlank()) {
                                                lastGeocodedQuery = name.trim()
                                                onAddressChange(name)
                                            }
                                        }
                                    }
                                }
                            },
                            "AndroidBridge",
                        )

                        loadDataWithBaseURL(
                            "https://cdn.jsdelivr.net/",
                            MAP_HTML,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    webView = view
                    if (view.height > 0) {
                        view.post { invalidateMap() }
                    }
                },
            )

            if (!mapReady && mapError == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = OsNavy,
                )
            }
            mapError?.let { err ->
                Text(
                    err,
                    color = OsNavy,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
        }

        geoHint?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(it, color = OsNavy, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class NominatimHit(val lat: Double, val lng: Double, val displayName: String)

private fun nominatimSearch(query: String): NominatimHit? {
    val q = URLEncoder.encode(query, "UTF-8")
    val url = URL("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=1")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "OSGatewayDistributor/1.0")
        connectTimeout = 12_000
        readTimeout = 12_000
    }
    return try {
        conn.inputStream.bufferedReader().use { reader ->
            val arr = JSONArray(reader.readText())
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            NominatimHit(
                lat = o.getString("lat").toDouble(),
                lng = o.getString("lon").toDouble(),
                displayName = o.optString("display_name", query),
            )
        }
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

private fun nominatimReverse(lat: Double, lng: Double): String? {
    val url = URL(
        "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json",
    )
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "OSGatewayDistributor/1.0")
        connectTimeout = 12_000
        readTimeout = 12_000
    }
    return try {
        conn.inputStream.bufferedReader().use { reader ->
            JSONObject(reader.readText()).optString("display_name").ifBlank { null }
        }
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

private const val MAP_HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css"/>
  <style>
    html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:#e8eef3; }
    #map { position:absolute; inset:0; width:100%; height:100%; min-height:280px; }
  </style>
</head>
<body>
<div id="map"></div>
<script>
  window.__mapInitError = null;
  function bootMap() {
    try {
      if (typeof L === 'undefined') {
        window.__mapInitError = 'Leaflet non chargé';
        if (window.AndroidBridge && AndroidBridge.onMapError) AndroidBridge.onMapError(window.__mapInitError);
        return;
      }
      delete L.Icon.Default.prototype._getIconUrl;
      L.Icon.Default.mergeOptions({
        iconRetinaUrl: 'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/images/marker-icon-2x.png',
        iconUrl: 'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/images/marker-icon.png',
        shadowUrl: 'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/images/marker-shadow.png'
      });
      window.map = L.map('map', { zoomControl: true }).setView([12.6392, -8.0029], 12);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap'
      }).addTo(window.map);
      var marker = null;
      window.setMarker = function(lat, lng, fly) {
        if (marker) { marker.setLatLng([lat, lng]); }
        else { marker = L.marker([lat, lng]).addTo(window.map); }
        if (fly) window.map.setView([lat, lng], Math.max(window.map.getZoom(), 14));
        setTimeout(function(){ window.map.invalidateSize(true); }, 0);
      };
      window.map.on('click', function(e) {
        window.setMarker(e.latlng.lat, e.latlng.lng, false);
        if (window.AndroidBridge && AndroidBridge.onMapClick) {
          AndroidBridge.onMapClick(e.latlng.lat, e.latlng.lng);
        }
      });
      setTimeout(function(){ window.map.invalidateSize(true); }, 50);
      setTimeout(function(){ window.map.invalidateSize(true); }, 250);
      setTimeout(function(){ window.map.invalidateSize(true); }, 600);
      if (window.AndroidBridge && AndroidBridge.onMapReady) AndroidBridge.onMapReady();
    } catch (e) {
      window.__mapInitError = e && e.message ? e.message : 'Erreur carte';
      if (window.AndroidBridge && AndroidBridge.onMapError) AndroidBridge.onMapError(window.__mapInitError);
    }
  }
</script>
<script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js" onload="bootMap()" onerror="window.__mapInitError='Script Leaflet'; if(window.AndroidBridge&&AndroidBridge.onMapError)AndroidBridge.onMapError(window.__mapInitError);"></script>
</body>
</html>
"""
