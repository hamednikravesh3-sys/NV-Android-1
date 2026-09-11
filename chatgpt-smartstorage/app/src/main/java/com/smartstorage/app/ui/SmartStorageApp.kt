package com.smartstorage.app.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.app.StorageUiState
import com.smartstorage.app.data.*
import java.text.SimpleDateFormat
import java.util.*

private enum class Tab(val title: String) { HOME("خانه"), SMART("هوشمند"), FILES("فایل‌ها"), APPS("برنامه‌ها") }

@Composable
fun SmartStorageApp(
    state: StorageUiState,
    onScan: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRequestUsage: () -> Unit,
    onChooseFolder: () -> Unit,
    onToggle: (StorageItem) -> Unit,
    onSelectRecommended: () -> Unit,
    onClearSelection: () -> Unit,
    onQuery: (String) -> Unit,
    onOpenAppStorage: (String) -> Unit,
    onDelete: (List<StorageItem>) -> Unit
) {
    SmartStorageTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var tab by remember { mutableStateOf(Tab.HOME) }
            var confirmDelete by remember { mutableStateOf(false) }
            val selectedItems = remember(state.selected, state.result.items) {
                state.result.items.filter { it.key in state.selected }
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = {
                                    Icon(
                                        when (item) {
                                            Tab.HOME -> Icons.Default.Home
                                            Tab.SMART -> Icons.Default.AutoAwesome
                                            Tab.FILES -> Icons.Default.Folder
                                            Tab.APPS -> Icons.Default.Apps
                                        }, null
                                    )
                                },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(state, onScan, onRequestAllFiles, onRequestUsage, onChooseFolder)
                        Tab.SMART -> SmartScreen(state, onToggle, onSelectRecommended, onClearSelection) { confirmDelete = true }
                        Tab.FILES -> FilesScreen(state, onToggle, onQuery) { confirmDelete = true }
                        Tab.APPS -> AppsScreen(state, onRequestUsage, onOpenAppStorage)
                    }
                    if (state.scanning) ScanOverlay(state.progress, state.status)
                }
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    icon = { Icon(Icons.Default.DeleteForever, null) },
                    title = { Text("تأیید حذف") },
                    text = {
                        Text("${selectedItems.size} مورد با حجم ${formatBytes(selectedItems.sumOf { it.sizeBytes })} انتخاب شده است. حذف فقط پس از تأیید شما انجام می‌شود.")
                    },
                    confirmButton = {
                        Button(onClick = {
                            confirmDelete = false
                            onDelete(selectedItems)
                        }) { Text("ادامه و تأیید حذف") }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: StorageUiState,
    onScan: () -> Unit,
    onAllFiles: () -> Unit,
    onUsage: () -> Unit,
    onFolder: () -> Unit
) {
    val d = state.result.device
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Smart Storage", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("تحلیل هوشمند حافظه؛ تصمیم حذف با شماست", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { StorageGauge(d.usedBytes, d.totalBytes) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("استفاده‌شده", formatBytes(d.usedBytes), Modifier.weight(1f))
                StatCard("آزاد", formatBytes(d.freeBytes), Modifier.weight(1f))
                StatCard("قابل مشاهده", formatBytes(d.accessibleBytes), Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Other / محافظت‌شده", fontWeight = FontWeight.Bold)
                    Text(formatBytes(d.unresolvedBytes), fontSize = 26.sp, color = MaterialTheme.colorScheme.tertiary)
                    Text("این عدد برآورد فضای استفاده‌شده‌ای است که فایل قابل‌دسترسی یا آمار اپ برای آن پیدا نشده؛ ممکن است شامل سیستم و داده‌های خصوصی برنامه‌ها باشد.")
                }
            }
        }
        item {
            PermissionCard(
                title = "دسترسی کامل به فایل‌های مشترک",
                granted = state.result.allFilesAccess,
                description = "برای دیدن Downloads، APK، ZIP و فایل‌های غیررسانه‌ای بهتر است فعال شود.",
                action = onAllFiles
            )
        }
        item {
            PermissionCard(
                title = "آمار فضای برنامه‌ها",
                granted = state.result.usageAccess,
                description = "برای تشخیص اینکه کدام برنامه‌ها بخش زیادی از Other را ساخته‌اند.",
                action = onUsage
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("اسکن هوشمند")
                }
                OutlinedButton(onClick = onFolder, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("افزودن پوشه")
                }
            }
        }
        item { Text("دسته‌بندی بر اساس نوع فایل", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(state.result.categories.take(8)) { summary -> CategoryRow(summary) }
        if (state.result.items.isNotEmpty()) {
            item { Text("دسته‌بندی بر اساس منبع", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(
                state.result.items.groupBy { it.source }
                    .map { (source, list) -> Triple(source, list.sumOf { it.sizeBytes }, list.size) }
                    .sortedByDescending { it.second }
                    .take(8)
            ) { source -> SourceRow(source.first, source.second, source.third) }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun StorageGauge(used: Long, total: Long) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Card {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 15.dp.toPx()
                    drawArc(surfaceVariant, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(primary, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(fraction * 100).toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("مصرف حافظه")
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("کل حافظه", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBytes(total), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("گزارش بر اساس دسترسی‌های فعلی گوشی ساخته می‌شود.")
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(title, fontSize = 12.sp); Text(value, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun PermissionCard(title: String, granted: Boolean, description: String, action: () -> Unit) {
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Lock, null, tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) TextButton(onClick = action) { Text("فعال‌سازی") }
        }
    }
}

@Composable
private fun CategoryRow(summary: CategorySummary) {
    Card {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(summary.category), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.category.faTitle, fontWeight = FontWeight.SemiBold)
                Text("${summary.count} فایل", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatBytes(summary.bytes), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SourceRow(source: String, bytes: Long, count: Int) {
    Card {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Source, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source, fontWeight = FontWeight.SemiBold)
                Text("$count فایل", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatBytes(bytes), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SmartScreen(
    state: StorageUiState,
    onToggle: (StorageItem) -> Unit,
    onSelectRecommended: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    val recommended = state.result.items.filter { it.riskScore <= 30 }
    val selectedBytes = state.result.items.filter { it.key in state.selected }.sumOf { it.sizeBytes }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("پیشنهادهای هوشمند", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("برنامه پیشنهاد می‌دهد؛ حذف نهایی فقط توسط شما انجام می‌شود.")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${formatBytes(recommended.sumOf { it.sizeBytes })} کم‌ریسک", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text("${recommended.size} فایل بر اساس نوع، محل، سن و حجم پیشنهاد شده‌اند.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSelectRecommended) { Text("انتخاب پیشنهادی‌ها") }
                        TextButton(onClick = onClear) { Text("پاک کردن انتخاب") }
                    }
                }
            }
        }
        items(recommended.take(100), key = { it.key }) { item -> FileRow(item, item.key in state.selected, onToggle) }
        if (state.selected.isNotEmpty()) {
            item {
                Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("حذف ${formatBytes(selectedBytes)} انتخاب‌شده")
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(
    state: StorageUiState,
    onToggle: (StorageItem) -> Unit,
    onQuery: (String) -> Unit,
    onDelete: () -> Unit
) {
    val filtered = remember(state.query, state.result.items) {
        if (state.query.isBlank()) state.result.items
        else state.result.items.filter {
            it.displayName.contains(state.query, true) || it.source.contains(state.query, true) || it.category.faTitle.contains(state.query, true)
        }
    }
    val selectedBytes = state.result.items.filter { it.key in state.selected }.sumOf { it.sizeBytes }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("همه فایل‌های قابل‌دسترسی", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("نام، دسته یا منبع فایل...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.key }) { item -> FileRow(item, item.key in state.selected, onToggle) }
        }
        AnimatedVisibility(state.selected.isNotEmpty()) {
            Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("حذف ${state.selected.size} مورد • ${formatBytes(selectedBytes)}")
            }
        }
    }
}

@Composable
private fun FileRow(item: StorageItem, checked: Boolean, onToggle: (StorageItem) -> Unit) {
    var expanded by remember(item.key) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(item) })
                Icon(categoryIcon(item.category), null, tint = recommendationColor(item.recommendation))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${item.category.faTitle} • ${item.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytes(item.sizeBytes), fontWeight = FontWeight.Bold)
                    Text("ریسک ${item.riskScore}/100", fontSize = 11.sp, color = recommendationColor(item.recommendation))
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp, start = 48.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    item.reasons.forEach { Text("• $it", fontSize = 12.sp) }
                    if (item.modifiedAtMillis > 0) Text("آخرین تغییر: ${date(item.modifiedAtMillis)}", fontSize = 12.sp)
                    Text(item.relativePath, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AppsScreen(state: StorageUiState, onUsage: () -> Unit, onOpenAppStorage: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("تحلیل برنامه‌ها", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("حجم کد، داده و کش برنامه‌های قابل مشاهده")
        }
        if (!state.result.usageAccess) {
            item { Button(onClick = onUsage, modifier = Modifier.fillMaxWidth()) { Text("فعال‌کردن Usage Access") } }
        }
        items(state.result.apps, key = { it.packageName }) { app ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(app.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(formatBytes(app.totalBytes), fontWeight = FontWeight.Bold)
                    }
                    Text("داده: ${formatBytes(app.dataBytes)} • کش: ${formatBytes(app.cacheBytes)} • برنامه: ${formatBytes(app.appBytes)}", fontSize = 12.sp)
                    TextButton(onClick = { onOpenAppStorage(app.packageName) }, modifier = Modifier.align(Alignment.End)) {
                        Text("مدیریت توسط کاربر")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanOverlay(progress: Int, status: String) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = .88f)), contentAlignment = Alignment.Center) {
        Card {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(14.dp))
                Text("$progress%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(status)
            }
        }
    }
}

@Composable
private fun recommendationColor(r: Recommendation) = when (r) {
    Recommendation.SAFE -> MaterialTheme.colorScheme.secondary
    Recommendation.REVIEW -> MaterialTheme.colorScheme.tertiary
    Recommendation.PROTECTED -> MaterialTheme.colorScheme.error
}

private fun categoryIcon(c: StorageCategory) = when (c) {
    StorageCategory.DOWNLOADS -> Icons.Default.Download
    StorageCategory.PHOTOS -> Icons.Default.Image
    StorageCategory.VIDEOS -> Icons.Default.Movie
    StorageCategory.AUDIO -> Icons.Default.AudioFile
    StorageCategory.DOCUMENTS -> Icons.Default.Description
    StorageCategory.APK -> Icons.Default.Android
    StorageCategory.ARCHIVES -> Icons.Default.FolderZip
    StorageCategory.BACKUPS -> Icons.Default.Backup
    StorageCategory.TEMPORARY -> Icons.Default.CleaningServices
    StorageCategory.OTHER -> Icons.Default.MoreHoriz
}

@Composable
private fun formatBytes(bytes: Long): String = Formatter.formatFileSize(LocalContext.current, bytes.coerceAtLeast(0))
private fun date(ms: Long): String = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(ms))
