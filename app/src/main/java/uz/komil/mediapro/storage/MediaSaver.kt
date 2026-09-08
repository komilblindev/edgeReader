package uz.komil.mediapro.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import uz.komil.mediapro.data.ErrorLog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes a finished media file into the public Downloads area, into the
 * language-named operation folder (spec section 7).
 *
 *  - API 29+: MediaStore.Downloads with RELATIVE_PATH = "Download/MVD/<folder>"
 *  - API 28 : legacy File write into Environment.DIRECTORY_DOWNLOADS/MVD/<folder>
 *            + a MediaScanner scan so file managers / share pickers see it.
 *
 * Returns the display name + the content/file URI, or throws a descriptive
 * exception the caller logs to ErrorLog.
 */
object MediaSaver {

    /**
     * Save [srcFile] as [displayName] into [folder]. Returns a Uri usable by
     * ACTION_VIEW / ACTION_SEND / open-folder, or null when the user picked an
     * action type that maps to the legacy path.
     */
    @Throws(Exception::class)
    suspend fun save(ctx: Context, srcFile: File, displayName: String, folder: MediaFolder): String {
        val relFolder = MediaFolders.relativePath(ctx, folder)
        return if (Build.VERSION.SDK_INT >= 29) {
            saveToMediaStore(ctx, srcFile, displayName, relFolder)
        } else {
            saveLegacy(ctx, srcFile, displayName, relFolder)
        }
    }

    /** Copy a byte stream (already fully in memory) into the folder. */
    @Throws(Exception::class)
    suspend fun saveBytes(
        ctx: Context,
        data: ByteArray,
        displayName: String,
        mime: String,
        folder: MediaFolder
    ): String {
        val relFolder = MediaFolders.relativePath(ctx, folder)
        return if (Build.VERSION.SDK_INT >= 29) {
            saveBytesToMediaStore(ctx, data, displayName, mime, relFolder)
        } else {
            saveBytesLegacy(ctx, data, displayName, relFolder)
        }
    }

    // ---------- API 29+ : MediaStore.Downloads ----------

    private fun saveToMediaStore(
        ctx: Context,
        srcFile: File,
        displayName: String,
        relFolder: String
    ): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitize(displayName))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relFolder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert returned null")
        resolver.openOutputStream(uri).use { os ->
            if (os == null) throw IllegalStateException("openOutputStream null for $uri")
            srcFile.inputStream().use { it.copyTo(os) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }

    private fun saveBytesToMediaStore(
        ctx: Context,
        data: ByteArray,
        displayName: String,
        mime: String,
        relFolder: String
    ): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitize(displayName))
            put(MediaStore.MediaColumns.MIME_TYPE, mime.ifBlank { mimeFor(displayName) })
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relFolder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert returned null")
        resolver.openOutputStream(uri).use { os ->
            if (os == null) throw IllegalStateException("openOutputStream null for $uri")
            os.write(data)
            os.flush()
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }

    // ---------- API 28 : legacy file write ----------

    private fun saveLegacy(
        ctx: Context,
        srcFile: File,
        displayName: String,
        relFolder: String
    ): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            relFolder
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("mkdirs failed: $dir")
        }
        val dest = File(dir, sanitize(displayName))
        srcFile.inputStream().use { inp -> FileOutputStream(dest).use { inp.copyTo(it) } }
        scan(ctx, dest)
        return dest.absolutePath
    }

    private fun saveBytesLegacy(
        ctx: Context,
        data: ByteArray,
        displayName: String,
        relFolder: String
    ): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            relFolder
        )
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("mkdirs failed: $dir")
        val dest = File(dir, sanitize(displayName))
        FileOutputStream(dest).use { it.write(data) }
        scan(ctx, dest)
        return dest.absolutePath
    }

    private fun scan(ctx: Context, file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                ctx, arrayOf(file.absolutePath), null, null
            )
        } catch (e: Exception) {
            ErrorLog.e("MediaSaver", "scan failed", e)
        }
    }

    private fun sanitize(name: String): String {
        val n = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return n.ifBlank { "media" + System.currentTimeMillis() }
    }

    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "m4v" -> "video/x-m4v"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "ts", "m2ts" -> "video/mp2t"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/x-wav"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "amr" -> "audio/amr"
            "m3u8" -> "application/vnd.apple.mpegurl"
            else -> "application/octet-stream"
        }
    }
}
