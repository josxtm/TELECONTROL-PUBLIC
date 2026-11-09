package io.github.josxtm.mangossh.core

import com.jcraft.jsch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Vector

interface SshClient {
    val state: StateFlow<ConnectionState>
    suspend fun connect(profile: Profile, credentials: Credentials, timeoutMs: Int = 15000, accept: suspend (HostIdentity) -> Boolean)
    suspend fun execute(command: DiagnosticCommand, timeoutMs: Long = 15000): CommandResult
    fun activateAdministrator()
    suspend fun openShell(columns: Int, rows: Int, output: (ByteArray) -> Unit): InteractiveShell
    fun disconnect()
}

interface InteractiveShell : AutoCloseable {
    suspend fun write(bytes: ByteArray)
    fun resize(columns: Int, rows: Int)
    val connected: Boolean
}

/** Pure JVM transport. No Android, UI, storage framework, or AutoControL dependencies. */
class SshEngine(private val hosts: TrustedHosts) : SshClient {
    private val mutableState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = mutableState
    private val connectionLock = Mutex()
    private val commandLock = Mutex()
    @Volatile private var session: Session? = null
    @Volatile private var admin = false
    @Volatile private var shell: InteractiveShell? = null
    private val monitor = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override suspend fun connect(profile: Profile, credentials: Credentials, timeoutMs: Int, accept: suspend (HostIdentity) -> Boolean) = withContext(Dispatchers.IO) { connectionLock.withLock {
        profile.validate()
        require(timeoutMs in 1000..60000)
        disconnect()
        mutableState.value = ConnectionState.CONNECTING
        val jsch = JSch()
        var changed = false
        jsch.hostKeyRepository = object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
                val old = hosts.fingerprint(profile.endpoint)
                if (old != null) {
                    if (old == fingerprint) return HostKeyRepository.OK
                    changed = true
                    return HostKeyRepository.CHANGED
                }
                val identity = HostIdentity(profile.endpoint, HostKey(host, key).type, fingerprint)
                return if (runBlocking { accept(identity) }) {
                    hosts.trust(identity)
                    HostKeyRepository.OK
                } else HostKeyRepository.NOT_INCLUDED
            }
            override fun add(key: HostKey, userInfo: UserInfo?) = Unit
            override fun remove(host: String?, type: String?) = Unit
            override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID() = "MangoSSH explicit SHA256 pins"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        try {
            withContext(Dispatchers.IO) {
                runInterruptible {
                    if (credentials is Credentials.PrivateKey) jsch.addIdentity("session", credentials.bytes, null, credentials.passphrase)
                    val next = jsch.getSession(profile.user, profile.host, profile.port)
                    session = next
                    next.setConfig("StrictHostKeyChecking", "yes")
                    next.setConfig("PreferredAuthentications", if (credentials is Credentials.Password) "password" else "publickey")
                    if (credentials is Credentials.Password) next.setPassword(credentials.bytes)
                    next.serverAliveInterval = 15000
                    next.serverAliveCountMax = 2
                    next.timeout = timeoutMs
                    next.connect(timeoutMs)
                }
            }
            ensureActiveContext()
            mutableState.value = ConnectionState.CONNECTED
            val connectedSession = session
            monitorJob = monitor.launch {
                while (isActive && connectedSession?.isConnected == true) delay(1000)
                if (session === connectedSession) disconnect()
            }
        } catch (error: Exception) {
            disconnect()
            currentCoroutineContext().ensureActive()
            if (changed) throw HostKeyChangedException()
            throw error
        } finally {
            jsch.removeAllIdentity()
            credentials.close()
        }
    } }

    private suspend fun ensureActiveContext() = currentCoroutineContext().ensureActive()

    override suspend fun execute(command: DiagnosticCommand, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) { commandLock.withLock {
        require(timeoutMs in 100..60000)
        val active = session?.takeIf { it.isConnected } ?: error("SSH desconectado")
        val channel = active.openChannel("exec") as ChannelExec
        val out = LimitedOutput(128 * 1024)
        val err = LimitedOutput(128 * 1024)
        val started = System.nanoTime()
        try {
            withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    channel.setCommand(command.shell)
                    channel.setInputStream(null)
                    channel.setOutputStream(out)
                    channel.setErrStream(err)
                    runInterruptible { channel.connect(minOf(timeoutMs, 10000).toInt()) }
                    while (!channel.isClosed) delay(20)
                }
            }
            CommandResult(command.id, out.text(), err.text(), channel.exitStatus,
                (System.nanoTime() - started) / 1_000_000, out.truncated || err.truncated)
        } finally { channel.disconnect() }
    } }

    override fun activateAdministrator() {
        check(state.value == ConnectionState.CONNECTED) { "Conecta primero" }
        admin = true
    }

    override suspend fun openShell(columns: Int, rows: Int, output: (ByteArray) -> Unit): InteractiveShell = withContext(Dispatchers.IO) {
        check(admin) { "Activa explícitamente el modo administrador" }
        shell?.close()
        val active = session?.takeIf { it.isConnected } ?: error("SSH desconectado")
        val channel = active.openChannel("shell") as ChannelShell
        channel.setPty(true)
        channel.setPtyType("xterm-256color", columns.coerceIn(10, 500), rows.coerceIn(2, 200), 0, 0)
        channel.setOutputStream(object : OutputStream() {
            override fun write(b: Int) = output(byteArrayOf(b.toByte()))
            override fun write(b: ByteArray, off: Int, len: Int) = output(b.copyOfRange(off, off + len))
        })
        val input = channel.outputStream
        try { runInterruptible { channel.connect(10000) } } catch (e: Exception) { channel.disconnect(); throw e }
        object : InteractiveShell {
            private val writeLock = Mutex()
            override val connected get() = channel.isConnected
            override suspend fun write(bytes: ByteArray) = writeLock.withLock {
                withContext(Dispatchers.IO) { input.write(bytes); input.flush() }
            }
            override fun resize(columns: Int, rows: Int) {
                channel.setPtySize(columns.coerceIn(10, 500), rows.coerceIn(2, 200), 0, 0)
            }
            override fun close() { channel.disconnect() }
        }.also { shell = it }
    }

    override fun disconnect() {
        admin = false
        monitorJob?.cancel()
        monitorJob = null
        shell?.close(); shell = null
        val old = session
        session = null
        old?.disconnect()
        mutableState.value = ConnectionState.DISCONNECTED
    }
}

internal class LimitedOutput(private val limit: Int) : OutputStream() {
    private val buffer = java.io.ByteArrayOutputStream()
    @Volatile var truncated = false
        private set
    @Synchronized override fun write(b: Int) {
        if (buffer.size() < limit) buffer.write(b) else truncated = true
    }
    @Synchronized override fun write(b: ByteArray, off: Int, len: Int) {
        val count = minOf(len, limit - buffer.size())
        buffer.write(b, off, count)
        if (count < len) truncated = true
    }
    @Synchronized fun text(): String = buffer.toString("UTF-8")
}
