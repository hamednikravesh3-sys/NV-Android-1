package com.nv.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.roundToInt

private val NvDark = Color(0xFF06101B)
private val NvPanel = Color(0xEE101B2A)
private val NvBlue = Color(0xFF13C7FF)
private val NvGreen = Color(0xFF68FF3D)
private val NvMuted = Color(0xFFA5B4C8)
private val NvRed = Color(0xFFFF6B6B)

enum class Profile(val fa: String) {
    FASTEST("سریع‌ترین"), BALANCED("متعادل"), ECO("کم‌مصرف")
}

data class RouteResult(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<GeoPoint>
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    val durationMin: Int get() = (durationSeconds / 60.0).roundToInt()
    val energyWh: Int get() = (distanceKm * 145.0 + durationMin * 2.0).roundToInt()

    fun score(profile: Profile): Double {
        val time = durationSeconds / 3600.0
        val distance = distanceKm / 100.0
        val energy = energyWh / 15000.0
        return when (profile) {
            Profile.FASTEST -> 0.75 * time + 0.15 * distance + 0.10 * energy
            Profile.BALANCED -> 0.50 * time + 0.25 * distance + 0.25 * energy
            Profile.ECO -> 0.30 * time + 0.20 * distance + 0.50 * energy
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NvGreen,
                    secondary = NvBlue,
                    background = NvDark,
                    surface = NvPanel
                )
            ) {
                NvFunctionalApp()
            }
        }
    }
}

@Composable
private fun NvFunctionalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    var originText by remember { mutableStateOf("موقعیت فعلی") }
    var destinationText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("مقصد را وارد کنید") }
    var loading by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(Profile.BALANCED) }
    var routes by remember { mutableStateOf<List<RouteResult>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var originPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var navigating by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        status = if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) "مجوز موقعیت فعال شد؛ محاسبه مسیر را بزنید" else "بدون مجوز موقعیت، برای مبدأ یک نام یا مختصات وارد کنید"
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun calculateRoute() {
        if (destinationText.isBlank()) {
            status = "مقصد را وارد کنید"
            return
        }
        if (originText.trim() == "موقعیت فعلی" && !hasLocationPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }

        scope.launch {
            loading = true
            status = "در حال یافتن مبدأ و مقصد…"
            try {
                val start = if (originText.trim() == "موقعیت فعلی") {
                    val location = fused.lastLocation.await()
                        ?: fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                        ?: error("GPS موقعیت فعلی را برنگرداند")
                    GeoPoint(location.latitude, location.longitude)
                } else {
                    parseCoordinates(originText) ?: geocode(originText)
                        ?: error("مبدأ پیدا نشد")
                }

                val end = parseCoordinates(destinationText) ?: geocode(destinationText)
                    ?: error("مقصد پیدا نشد")

                originPoint = start
                destinationPoint = end
                status = "در حال محاسبه مسیرهای واقعی…"
                val fetched = fetchRoutes(start, end)
                if (fetched.isEmpty()) error("هیچ مسیر قابل رانندگی پیدا نشد")
                routes = fetched
                selectedIndex = fetched.minBy { it.score(profile) }.index
                status = "${fetched.size} مسیر واقعی پیدا شد • منبع مسیر: OSRM/OpenStreetMap"
            } catch (e: Exception) {
                routes = emptyList()
                status = "خطا: ${e.message ?: "ارتباط با سرویس مسیر برقرار نشد"}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(profile, routes) {
        if (routes.isNotEmpty()) selectedIndex = routes.minBy { it.score(profile) }.index
    }

    LaunchedEffect(navigating, destinationPoint) {
        val destination = destinationPoint ?: return@LaunchedEffect
        while (navigating) {
            delay(60_000)
            if (!hasLocationPermission()) continue
            try {
                val location = fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() ?: continue
                val current = GeoPoint(location.latitude, location.longitude)
                val updated = fetchRoutes(current, destination)
                if (updated.isNotEmpty()) {
                    originPoint = current
                    routes = updated
                    selectedIndex = updated.minBy { it.score(profile) }.index
                    status = "مسیر با موقعیت فعلی بازبرنامه‌ریزی شد"
                }
            } catch (_: Exception) {
                status = "بازبرنامه‌ریزی این نوبت انجام نشد؛ مسیر قبلی حفظ شد"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(NvDark)) {
        Header()

        Card(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NvPanel)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NvField("مبدأ", originText, NvBlue, { originText = it }, { originText = "" })
                NvField("مقصد", destinationText, NvGreen, { destinationText = it }, { destinationText = "" })
                Text(
                    "برای مختصات می‌توانید به‌صورت 35.6892,51.3890 وارد کنید.",
                    color = NvMuted,
                    fontSize = 10.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Profile.entries.forEach { item ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable { profile = item },
                            shape = RoundedCornerShape(10.dp),
                            color = if (item == profile) NvGreen else Color(0xFF1A2939)
                        ) {
                            Text(
                                item.fa,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                color = if (item == profile) NvDark else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Button(
                    onClick = { calculateRoute() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NvGreen)
                ) {
                    Text(if (loading) "در حال محاسبه…" else "محاسبه مسیر واقعی", color = NvDark, fontWeight = FontWeight.Black)
                }
            }
        }

        NvMap(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            origin = originPoint,
            destination = destinationPoint,
            routes = routes,
            selectedIndex = selectedIndex
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NvPanel)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(status, color = Color.White, fontSize = 11.sp)
                routes.sortedBy { it.score(profile) }.forEach { route ->
                    val active = route.index == selectedIndex
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { selectedIndex = route.index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) Color(0xFF19372E) else Color(0xFF162433)
                    ) {
                        Row(
                            Modifier.padding(9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("مسیر ${route.index + 1}", color = if (active) NvGreen else Color.White, fontWeight = FontWeight.Bold)
                                Text(String.format(Locale.US, "%.1f km", route.distanceKm), color = NvMuted, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${route.durationMin} دقیقه", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("انرژی تخمینی ${route.energyWh} Wh", color = NvMuted, fontSize = 9.sp)
                            }
                        }
                    }
                }
                if (routes.isNotEmpty()) {
                    Button(
                        onClick = { navigating = !navigating },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (navigating) NvRed else NvBlue)
                    ) {
                        Text(if (navigating) "توقف بازبرنامه‌ریزی" else "شروع بازبرنامه‌ریزی هر ۶۰ ثانیه", color = NvDark, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "این نسخه از نقشه و مسیر واقعی استفاده می‌کند. ترافیک زنده هنوز متصل نشده است.",
                    color = NvMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("NV", color = NvGreen, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text("SMART NAVIGATION • FUNCTIONAL v3", color = NvBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NvField(
    label: String,
    value: String,
    accent: Color,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            if (value.isNotBlank()) TextButton(onClick = onClear) { Text("×", color = Color.White, fontSize = 20.sp) }
        },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent)
    )
}

@Composable
private fun NvMap(
    modifier: Modifier,
    origin: GeoPoint?,
    destination: GeoPoint?,
    routes: List<RouteResult>,
    selectedIndex: Int
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(5.5)
                controller.setCenter(GeoPoint(32.0, 53.0))
            }
        },
        update = { map ->
            map.overlays.clear()

            origin?.let {
                Marker(map).apply {
                    position = it
                    title = "مبدأ"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                }
            }
            destination?.let {
                Marker(map).apply {
                    position = it
                    title = "مقصد"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                }
            }

            routes.forEach { route ->
                Polyline(map).apply {
                    setPoints(route.points)
                    outlinePaint.strokeWidth = if (route.index == selectedIndex) 11f else 5f
                    outlinePaint.color = if (route.index == selectedIndex) 0xFF68FF3D.toInt() else 0x6613C7FF
                    map.overlays.add(this)
                }
            }

            val selected = routes.firstOrNull { it.index == selectedIndex }
            if (selected != null && selected.points.isNotEmpty()) {
                map.zoomToBoundingBox(selected.pointsBoundingBox(), true, 70)
            } else if (origin != null) {
                map.controller.setZoom(15.0)
                map.controller.animateTo(origin)
            }
            map.invalidate()
        }
    )
}

private fun List<GeoPoint>.pointsBoundingBox(): org.osmdroid.util.BoundingBox {
    val north = maxOf { it.latitude }
    val south = minOf { it.latitude }
    val east = maxOf { it.longitude }
    val west = minOf { it.longitude }
    return org.osmdroid.util.BoundingBox(north, east, south, west)
}

private fun parseCoordinates(text: String): GeoPoint? {
    val parts = text.trim().split(',')
    if (parts.size != 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lon = parts[1].trim().toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return GeoPoint(lat, lon)
}

private suspend fun geocode(query: String): GeoPoint? = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
    val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12_000
        readTimeout = 12_000
        setRequestProperty("User-Agent", "NVSmartNavigation/3.0")
        setRequestProperty("Accept-Language", "fa,en")
    }
    try {
        if (conn.responseCode !in 200..299) return@withContext null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(body)
        if (arr.length() == 0) return@withContext null
        val item = arr.getJSONObject(0)
        GeoPoint(item.getString("lat").toDouble(), item.getString("lon").toDouble())
    } finally {
        conn.disconnect()
    }
}

private suspend fun fetchRoutes(start: GeoPoint, end: GeoPoint): List<RouteResult> = withContext(Dispatchers.IO) {
    val endpoint = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?alternatives=true&overview=full&geometries=geojson&steps=false"
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "NVSmartNavigation/3.0")
    }
    try {
        if (conn.responseCode !in 200..299) error("خطای سرویس مسیر ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        if (root.optString("code") != "Ok") error("سرویس مسیر پاسخ معتبر نداد")
        val arr = root.getJSONArray("routes")
        buildList {
            for (i in 0 until arr.length()) {
                val route = arr.getJSONObject(i)
                val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = ArrayList<GeoPoint>(coordinates.length())
                for (j in 0 until coordinates.length()) {
                    val pair = coordinates.getJSONArray(j)
                    points.add(GeoPoint(pair.getDouble(1), pair.getDouble(0)))
                }
                add(
                    RouteResult(
                        index = i,
                        distanceMeters = route.getDouble("distance"),
                        durationSeconds = route.getDouble("duration"),
                        points = points
                    )
                )
            }
        }
    } finally {
        conn.disconnect()
    }
}
