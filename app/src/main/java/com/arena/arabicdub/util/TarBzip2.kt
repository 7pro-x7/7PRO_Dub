package com.arena.arabicdub.util

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.Bzip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File

/**
 * فك ضغط أرشيفات tar.bz2 على الجهاز (أندرويد لا يوفر bzip2 افتراضيًا).
 * يعتمد على Apache Commons Compress (جافا خالصة، MIT/Apache).
 */
object TarBzip2 {

    /**
     * يفك [archive] داخل [destDir].
     * إن احتوت الحزمة على مجلد جذر وحيد يُزال (يُفكّ محتواه مباشرة في destDir).
     */
    fun extractTo(archive: File, destDir: File) {
        destDir.mkdirs()
        archive.inputStream().use { raw ->
            Bzip2CompressorInputStream(BufferedInputStream(raw)).use { bzip2 ->
                TarArchiveInputStream(bzip2).use { tar ->
                    var commonRoot: String? = null
                    var entry = tar.nextEntry
                    while (entry != null) {
                        if (commonRoot == null) {
                            commonRoot = entry.name.substringBefore('/').ifEmpty { null }
                        }
                        val root = commonRoot
                        val name = if (root != null && entry.name.startsWith("$root/")) {
                            entry.name.removePrefix("$root/")
                        } else {
                            entry.name
                        }
                        if (name.isNotEmpty()) {
                            val target = File(destDir, name)
                            // حماية من تسرب المسارات خارج مجلد الهدف
                            if (!target.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                                throw SecurityException("مسار غير صالح داخل الأرشيف: ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { out -> tar.copyTo(out) }
                            }
                        }
                        tar.closeEntry()
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }
}
