package io.github.josxtm.mangossh

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import io.github.josxtm.mangossh.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TrustRequest(val identity: HostIdentity, val decision: CompletableDeferred<Boolean>)
data class ScreenState(
    val selected: Profile? = null, val connectedProfile: Profile? = null, val admin: Boolean = false,
    val busy: Boolean = false, val message: String = "Sin conexión · Diagnóstico",
    val result: String = "Conecta a un equipo para ver resultados reales.",
    val trust: TrustRequest? = null, val audit: String = "", val terminalEpoch: Int = 0,
    val terminalPreview: String = "", val terminalUiStatus: String = "Cargando terminal…",
    val trusted: List<Pair<String, String>> = emptyList(),
)

data class CtProbe(val name: String, val ip: String, val status: String, val detail: String = "")
data class CtSession(
    val plan: CtPlan? = null, val stage: String = "Sin configurar",
    val configured: Boolean = false, val working: Boolean = false,
    val probes: List<CtProbe> = emptyList(), val lastOutput: String = ""
)

data class CtImportUiState(
    val phase: String = "idle", val message: String = "No hay catálogo JSON cargado",
    val fileName: String = "", val valid: Int = 0, val examined: Int = 0,
    val invalid: Int = 0
)

class MangoViewModel(application: Application) : AndroidViewModel(application) {
    val database = Room.databaseBuilder(application, MangoDatabase::class.java, "mangossh.db").build()
    val dao = database.dao()
    val vault = SecretVault(application)
    private val settingsStore = Settings(application)
    val preferences = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val profiles = dao.profiles().map { rows -> rows.map { it.model() } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val engine = SshEngine(vault)
    val connection = engine.state
    private val mutable = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = mutable
    private var operation: Job? = null
    private var shell: InteractiveShell? = null
    private val terminalChunks = ArrayDeque<ByteArray>()
    private var terminalBytes = 0
    private val terminalOutput = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val output = terminalOutput.asSharedFlow()
    var columns = 80
    var rows = 24
    // Kept in the ViewModel: SAF can recreate the Activity while its picker is open.
    var pendingExport = ""

    init {
        viewModelScope.launch {
            connection.collect { value ->
                if (value == ConnectionState.DISCONNECTED) {
                    shell = null
                    mutable.update { it.copy(admin = false, connectedProfile = null) }
                    clearTerminal()
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { dao.prune(System.currentTimeMillis() - 30L * 86400000) }
        // Non-destructive upgrade from the legacy Java MangoSSH screen.
        // Only the user-entered host, port and account are carried across.
        // Passwords were not stored by that screen; existing host trust is
        // NOT copied into a different trust store without re-verification.
        viewModelScope.launch(Dispatchers.IO) { importLegacyPublicProfile() }

    }
    private suspend fun importLegacyPublicProfile() {
        val app = getApplication<Application>()
        val marker = app.getSharedPreferences("mangossh-compose-import", android.content.Context.MODE_PRIVATE)
        if (marker.getBoolean("legacy-profile-v1", false)) return
        val legacy = app.getSharedPreferences("public-profile", android.content.Context.MODE_PRIVATE)
        val host = legacy.getString("host", "").orEmpty().trim()
        val user = legacy.getString("user", "").orEmpty().trim()
        val port = legacy.getString("port", "22").orEmpty().toIntOrNull() ?: return
        if (host.isBlank() || user.isBlank()) return
        val id = java.util.UUID.nameUUIDFromBytes(
            ("mangossh-public-import|" + host + "|" + user + "|" + port).toByteArray(Charsets.UTF_8)
        ).toString()
        val profile = Profile(id = id, name = "Conexión recuperada", host = host,
            port = port, user = user, auth = AuthType.PASSWORD)
        if (runCatching { profile.validate() }.isFailure) return
        runCatching {
            val existing = dao.profiles().first()
            if (existing.none { it.host == host && it.user == user && it.port == port }) {
                dao.save(ProfileRow.from(profile))
            }
            check(marker.edit().putBoolean("legacy-profile-v1", true).commit())
        }.onFailure { message("No se pudo importar el perfil anterior; sigue conservado.") }
    }

    fun select(profile: Profile) { mutable.update { it.copy(selected = profile) } }
    fun message(text: String) { mutable.update { it.copy(message = text) } }
    fun save(profile: Profile) = viewModelScope.launch {
        runCatching { profile.validate(); dao.save(ProfileRow.from(profile)); select(profile) }
            .onFailure { message(it.message ?: "Perfil inválido") }
    }
    fun delete(profile: Profile) = viewModelScope.launch(Dispatchers.IO) {
        if (state.value.connectedProfile?.id == profile.id) disconnect()
        vault.remove("secret:${profile.id}")
        dao.deleteProfile(profile.id)
        mutable.update { if (it.selected?.id == profile.id) it.copy(selected = null) else it }
    }

    fun importKey(uri: Uri, result: (ByteArray?) -> Unit) = viewModelScope.launch {
        val bytes = withContext(Dispatchers.IO) { runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (buffer.size() <= 262144) {
                    val count = it.read(chunk, 0, minOf(chunk.size, 262145 - buffer.size()))
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                }
                val data = buffer.toByteArray()
                require(data.size in 1..262144) { "Clave vacía o mayor de 256 KiB" }; data
            }
        }.onFailure { message(it.message ?: "No se pudo importar la clave") }.getOrNull() }
        result(bytes)
    }

    fun connect(profile: Profile, credentials: Credentials?, remember: Boolean) {
        if (connection.value != ConnectionState.DISCONNECTED) { credentials?.close(); return }
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, message = "Conectando…") }
            try {
                val secret = withContext(Dispatchers.IO) { credentials ?: vault.credentials(profile) }
                    ?: error("Introduce credenciales o importa una clave privada")
                try {
                    if (remember) withContext(Dispatchers.IO) { vault.saveCredentials(profile, secret) }
                    engine.connect(profile, secret, preferences.value.timeout * 1000) { identity ->
                        val request = TrustRequest(identity, CompletableDeferred())
                        mutable.update { it.copy(trust = request) }
                        try { withTimeout(120000) { request.decision.await() } }
                        finally { mutable.update { it.copy(trust = null) } }
                    }
                } finally { secret.close() }
                mutable.update { it.copy(connectedProfile = profile, selected = profile, message = "SSH conectado · Diagnóstico") }
            } catch (e: CancellationException) { withContext(NonCancellable + Dispatchers.IO) { engine.disconnect() }; message("Operación cancelada"); throw e }
            catch (e: Exception) { message(e.message ?: "Error SSH") }
            finally { mutable.update { it.copy(busy = false, trust = null) } }
        }
    }
    fun trust(accept: Boolean) { state.value.trust?.decision?.complete(accept) }
    fun disconnect() {
        state.value.trust?.decision?.complete(false)
        operation?.cancel()
        viewModelScope.launch(Dispatchers.IO) { engine.disconnect() }; shell = null
        mutable.update { it.copy(admin = false, busy = false, connectedProfile = null, message = "SSH desconectado") }
        clearTerminal()
    }
    fun cancel() { operation?.cancel() }

    private suspend fun perform(profile: Profile, command: DiagnosticCommand): CommandResult {
        val started = System.currentTimeMillis()
        try {
            val result = engine.execute(command)
            dao.record(HistoryRow(timestamp = started, profileId = profile.id, profileName = profile.name,
                host = profile.endpoint, commandId = command.id, command = command.shell, durationMs = result.durationMs,
                status = if (result.successful) "OK" else "ERROR", exitCode = result.exitCode,
                output = if (preferences.value.saveOutput && command.id in setOf("firmware", "kernel", "architecture", "cpu", "ram", "disk", "uptime", "interfaces", "addresses", "routes", "interfaceStatus", "model", "glFirmware")) result.report().take(32768) else null))
            dao.prune(System.currentTimeMillis() - 30L * 86400000)
            mutable.update { it.copy(result = "${profile.name} · ${profile.endpoint}\n${command.title}\n${result.report()}") }
            return result
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                dao.record(HistoryRow(timestamp = started, profileId = profile.id, profileName = profile.name,
                    host = profile.endpoint, commandId = command.id, command = command.shell,
                    durationMs = System.currentTimeMillis() - started,
                    status = if (e is CancellationException) "CANCELADO" else "ERROR", exitCode = null, output = null))
            }
            throw e
        }
    }
    fun run(command: DiagnosticCommand) {
        val profile = state.value.connectedProfile ?: return message("Conecta primero")
        if (state.value.busy) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, message = "Ejecutando ${command.title}…") }
            try { perform(profile, command); message("Consulta terminada") }
            catch (e: CancellationException) { message("Consulta cancelada; canal exec cerrado"); throw e }
            catch (e: Exception) { message(e.message ?: "Error de consulta") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun ping(ip: String) { runCatching { Diagnostics.ping(ip) }.onSuccess { run(it) }.onFailure { message(it.message ?: "IP inválida") } }
    fun pingRouter() {
        val profile = state.value.connectedProfile ?: return message("Conecta primero")
        if (state.value.busy) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                val address = withContext(Dispatchers.IO) {
                    if (Validation.ipv4(profile.host)) profile.host else java.net.InetAddress.getAllByName(profile.host)
                        .firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: error("El router no resuelve a IPv4")
                }
                // Resolve locally, then validate again: hostnames never become shell text.
                perform(profile, Diagnostics.ping(address))
                message("Ping al router terminado")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "No se pudo resolver el router") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun pingGateway() {
        val profile = state.value.connectedProfile ?: return
        if (state.value.busy) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                val routes = perform(profile, Diagnostics.byId("routes")!!)
                val gateway = routes.stdout.lineSequence().map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 3 && it[0] == "default" && it[1] == "via" }?.get(2)
                    ?: error("No hay puerta de enlace IPv4 en las rutas")
                perform(profile, Diagnostics.ping(gateway))
                message("Ping a puerta de enlace terminado")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "Error") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun audit() {
        val profile = state.value.connectedProfile ?: return message("Conecta primero")
        if (state.value.busy) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, audit = "", message = "Auditando Mango…") }
            val results = mutableListOf<Pair<DiagnosticCommand, CommandResult>>()
            try {
                for (command in Diagnostics.audit) {
                    results += command to perform(profile, command)
                    mutable.update { it.copy(audit = Diagnostics.report(profile, results)) }
                }
                message("Auditoría terminada · ${results.size} consultas")
            } catch (e: CancellationException) { message("Auditoría cancelada; informe parcial"); throw e }
            catch (e: Exception) { message(e.message ?: "Auditoría interrumpida") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun activateAdmin() {
        if (connection.value != ConnectionState.CONNECTED || state.value.busy) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                engine.activateAdministrator()
                shell = engine.openShell(columns, rows) { bytes ->
                    synchronized(terminalChunks) {
                        terminalChunks.addLast(bytes.copyOf()); terminalBytes += bytes.size
                        while (terminalBytes > 262144 && terminalChunks.size > 1) terminalBytes -= terminalChunks.removeFirst().size
                    }
                    if (bytes.isNotEmpty()) mutable.update { old ->
                        old.copy(terminalPreview = (old.terminalPreview + bytes.toString(Charsets.UTF_8)).takeLast(12000))
                    }
                    if (!terminalOutput.tryEmit(bytes)) message("Salida intensa: vuelve a abrir Terminal para restaurar el búfer")
                }
                mutable.update { it.copy(admin = true, message = "ADMINISTRADOR · shell PTY activa; esperando prompt") }
                // Un retorno de carro solicita el prompt sin cambiar configuraciones del router.
                shell?.write(byteArrayOf(13))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "No se pudo abrir PTY") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun write(bytes: ByteArray) {
        if (!state.value.admin) return
        viewModelScope.launch {
            try { (shell?.takeIf { it.connected } ?: error("Shell cerrada; desconecta y vuelve a conectar")).write(bytes) }
            catch (e: Exception) { message(e.message ?: "Error de terminal") }
        }
    }
    fun resize(cols: Int, lines: Int) {
        columns = cols.coerceIn(10, 500); rows = lines.coerceIn(2, 200)
        viewModelScope.launch(Dispatchers.IO) { shell?.resize(columns, rows) }
    }
    fun terminalSnapshot(): ByteArray = synchronized(terminalChunks) {
        val buffer = java.io.ByteArrayOutputStream(); terminalChunks.forEach { buffer.write(it) }; buffer.toByteArray()
    }
    fun clearTerminal() {
        synchronized(terminalChunks) { terminalChunks.clear(); terminalBytes = 0 }
        mutable.update { it.copy(terminalEpoch = it.terminalEpoch + 1, terminalPreview = "") }
    }
    fun terminalUiStatus(status: String) { mutable.update { it.copy(terminalUiStatus = status) } }

    // CT data never leaves the tablet and never enters the unencrypted SSH history.
    private val ctCatalogData = MutableStateFlow<List<CtRecord>>(emptyList())
    val ctCatalog: StateFlow<List<CtRecord>> = ctCatalogData
    private val ctSessionData = MutableStateFlow(CtSession())
    val ctSession: StateFlow<CtSession> = ctSessionData

    // Import state is displayed persistently in CT · Red local, not only the tiny app header.
    private val ctImportData = MutableStateFlow(CtImportUiState())
    val ctImport: StateFlow<CtImportUiState> = ctImportData
    private val ctCatalogSource = application.getSharedPreferences(
        "ct_catalog_document", android.content.Context.MODE_PRIVATE)

    init {
        // A SAF document grant survives ordinary, same-package/same-signature
        // APK upgrades. Rebuild only the in-memory CT index after process restart.
        ctCatalogSource.getString("source_uri", null)?.let { saved ->
            importCtCatalog(Uri.parse(saved), restored = true)
        }
    }

    fun importCtCatalog(uri: Uri, restored: Boolean = false) {
        if (ctImportData.value.phase == "loading") return
        val previousCount = ctCatalogData.value.size
        ctImportData.value = CtImportUiState(phase = "loading", message = "Leyendo y validando catálogo JSON…")
        viewModelScope.launch {
            try {
                val (fileName, report) = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val displayName = runCatching {
                        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (index >= 0) cursor.getString(index) else null
                            } else null
                        }
                    }.getOrNull()?.take(100) ?: "catálogo.json"
                    val report = resolver.openInputStream(uri)?.use { stream ->
                        CtImportStream.parse(stream) { processed ->
                            val megabytes = processed / (1024L * 1024L)
                            ctImportData.value = CtImportUiState(
                                phase = "loading", fileName = displayName,
                                message = "Leyendo JSON grande: $megabytes MB procesados…")
                        }
                    } ?: error("No se pudo abrir el archivo")
                    displayName to report
                }
                if (report.records.isNotEmpty()) {
                    ctCatalogData.value = report.records
                    val sourceRemembered = if (restored) true else try {
                        // The picker uses ACTION_OPEN_DOCUMENT, so the read grant
                        // can be retained without copying private CT data.
                        getApplication<Application>().contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctCatalogSource.edit().putString("source_uri", uri.toString()).commit()
                    } catch (_: SecurityException) { false }
                    val sourceNote = if (sourceRemembered) ""
                        else " Catálogo cargado solo en esta sesión: reimpórtalo al abrir de nuevo."
                    val usable = report.records.count { it.ipLan.isNotBlank() }
                    val rejected = report.invalidIp + report.missingMatricula
                    val info = "JSON importado: ${report.records.size} CT; " +
                        "$usable con IP\u0020LAN válida; ${report.missingIp} sin IP\u0020LAN." +
                        if (rejected > 0 || report.duplicates > 0)
                            " Filas rechazadas: $rejected; repetidas: ${report.duplicates}." else ""
                    ctImportData.value = CtImportUiState("success", info + sourceNote, fileName,
                        usable, report.candidates, rejected + report.duplicates)
                    message("Catálogo CT listo: ${report.records.size} centros ($usable con LAN)")
                } else {
                    val reason = when {
                        report.candidates == 0 -> "No se han reconocido registros con MATRÍCULA / IP\u0020LAN"
                        report.missingIp > 0 -> "Se encontraron registros, pero les falta IP\u0020LAN"
                        report.invalidIp > 0 -> "Se encontraron direcciones IP\u0020LAN no válidas"
                        else -> "No se encontraron CT válidos"
                    }
                    val retained = if (previousCount > 0) " Se conserva el catálogo anterior ($previousCount CT)." else ""
                    ctImportData.value = CtImportUiState("empty",
                        "JSON leído, pero 0 CT importados. $reason.$retained", fileName,
                        0, report.candidates, report.missingIp + report.invalidIp + report.missingMatricula)
                    message("No se importaron CT: revisa la estructura del JSON")
                }
            } catch (e: Exception) {
                val summary = when (e) {
                    is org.json.JSONException -> "El archivo no contiene un JSON válido"
                    is IllegalArgumentException -> e.message ?: "Catálogo JSON no válido"
                    is java.io.IOException -> "El archivo no se pudo leer: JSON incompleto, mal formado o corrupto"
                    else -> "No se pudo abrir o leer el archivo JSON"
                }
                val retained = if (previousCount > 0) " Se conserva el catálogo anterior ($previousCount CT)." else ""
                ctImportData.value = CtImportUiState("error", summary + "." + retained)
                message("Importación CT fallida: revisa el mensaje del panel")
            }
        }
    }

    private fun requireMangoConnection() {
        val p = state.value.connectedProfile ?: error("Conéctate primero al Mango")
        require(connection.value == ConnectionState.CONNECTED && p.host == mangoDefaultHost() &&
            p.user == "root" && p.port == 22) {
            "Conecta como root@${mangoDefaultHost()}:22; no se configurará ningún otro dispositivo"
        }
    }

    /** Explicit confirmation is supplied by the CT panel dialog; always uses exec,
     * never sends writes into an administrator PTY or records commands in Room.
     */
    fun setupAndProbeCt(matricula: String, base: String, mask: String, confirmed: Boolean) {
        if (!confirmed || state.value.busy || ctSessionData.value.working) return
        val plan = runCatching { CtPlanning.build(matricula, base, mask) }
            .getOrElse { return message(it.message ?: "Plan CT inválido") }
        runCatching { requireMangoConnection() }
            .onFailure { return message(it.message ?: "Mango no conectado") }
        operation = viewModelScope.launch {
            ctSessionData.value = CtSession(plan = plan, stage = "Comprobando identidad y cable WAN", working = true)
            mutable.update { it.copy(busy = true) }
            try {
                val check = engine.execute(CtCommands.preflight(), 15000)
                check(check.successful && check.stdout.contains("CT_PREFLIGHT_OK")) {
                    "Mango no preparado: ${check.stderr.ifBlank { check.stdout }.take(400)}"
                }
                ctSessionData.value = ctSessionData.value.copy(stage = "Asignando WAN ${plan.mangoIp}/${plan.prefix}")
                val change = engine.execute(CtCommands.apply(plan), 45000)
                check(change.successful && change.stdout.contains("CT_WAN_CONFIG_OK")) {
                    "WAN sin confirmar: ${change.stderr.ifBlank { change.stdout }.take(400)}"
                }
                ctSessionData.value = ctSessionData.value.copy(stage = "Verificando ruta", configured = true)
                val verify = engine.execute(CtCommands.verify(plan), 15000)
                check(verify.successful && verify.stdout.contains("CT_WAN_OK")) {
                    "WAN asignada pero no verificada: ${verify.report().take(400)}"
                }
                ctSessionData.value = ctSessionData.value.copy(stage = "Ejecutando siete pings", lastOutput = verify.report())
                executeCtPings(plan)
            } catch (e: CancellationException) {
                ctSessionData.value = ctSessionData.value.copy(stage = "Cancelado. Comprueba y restaura WAN si procede")
                throw e
            } catch (e: Exception) {
                ctSessionData.value = ctSessionData.value.copy(
                    stage = "ERROR: ${e.message}", lastOutput = (e.message ?: "Error").take(500))
            } finally {
                ctSessionData.value = ctSessionData.value.copy(working = false)
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun executeCtPings(plan: CtPlan) {
        val items = mutableListOf<CtProbe>()
        for (target in plan.targets) {
            currentCoroutineContext().ensureActive()
            ctSessionData.value = ctSessionData.value.copy(stage = "Ping ${target.name} · ${target.ip}")
            try {
                val result = engine.execute(CtCommands.ping(plan, target), 20000)
                val loss = Regex("(\\d+)% packet loss").find(result.stdout)?.groupValues?.get(1)?.toIntOrNull()
                val status = when {
                    !result.successful -> "KO"
                    loss != null && loss > 0 -> "PARCIAL"
                    else -> "OK"
                }
                // Show all four ICMP replies (seq, TTL and time) in the tablet,
                // not merely the final packet-loss summary.
                items.add(CtProbe(target.name, target.ip, status,
                    result.stdout.trim().ifBlank { result.stderr.trim() }.takeLast(2600)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { items.add(CtProbe(target.name, target.ip, "ERROR", e.message.orEmpty().take(160))) }
            ctSessionData.value = ctSessionData.value.copy(probes = items.toList())
        }
        val count = items.count { it.status == "OK" || it.status == "PARCIAL" }
        ctSessionData.value = ctSessionData.value.copy(stage = "Finalizado: $count/${items.size} equipos responden")
    }

    fun repeatCtPings() {
        if (state.value.busy || ctSessionData.value.working) return
        val plan = ctSessionData.value.plan ?: return message("Configura antes el CT")
        if (!ctSessionData.value.configured) return message("La WAN no está configurada")
        runCatching { requireMangoConnection() }.onFailure { return message(it.message ?: "Mango no conectado") }
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            ctSessionData.value = ctSessionData.value.copy(working = true, probes = emptyList())
            try {
                val check = engine.execute(CtCommands.verify(plan), 15000)
                require(check.successful && check.stdout.contains("CT_WAN_OK")) { "La WAN ha cambiado; configura CT de nuevo" }
                executeCtPings(plan)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ctSessionData.value = ctSessionData.value.copy(stage = "ERROR: ${e.message}") }
            finally {
                ctSessionData.value = ctSessionData.value.copy(working = false)
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun restoreCtWan(confirmed: Boolean) {
        if (!confirmed || state.value.busy || ctSessionData.value.working) return
        runCatching { requireMangoConnection() }.onFailure { return message(it.message ?: "Mango no conectado") }
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            ctSessionData.value = ctSessionData.value.copy(working = true, stage = "Restaurando WAN original")
            try {
                val r = engine.execute(CtCommands.restore(), 30000)
                check(r.successful && r.stdout.contains("CT_WAN_RESTORE_OK")) { r.report().take(400) }
                ctSessionData.value = CtSession(stage = "Configuración WAN original restaurada")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ctSessionData.value = ctSessionData.value.copy(stage = "ERROR RESTAURACIÓN: ${e.message}") }
            finally {
                ctSessionData.value = ctSessionData.value.copy(working = false)
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun updatePreferences(p: Preferences) = viewModelScope.launch { settingsStore.update(p) }
    fun refreshTrusted() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { vault.trusted() }.onSuccess { list -> mutable.update { it.copy(trusted = list) } }
            .onFailure { message("No se pudo leer Keystore. No se aceptarán claves automáticamente.") }
    }
    fun forgetHost(endpoint: String) = viewModelScope.launch(Dispatchers.IO) {
        if (connection.value != ConnectionState.DISCONNECTED) return@launch message("Desconecta antes de eliminar una identidad")
        vault.forgetHost(endpoint); refreshTrusted()
    }
    fun forgetSecret(profile: Profile) = viewModelScope.launch(Dispatchers.IO) { vault.remove("secret:${profile.id}"); message("Credencial eliminada") }
    fun deleteHistory(id: Long) = viewModelScope.launch { dao.deleteHistory(id) }
    fun clearHistory() = viewModelScope.launch { dao.clearHistory() }
    fun historyReport(): String = buildString {
        appendLine("MangoSSH · Historial · Revisar información sensible antes de compartir")
        history.value.forEach { appendLine("${java.util.Date(it.timestamp)} · ${it.profileName} · ${it.host}\n${it.command}\n${it.status} · ${it.exitCode} · ${it.durationMs} ms\n${it.output ?: "Salida no guardada"}\n") }
    }
    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch { engine.disconnect(); database.close() }
        super.onCleared()
    }
}
