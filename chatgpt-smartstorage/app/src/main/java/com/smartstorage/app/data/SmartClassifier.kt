package com.smartstorage.app.data

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object SmartClassifier {
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub")
    private val backupTokens = listOf("backup", "bak", "msgstore", "crypt", "restore")
    private val tempTokens = listOf("cache", "temp", "tmp", "thumbnail", ".thumbnails")

    fun classify(name: String, mime: String?, path: String): StorageCategory {
        val lowerName = name.lowercase(Locale.US)
        val lowerPath = path.lowercase(Locale.US)
        val ext = lowerName.substringAfterLast('.', "")
        return when {
            backupTokens.any { lowerName.contains(it) || lowerPath.contains(it) } -> StorageCategory.BACKUPS
            tempTokens.any { lowerName.contains(it) || lowerPath.contains(it) } -> StorageCategory.TEMPORARY
            ext == "apk" -> StorageCategory.APK
            ext in archiveExtensions -> StorageCategory.ARCHIVES
            mime?.startsWith("image/") == true -> StorageCategory.PHOTOS
            mime?.startsWith("video/") == true -> StorageCategory.VIDEOS
            mime?.startsWith("audio/") == true -> StorageCategory.AUDIO
            ext in documentExtensions || mime?.startsWith("text/") == true || mime == "application/pdf" -> StorageCategory.DOCUMENTS
            lowerPath.contains("download") -> StorageCategory.DOWNLOADS
            else -> StorageCategory.OTHER
        }
    }

    fun source(name: String, path: String): String {
        val p = "$path/$name".lowercase(Locale.US)
        val app = when {
            "telegram" in p -> "Telegram"
            "whatsapp" in p -> "WhatsApp"
            "instagram" in p -> "Instagram"
            else -> null
        }
        val origin = when {
            "download" in p -> "Downloads"
            "bluetooth" in p -> "Bluetooth"
            "screenshots" in p || "screenshot" in p -> "Screenshot"
            "dcim/camera" in p || "/camera" in p -> "Camera"
            "documents" in p -> "Documents"
            "android/media" in p -> "App media"
            else -> "Shared storage"
        }
        return if (app != null && origin != "Shared storage") "$app / $origin" else app ?: origin
    }

    fun assess(
        name: String,
        category: StorageCategory,
        source: String,
        sizeBytes: Long,
        modifiedAtMillis: Long
    ): Triple<Int, Recommendation, List<String>> {
        var risk = 55
        val reasons = mutableListOf<String>()
        val ageDays = if (modifiedAtMillis > 0) {
            max(0, (System.currentTimeMillis() - modifiedAtMillis) / 86_400_000L)
        } else 0

        when (category) {
            StorageCategory.TEMPORARY -> { risk -= 35; reasons += "فایل موقت یا کش تشخیص داده شد" }
            StorageCategory.APK -> { risk -= 22; reasons += "فایل نصب APK است" }
            StorageCategory.ARCHIVES -> { risk -= 15; reasons += "فایل فشرده است" }
            StorageCategory.DOWNLOADS -> { risk -= 15; reasons += "در بخش دانلود قرار دارد" }
            StorageCategory.BACKUPS -> { risk += 40; reasons += "احتمال فایل پشتیبان وجود دارد" }
            StorageCategory.DOCUMENTS -> { risk += 22; reasons += "ممکن است سند مهم باشد" }
            StorageCategory.PHOTOS -> { risk += 18; reasons += "ممکن است تصویر شخصی باشد" }
            StorageCategory.VIDEOS -> risk += 5
            StorageCategory.AUDIO -> risk += 3
            StorageCategory.OTHER -> risk += 10
        }

        if (source.contains("Camera") || source.contains("Screenshot")) {
            risk += if (source.contains("Camera")) 15 else 5
            reasons += "منبع فایل: $source"
        }
        if (source.startsWith("Telegram") || source.startsWith("WhatsApp")) {
            risk += 5
            reasons += "منبع فایل: $source"
        }
        when {
            ageDays >= 365 -> { risk -= 18; reasons += "بیش از یک سال از آخرین تغییر گذشته" }
            ageDays >= 180 -> { risk -= 12; reasons += "بیش از ۶ ماه قدیمی است" }
            ageDays >= 60 -> { risk -= 6; reasons += "بیش از ۶۰ روز قدیمی است" }
            ageDays in 0..7 -> { risk += 15; reasons += "فایل جدید است" }
        }
        if (sizeBytes >= 1_000_000_000L) {
            risk -= 8
            reasons += "حجم بسیار زیادی دارد"
        } else if (sizeBytes >= 250_000_000L) {
            risk -= 4
            reasons += "فایل حجیم است"
        }
        if (name.startsWith(".") || name.endsWith(".tmp", true)) {
            risk -= 20
            reasons += "الگوی فایل موقت دارد"
        }

        risk = min(100, max(0, risk))
        val recommendation = when {
            risk <= 30 -> Recommendation.SAFE
            risk <= 70 -> Recommendation.REVIEW
            else -> Recommendation.PROTECTED
        }
        return Triple(risk, recommendation, reasons.distinct().take(4))
    }
}
