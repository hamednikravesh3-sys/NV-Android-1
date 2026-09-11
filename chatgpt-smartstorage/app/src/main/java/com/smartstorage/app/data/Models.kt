package com.smartstorage.app.data

import android.net.Uri

enum class StorageCategory(val faTitle: String) {
    DOWNLOADS("دانلودها"),
    PHOTOS("تصاویر"),
    VIDEOS("ویدئوها"),
    AUDIO("صوت"),
    DOCUMENTS("اسناد"),
    APK("فایل نصب APK"),
    ARCHIVES("فایل فشرده"),
    BACKUPS("پشتیبان‌ها"),
    TEMPORARY("موقت و کش"),
    OTHER("سایر")
}

enum class Recommendation(val faTitle: String) {
    SAFE("کم‌ریسک"), REVIEW("نیاز به بررسی"), PROTECTED("حساس")
}

data class StorageItem(
    val key: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val modifiedAtMillis: Long,
    val relativePath: String,
    val source: String,
    val category: StorageCategory,
    val riskScore: Int,
    val recommendation: Recommendation,
    val reasons: List<String>,
    val directPath: String? = null
)

data class CategorySummary(
    val category: StorageCategory,
    val bytes: Long,
    val count: Int
)

data class AppStorage(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long
) {
    val totalBytes: Long get() = appBytes + dataBytes
}

data class DeviceStorage(
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val accessibleBytes: Long = 0,
    val appBytes: Long = 0,
    val unresolvedBytes: Long = 0
)

data class ScanResult(
    val items: List<StorageItem> = emptyList(),
    val apps: List<AppStorage> = emptyList(),
    val device: DeviceStorage = DeviceStorage(),
    val categories: List<CategorySummary> = emptyList(),
    val allFilesAccess: Boolean = false,
    val usageAccess: Boolean = false
)
