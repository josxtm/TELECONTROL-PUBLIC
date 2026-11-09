package io.github.josxtm.mangossh.core

import java.util.UUID

/** Public factory-default router address; never a CT address. */
fun mangoDefaultHost(): String = listOf(192, 168, 8, 1).joinToString(".")

enum class AuthType { PASSWORD, PRIVATE_KEY }
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Mango", val host: String = mangoDefaultHost(),
    val port: Int = 22, val user: String = "root", val auth: AuthType = AuthType.PASSWORD,
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 80) { "Nombre inválido" }
        require(Validation.host(host)) { "IP o hostname inválido" }
        require(port in 1..65535) { "Puerto entre 1 y 65535" }
        require(user.matches(Regex("[a-zA-Z0-9_.@-]{1,64}"))) { "Usuario inválido" }
    }
    val endpoint: String get() = "[$host]:$port"
}

object Validation {
    fun ipv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all {
            it.matches(Regex("0|[1-9][0-9]{0,2}")) && (it.toIntOrNull() ?: 256) in 0..255
        }
    }
    fun host(value: String): Boolean {
        if (ipv4(value)) return true
        if (value.all { it.isDigit() || it == '.' }) return false
        return value.length in 1..253 && value.split('.').all {
            it.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"))
        }
    }
}

sealed class Credentials : AutoCloseable {
    class Password(val bytes: ByteArray) : Credentials() { override fun close() { bytes.fill(0) } }
    class PrivateKey(val bytes: ByteArray, val passphrase: ByteArray? = null) : Credentials() {
        override fun close() { bytes.fill(0); passphrase?.fill(0) }
    }
}
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }
data class HostIdentity(val endpoint: String, val algorithm: String, val sha256: String)
interface TrustedHosts {
    fun fingerprint(endpoint: String): String?
    fun trust(identity: HostIdentity)
}
data class CommandResult(
    val commandId: String, val stdout: String, val stderr: String, val exitCode: Int,
    val durationMs: Long, val truncated: Boolean,
) {
    val successful get() = exitCode == 0
    fun report() = "stdout:\n$stdout\nstderr:\n$stderr\nCódigo: $exitCode · ${durationMs} ms" +
        if (truncated) "\n[Salida truncada]" else ""
}
class HostKeyChangedException : SecurityException("La identidad SSH ha cambiado. Conexión rechazada; verifica el equipo por un canal independiente.")
