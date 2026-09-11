package com.smartstorage.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class StorageRepository(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun scan(progress: (Int, String) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        progress(3, "در حال بررسی مجوزها")
        val allFiles = Environment.isExternalStorageManager()
        val usage = hasUsageAccess()

        val items = if (allFiles) {
            progress(10, "اسکن حافظه مشترک")
            scanDirectStorage(progress)
        } else {
            progress(10, "اسکن MediaStore")
            scanMediaStore(progress)
        }.toMutableList()

        progress(62, "بررسی پوشه‌های انتخاب‌شده")
        if (!allFiles) items += scanPersistedTree()

        val deduped = items.distinctBy { it.key }
        progress(72, "تحلیل هوشمند فایل‌ها")

        val apps = if (usage) {
            progress(80, "تحلیل فضای برنامه‌ها")
            scanAppStorage()
        } else emptyList()

        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0)
        val accessible = deduped.sumOf { it.sizeBytes.coerceAtLeast(0) }
        val appBytes = apps.sumOf { it.totalBytes.coerceAtLeast(0) }
        val unresolved = (used - accessible - appBytes).coerceAtLeast(0)
        val categories = deduped.groupBy { it.category }
            .map { (category, list) -> CategorySummary(category, list.sumOf { it.sizeBytes }, list.size) }
            .sortedByDescending { it.bytes }

        progress(100, "اسکن کامل شد")
        ScanResult(
            items = deduped.sortedByDescending { it.sizeBytes },
            apps = apps.sortedByDescending { it.totalBytes },
            device = DeviceStorage(total, used, free, accessible, appBytes, unresolved),
            categories = categories,
            allFilesAccess = allFiles,
            usageAccess = usage
        )
    }

    private suspend fun scanDirectStorage(progress: (Int, String) -> Unit): List<StorageItem> {
        val root = Environment.getExternalStorageDirectory()
        val result = ArrayList<StorageItem>(4096)
        var visited = 0
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val file = stack.removeLast()
            if (file.isDirectory) {
                if (isBlockedAndroidPrivate(file)) continue
                file.listFiles()?.forEach { stack.add(it) }
            } else if (file.isFile) {
                result += fromFile(file)
            }
            visited++
            if (visited % 600 == 0) {
                val p = (10 + (visited / 600).coerceAtMost(45)).coerceAtMost(55)
                progress(p, "اسکن فایل‌ها: ${result.size}")
            }
        }
        return result
    }

    private fun isBlockedAndroidPrivate(file: File): Boolean {
        val p = file.absolutePath.replace('\\', '/').lowercase()
        return p.contains("/android/data") || p.contains("/android/obb")
    }

    private fun fromFile(file: File): StorageItem {
        val path = file.parent ?: ""
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
        return buildItem(
            key = "file:${file.absolutePath}",
            uri = Uri.fromFile(file),
            name = file.name,
            size = file.length(),
            mime = mime,
            modified = file.lastModified(),
            path = path,
            directPath = file.absolutePath
        )
    }

    private fun scanMediaStore(progress: (Int, String) -> Unit): List<StorageItem> {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        val result = mutableListOf<StorageItem>()
        try {
            resolver.query(uri, projection, null, null, "${MediaStore.Files.FileColumns.SIZE} DESC")?.use { cursor ->
                val id = cursor.indexOf(MediaStore.Files.FileColumns._ID)
                val name = cursor.indexOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val size = cursor.indexOf(MediaStore.Files.FileColumns.SIZE)
                val mime = cursor.indexOf(MediaStore.Files.FileColumns.MIME_TYPE)
                val modified = cursor.indexOf(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val path = cursor.indexOf(MediaStore.Files.FileColumns.RELATIVE_PATH)
                var count = 0
                while (cursor.moveToNext()) {
                    val itemUri = ContentUris.withAppendedId(uri, cursor.longOrZero(id))
                    result += buildItem(
                        key = itemUri.toString(),
                        uri = itemUri,
                        name = cursor.stringOrEmpty(name),
                        size = cursor.longOrZero(size),
                        mime = cursor.stringOrNull(mime),
                        modified = cursor.longOrZero(modified) * 1000L,
                        path = cursor.stringOrEmpty(path),
                        directPath = null
                    )
                    count++
                    if (count % 500 == 0) progress((10 + count / 500).coerceAtMost(55), "MediaStore: $count فایل")
                }
            }
        } catch (_: SecurityException) {
            // The UI will explain that full-file access or media permissions are needed.
        }
        return result
    }

    private fun Cursor.indexOf(column: String): Int = getColumnIndex(column)
    private fun Cursor.stringOrEmpty(index: Int): String = if (index >= 0 && !isNull(index)) getString(index) ?: "" else ""
    private fun Cursor.stringOrNull(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null
    private fun Cursor.longOrZero(index: Int): Long = if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private fun scanPersistedTree(): List<StorageItem> {
        val uri = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
            .getString("treeUri", null)?.let(Uri::parse) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        val out = mutableListOf<StorageItem>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)
        var guard = 0
        while (stack.isNotEmpty() && guard < 50_000) {
            val doc = stack.removeLast()
            if (doc.isDirectory) {
                runCatching { doc.listFiles().forEach { stack.add(it) } }
            } else if (doc.isFile) {
                val name = doc.name ?: "بدون نام"
                out += buildItem(
                    key = doc.uri.toString(),
                    uri = doc.uri,
                    name = name,
                    size = doc.length(),
                    mime = doc.type,
                    modified = doc.lastModified(),
                    path = uri.toString(),
                    directPath = null
                )
            }
            guard++
        }
        return out
    }

    private fun buildItem(
        key: String,
        uri: Uri,
        name: String,
        size: Long,
        mime: String?,
        modified: Long,
        path: String,
        directPath: String?
    ): StorageItem {
        val category = SmartClassifier.classify(name, mime, path)
        val source = SmartClassifier.source(name, path)
        val (risk, recommendation, reasons) = SmartClassifier.assess(name, category, source, size, modified)
        return StorageItem(key, uri, name, size, mime, modified, path, source, category, risk, recommendation, reasons, directPath)
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun scanAppStorage(): List<AppStorage> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
        val statsManager = context.getSystemService(StorageStatsManager::class.java)
        return packages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                val stats = statsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    pkg,
                    Process.myUserHandle()
                )
                AppStorage(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = stats.cacheBytes
                )
            }.getOrNull()
        }
    }
}
