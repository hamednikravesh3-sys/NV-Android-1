package com.nv.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private val NvDark = Color(0xFF06101B)
private val NvPanel = Color(0xEC101B2A)
private val NvBlue = Color(0xFF13C7FF)
private val NvGreen = Color(0xFF68FF3D)
private val NvYellow = Color(0xFFFFC84A)
private val NvRed = Color(0xFFFF6B6B)
private val NvMuted = Color(0xFFA5B4C8)

enum class Profile(val fa: String) { FASTEST("سریع‌ترین"), BALANCED("متعادل"), ECO("کم‌مصرف") }
enum class Traffic { LIGHT, MEDIUM, HEAVY }

data class Route(
    val id: String,
    val title: String,
    val baseMin: Int,
    val km: Double,
    val energyWh: Int,
    val traffic: Traffic,
    val confidence: Double
) {
    val delay: Int get() = when (traffic) { Traffic.LIGHT -> 2; Traffic.MEDIUM -> 7; Traffic.HEAVY -> 14 }
    val eta: Int get() = baseMin + delay
    fun score(profile: Profile): Double {
        val t = eta / 60.0
        val d = km / 50.0
        val e = energyWh / 10000.0
        val u = 1.0 - confidence
        return when (profile) {
            Profile.FASTEST -> .72*t + .12*d + .10*e + .06*u
            Profile.BALANCED -> .50*t + .22*d + .22*e + .06*u
            Profile.ECO -> .30*t + .20*d + .44*e + .06*u
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = NvGreen, secondary = NvBlue, background = NvDark, surface = NvPanel)) {
                NvSmartScreen()
            }
        }
    }
}

@Composable
private fun NvSmartScreen() {
    var origin by remember { mutableStateOf("موقعیت فعلی") }
    var destination by remember { mutableStateOf("اصفهان") }
    var profile by remember { mutableStateOf(Profile.BALANCED) }
    var navigating by remember { mutableStateOf(false) }
    var replans by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("پیش‌بینی ترافیک فعال است") }
    var routes by remember {
        mutableStateOf(
            listOf(
                Route("r1", "مسیر سریع شهری", 22, 14.0, 2410, Traffic.MEDIUM, .88),
                Route("r2", "مسیر متعادل", 25, 15.7, 2260, Traffic.LIGHT, .91),
                Route("r3", "مسیر کم‌مصرف", 29, 17.2, 2070, Traffic.LIGHT, .86)
            )
        )
    }
    var selected by remember { mutableStateOf("r2") }

    fun bestRoute(list: List<Route>): Route = list.minBy { it.score(profile) }

    LaunchedEffect(profile, routes.size) {
        selected = bestRoute(routes).id
    }

    LaunchedEffect(navigating, profile) {
        if (!navigating) return@LaunchedEffect
        while (navigating) {
            delay(8000)
            val oldSelected = routes.firstOrNull { it.id == selected }
            routes = routes.map { r ->
                val nextTraffic = when (r.traffic) {
                    Traffic.LIGHT -> if (Random.nextFloat() < .22f) Traffic.MEDIUM else Traffic.LIGHT
                    Traffic.MEDIUM -> when {
                        Random.nextFloat() < .18f -> Traffic.HEAVY
                        Random.nextFloat() < .70f -> Traffic.MEDIUM
                        else -> Traffic.LIGHT
                    }
                    Traffic.HEAVY -> if (Random.nextFloat() < .60f) Traffic.HEAVY else Traffic.MEDIUM
                }
                r.copy(traffic = nextTraffic, confidence = (r.confidence + Random.nextDouble(-.03, .03)).coerceIn(.72, .96))
            }
            replans++
            val best = bestRoute(routes)
            if (best.id != selected) {
                val previousEta = oldSelected?.eta ?: best.eta
                val gain = previousEta - best.eta
                selected = best.id
                message = if (gain > 0) "مسیر بهینه‌تر پیدا شد؛ حدود $gain دقیقه سریع‌تر" else "مسیر بر اساس هدف ${profile.fa} بازبرنامه‌ریزی شد"
            } else {
                message = "ترافیک دوباره ارزیابی شد؛ مسیر فعلی هنوز بهترین گزینه است"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(NvDark)) {
        SmartMapBackdrop(selected)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).background(NvGreen, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("NV", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Spacer(Modifier.width(7.dp))
                            Text("SMART", color = NvBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                        Text("☀ 18°  |  ترافیک زنده", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Color.White, fontSize = 11.sp)
                    }
                }

                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        RouteField("مبدأ", origin, NvBlue, { origin = it }, { origin = "" })
                        RouteField("مقصد", destination, NvGreen, { destination = it }, { destination = "" })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { val x = origin; origin = destination; destination = x }) { Text("⇅ تعویض", color = NvBlue) }
                            Text("GPS اختیاری • انتخاب مبدأ آزاد", color = NvMuted, fontSize = 10.sp)
                        }
                    }
                }

                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NvPanel)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("هدف بهینه‌سازی", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Profile.entries.forEach { p ->
                                val active = p == profile
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { profile = p; message = "بهینه‌سازی روی ${p.fa} تنظیم شد" },
                                    shape = RoundedCornerShape(13.dp),
                                    color = if (active) NvGreen else Color(0xFF172536)
                                ) {
                                    Text(p.fa, Modifier.padding(vertical = 9.dp), color = if (active) NvDark else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE9152638))) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(message, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("بازبرنامه‌ریزی: $replans بار • تحلیل زمان/مسافت/انرژی", color = NvMuted, fontSize = 9.sp)
                    }
                }

                routes.sortedBy { it.score(profile) }.forEach { route ->
                    RouteCard(route, route.id == selected) { selected = route.id; message = "مسیر ${route.title} انتخاب شد" }
                }

                Button(
                    onClick = { if (destination.isNotBlank()) { navigating = !navigating; message = if (navigating) "ناوبری هوشمند و بازبرنامه‌ریزی مداوم فعال شد" else "ناوبری متوقف شد" } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (navigating) NvRed else NvGreen)
                ) {
                    Text(if (navigating) "توقف ناوبری" else "شروع مسیریابی هوشمند", color = NvDark, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun RouteField(label: String, value: String, accent: Color, onChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Box(Modifier.size(10.dp).background(accent, CircleShape)) },
        trailingIcon = { if (value.isNotBlank()) TextButton(onClick = onClear) { Text("×", color = Color.White, fontSize = 20.sp) } },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent)
    )
}

@Composable
private fun RouteCard(route: Route, active: Boolean, onClick: () -> Unit) {
    val trafficColor = when (route.traffic) { Traffic.LIGHT -> NvGreen; Traffic.MEDIUM -> NvYellow; Traffic.HEAVY -> NvRed }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) Color(0xF0172B35) else NvPanel)
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).background(if (active) NvGreen else trafficColor, CircleShape))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(route.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${route.km} km • ${route.energyWh} Wh • اطمینان ${(route.confidence*100).roundToInt()}٪", color = NvMuted, fontSize = 9.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${route.eta} دقیقه", color = if (active) NvGreen else Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(when (route.traffic) { Traffic.LIGHT -> "ترافیک سبک"; Traffic.MEDIUM -> "ترافیک متوسط"; Traffic.HEAVY -> "ترافیک سنگین" }, color = trafficColor, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SmartMapBackdrop(selected: String) {
    Canvas(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF13233A), NvDark)))) {
        val w = size.width
        val h = size.height
        repeat(9) { i ->
            drawLine(Color(0xFF17344B), Offset(0f, h*(i+1)/10f), Offset(w, h*(i+.4f)/10f), 2f)
        }
        val road = Path().apply {
            moveTo(w*.08f, h)
            cubicTo(w*.16f,h*.78f,w*.72f,h*.80f,w*.58f,h*.54f)
            cubicTo(w*.50f,h*.38f,w*.82f,h*.32f,w*.88f,h*.16f)
        }
        drawPath(road, Color(0xFF1A364A), style = Stroke(46f, cap = StrokeCap.Round))
        drawPath(road, NvBlue.copy(alpha=.45f), style = Stroke(17f, cap = StrokeCap.Round))
        drawPath(road, if (selected == "r3") NvGreen else NvBlue, style = Stroke(7f, cap = StrokeCap.Round))
        drawCircle(NvGreen.copy(alpha=.20f), 35f, Offset(w*.88f,h*.16f))
        drawCircle(NvGreen, 15f, Offset(w*.88f,h*.16f))
    }
}
