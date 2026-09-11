package com.smartstorage.app.ui

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.app.StorageUiState
import com.smartstorage.app.data.AppStorage
import com.smartstorage.app.data.ScanResult
import com.smartstorage.app.data.StorageItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MemoryTabV3(val title: String) {
    HOME("خانه"), FOLDERS("پوشه‌ها"), SMART("هوشمند"), FILES("فایل‌ها"), APPS("برنامه‌ها")
}

private enum class FolderAccessV3(val title: String) {
    DIRECT("قابل مشاهده"), STATISTICAL("آماری"), ESTIMATED("تخمینی")
}

private data class FolderStatV3(
    val name: String,
    val path: String,
    val bytes: Long,
    val count: Int,
    val access: FolderAccessV3,
    val note: String = ""
)

@Composable
fun MemoryAppV3(
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
            var tab by remember { mutableStateOf(MemoryTabV3.HOME) }
            var confirmDelete by remember { mutableStateOf(false) }
            val selectedItems = remember(state.selected, state.result.items) {
                state.result.items.filter { it.key in state.selected }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MemoryTabV3.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = {
                                    Icon(
                                        when (item) {
                                            MemoryTabV3.HOME -> Icons.Default.Home
                                            MemoryTabV3.FOLDERS -> Icons.Default.Folder
                                            MemoryTabV3.SMART -> Icons.Default.AutoAwesome
                                            MemoryTabV3.FILES -> Icons.Default.Description
                                            MemoryTabV3.APPS -> Icons.Default.Apps
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
                        MemoryTabV3.HOME -> MemoryHomeV3(
                            state = state,
                            onScan = onScan,
                            onAllFiles = onRequestAllFiles,
                            onUsage = onRequestUsage,
                            onFolder = onChooseFolder,
                            openFolders = { tab = MemoryTabV3.FOLDERS },
                            openFiles = { tab = MemoryTabV3.FILES },
                            openApps = { tab = MemoryTabV3.APPS }
                        )
                        MemoryTabV3.FOLDERS -> FolderMapScreenV3(
                            state = state,
                            onAllFiles = onRequestAllFiles,
                            onUsage = onRequestUsage,
                            openApps = { tab = MemoryTabV3.APPS },
                            openFiles = { tab = MemoryTabV3.FILES }
                        )
                        MemoryTabV3.SMART -> SmartCleanupScreenV3(
                            state,
                            onToggle,
                            onSelectRecommended,
                            onClearSelection
                        ) { confirmDelete = true }
                        MemoryTabV3.FILES -> MemoryFilesScreenV3(
                            state,
                            onToggle,
                            onQuery
                        ) { confirmDelete = true }
                        MemoryTabV3.APPS -> MemoryAppsScreenV3(
                            state,
                            onRequestUsage,
                            onOpenAppStorage
                        )
                    }

                    if (state.scanning) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(32.dp),
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
                        Text(
                            "${selectedItems.size} فایل با حجم ${formatBytesV3(selectedItems.sumOf { it.sizeBytes })} انتخاب شده است. حذف فقط با تأیید شما انجام می‌شود."
                        )
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
private fun MemoryHomeV3(
    state: StorageUiState,
    onScan: () -> Unit,
    onAllFiles: () -> Unit,
    onUsage: () -> Unit,
    onFolder: () -> Unit,
    openFolders: () -> Unit,
    openFiles: () -> Unit,
    openApps: () -> Unit
) {
    val result = state.result
    val d = result.device
    val folderStats = remember(result.items, d.appBytes, d.unresolvedBytes) { buildFolderStatsV3(result) }
    val download = folderStats.firstOrNull { it.name == "Downloads" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Storage,
                    null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
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
                    val fraction = if (d.totalBytes > 0) {
                        (d.usedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f)
                    } else 0f
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("حافظه استفاده‌شده", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBytesV3(d.usedBytes), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("از ${formatBytesV3(d.totalBytes)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = fraction,
                        modifier = Modifier.fillMaxWidth().height(9.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(fraction * 100).toInt()}٪ پر • ${formatBytesV3(d.freeBytes)} آزاد", fontSize = 13.sp)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StorageMetricCardV3(
                    title = "Downloads",
                    value = formatBytesV3(download?.bytes ?: 0L),
                    subtitle = if (result.allFilesAccess) {
                        "${download?.count ?: 0} فایل • برای جزئیات لمس کنید"
                    } else "ممکن است ناقص باشد • لمس کنید",
                    icon = Icons.Default.Download,
                    modifier = Modifier.weight(1f),
                    onClick = openFolders
                )
                StorageMetricCardV3(
                    title = "داده برنامه‌ها",
                    value = formatBytesV3(d.appBytes),
                    subtitle = if (result.usageAccess) {
                        "Data شامل Cache • برای جزئیات لمس کنید"
                    } else "نیاز به Usage Access",
                    icon = Icons.Default.Apps,
                    modifier = Modifier.weight(1f),
                    onClick = if (result.usageAccess) openApps else onUsage
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StorageMetricCardV3(
                    title = "System / Protected",
                    value = formatBytesV3(d.unresolvedBytes),
                    subtitle = "باقیمانده غیرقابل فهرست • لمس کنید",
                    icon = Icons.Default.Lock,
                    modifier = Modifier.weight(1f),
                    onClick = openFolders
                )
                StorageMetricCardV3(
                    title = "فایل‌های قابل مشاهده",
                    value = formatBytesV3(d.accessibleBytes),
                    subtitle = "برای مشاهده فایل‌ها لمس کنید",
                    icon = Icons.Default.FolderOpen,
                    modifier = Modifier.weight(1f),
                    onClick = openFiles
                )
            }
        }

        if (result.usageAccess) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("روش محاسبه اصلاح‌شده", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "عدد «داده برنامه‌ها» فقط Data گزارش‌شده توسط Android است و با فایل‌های قابل مشاهده همپوشانی‌زدایی می‌شود. Cache زیرمجموعه Data است و دوباره جمع نمی‌شود.",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (!result.allFilesAccess) {
            item {
                AccessWarningCardV3(
                    title = "برای اندازه دقیق Downloads و پوشه‌های مشترک",
                    body = "دسترسی All files را فعال کن. بدون آن Android فقط بخشی از فایل‌ها را در اختیار Memory می‌گذارد.",
                    button = "فعال‌سازی دسترسی فایل‌ها",
                    onClick = onAllFiles
                )
            }
        }

        if (!result.usageAccess) {
            item {
                AccessWarningCardV3(
                    title = "برای دیدن داده برنامه‌ها",
                    body = "Usage Access را فعال کن تا Memory حجم Data برنامه‌ها را از Android دریافت کند.",
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

        item { SectionTitleV3("بزرگ‌ترین بخش‌های حافظه") }
        items(folderStats.take(8)) { stat ->
            FolderStatRowV3(
                stat = stat,
                onClick = when (stat.access) {
                    FolderAccessV3.STATISTICAL -> openApps
                    FolderAccessV3.DIRECT -> openFiles
                    FolderAccessV3.ESTIMATED -> openFolders
                }
            )
        }

        if (result.categories.isNotEmpty()) {
            item { SectionTitleV3("نوع فایل‌ها") }
            items(result.categories.take(6)) { c ->
                Card(modifier = Modifier.clickable(onClick = openFiles)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.category.faTitle, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${c.count} فایل • برای مشاهده لمس کنید",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatBytesV3(c.bytes), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun FolderMapScreenV3(
    state: StorageUiState,
    onAllFiles: () -> Unit,
    onUsage: () -> Unit,
    openApps: () -> Unit,
    openFiles: () -> Unit
) {
    val stats = remember(state.result.items, state.result.device) { buildFolderStatsV3(state.result) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("نقشه پوشه‌ها", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(
                "قابل مشاهده، آماری و تخمینی از هم جدا شده‌اند.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!state.result.allFilesAccess) {
            item {
                AccessWarningCardV3(
                    "اسکن پوشه‌ها کامل نیست",
                    "برای اندازه دقیق‌تر Download، DCIM، Movies، Pictures و Android/media دسترسی فایل‌ها را فعال کن.",
                    "فعال‌سازی",
                    onAllFiles
                )
            }
        }
        if (!state.result.usageAccess) {
            item {
                AccessWarningCardV3(
                    "داده خصوصی برنامه‌ها نامشخص است",
                    "برای مشاهده حجم آماری Data برنامه‌ها Usage Access لازم است.",
                    "فعال‌سازی",
                    onUsage
                )
            }
        }

        items(stats) { stat ->
            FolderStatRowV3(
                stat = stat,
                onClick = when (stat.access) {
                    FolderAccessV3.STATISTICAL -> openApps
                    FolderAccessV3.DIRECT -> openFiles
                    FolderAccessV3.ESTIMATED -> null
                }
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("چرا Android/data باز نمی‌شود؟", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android اجازه فهرست‌کردن مستقیم فایل‌های خصوصی سایر برنامه‌ها را به Memory نمی‌دهد. برای این بخش حجم Data از StorageStats گرفته می‌شود و برای مدیریت هر برنامه باید از صفحه «برنامه‌ها» وارد تنظیمات همان برنامه شوی."
                    )
                    TextButton(onClick = openApps) { Text("باز کردن برنامه‌ها") }
                }
            }
        }
    }
}

@Composable
private fun MemoryAppsScreenV3(
    state: StorageUiState,
    onUsage: () -> Unit,
    onOpenAppStorage: (String) -> Unit
) {
    val result = state.result
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("داده برنامه‌ها", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(
                "فایل خصوصی مستقیماً باز نمی‌شود؛ اما می‌توانی حجم Data هر برنامه را ببینی و تنظیمات رسمی Android آن را باز کنی.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!result.usageAccess) {
            item {
                AccessWarningCardV3(
                    "Usage Access لازم است",
                    "برای دریافت Data، Cache و App code برنامه‌ها این دسترسی را فعال کن.",
                    "فعال‌سازی",
                    onUsage
                )
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Data قابل انتساب", fontWeight = FontWeight.Bold)
                        Text(
                            formatBytesV3(result.device.appBytes),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "این عدد برای سازگاری با مصرف فیزیکی حافظه محدود شده است؛ App code جدا از Data است.",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        items(result.apps) { app ->
            AppStorageCardV3(app, onOpenAppStorage)
        }

        if (result.usageAccess && result.apps.isEmpty()) {
            item {
                Text(
                    "Android جزئیات برنامه قابل نمایشی برنگرداند. یک بار اسکن را دوباره اجرا کن.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppStorageCardV3(app: AppStorage, onOpenAppStorage: (String) -> Unit) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold)
                    Text(
                        app.packageName,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytesV3(app.dataBytes), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Data", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatV3("Cache", formatBytesV3(app.cacheBytes), Modifier.weight(1f))
                MiniStatV3("App code", formatBytesV3(app.appBytes), Modifier.weight(1f))
            }
            Text(
                "Cache داخل Data حساب شده و جداگانه به آن اضافه نمی‌شود.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { onOpenAppStorage(app.packageName) }) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(6.dp))
                Text("مدیریت در تنظیمات Android")
            }
        }
    }
}

@Composable
private fun MiniStatV3(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(9.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SmartCleanupScreenV3(
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
            Text("Memory پیشنهاد می‌دهد؛ حذف نهایی فقط با انتخاب و تأیید شماست.")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        formatBytesV3(recommended.sumOf { it.sizeBytes }),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
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
            MemoryFileRowV3(item, item.key in state.selected, onToggle)
        }
        if (state.selected.isNotEmpty()) {
            item {
                Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("حذف ${formatBytesV3(selectedBytes)} انتخاب‌شده")
                }
            }
        }
    }
}

@Composable
private fun MemoryFilesScreenV3(
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
        Text(
            "فقط فایل‌هایی که Android اجازه دیدن آن‌ها را می‌دهد.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                Text("حذف ${state.selected.size} فایل • ${formatBytesV3(selectedBytes)}")
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.key }) { item ->
                MemoryFileRowV3(item, item.key in state.selected, onToggle)
            }
        }
    }
}

@Composable
private fun StorageMetricCardV3(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessWarningCardV3(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit
) {
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
private fun FolderStatRowV3(stat: FolderStatV3, onClick: (() -> Unit)?) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (stat.access) {
                        FolderAccessV3.DIRECT -> Icons.Default.Folder
                        FolderAccessV3.STATISTICAL -> Icons.Default.Apps
                        FolderAccessV3.ESTIMATED -> Icons.Default.Lock
                    },
                    null,
                    tint = when (stat.access) {
                        FolderAccessV3.DIRECT -> MaterialTheme.colorScheme.primary
                        FolderAccessV3.STATISTICAL -> MaterialTheme.colorScheme.secondary
                        FolderAccessV3.ESTIMATED -> MaterialTheme.colorScheme.tertiary
                    }
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(stat.name, fontWeight = FontWeight.Bold)
                    Text(
                        stat.path,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytesV3(stat.bytes), fontWeight = FontWeight.Bold)
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
private fun MemoryFileRowV3(item: StorageItem, selected: Boolean, onToggle: (StorageItem) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle(item) })
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName.ifBlank { "بدون نام" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.category.faTitle} • ${item.source}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.relativePath.isNotBlank()) {
                    Text(
                        item.relativePath,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytesV3(item.sizeBytes), fontWeight = FontWeight.Bold)
                Text("ریسک ${item.riskScore}/100", fontSize = 10.sp)
                if (item.modifiedAtMillis > 0) {
                    Text(
                        formatDateV3(item.modifiedAtMillis),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitleV3(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

private fun buildFolderStatsV3(result: ScanResult): List<FolderStatV3> {
    val grouped = result.items.groupBy { topFolderV3(it) }
    val direct = grouped.map { (folder, files) ->
        FolderStatV3(
            name = folder.first,
            path = folder.second,
            bytes = files.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            count = files.size,
            access = FolderAccessV3.DIRECT,
            note = if (result.allFilesAccess) "اسکن مستقیم" else "بر اساس دسترسی فعلی"
        )
    }.filter { it.bytes > 0 }

    val synthetic = buildList {
        if (result.device.appBytes > 0 || !result.usageAccess) {
            add(
                FolderStatV3(
                    name = "App Data / Private",
                    path = "فضای Data برنامه‌ها؛ Cache زیرمجموعه آن است",
                    bytes = result.device.appBytes,
                    count = result.apps.size,
                    access = FolderAccessV3.STATISTICAL,
                    note = if (result.usageAccess) {
                        "آمار Android با سقف مصرف فیزیکی؛ لمس برای جزئیات"
                    } else "برای محاسبه Usage Access را فعال کنید"
                )
            )
        }
        add(
            FolderStatV3(
                name = "System / Protected / Unknown",
                path = "کد برنامه‌ها، سیستم و بخش‌های غیرقابل فهرست یا حل‌نشده",
                bytes = result.device.unresolvedBytes,
                count = 0,
                access = FolderAccessV3.ESTIMATED,
                note = "باقیمانده محاسبات؛ یک پوشه واقعی واحد نیست"
            )
        )
    }

    return (direct + synthetic).sortedByDescending { it.bytes }
}

private fun topFolderV3(item: StorageItem): Pair<String, String> {
    var path = (item.directPath ?: item.relativePath).replace('\\', '/')
    if (path.startsWith("content://")) {
        return "Selected folder" to "پوشه انتخاب‌شده توسط کاربر"
    }
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
        "android" -> if (parts.getOrNull(1)?.equals("media", true) == true) {
            "Android/media"
        } else "Android"
        else -> parts.first()
    }
    val shownPath = if (first == "Android/media") {
        "Internal storage/Android/media"
    } else "Internal storage/$first"
    return first to shownPath
}

@Composable
private fun formatBytesV3(bytes: Long): String =
    Formatter.formatShortFileSize(LocalContext.current, bytes.coerceAtLeast(0L))

private fun formatDateV3(millis: Long): String = runCatching {
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(millis))
}.getOrDefault("")
