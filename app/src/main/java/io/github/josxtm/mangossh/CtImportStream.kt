package io.github.josxtm.mangossh

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.util.zip.GZIPInputStream

/** Limit applies to expanded data, protecting against memory pressure and gzip bombs. */
internal class CtLimitedInputStream(
    input: InputStream,
    private val limitBytes: Long,
    private val onProgress: (Long) -> Unit = {}
) : FilterInputStream(input) {
    private var totalBytes = 0L
    private var lastProgress = 0L
    override fun read(): Int {
        val result = super.read()
        if (result >= 0) increment(1)
        return result
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) increment(count)
        return count
    }
    private fun increment(value: Int) {
        totalBytes += value
        if (totalBytes > limitBytes)
            throw IllegalArgumentException("El JSON supera el límite de 256 MB descomprimidos")
        if (totalBytes - lastProgress >= 2L * 1024 * 1024) {
            lastProgress = totalBytes
            onProgress(totalBytes)
        }
    }
}

object CtImportStream {
    const val MAX_UNCOMPRESSED_BYTES: Long = 256L * 1024 * 1024

    fun parse(input: InputStream, onProgress: (Long) -> Unit = {}): CtCatalogReport {
        BufferedInputStream(input, 64 * 1024).use { buffered ->
            buffered.mark(2)
            val first = buffered.read()
            val second = buffered.read()
            buffered.reset()
            val decompressed: InputStream =
                if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered, 64 * 1024)
                else buffered
            CtLimitedInputStream(decompressed, MAX_UNCOMPRESSED_BYTES, onProgress).use { limited ->
                PushbackReader(InputStreamReader(limited, Charsets.UTF_8), 1).use { reader ->
                    val head = reader.read()
                    if (head >= 0 && head != 0xFEFF) reader.unread(head)
                    return CtCatalogStreamParser.parse(reader)
                }
            }
        }
    }
}
