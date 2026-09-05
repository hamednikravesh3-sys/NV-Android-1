package com.nv.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NvDark = Color(0xFF07111D)
private val NvPanel = Color(0xE6111E2C)
private val NvBlue = Color(0xFF00BFFF)
private val NvGreen = Color(0xFF6CFF4A)
private val NvMuted = Color(0xFF93A4B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NvHomeScreen()
            }
        }
    }
}

@Composable
private fun NvHomeScreen() {
    var origin by remember { mutableStateOf("موقعیت فعلی") }
    var destination by remember { mutableStateOf("") }
    var routeStarted by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = NvDark) {
        Box(modifier = Modifier.fillMaxSize()) {
            NvMapBackground()

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
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            color = Color(0xFF07111D),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
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
private fun NvMapBackground() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(NvDark)
    ) {
        val w = size.width
        val h = size.height

        for (i in 1..7) {
            drawLine(
                color = Color(0xFF10273A),
                start = Offset(0f, h * i / 8f),
                end = Offset(w, h * (i - 0.7f) / 8f),
                strokeWidth = 2f
            )
        }

        val road = Path().apply {
            moveTo(w * 0.12f, h)
            cubicTo(w * 0.25f, h * 0.72f, w * 0.73f, h * 0.73f, w * 0.62f, h * 0.48f)
            cubicTo(w * 0.55f, h * 0.34f, w * 0.80f, h * 0.28f, w * 0.86f, h * 0.18f)
        }

        drawPath(
            path = road,
            color = Color(0xFF17334A),
            style = Stroke(width = 42f, cap = StrokeCap.Round)
        )
        drawPath(
            path = road,
            color = NvBlue.copy(alpha = 0.45f),
            style = Stroke(width = 16f, cap = StrokeCap.Round)
        )
        drawPath(
            path = road,
            color = NvGreen,
            style = Stroke(width = 7f, cap = StrokeCap.Round)
        )

        drawCircle(
            color = NvGreen.copy(alpha = 0.25f),
            radius = 34f,
            center = Offset(w * 0.86f, h * 0.18f)
        )
        drawCircle(
            color = NvGreen,
            radius = 16f,
            center = Offset(w * 0.86f, h * 0.18f)
        )

        drawArc(
            color = NvBlue.copy(alpha = 0.18f),
            startAngle = 200f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(w * 0.03f, h * 0.22f),
            size = Size(w * 0.94f, h * 0.52f),
            style = Stroke(width = 3f)
        )
    }
}
