package io.github.josxtm.mangossh

import java.io.StringReader
import java.util.Locale

/** CT data stays local to the Android device. */
data class CtRecord(val matricula: String, val name: String, val ipLan: String, val netmask: String)
data class CtCatalogReport(
    val records: List<CtRecord>,
    val candidates: Int,
    val missingIp: Int,
    val invalidIp: Int,
    val missingMatricula: Int,
    val duplicates: Int
)

/** Shared API for small test examples and the large, streamed file importer. */
object CtCatalogParser {
    fun normalizeMatricula(value: String): String =
        value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), "")

    fun parse(raw: String): List<CtRecord> = parseReport(raw).records

    fun parseReport(raw: String): CtCatalogReport {
        require(raw.isNotBlank()) { "El archivo JSON está vacío" }
        // Do not duplicate parsers: exactly the same token-by-token code is used for
        // unit/UI tests and for real CT JSON loaded through Android's document picker.
        return CtCatalogStreamParser.parse(StringReader(raw.trimStart('\uFEFF')))
    }
}
