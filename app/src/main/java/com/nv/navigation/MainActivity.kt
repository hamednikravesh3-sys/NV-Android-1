package com.nv.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val NvDark = Color(0xFF07111D)
private val NvPanel = Color(0xF2111E2C)
private val NvBlue = Color(0xFF00BFFF)
private val NvGreen = Color(0xFF6CFF4A)
private val NvMuted = Color(0xFF93A4B8)

private data class PinnedLocation(val latitude: Double, val longitude: Double) {
    val nvCode: String get() = "NV-${(latitude * 100_000).roundToLong()}-${(longitude * 100_000).roundToLong()}"
    val coordinates: String get() = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    val qrPayload: String get() {
        val point = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
        return "geo:$point?q=$point($nvCode)"
    }
}

private data class VehiclePosition(val point: GeoPoint, val bearing: Float, val speedKmh: Float)
private data class RouteStep(val point: GeoPoint, val type: String, val modifier: String?, val roadName: String, val distance: Double)
private data class RouteData(val points: List<GeoPoint>, val duration: Double, val distance: Double, val steps: List<RouteStep>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { MaterialTheme { NvHomeScreen() } }
    }
}

@Composable
private fun NvHomeScreen() {
    val context = LocalContext.current
    var pinnedLocation by remember { mutableStateOf(loadPinnedLocation(context)) }
    var showQr by remember { mutableStateOf(false) }
    var routeStarted by remember { mutableStateOf(false) }
    var vehicle by remember { mutableStateOf<VehiclePosition?>(null) }
    var routeData by remember { mutableStateOf<RouteData?>(null) }
    var routeRequestKey by remember { mutableIntStateOf(0) }
    var currentStep by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("برای تعیین مقصد، روی نقشه چند ثانیه نگه دارید") }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var lastRerouteAt by remember { mutableStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    TrackLocation(permissionGranted = permissionGranted, onLocation = { vehicle = it })

    LaunchedEffect(routeRequestKey) {
        if (!routeStarted) return@LaunchedEffect
        val start = vehicle?.point ?: return@LaunchedEffect
        val end = pinnedLocation ?: return@LaunchedEffect
        statusText = if (routeData == null) "در حال محاسبه مسیر..." else "در حال محاسبه مسیر جایگزین..."
        routeData = runCatching { fetchRoute(start, GeoPoint(end.latitude, end.longitude)) }.getOrNull()
        currentStep = 0
        statusText = if (routeData == null) "خطا در دریافت مسیر؛ اینترنت را بررسی کنید" else "مسیریابی فعال است"
    }

    LaunchedEffect(vehicle, routeData, routeStarted) {
        if (!routeStarted) return@LaunchedEffect
        val v = vehicle ?: return@LaunchedEffect
        val route = routeData ?: return@LaunchedEffect
        if (route.steps.isNotEmpty()) {
            val idx = currentStep.coerceIn(0, route.steps.lastIndex)
            if (v.point.distanceToAsDouble(route.steps[idx].point) < 35.0 && idx < route.steps.lastIndex) currentStep = idx + 1
        }
        val distanceFromRoute = nearestRouteDistance(v.point, route.points)
        val now = System.currentTimeMillis()
        if (distanceFromRoute > 75.0 && now - lastRerouteAt > 10_000) {
            lastRerouteAt = now
            statusText = "از مسیر خارج شدید؛ مسیر جدید در حال محاسبه است"
            routeRequestKey++
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = NvDark) {
        Box(modifier = Modifier.fillMaxSize()) {
            NvInteractiveMap(
                pinnedLocation = pinnedLocation,
                vehicle = vehicle,
                route = routeData,
                followVehicle = routeStarted,
                onPinSelected = { pin ->
                    pinnedLocation = pin
                    savePinnedLocation(context, pin)
                    showQr = true
                    if (routeStarted && vehicle != null) routeRequestKey++
                }
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(if (permissionGranted) NvGreen else Color.Red, CircleShape))
                                Spacer(Modifier.size(8.dp))
                                Text("NV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                            Text(if (vehicle == null) "GPS..." else "${vehicle!!.speedKmh.roundToInt()} km/h", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Color.White, fontSize = 12.sp)
                        }
                    }

                    if (routeStarted && routeData != null) {
                        val step = routeData!!.steps.getOrNull(currentStep)
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF21A2B3C))) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(turnArrow(step), fontSize = 42.sp, color = NvGreen, modifier = Modifier.padding(end = 14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stepInstruction(step), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(statusText, color = NvMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                            Text(statusText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = NvGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pinnedLocation?.let { pin ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { showQr = true }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("● مقصد سنجاق‌شده", color = NvGreen, fontWeight = FontWeight.Bold)
                                    Text(pin.coordinates, color = NvMuted, fontSize = 12.sp)
                                }
                                Text("QR", color = NvBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    routeData?.let { route ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${(route.distance / 1000.0).let { String.format(Locale.US, "%.1f km", it) }}", color = Color.White)
                                Text("${(route.duration / 60.0).roundToInt()} دقیقه", color = NvGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (pinnedLocation == null) {
                                statusText = "ابتدا مقصد را با لمس طولانی روی نقشه انتخاب کنید"
                            } else if (vehicle == null) {
                                statusText = "در انتظار موقعیت GPS..."
                                if (!permissionGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else {
                                routeStarted = !routeStarted
                                if (routeStarted) routeRequestKey++ else {
                                    routeData = null
                                    currentStep = 0
                                    statusText = "مسیریابی متوقف شد"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NvGreen)
                    ) {
                        Text(if (routeStarted) "توقف مسیریابی" else "شروع مسیریابی", color = NvDark, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            if (showQr && pinnedLocation != null) {
                QrOverlay(pinnedLocation!!, onShare = { sharePinnedLocation(context, pinnedLocation!!) }, onDismiss = { showQr = false })
            }
        }
    }
}

@Composable
private fun TrackLocation(permissionGranted: Boolean, onLocation: (VehiclePosition) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(permissionGranted) {
        if (!permissionGranted) return@DisposableEffect onDispose { }
        val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L).setMinUpdateIntervalMillis(700L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation ?: return
                onLocation(VehiclePosition(GeoPoint(l.latitude, l.longitude), l.bearing, l.speed * 3.6f))
            }
        }
        try { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) } catch (_: SecurityException) { }
        onDispose { client.removeLocationUpdates(callback) }
    }
}

@Composable
private fun NvInteractiveMap(
    pinnedLocation: PinnedLocation?,
    vehicle: VehiclePosition?,
    route: RouteData?,
    followVehicle: Boolean,
    onPinSelected: (PinnedLocation) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    AndroidView(
        modifier = Modifier.fillMaxSize().background(NvDark),
        factory = { context ->
            MapView(context).apply {
                id = View.generateViewId()
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.5
                maxZoomLevel = 20.0
                controller.setZoom(5.5)
                controller.setCenter(GeoPoint(32.4279, 53.6880))
                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean = false
                    override fun longPressHelper(point: GeoPoint?): Boolean {
                        point ?: return false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPinSelected(PinnedLocation(point.latitude, point.longitude))
                        return true
                    }
                }))
            }
        },
        update = { map ->
            updateMap(map, pinnedLocation, vehicle, route)
            if (followVehicle && vehicle != null) {
                map.controller.setZoom(18.0)
                map.controller.animateTo(vehicle.point)
                map.mapOrientation = -vehicle.bearing
            } else if (!followVehicle) {
                map.mapOrientation = 0f
            }
        }
    )
}

private fun updateMap(map: MapView, pin: PinnedLocation?, vehicle: VehiclePosition?, route: RouteData?) {
    map.overlays.removeAll(map.overlays.filter { it is Marker && (it.relatedObject == "nv-pin" || it.relatedObject == "nv-car") }.toSet())
    map.overlays.removeAll(map.overlays.filter { it is Polyline && it.relatedObject == "nv-route" }.toSet())

    route?.let {
        val line = Polyline().apply {
            setPoints(it.points)
            outlinePaint.color = android.graphics.Color.rgb(0, 191, 255)
            outlinePaint.strokeWidth = 14f
            relatedObject = "nv-route"
        }
        map.overlays.add(line)
    }

    pin?.let {
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(it.latitude, it.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = it.nvCode
            snippet = it.coordinates
            relatedObject = "nv-pin"
        })
    }

    vehicle?.let {
        map.overlays.add(Marker(map).apply {
            position = it.point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createCarDrawable(map.context)
            rotation = it.bearing
            title = "خودرو"
            relatedObject = "nv-car"
        })
    }
    map.invalidate()
}

private suspend fun fetchRoute(start: GeoPoint, end: GeoPoint): RouteData = withContext(Dispatchers.IO) {
    val url = URL("https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson&steps=true")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "NV-Android/0.4")
    }
    try {
        if (connection.responseCode !in 200..299) error("routing http ${connection.responseCode}")
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val route = json.getJSONArray("routes").getJSONObject(0)
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = ArrayList<GeoPoint>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            points += GeoPoint(c.getDouble(1), c.getDouble(0))
        }
        val steps = mutableListOf<RouteStep>()
        val legs = route.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val arr = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                steps += RouteStep(
                    point = GeoPoint(loc.getDouble(1), loc.getDouble(0)),
                    type = m.optString("type", "turn"),
                    modifier = m.optString("modifier", "").ifBlank { null },
                    roadName = s.optString("name", ""),
                    distance = s.optDouble("distance", 0.0)
                )
            }
        }
        RouteData(points, route.getDouble("duration"), route.getDouble("distance"), steps)
    } finally { connection.disconnect() }
}

private fun nearestRouteDistance(point: GeoPoint, route: List<GeoPoint>): Double {
    if (route.isEmpty()) return Double.MAX_VALUE
    var best = Double.MAX_VALUE
    val stride = (route.size / 500).coerceAtLeast(1)
    var i = 0
    while (i < route.size) {
        val d = point.distanceToAsDouble(route[i])
        if (d < best) best = d
        i += stride
    }
    return best
}

private fun turnArrow(step: RouteStep?): String = when (step?.modifier) {
    "left", "slight left", "sharp left" -> "↰"
    "right", "slight right", "sharp right" -> "↱"
    "uturn" -> "↶"
    "straight" -> "↑"
    else -> if (step?.type == "arrive") "●" else "↑"
}

private fun stepInstruction(step: RouteStep?): String {
    if (step == null) return "در مسیر بمانید"
    val road = if (step.roadName.isBlank()) "مسیر بعدی" else step.roadName
    return when (step.type) {
        "arrive" -> "به مقصد رسیدید"
        "depart" -> "حرکت را آغاز کنید"
        else -> when (step.modifier) {
            "left", "slight left", "sharp left" -> "به چپ بپیچید • $road"
            "right", "slight right", "sharp right" -> "به راست بپیچید • $road"
            "uturn" -> "دور بزنید • $road"
            else -> "مستقیم ادامه دهید • $road"
        }
    }
}

@Composable
private fun QrOverlay(pinnedLocation: PinnedLocation, onShare: () -> Unit, onDismiss: () -> Unit) {
    val blocker = remember { MutableInteractionSource() }
    val qrImage = remember(pinnedLocation.qrPayload) { createQrBitmap(pinnedLocation.qrPayload).asImageBitmap() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(interactionSource = blocker, indication = null, onClick = {}), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF132334))) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QR مکان NV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(pinnedLocation.nvCode, color = NvGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Image(bitmap = qrImage, contentDescription = "QR مکان سنجاق‌شده", modifier = Modifier.size(232.dp).background(Color.White).padding(10.dp))
                Text(pinnedLocation.coordinates, color = NvMuted, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("بستن", color = Color.White) }
                    Button(onClick = onShare, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NvGreen)) { Text("اشتراک", color = NvDark, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun createCarDrawable(context: Context): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(72, 108, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(108, 255, 74) }
    val window = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(7, 17, 29) }
    val arrow = Path().apply { moveTo(36f, 2f); lineTo(12f, 36f); lineTo(60f, 36f); close() }
    canvas.drawPath(arrow, body)
    canvas.drawRoundRect(12f, 28f, 60f, 102f, 16f, 16f, body)
    canvas.drawRoundRect(21f, 43f, 51f, 68f, 8f, 8f, window)
    return BitmapDrawable(context.resources, bitmap)
}

private fun createQrBitmap(value: String, size: Int = 768): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
}

private fun savePinnedLocation(context: Context, pin: PinnedLocation) {
    context.getSharedPreferences("nv_map", Context.MODE_PRIVATE).edit().putLong("lat", java.lang.Double.doubleToRawLongBits(pin.latitude)).putLong("lon", java.lang.Double.doubleToRawLongBits(pin.longitude)).putBoolean("has_pin", true).apply()
}

private fun loadPinnedLocation(context: Context): PinnedLocation? {
    val p = context.getSharedPreferences("nv_map", Context.MODE_PRIVATE)
    if (!p.getBoolean("has_pin", false)) return null
    return PinnedLocation(java.lang.Double.longBitsToDouble(p.getLong("lat", 0L)), java.lang.Double.longBitsToDouble(p.getLong("lon", 0L)))
}

private fun sharePinnedLocation(context: Context, pin: PinnedLocation) {
    val text = "NV location\n${pin.coordinates}\n${pin.qrPayload}"
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "اشتراک مکان NV"))
}
