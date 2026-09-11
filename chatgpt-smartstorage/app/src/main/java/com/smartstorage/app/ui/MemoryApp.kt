package com.smartstorage.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.Date
import java.util.Locale

private enum class MemoryTab(val title: String) {
    HOME("خانه"), FOLDERS("پوشه‌ها"), SMART("هوشمند"), FILES("فایل‌ها"), APPS("برنامه‌ها")
}

private enum class FolderAccess(val title: String) {
    DIRECT("قابل مشاهده"), STATISTICAL("آماری"), ESTIMATED("تخمینی")
}

private data class FolderStat(
    val name: String,
    val path: String,
    val bytes: Long,
    val count: Int,
    val access: FolderAccess,
    val note: String = ""
)

@Composable
fun MemoryApp(
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
            var tab by remember { mutableStateOf(MemoryTab.HOME) }
            var confirmDelete by remember { mutableStateOf(false) }
            val selectedItems = remember(state.selected, state.result.items) {
                state.result.items.filter { it.key in state.selected }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MemoryTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = {
                                    Icon(
                                        when (item) {
                                            MemoryTab.HOME -> Icons.Default.Home
                                            MemoryTab.FOLDERS -> Icons.Default.Folder
                                            MemoryTab.SMART -> Icons.Default.AutoAwesome
                                            MemoryTab.FILES -> Icons.Default.Description
                                            MemoryTab.APPS -> Icons.Default.Apps
                                        },
                                        contentDescription = null
                                    )
                                },
                                label = { Text(item.title, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        MemoryTab.HOME -> MemoryHome(state, onScan, onRequestAllFiles, onRequestUsage, onChooseFolder)
                        MemoryTab.FOLDERS -> FolderMapScreen(state, onRequestAllFiles, onRequestUsage)
                        MemoryTab.SMART -> SmartCleanupScreen(state, onToggle, onSelectRecommended, onClearSelection) { confirmDelete = true }
                        MemoryTab.FILES -> MemoryFilesScreen(state, onToggle, onQuery) { confirmDelete = true }
                        MemoryTab.APPS -> MemoryAppsScreen(state, onRequestUsage, onOpenAppStorage)
                    }

                    if (state.scanning) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(18.dp))
                                Text(state.status, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("${state.progress}%")
                            }
                        }
                    }
                }
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    icon = { Icon(Icons.Default.Delete, null) },
                    title = { Text("تأیید حذف") },
                    text = {
                        Text("${selectedItems.size} فایل با حجم ${formatBytes(selectedItems.sumOf { it.sizeBytes })} انتخاب شده است. حذف فقط با تأیید شما انجام می‌شود.")
                    },
                    confirmButton = {
                        Button(onClick = {
                            confirmDelete = false
                            onDelete(selectedItems)
                        }) { Text("تأیید توسط من") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("انصراف") }
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoryHome(
    state: StorageUiState,
    onScan: () -> Unit,
    onAllFiles: () -> Unit,
    onUsage: () -> Unit,
    onFolder: () -> Unit
) {
    val result = state.result
    val d = result.device
    val folderStats = remember(result.items, d.appBytes, d.unresolvedBytes) { buildFolderStats(result) }
    val download = folderStats.firstOrNull { it.name == "Downloads" }
    val downloadBytes = download?.bytes ?: 0L
    val downloadCount = download?.count ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Memory", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("نقشه واقعی مصرف حافظه", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    val fraction = if (d.totalBytes > 0) (d.usedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f) else 0f
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("حافظه استفاده‌شده", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBytes(d.usedBytes), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("از ${formatBytes(d.totalBytes)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth().height(9.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("${(fraction * 100).toInt()}٪ پر • ${formatBytes(d.freeBytes)} آزاد", fontSize = 13.sp)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StorageMetricCard(
                    title = "Downloads",
                    value = formatBytes(downloadBytes),
                    subtitle = if (result.allFilesAccess) "$downloadCount فایل" else "عدد ممکن است ناقص باشد",
                    icon = Icons.Default.Download,
                    modifier = Modifier.weight(1f)
                )
                StorageMetricCard(
                    title = "برنامه‌ها و داده خصوصی",
                    value = formatBytes(d.appBytes),
                    subtitle = if (result.usageAccess) "آمار Android" else "نیاز به Usage Access",
                    icon = Icons.Default.Apps,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StorageMetricCard(
                    title = "System / Protected",
                    value = formatBytes(d.unresolvedBytes),
                    subtitle = "تخمین بخش غیرقابل فهرست",
                    icon = Icons.Default.Lock,
                    modifier = Modifier.weight(1f)
                )
                StorageMetricCard(
                    title = "فایل‌های قابل مشاهده",
                    value = formatBytes(d.accessibleBytes),
                    subtitle = "اسکن مستقیم/MediaStore",
                    icon = Icons.Default.FolderOpen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (!result.allFilesAccess) {
            item {
                AccessWarningCard(
                    title = "برای اندازه دقیق Downloads و پوشه‌های مشترک",
                    body = "دسترسی All files را فعال کن. بدون آن Android فقط بخشی از فایل‌ها را در اختیار Memory می‌گذارد.",
                    button = "فعال‌سازی دسترسی فایل‌ها",
                    onClick = onAllFiles
                )
            }
        }

        if (!result.usageAccess) {
            item {
                AccessWarningCard(
                    title = "برای توضیح بهتر Other",
                    body = "Usage Access را فعال کن تا Memory بتواند حجم آماری برنامه‌ها و داده خصوصی آن‌ها را از Android دریافت کند.",
                    button = "فعال‌سازی آمار برنامه‌ها",
                    onClick = onUsage
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("اسکن حافظه")
                }
                OutlinedButton(onClick = onFolder, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CreateNewFolder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("پوشه انتخابی")
                }
            }
        }

        item { SectionTitle("بزرگ‌ترین بخش‌های حافظه") }
        items(folderStats.take(8)) { stat -> FolderStatRow(stat) }

        if (result.categories.isNotEmpty()) {
            item { SectionTitle("نوع فایل‌ها") }
            items(result.categories.take(6)) { c ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.category.faTitle, fontWeight = FontWeight.SemiBold)
                            Text("${c.count} فایل", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatBytes(c.bytes), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun FolderMapScreen(
    state: StorageUiState,
    onAllFiles: () -> Unit,
    onUsage: () -> Unit
) {
    val stats = remember(state.result.items, state.result.device) { buildFolderStats(state.result) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("نقشه پوشه‌ها", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("هر عدد نشان می‌دهد آن پوشه یا بخش تقریباً چه مقدار از حافظه را اشغال کرده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!state.result.allFilesAccess) {
            item {
                AccessWarningCard(
                    "اسکن پوشه‌ها کامل نیست",
                    "برای اندازه دقیق‌تر Download، DCIM، Movies، Pictures و Android/media دسترسی فایل‌ها را فعال کن.",
                    "فعال‌سازی",
                    onAllFiles
                )
            }
        }
        if (!state.result.usageAccess) {
            item {
                AccessWarningCard(
                    "داده خصوصی برنامه‌ها نامشخص است",
                    "برای مشاهده حجم آماری فضای برنامه‌ها Usage Access لازم است.",
                    "فعال‌سازی",
                    onUsage
                )
            }
        }
        items(stats) { stat -> FolderStatRow(stat) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("چرا Android/data باز نمی‌شود؟", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Android دسترسی مستقیم برنامه‌های عادی به پوشه خصوصی سایر برنامه‌ها را مسدود می‌کند. Memory برای این قسمت حجم آماری برنامه‌ها را نشان می‌دهد و فایل جعلی یا عدد ساختگی تولید نمی‌کند.")
                }
            }
        }
    }
}

@Composable
private fun SmartCleanupScreen(
    state: StorageUiState,
    onToggle: (StorageItem) -> Unit,
    onSelectRecommended: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    val recommended = state.result.items.filter { it.riskScore <= 30 }
    val selectedBytes = state.result.items.filter { it.key in state.selected }.sumOf { it.sizeBytes }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("پاک‌سازی هوشمند", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Memory فقط پیشنهاد می‌دهد؛ حذف نهایی با انتخاب و تأیید شماست.")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(formatBytes(recommended.sumOf { it.sizeBytes }), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text("${recommended.size} فایل کم‌ریسک شناسایی شده")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSelectRecommended) { Text("انتخاب پیشنهادی‌ها") }
                        TextButton(onClick = onClear) { Text("لغو انتخاب") }
                    }
                }
            }
        }
        items(recommended.take(200), key = { it.key }) { item ->
            MemoryFileRow(item, item.key in state.selected, onToggle)
        }
        if (state.selected.isNotEmpty()) {
            item {
                Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("حذف ${formatBytes(selectedBytes)} انتخاب‌شده")
                }
            }
        }
    }
}

@Composable
private fun MemoryFilesScreen(
    state: StorageUiState,
    onToggle: (StorageItem) -> Unit,
    onQuery: (String) -> Unit,
    onDelete: () -> Unit
) {
    val filtered = remember(state.query, state.result.items) {
        if (state.query.isBlank()) state.result.items
        else state.result.items.filter {
            it.displayName.contains(state.query, true) ||
                it.source.contains(state.query, true) ||
                it.relativePath.contains(state.query, true) ||
                it.category.faTitle.contains(state.query, true)
        }
    }
    val selectedBytes = state.result.items.filter { it.key in state.selected }.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("فایل‌های قابل‌دسترسی", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text("جستجو در نام، منبع یا مسیر") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        if (state.selected.isNotEmpty()) {
            Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("حذف ${state.selected.size} فایل • ${formatBytes(selectedBytes)}")
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.key }) { item -> MemoryFileRow(item, item.key in state.selected, onToggle) }
        }
    }
}

@Composable
private fun MemoryAppsScreen(
    state: StorageUiState,
    onUsage: () -> Unit,
    onOpenAppStorage: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("فضای برنامه‌ها", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("فایل‌های خصوصی قابل بازکردن نیستند، اما Android می‌تواند حجم آن‌ها را آماری گزارش کند.")
        }
        if (!state.result.usageAccess) {
            item { AccessWarningCard("Usage Access لازم است", "برای دریافت اندازه برنامه، Data و Cache این مجوز را فعال کن.", "فعال‌سازی", onUsage) }
        }
        items(state.result.apps) { app ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.Bold)
                            Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatBytes(app.totalBytes), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("App: ${formatBytes(app.appBytes)}   Data: ${formatBytes(app.dataBytes)}   Cache: ${formatBytes(app.cacheBytes)}", fontSize = 12.sp)
                    TextButton(onClick = { onOpenAppStorage(app.packageName) }) { Text("مدیریت توسط کاربر") }
                }
            }
        }
    }
}

@Composable
private fun StorageMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(13.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessWarningCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 13.sp)
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun FolderStatRow(stat: FolderStat) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (stat.access) {
                        FolderAccess.DIRECT -> Icons.Default.Folder
                        FolderAccess.STATISTICAL -> Icons.Default.Apps
                        FolderAccess.ESTIMATED -> Icons.Default.Lock
                    },
                    null,
                    tint = when (stat.access) {
                        FolderAccess.DIRECT -> MaterialTheme.colorScheme.primary
                        FolderAccess.STATISTICAL -> MaterialTheme.colorScheme.secondary
                        FolderAccess.ESTIMATED -> MaterialTheme.colorScheme.tertiary
                    }
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(stat.name, fontWeight = FontWeight.Bold)
                    Text(stat.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytes(stat.bytes), fontWeight = FontWeight.Bold)
                    Text(stat.access.title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stat.count > 0 || stat.note.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    buildString {
                        if (stat.count > 0) append("${stat.count} فایل")
                        if (stat.count > 0 && stat.note.isNotBlank()) append(" • ")
                        append(stat.note)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemoryFileRow(item: StorageItem, selected: Boolean, onToggle: (StorageItem) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle(item) })
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName.ifBlank { "بدون نام" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.category.faTitle} • ${item.source}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.relativePath.isNotBlank()) {
                    Text(item.relativePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(item.sizeBytes), fontWeight = FontWeight.Bold)
                Text("ریسک ${item.riskScore}/100", fontSize = 10.sp)
                if (item.modifiedAtMillis > 0) Text(formatDate(item.modifiedAtMillis), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

private fun buildFolderStats(result: ScanResult): List<FolderStat> {
    val grouped = result.items.groupBy { topFolder(it) }
    val direct = grouped.map { (folder, files) ->
        FolderStat(
            name = folder.first,
            path = folder.second,
            bytes = files.sumOf { it.sizeBytes.coerceAtLeast(0) },
            count = files.size,
            access = FolderAccess.DIRECT,
            note = if (result.allFilesAccess) "اسکن مستقیم" else "بر اساس دسترسی فعلی"
        )
    }.filter { it.bytes > 0 }

    val synthetic = buildList {
        if (result.device.appBytes > 0 || !result.usageAccess) {
            add(
                FolderStat(
                    name = "App Data / Private",
                    path = "Android/data + فضای داخلی خصوصی برنامه‌ها",
                    bytes = result.device.appBytes,
                    count = result.apps.size,
                    access = FolderAccess.STATISTICAL,
                    note = if (result.usageAccess) "اندازه از StorageStats Android؛ فایل‌ها قابل فهرست مستقیم نیستند" else "برای محاسبه، Usage Access را فعال کنید"
                )
            )
        }
        add(
            FolderStat(
                name = "System / Protected / Unknown",
                path = "سیستم و بخش‌های محافظت‌شده یا حل‌نشده",
                bytes = result.device.unresolvedBytes,
                count = 0,
                access = FolderAccess.ESTIMATED,
                note = "باقیمانده محاسبات؛ پوشه واقعی واحدی با این نام وجود ندارد"
            )
        )
    }

    return (direct + synthetic).sortedByDescending { it.bytes }
}

private fun topFolder(item: StorageItem): Pair<String, String> {
    var path = (item.directPath ?: item.relativePath).replace('\\', '/')
    if (path.startsWith("content://")) return "Selected folder" to "پوشه انتخاب‌شده توسط کاربر"
    path = path.substringAfter("/storage/emulated/0/", path)
    path = path.substringAfter("/sdcard/", path)
    path = path.trim('/')
    val parts = path.split('/').filter { it.isNotBlank() }
    if (parts.isEmpty()) return "Shared storage" to "/"

    val first = when (parts.first().lowercase(Locale.US)) {
        "download", "downloads" -> "Downloads"
        "dcim" -> "DCIM"
        "pictures" -> "Pictures"
        "movies" -> "Movies"
        "music" -> "Music"
        "documents" -> "Documents"
        "bluetooth" -> "Bluetooth"
        "telegram" -> "Telegram"
        "whatsapp" -> "WhatsApp"
        "android" -> if (parts.getOrNull(1)?.equals("media", true) == true) "Android/media" else "Android"
        else -> parts.first()
    }
    val shownPath = if (first == "Android/media") "Internal storage/Android/media" else "Internal storage/$first"
    return first to shownPath
}

@Composable
private fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes.coerceAtLeast(0))

private fun formatDate(millis: Long): String = runCatching {
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(millis))
}.getOrDefault("")
