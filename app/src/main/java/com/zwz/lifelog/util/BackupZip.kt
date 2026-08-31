package com.zwz.lifelog.util

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份包：lifelog.json + photos/ 目录，打包成单个 zip，方便整体迁移。
 */
object BackupZip {

    fun create(zipFile: File, json: String, photoDir: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zos.putNextEntry(ZipEntry("lifelog.json"))
            zos.write(json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val photos = photoDir.listFiles()?.filter { it.isFile } ?: emptyList()
            photos.forEach { f ->
                zos.putNextEntry(ZipEntry("photos/${f.name}"))
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /** 解包，返回 json 文本；照片解压到 photoDir。 */
    fun extract(zipFile: File, photoDir: File): String {
        if (!photoDir.exists()) photoDir.mkdirs()
        var json = ""
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "lifelog.json" -> {
                        json = zis.readBytes().toString(Charsets.UTF_8)
                    }
                    entry.name.startsWith("photos/") && !entry.isDirectory -> {
                        val name = entry.name.removePrefix("photos/")
                        if (name.isNotBlank()) {
                            val out = File(photoDir, name)
                            FileOutputStream(out).use { zis.copyTo(it) }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return json
    }
}
