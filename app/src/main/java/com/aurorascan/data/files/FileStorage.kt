package com.aurorascan.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal-storage layout from blueprint section 12. Database stores RELATIVE
 * paths; this class resolves them against the app's private files dir.
 *
 * files/documents/{documentId}/pages/{pageId}/original.jpg
 *                                            /preview.webp ...
 *                              /exports/...
 */
@Singleton
class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.filesDir, "documents").apply { mkdirs() }

    data class ImportedImage(
        val relativePath: String,
        val width: Int,
        val height: Int,
        val checksumSha256: String,
    )

    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    private fun relativeOf(file: File): String =
        file.relativeTo(context.filesDir).path

    fun pageDir(documentId: String, pageId: String): File =
        File(root, "$documentId/pages/$pageId").apply { mkdirs() }

    fun exportsDir(documentId: String): File =
        File(root, "$documentId/exports").apply { mkdirs() }

    /**
     * Copies a (possibly temporary) scanner output URI into permanent internal
     * storage using atomic write-then-rename (blueprint 12).
     */
    suspend fun importOriginal(
        source: Uri,
        documentId: String,
        pageId: String,
    ): ImportedImage = withContext(Dispatchers.IO) {
        val dir = pageDir(documentId, pageId)
        val finalFile = File(dir, "original.jpg")
        val tmpFile = File(dir, "original.jpg.tmp")

        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(source)?.use { input ->
            tmpFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } ?: error("Unable to open scanner output stream: $source")

        check(tmpFile.length() > 0) { "Imported file is empty: $source" }
        if (finalFile.exists()) finalFile.delete()
        check(tmpFile.renameTo(finalFile)) { "Atomic rename failed for $finalFile" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(finalFile.absolutePath, bounds)

        ImportedImage(
            relativePath = relativeOf(finalFile),
            width = bounds.outWidth.coerceAtLeast(0),
            height = bounds.outHeight.coerceAtLeast(0),
            checksumSha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    /**
     * Writes an app-synthesized image (e.g. a composited ID-card page) as the
     * page original, using the same atomic write-then-rename guarantee.
     */
    suspend fun writeOriginalBitmap(
        documentId: String,
        pageId: String,
        bitmap: Bitmap,
        quality: Int = 92,
    ): ImportedImage = withContext(Dispatchers.IO) {
        val dir = pageDir(documentId, pageId)
        val finalFile = File(dir, "original.jpg")
        val tmpFile = File(dir, "original.jpg.tmp")

        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
        tmpFile.outputStream().use { it.write(bytes); it.flush() }

        check(tmpFile.length() > 0) { "Composed image is empty" }
        if (finalFile.exists()) finalFile.delete()
        check(tmpFile.renameTo(finalFile)) { "Atomic rename failed for $finalFile" }

        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        ImportedImage(
            relativePath = relativeOf(finalFile),
            width = bitmap.width,
            height = bitmap.height,
            checksumSha256 = checksum,
        )
    }

    /** Removes interrupted temporary files on startup (blueprint 12). */
    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".tmp") }
            .forEach { runCatching { it.delete() } }
    }
}
