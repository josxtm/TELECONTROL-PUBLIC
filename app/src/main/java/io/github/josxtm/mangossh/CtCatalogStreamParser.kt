package io.github.josxtm.mangossh

import android.util.JsonReader
import android.util.JsonToken
import io.github.josxtm.mangossh.core.Validation
import java.io.Reader
import java.text.Normalizer
import java.util.Locale

/**
 * Streaming reader for confidential, large CT catalogs.
 * Reads one JSON token at a time; never materializes the complete source file.
 * Only keeps a small set of fields per record and the resulting CT index.
 */
object CtCatalogStreamParser {
    private fun key(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")

    private val idKeys = setOf("MATRICULA", "CTMATRICULA", "MATRICULACT", "IDCT")
    private val ipKeys = setOf("IPLAN", "LANIP", "DIRECCIONIPLAN", "DIRECCIONLAN", "IPREDLAN")
    private val nameKeys = setOf("NOMBRE", "NOMBRECT", "NOMBRECENTRO", "CENTRO")
    private val maskKeys = setOf("MASCARA", "MASCARARED", "NETMASK", "SUBNETMASK", "LANMASK")
    private val descriptorNames = setOf("NOMBRE", "CAMPO", "FIELD", "CLAVE", "KEY", "ETIQUETA")
    private val descriptorValues = setOf("VALOR", "VALUE", "DATO", "DATA", "TEXT", "TEXTO")
    private val fieldContainers = setOf("CAMPOS", "FIELDS", "DATOS", "DATA", "FICHA", "ATRIBUTOS")
    private val columnKeys = setOf("COLUMNS", "COLUMNAS", "HEADERS", "CABECERAS")
    private val rowKeys = setOf("ROWS", "FILAS", "REGISTROS", "DATA", "DATOS", "VALUES")
    private val recognized = idKeys + ipKeys + nameKeys + maskKeys + descriptorNames + descriptorValues
    // Depth bounds recursion; the separate input stream bounds decoded JSON size to 256 MB.
    // Do not impose an arbitrary total-element limit: CT exports can contain millions
    // of fields that are irrelevant to the indexed matrícula/IP\u0020LAN pairs.
    private const val MAX_DEPTH = 24
    private const val MAX_RECORDS = 300_000
    private const val MAX_COLUMNS = 256
    // TELECONTROL_CT_V1 has hundreds of source fields, separate from generic
    // tabular JSON. Each record prepends a fixed 17-item search summary.
    private const val MAX_TELECONTROL_FIELDS = 2_048
    private const val TELECONTROL_PREFIX_FIELDS = 17

    private data class TelecontrolLayout(
        val columnCount: Int,
        val idColumn: Int,
        val nameColumn: Int,
        val lanColumn: Int
    )

    fun parse(reader: Reader): CtCatalogReport {
        val json = JsonReader(reader)
        json.isLenient = false
        val found = linkedMapOf<String, CtRecord>()
        var candidates = 0
        var missingIp = 0
        var invalidIp = 0
        var missingId = 0
        var duplicates = 0

        fun tick(depth: Int) {
            require(depth <= MAX_DEPTH) { "JSON demasiado profundo" }
        }

        fun isId(value: String): Boolean {
            val id = CtCatalogParser.normalizeMatricula(value)
            return id.matches(Regex("[A-Z0-9-]{1,40}")) && id.any { it.isDigit() }
        }

        fun add(fields: Map<String, String>, fallback: String, keepWithoutIp: Boolean = false) {
            val suppliedId = idKeys.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) }
                ?: fallback.takeIf(::isId).orEmpty()
            val ipLan = ipKeys.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) }.orEmpty()
            if (suppliedId.isBlank() && ipLan.isBlank()) return
            candidates++
            if (candidates > MAX_RECORDS) throw IllegalArgumentException("Más de 300.000 registros CT")
            val id = CtCatalogParser.normalizeMatricula(suppliedId)
            if (!isId(id)) { missingId++; return }
            if (ipLan.isBlank()) {
                missingIp++
                if (!keepWithoutIp) return
            }
            if (ipLan.isNotBlank() && !Validation.ipv4(ipLan)) { invalidIp++; return }
            if (id in found) { duplicates++; return }
            found[id] = CtRecord(id,
                nameKeys.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) } ?: id,
                ipLan, maskKeys.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) }.orEmpty())
        }

        fun readScalar(): String = when (json.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> json.nextString().trim().take(1024)
            else -> { json.skipValue(); "" }
        }

        fun consumeColumns(depth: Int): List<String> {
            tick(depth)
            val keys = mutableListOf<String>()
            json.beginArray()
            while (json.hasNext()) {
                require(keys.size < MAX_COLUMNS) { "Tabla con más de 256 columnas" }
                keys.add(key(readScalar()))
            }
            json.endArray()
            return keys
        }

        // Native TELECONTROL_CT_V1:
        // {
        //   "format":"TELECONTROL_CT_V1", "version":1,
        //   "fieldNames":["MATRICULA [BDI]",...,"IP\u0020LAN [EDIT]",...],
        //   "records":{"28TEST1":[17 summary values, ...all fieldNames values]}
        // }
        // Unlike conventional JSON rows, this format uses a matrícula-keyed
        // dictionary and long positional arrays. Keep only IDs/names/IPs in RAM.
        fun readTelecontrolLayout(depth: Int): TelecontrolLayout {
            tick(depth)
            json.beginArray()
            var count = 0
            var id = -1
            var name = -1
            var lan = -1
            while (json.hasNext()) {
                require(count < MAX_TELECONTROL_FIELDS) {
                    "TELECONTROL_CT_V1: demasiados nombres de campo"
                }
                when (key(readScalar())) {
                    "MATRICULABDI", "MATRICULA" -> if (id < 0) id = count
                    "NOMBREBDI", "NOMBRE" -> if (name < 0) name = count
                    "IPLANEDIT", "IPLAN" -> if (lan < 0) lan = count
                }
                count++
            }
            json.endArray()
            require(id >= 0 && lan >= 0) {
                "TELECONTROL_CT_V1: faltan los campos MATRÍCULA [BDI] o IP\u0020LAN [EDIT]"
            }
            return TelecontrolLayout(count, id, name, lan)
        }

        fun readTelecontrolRecords(layout: TelecontrolLayout, depth: Int) {
            tick(depth)
            json.beginObject()
            while (json.hasNext()) {
                val recordKey = json.nextName()
                if (json.peek() != JsonToken.BEGIN_ARRAY) {
                    json.skipValue()
                    continue
                }
                json.beginArray()
                var index = 0
                var prefixId = ""
                var prefixName = ""
                var prefixLan = ""
                var fieldId = ""
                var fieldName = ""
                var fieldLan = ""
                while (json.hasNext()) {
                    when (index) {
                        0 -> prefixId = readScalar()
                        1 -> prefixName = readScalar()
                        13 -> prefixLan = readScalar()
                        TELECONTROL_PREFIX_FIELDS + layout.idColumn -> fieldId = readScalar()
                        TELECONTROL_PREFIX_FIELDS + layout.nameColumn -> fieldName = readScalar()
                        TELECONTROL_PREFIX_FIELDS + layout.lanColumn -> fieldLan = readScalar()
                        else -> json.skipValue()
                    }
                    index++
                }
                json.endArray()
                // Never silently shift field positions or use a stale record's IP.
                candidates++
                require(candidates <= MAX_RECORDS) { "Más de 300.000 registros CT" }
                val keyId = CtCatalogParser.normalizeMatricula(recordKey)
                val summaryId = CtCatalogParser.normalizeMatricula(prefixId)
                val fullId = CtCatalogParser.normalizeMatricula(fieldId)
                if (index != TELECONTROL_PREFIX_FIELDS + layout.columnCount ||
                    !isId(keyId) || keyId != summaryId || keyId != fullId) {
                    missingId++
                    continue
                }
                if (prefixLan != fieldLan) {
                    // Contradictory LAN values must not trigger any network action.
                    invalidIp++
                    continue
                }
                if (fieldLan.isBlank()) missingIp++
                else if (!Validation.ipv4(fieldLan)) {
                    invalidIp++
                    continue
                }
                if (keyId in found) {
                    duplicates++
                    continue
                }
                // Keep CTs without an IP\u0020LAN searchable, but disable network
                // planning for them until a technician supplies a verified IP.
                found[keyId] = CtRecord(
                    keyId,
                    fieldName.ifBlank { prefixName }.ifBlank { keyId },
                    fieldLan,
                    "" // A real subnet mask is not present in this CT export.
                )
            }
            json.endObject()
        }

        // Recursion is implemented through a local parser class to avoid storing the tree.
        class Walker {
            fun value(fallback: String = "", depth: Int = 0): Map<String, String> {
                tick(depth)
                return when (json.peek()) {
                    JsonToken.BEGIN_OBJECT -> objectValue(fallback, depth + 1)
                    JsonToken.BEGIN_ARRAY -> arrayValue(depth + 1)
                    else -> { json.skipValue(); emptyMap() }
                }
            }

            private fun arrayValue(depth: Int): Map<String, String> {
                val descriptors = linkedMapOf<String, String>()
                json.beginArray()
                while (json.hasNext()) {
                    val field = value("", depth + 1)
                    val label = descriptorNames.firstNotNullOfOrNull {
                        field[it]?.takeIf(String::isNotBlank)
                    }
                    val content = descriptorValues.firstNotNullOfOrNull {
                        field[it]?.takeIf(String::isNotBlank)
                    }
                    if (label != null && content != null && key(label) in
                        (idKeys + ipKeys + nameKeys + maskKeys)) descriptors[key(label)] = content
                }
                json.endArray()
                return descriptors
            }

            private fun readTableRows(columns: List<String>, depth: Int) {
                json.beginArray()
                val idxId = columns.indexOfFirst { it in idKeys }
                val idxIp = columns.indexOfFirst { it in ipKeys }
                val idxName = columns.indexOfFirst { it in nameKeys }
                val idxMask = columns.indexOfFirst { it in maskKeys }
                if (idxId < 0 || idxIp < 0) {
                    while (json.hasNext()) json.skipValue()
                    json.endArray()
                    return
                }
                while (json.hasNext()) {
                    tick(depth)
                    if (json.peek() != JsonToken.BEGIN_ARRAY) {
                        value("", depth + 1)
                        continue
                    }
                    val row = linkedMapOf<String, String>()
                    json.beginArray()
                    var index = 0
                    while (json.hasNext()) {
                        val fieldKey = when (index) {
                            idxId -> "MATRICULA"
                            idxIp -> "IPLAN"
                            idxName -> "NOMBRE"
                            idxMask -> "MASCARA"
                            else -> null
                        }
                        if (fieldKey != null) row[fieldKey] = readScalar()
                        else json.skipValue()
                        index++
                    }
                    json.endArray()
                    add(row, "")
                }
                json.endArray()
            }

            private fun objectValue(fallback: String, depth: Int): Map<String, String> {
                val fields = linkedMapOf<String, String>()
                var columns: List<String>? = null
                var telecontrolFormat = false
                var telecontrolVersion = 0
                var telecontrolLayout: TelecontrolLayout? = null
                json.beginObject()
                while (json.hasNext()) {
                    val originalName = json.nextName()
                    val name = key(originalName)
                    if (depth == 1) {
                        when (name) {
                            "FORMAT" -> {
                                telecontrolFormat = readScalar() == "TELECONTROL_CT_V1"
                                continue
                            }
                            "VERSION" -> {
                                telecontrolVersion = readScalar().toIntOrNull() ?: 0
                                continue
                            }
                            "FIELDNAMES" -> if (telecontrolFormat) {
                                require(json.peek() == JsonToken.BEGIN_ARRAY) {
                                    "TELECONTROL_CT_V1: fieldNames debe ser una lista"
                                }
                                telecontrolLayout = readTelecontrolLayout(depth + 1)
                                continue
                            }
                            "RECORDS" -> if (telecontrolFormat) {
                                require(telecontrolVersion == 1) {
                                    "Versión TELECONTROL_CT_V1 no compatible"
                                }
                                val layout = requireNotNull(telecontrolLayout) {
                                    "TELECONTROL_CT_V1: fieldNames debe preceder a records"
                                }
                                require(json.peek() == JsonToken.BEGIN_OBJECT) {
                                    "TELECONTROL_CT_V1: records debe ser un objeto"
                                }
                                readTelecontrolRecords(layout, depth + 1)
                                continue
                            }
                        }
                    }
                    when (json.peek()) {
                        JsonToken.BEGIN_OBJECT -> {
                            val child = value(if (isId(originalName)) originalName else "", depth + 1)
                            if (name in fieldContainers) {
                                // Embedded {campos:{IP\u0020LAN:..., ...}}.
                                for ((k, v) in child) if (k in recognized && k !in fields) fields[k] = v
                            } else if (name in recognized) {
                                // Wrapped scalar, e.g. "MATRÍCULA":{"valor":"28LABX"}.
                                val wrapped = descriptorValues.firstNotNullOfOrNull {
                                    child[it]?.takeIf(String::isNotBlank)
                                }
                                if (wrapped != null) fields[name] = wrapped
                            }
                        }
                        JsonToken.BEGIN_ARRAY -> {
                            when {
                                name in columnKeys -> columns = consumeColumns(depth + 1)
                                name in rowKeys && columns != null -> readTableRows(columns, depth + 1)
                                else -> {
                                    val child = value("", depth + 1)
                                    if (name in fieldContainers)
                                        for ((k, v) in child) if (k in recognized) fields.putIfAbsent(k, v)
                                }
                            }
                        }
                        else -> {
                            if (name in recognized) fields[name] = readScalar()
                            else json.skipValue()
                        }
                    }
                }
                json.endObject()
                // Count complete row once; descriptor containers do not count as CTs.
                add(fields, fallback)
                return fields
            }
        }

        val root = json.peek()
        require(root == JsonToken.BEGIN_OBJECT || root == JsonToken.BEGIN_ARRAY) {
            "El JSON debe contener objetos o listas"
        }
        Walker().value()
        require(json.peek() == JsonToken.END_DOCUMENT) { "Hay contenido tras el final del JSON" }
        json.close()
        return CtCatalogReport(found.values.sortedBy { it.matricula },
            candidates, missingIp, invalidIp, missingId, duplicates)
    }
}
