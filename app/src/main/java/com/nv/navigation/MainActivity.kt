package com.nv.navigation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.roundToLong

private val NvDark = Color(0xFF07111D)
private val NvPanel = Color(0xF2111E2C)
private val NvBlue = Color(0xFF00BFFF)
private val NvGreen = Color(0xFF6CFF4A)
private val NvMuted = Color(0xFF93A4B8)

private data class PinnedLocation(
    val latitude: Double,
    val longitude: Double
) {
    val nvCode: String
        get() = "NV-${(latitude * 100_000).roundToLong()}-${(longitude * 100_000).roundToLong()}"

    val coordinates: String
        get() = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    val qrPayload: String
        get() {
            val point = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
            return "geo:$point?q=$point($nvCode)"
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                NvHomeScreen()
            }
        }
    }
}

@Composable
private fun NvHomeScreen() {
    val context = LocalContext.current
    var origin by remember { mutableStateOf("موقعیت فعلی") }
    var destination by remember { mutableStateOf("") }
    var routeStarted by remember { mutableStateOf(false) }
    var pinnedLocation by remember { mutableStateOf(loadPinnedLocation(context)) }
    var showQr by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = NvDark) {
        Box(modifier = Modifier.fillMaxSize()) {
            NvInteractiveMap(
                pinnedLocation = pinnedLocation,
                onPinSelected = { pin ->
                    pinnedLocation = pin
                    destination = pin.nvCode
                    savePinnedLocation(context, pin)
                    showQr = true
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NvPanel)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(NvGreen, CircleShape)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("NV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniChip("☀ 18°")
                            MiniChip("★ دیدنی‌ها")
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = NvPanel)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RouteField(
                                label = "مبدأ",
                                value = origin,
                                accent = NvBlue,
                                onValueChange = { origin = it },
                                onClear = { origin = "" }
                            )
                            RouteField(
                                label = "مقصد",
                                value = destination,
                                accent = NvGreen,
                                onValueChange = { destination = it },
                                onClear = { destination = "" }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    val oldOrigin = origin
                                    origin = destination
                                    destination = oldOrigin
                                }) {
                                    Text("⇅ تعویض مبدأ و مقصد", color = NvBlue)
                                }
                                Text("GPS اختیاری", color = NvMuted, fontSize = 12.sp)
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NvPanel)
                    ) {
                        Text(
                            text = "برای انتخاب مکان، انگشت خود را روی نقشه نگه دارید",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = NvGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pinnedLocation?.let { pin ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showQr = true },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = NvPanel)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("● مکان سنجاق‌شده", color = NvGreen, fontWeight = FontWeight.Bold)
                                    Text(pin.coordinates, color = NvMuted, fontSize = 12.sp)
                                }
                                Text("نمایش QR", color = NvBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (destination.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = NvPanel)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("مسیر پیشنهادی NV", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("سریع‌ترین مسیر • ترافیک کمتر", color = NvMuted, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("24 دقیقه", color = NvGreen, fontWeight = FontWeight.Bold)
                                    Text("12.8 km", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { if (destination.isNotBlank()) routeStarted = !routeStarted },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NvGreen)
                    ) {
                        Text(
                            if (routeStarted) "توقف مسیریابی" else "شروع مسیریابی",
                            color = NvDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            if (showQr && pinnedLocation != null) {
                QrOverlay(
                    pinnedLocation = pinnedLocation!!,
                    onShare = { sharePinnedLocation(context, pinnedLocation!!) },
                    onDismiss = { showQr = false }
                )
            }
        }
    }
}

@Composable
private fun QrOverlay(
    pinnedLocation: PinnedLocation,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val blocker = remember { MutableInteractionSource() }
    val qrImage = remember(pinnedLocation.qrPayload) {
        createQrBitmap(pinnedLocation.qrPayload).asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = blocker,
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132334))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("QR مکان NV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    pinnedLocation.nvCode,
                    color = NvGreen,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Image(
                    bitmap = qrImage,
                    contentDescription = "QR مکان سنجاق‌شده",
                    modifier = Modifier
                        .size(232.dp)
                        .background(Color.White)
                        .padding(10.dp)
                )
                Text(
                    pinnedLocation.coordinates,
                    color = NvMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Text("این مکان ذخیره شد", color = NvGreen, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("بستن", color = Color.White)
                    }
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NvGreen)
                    ) {
                        Text("اشتراک", color = NvDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteField(
    label: String,
    value: String,
    accent: Color,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, CircleShape)
            )
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                TextButton(onClick = onClear) {
                    Text("×", color = Color.White, fontSize = 22.sp)
                }
            }
        }
    )
}

@Composable
private fun MiniChip(text: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NvPanel)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun NvInteractiveMap(
    pinnedLocation: PinnedLocation?,
    onPinSelected: (PinnedLocation) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(NvDark),
        factory = { context ->
            MapView(context).apply {
                id = View.generateViewId()
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.5
                maxZoomLevel = 20.0
                controller.setZoom(5.5)
                controller.setCenter(GeoPoint(32.4279, 53.6880))

                overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean = false

                            override fun longPressHelper(point: GeoPoint?): Boolean {
                                point ?: return false
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPinSelected(
                                    PinnedLocation(
                                        latitude = point.latitude,
                                        longitude = point.longitude
                                    )
                                )
                                return true
                            }
                        }
                    )
                )
            }
        },
        update = { mapView ->
            updateMapMarker(mapView, pinnedLocation)
        }
    )
}

private fun updateMapMarker(mapView: MapView, pin: PinnedLocation?) {
    val oldMarkers = mapView.overlays.filterIsInstance<Marker>()
    if (pin == null) {
        mapView.overlays.removeAll(oldMarkers.toSet())
        mapView.invalidate()
        return
    }

    val marker = oldMarkers.firstOrNull() ?: Marker(mapView).also {
        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(it)
    }
    marker.position = GeoPoint(pin.latitude, pin.longitude)
    marker.title = pin.nvCode
    marker.snippet = pin.coordinates
    mapView.invalidate()
}

private fun createQrBitmap(value: String, size: Int = 768): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val row = y * size
        for (x in 0 until size) {
            pixels[row + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

private fun savePinnedLocation(context: Context, pin: PinnedLocation) {
    context.getSharedPreferences("nv_map", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("has_pinned_location", true)
        .putLong("pinned_latitude", pin.latitude.toBits())
        .putLong("pinned_longitude", pin.longitude.toBits())
        .apply()
}

private fun loadPinnedLocation(context: Context): PinnedLocation? {
    val preferences = context.getSharedPreferences("nv_map", Context.MODE_PRIVATE)
    if (!preferences.getBoolean("has_pinned_location", false)) return null
    return PinnedLocation(
        latitude = Double.fromBits(preferences.getLong("pinned_latitude", 0L)),
        longitude = Double.fromBits(preferences.getLong("pinned_longitude", 0L))
    )
}

private fun sharePinnedLocation(context: Context, pin: PinnedLocation) {
    val message = buildString {
        appendLine("مکان سنجاق‌شده در NV")
        appendLine(pin.nvCode)
        appendLine(pin.coordinates)
        append(pin.qrPayload)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "مکان ${pin.nvCode}")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری مکان"))
}
