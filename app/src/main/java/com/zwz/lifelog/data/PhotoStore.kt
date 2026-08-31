package com.zwz.lifelog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 照片存储：统一放在应用私有目录 filesDir/photos 下，随备份一起打包。
 * 保存时压缩到长边 1600px、质量 85%，避免几年后照片撑爆存储。
 */
class PhotoStore(private val context: Context) {

    companion object {
        private const val TAG = "PhotoStore"
        private const val MAX_EDGE = 1600
        private const val QUALITY = 85
    }

    val photoDir: File
        get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    fun fileOf(name: String): File = File(photoDir, name)

    suspend fun saveFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            val original = BitmapFactory.decodeStream(stream, null, null)
            stream.close()
            if (original == null) return@runCatching null

            val scaled = scaleIfNeeded(original)
            val name = "${UUID.randomUUID()}.jpg"
            val out = File(photoDir, name)
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, fos)
            }
            if (scaled !== original) scaled.recycle()
            if (original.isRecycled.not()) original.recycle()
            name
        }.getOrElse { e ->
            Log.e(TAG, "保存照片失败", e)
            null
        }
    }

    private fun scaleIfNeeded(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val max = maxOf(w, h)
        if (max <= MAX_EDGE) return src
        val ratio = MAX_EDGE.toFloat() / max
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    fun loadBitmap(name: String): Bitmap? = runCatching {
        val f = File(photoDir, name)
        if (!f.exists()) return null
        BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()

    fun delete(name: String) {
        runCatching { File(photoDir, name).delete() }
    }
}
