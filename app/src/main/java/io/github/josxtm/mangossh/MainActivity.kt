package io.github.josxtm.mangossh

import es.josxtm.telecontrol.community.BuildConfig

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.josxtm.mangossh.core.*
import io.github.josxtm.mangossh.ui.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MangoApp() }
    }
}


private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BCB86), onPrimary = Color(0xFF172418),
    secondary = Color(0xFFBED6AB), background = Color(0xFF10151B),
    onBackground = Color(0xFFF1F4F5), surface = Color(0xFF1A2229),
    onSurface = Color(0xFFF1F4F5), surfaceVariant = Color(0xFF29343C),
    outline = Color(0xFF67787C), error = Color(0xFFF3A9A3))
private val LightColors = lightColorScheme(
    primary = Color(0xFF3D741F), onPrimary = Color.White,
    secondary = Color(0xFF497D30), background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1C2832), surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2832), surfaceVariant = Color(0xFFE9F2E3),
    outline = Color(0xFF819388), error = Color(0xFFB12626))

private val menuTitles = listOf("Conexión", "Terminal", "Diagnóstico", "Historial", "Ajustes")
private val menuIcons: List<ImageVector> = listOf(
    Icons.Outlined.Link, Icons.Outlined.Terminal, Icons.Outlined.NetworkCheck,
    Icons.Outlined.History, Icons.Outlined.Settings
)

/** UI tokens are deliberately local: AutoControL can reuse this palette and icon map. */
@Composable private fun MangoNavigationRail(page: Int, onPage: (Int) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(8.dp))
        // In landscape the Connection page already contains the SSH console.
        // Keep the separate Terminal tab only in compact mobile navigation.
        listOf(0, 2, 3, 4).forEach { index ->
            val label = menuTitles[index]
            NavigationRailItem(
                selected = page == index || (index == 0 && page == 1),
                onClick = { onPage(index) },
                icon = { Icon(menuIcons[index], contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) }, alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedIconColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable fun MangoApp(vm: MangoViewModel = viewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val report = vm.pendingExport.takeIf { it.isNotEmpty() }
                    ?: error("El proceso se reinició; vuelve a exportar el informe")
                context.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
                    ?: error("No se pudo abrir el destino")
            } }.onSuccess { vm.message("Informe exportado") }
                .onFailure { vm.message("Error de exportación: " + it.message) }
        }
    }
    fun export(text: String) { vm.pendingExport = text; exporter.launch("MangoSSH-" + System.currentTimeMillis() + ".txt") }
    val dark = when (prefs.theme) {
        "Claro" -> false
        "Oscuro" -> true
        else -> isSystemInDarkTheme()
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    LaunchedEffect(wide) {
        if (wide && page == 1) page = 0 // console is embedded in wide Connection page
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        Scaffold(modifier = Modifier.imePadding(), containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(shadowElevation = 1.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Terminal, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("MangoSSH", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Text("v1.0 (comp. " + (BuildConfig.VERSION_CODE - 10000) + ")",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(if (state.admin) "ADMINISTRADOR" else "DIAGNÓSTICO",
                                color = if (state.admin) Color(0xFFD39721) else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Text(state.connectedProfile?.let {
                            it.user + "@" + it.host + ":" + it.port + " · " + connection.name
                        } ?: "Cliente SSH · " + connection.name,
                            style = MaterialTheme.typography.bodySmall)
                        if (state.message.isNotBlank())
                            Text(state.message.take(250), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    menuTitles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = page == index, onClick = { page = index },
                            icon = { Icon(menuIcons[index], contentDescription = title) },
                            label = { Text(title, maxLines = 1, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }) { padding ->
            if (wide) {
                Row(Modifier.fillMaxSize().padding(padding)) {
                    MangoNavigationRail(page) { page = it }
                    VerticalDivider()
                    if (page <= 2) {
                        Box(Modifier.width(310.dp).fillMaxHeight()) { Connections(vm) }
                        VerticalDivider()
                        Box(Modifier.weight(1f)) {
                            when (page) {
                                2 -> DiagnosticsPage(vm, ::export)
                                else -> TerminalPage(vm, prefs, onQuick = { page = 2 })
                            }
                        }
                    } else Box(Modifier.weight(1f)) {
                        when (page) {
                            3 -> HistoryPage(vm, ::export)
                            4 -> SettingsPage(vm, prefs, ::export)
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        0 -> Connections(vm)
                        1 -> TerminalPage(vm, prefs, onQuick = { page = 2 })
                        2 -> DiagnosticsPage(vm, ::export)
                        3 -> HistoryPage(vm, ::export)
                        4 -> SettingsPage(vm, prefs, ::export)
                    }
                }
            }
        }
        state.trust?.let { request ->
            AlertDialog(onDismissRequest = { vm.trust(false) },
                title = { Text("Verificar identidad SSH") },
                text = { SelectionContainer {
                    Text("Primera conexión a " + request.identity.endpoint + "\n" +
                        request.identity.algorithm + "\n\n" + request.identity.sha256 +
                        "\n\nCompara esta huella por un canal independiente con el propietario del equipo antes de confiar.")
                } },
                confirmButton = { TextButton(onClick = { vm.trust(true) }) { Text("Confiar y conectar") } },
                dismissButton = { TextButton(onClick = { vm.trust(false) }) { Text("Rechazar") } })
        }
    }
}


@Composable private fun Connections(vm: MangoViewModel) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var authenticate by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Conexiones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Button(onClick = { editing = Profile() }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nuevo perfil")
            }
        }
        if (profiles.isEmpty()) item {
            Text("Crea un perfil. Para Mango: ${mangoDefaultHost()} · 22 · root. Todos los valores se pueden editar.",
                style = MaterialTheme.typography.bodyMedium)
        }
        items(profiles, key = { it.id }) { profile ->
            val isConnected = connection == ConnectionState.CONNECTED &&
                state.connectedProfile?.id == profile.id
            Card(onClick = { vm.select(profile) },
                colors = CardDefaults.cardColors(containerColor =
                    if (isConnected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp,
                    if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Router, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(profile.user + "@" + profile.host + ":" + profile.port,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (isConnected) Icon(Icons.Outlined.CheckCircle, "Conectado",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (profile.auth == AuthType.PASSWORD) "Contraseña" else "Clave privada",
                        style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            enabled = connection == ConnectionState.DISCONNECTED && !state.busy,
                            onClick = { vm.select(profile); authenticate = profile }) {
                            Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp)); Text("Conectar", fontSize = 12.sp)
                        }
                        TextButton(onClick = { editing = profile }) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp)); Text("Editar", fontSize = 12.sp)
                        }
                        TextButton(onClick = { deleting = profile }) {
                            Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp)); Text("Eliminar", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (connection != ConnectionState.DISCONNECTED || state.busy) item {
            OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.LinkOff, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp)); Text("Desconectar / cancelar")
            }
        }
        item { Text("Solo redes autorizadas. No se necesita Internet. La conexión como root conserva todos sus privilegios en el equipo remoto.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    editing?.let { original ->
        ProfileDialog(original, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null })
    }
    authenticate?.let { p -> CredentialsDialog(p, vm, onDismiss = { authenticate = null }) }
    deleting?.let { p ->
        Confirm("Eliminar perfil",
            "Se eliminará " + p.name + " y su credencial guardada. El historial se conserva.",
            { deleting = null }) { vm.delete(p); deleting = null }
    }
}

@Composable private fun ProfileDialog(profile: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var host by remember { mutableStateOf(profile.host) }
    var port by remember { mutableStateOf(profile.port.toString()) }
    var user by remember { mutableStateOf(profile.user) }
    var auth by remember { mutableStateOf(profile.auth) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Perfil SSH") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(host, { host = it }, label = { Text("IP o hostname") }, singleLine = true, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
            OutlinedTextField(port, { port = it }, label = { Text("Puerto") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true)
            Row { AuthType.entries.forEach { type ->
                FilterChip(selected = auth == type, onClick = { auth = type }, label = { Text(if (type == AuthType.PASSWORD) "Contraseña" else "Clave") })
            } }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            val p = profile.copy(name = name.trim(), host = host.trim(), port = port.toIntOrNull() ?: 0, user = user.trim(), auth = auth)
            p.validate(); onSave(p)
        }.onFailure { error = it.message ?: "Perfil inválido" }
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun CredentialsDialog(profile: Profile, vm: MangoViewModel, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var key by remember { mutableStateOf<ByteArray?>(null) }
    var rememberSecret by remember { mutableStateOf(false) }
    var useStored by remember { mutableStateOf(vm.vault.hasSecret(profile.id)) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importKey(uri) { key?.fill(0); key = it; useStored = false }
    }
    DisposableEffect(Unit) { onDispose { key?.fill(0) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Conectar a ${profile.name}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${profile.user}@${profile.host}:${profile.port}")
            if (vm.vault.hasSecret(profile.id)) Row {
                Checkbox(useStored, { useStored = it }); Text("Usar credencial protegida guardada", Modifier.padding(top = 12.dp))
            }
            if (!useStored) {
                if (profile.auth == AuthType.PRIVATE_KEY) {
                    OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text(if (key == null) "Importar clave privada" else "Clave importada") }
                    Text("PEM u OpenSSH compatible. Máximo 256 KiB.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(password, { password = it }, label = { Text(if (profile.auth == AuthType.PASSWORD) "Contraseña" else "Frase de la clave (opcional)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
                Row { Checkbox(rememberSecret, { rememberSecret = it }); Text("Guardar con Android Keystore", Modifier.padding(top = 12.dp)) }
            }
            Text("Las credenciales solo se enviarán tras verificar la identidad SSH. No se guardan en el historial.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = useStored || profile.auth == AuthType.PASSWORD || key != null, onClick = {
        val credentials = if (useStored) null else if (profile.auth == AuthType.PASSWORD) Credentials.Password(password.toByteArray())
            else Credentials.PrivateKey(key!!.copyOf(), password.takeIf { it.isNotEmpty() }?.toByteArray())
        password = ""; vm.connect(profile, credentials, rememberSecret && !useStored); onDismiss()
    }) { Text("Conectar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}


@Composable private fun TerminalPage(vm: MangoViewModel, prefs: Preferences, onQuick: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    var warning by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onQuick) {
                Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("Comandos rápidos")
            }
            OutlinedButton(
                enabled = connection == ConnectionState.CONNECTED && !state.admin && !state.busy,
                onClick = { warning = true }) {
                Icon(Icons.Outlined.AdminPanelSettings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("Activar administrador")
            }
            TextButton(onClick = vm::clearTerminal) {
                Icon(Icons.Outlined.CleaningServices, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp)); Text("Limpiar")
            }
            TextButton(onClick = vm::disconnect) {
                Icon(Icons.Outlined.LinkOff, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp)); Text("Desconectar")
            }
        }
        if (!state.admin) Text(
            "Diagnóstico: entrada arbitraria bloqueada. Usa las consultas predefinidas.",
            style = MaterialTheme.typography.bodySmall)
        if (state.terminalUiStatus.contains("Error", ignoreCase = true))
            Text(state.terminalUiStatus, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        TerminalView(vm.output, vm::terminalSnapshot, state.terminalEpoch, prefs.fontSize, state.admin,
            vm::write, vm::resize, Modifier.fillMaxWidth().weight(1f),
            fallbackText = state.terminalPreview, onStatus = vm::terminalUiStatus)
        if (state.admin) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("Ctrl-C" to "\u0003", "Ctrl-D" to "\u0004", "Tab" to "\t",
                    "Esc" to "\u001b", "↑" to "\u001b[A", "↓" to "\u001b[B",
                    "←" to "\u001b[D", "→" to "\u001b[C").forEach { (label, value) ->
                    TextButton(onClick = { vm.write(value.toByteArray()) }) { Text(label) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f),
                    label = { Text("Enviar a shell (sin historial)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        vm.write((input + "\r").toByteArray()); input = ""
                    })
                )
                Button(onClick = { vm.write((input + "\r").toByteArray()); input = "" }) {
                    Icon(Icons.Outlined.Send, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Enviar")
                }
            }
        }
    }
    if (warning) Confirm("Activar modo administrador",
        "El modo administrador permite modificar la configuración del dispositivo remoto. Utilízalo únicamente en equipos autorizados.\n\nUna shell root puede ejecutar cualquier operación permitida por el servidor. La activación solo dura esta sesión. No se registrará lo escrito.",
        { warning = false }) {
        warning = false; vm.activateAdmin()
    }
}

@Composable private fun DiagnosticsPage(vm: MangoViewModel, export: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val ct by vm.ctSession.collectAsStateWithLifecycle()
    var ping by rememberSaveable { mutableStateOf(mangoDefaultHost()) }
    val connected = connection == ConnectionState.CONNECTED && !state.busy
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Diagnóstico", style = MaterialTheme.typography.headlineSmall) }
        item { CtPanel(vm) }
        item { Text("Consultas reales, sin cambios automáticos. root conserva privilegios administrativos.", style = MaterialTheme.typography.bodySmall) }
        item { Button(enabled = connected, onClick = vm::audit, modifier = Modifier.fillMaxWidth()) { Text("AUDITAR MANGO") } }
        if (state.busy && !ct.working) item { OutlinedButton(onClick = vm::cancel) { Text("Cancelar consulta") } }
        Diagnostics.commands.groupBy { it.category }.forEach { (category, commands) ->
            item { Text(category, style = MaterialTheme.typography.titleMedium) }
            items(commands.chunked(2)) { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { cmd -> OutlinedButton(enabled = connected, onClick = { vm.run(cmd) }, modifier = Modifier.weight(1f)) { Text(cmd.title) } }
            } }
        }
        item {
            OutlinedTextField(ping, { ping = it }, label = { Text("Destino ping (IPv4)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row { TextButton(enabled = connected, onClick = { vm.ping(ping) }) { Text("Ping IP") }
                TextButton(enabled = connected, onClick = vm::pingRouter) { Text("Ping router") }
                TextButton(enabled = connected, onClick = vm::pingGateway) { Text("Gateway") } }
        }
        item { Text("Resultado", style = MaterialTheme.typography.titleMedium) }
        item { Card { SelectionContainer { Text(state.result, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = prefs.fontSize.sp) } } }
        item { TextButton(onClick = { export(state.result) }) { Text("Exportar resultado") } }
        if (state.audit.isNotEmpty()) {
            item { TextButton(onClick = { export(state.audit) }) { Text("Exportar auditoría") } }
            item { SelectionContainer { Text(state.audit, fontFamily = FontFamily.Monospace, fontSize = prefs.fontSize.sp) } }
        }
    }
}

@Composable private fun HistoryPage(vm: MangoViewModel, export: (String) -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var clear by remember { mutableStateOf(false) }
    val filtered = history.filter { (filter == "Todos" || it.status == filter) && "${it.profileName} ${it.host} ${it.command}".contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Historial", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(query, { query = it }, label = { Text("Buscar perfil, host o comando") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("Todos", "OK", "ERROR", "CANCELADO").forEach { value ->
            FilterChip(filter == value, { filter = value }, label = { Text(value) })
        } } }
        item { Row { TextButton(onClick = { export(vm.historyReport()) }) { Text("Exportar") }; TextButton(onClick = { clear = true }) { Text("Limpiar historial") } } }
        item { Text("Máximo 500 registros / 30 días. Las entradas de la terminal administrativa no se registran.", style = MaterialTheme.typography.bodySmall) }
        items(filtered, key = { it.id }) { row ->
            Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("${row.profileName} · ${row.host}", style = MaterialTheme.typography.titleSmall)
                Text(DateFormat.getDateTimeInstance().format(Date(row.timestamp)), style = MaterialTheme.typography.labelSmall)
                SelectionContainer { Text(row.command, fontFamily = FontFamily.Monospace, maxLines = 4) }
                Text("${row.status} · código ${row.exitCode ?: "—"} · ${row.durationMs} ms", color = if (row.status == "OK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                row.output?.let { SelectionContainer { Text(it, maxLines = 12, fontFamily = FontFamily.Monospace) } }
                Row {
                    TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MangoSSH", row.command + "\n" + (row.output ?: ""))) }) { Text("Copiar") }
                    TextButton(onClick = { Diagnostics.repeat(row.commandId)?.let(vm::run) }) { Text("Repetir consulta") }
                    TextButton(onClick = { vm.deleteHistory(row.id) }) { Text("Eliminar") }
                }
            } }
        }
    }
    if (clear) Confirm("Borrar historial", "Se borrarán todos los registros locales.", { clear = false }) { vm.clearHistory(); clear = false }
}

@Composable private fun SettingsPage(vm: MangoViewModel, prefs: Preferences, export: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var forget by remember { mutableStateOf<String?>(null) }
    var outputWarning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshTrusted() }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Configuración", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Apariencia · MangoSSH / AutoControL", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Claro", "Oscuro", "Sistema").forEach { theme ->
            FilterChip(prefs.theme == theme, { vm.updatePreferences(prefs.copy(theme = theme)) }, label = { Text(theme) })
        } } }
        item { Text("Fuente terminal: ${prefs.fontSize} sp"); Slider(prefs.fontSize.toFloat(), { vm.updatePreferences(prefs.copy(fontSize = it.toInt())) }, valueRange = 10f..28f, steps = 17) }
        item { Text("Tiempo de conexión: ${prefs.timeout} segundos"); Slider(prefs.timeout.toFloat(), { vm.updatePreferences(prefs.copy(timeout = it.toInt())) }, valueRange = 1f..60f, steps = 58) }
        item { Row { Checkbox(prefs.saveOutput, { value -> if (value) outputWarning = true else vm.updatePreferences(prefs.copy(saveOutput = false)) }); Text("Guardar salida de consultas", Modifier.padding(top = 12.dp)) } }
        item { Text("Las salidas pueden contener direcciones, configuración y secretos. Room no cifra el historial. La salida no se guarda por defecto; revisa los informes antes de compartirlos.", style = MaterialTheme.typography.bodySmall) }
        item { Text("Identidades SSH confiables", style = MaterialTheme.typography.titleMedium) }
        if (state.trusted.isEmpty()) item { Text("Ninguna identidad guardada") }
        items(state.trusted) { (endpoint, fingerprint) ->
            Card { Column(Modifier.padding(10.dp)) { SelectionContainer { Text("$endpoint\n$fingerprint", fontFamily = FontFamily.Monospace) }
                TextButton(onClick = { forget = endpoint }) { Text("Eliminar confianza") } } }
        }
        item { Text("Credenciales protegidas", style = MaterialTheme.typography.titleMedium) }
        items(profiles) { p -> TextButton(onClick = { vm.forgetSecret(p) }) { Text("Eliminar credencial: ${p.name}") } }
        item { TextButton(onClick = { export(vm.historyReport()) }) { Text("Exportar historial") } }
        item { Text("MangoSSH v" + BuildConfig.VERSION_NAME + " (comp. " + BuildConfig.VERSION_CODE + ")\nSSH: JSch 2.28.8 (BSD) · Terminal: visor nativo de texto\nSin conexión a servicios externos. Wi-Fi / Ethernet LAN. Sesión en memoria mientras Android mantenga vivo el proceso. Sin servicio en segundo plano.\n\nNo se debilitan algoritmos SSH para conectar a firmware antiguo. Un error de negociación exige actualizar o configurar el servidor por un canal autorizado.") }
    }
    forget?.let { endpoint -> Confirm("Eliminar identidad", "Verifica el dispositivo por un canal independiente antes de volver a aceptar una huella para $endpoint. Debes estar desconectado.", { forget = null }) { vm.forgetHost(endpoint); forget = null } }
    if (outputWarning) Confirm("Guardar resultados", "Las consultas y los logs pueden contener información sensible. Se guardará hasta 32 KiB de salida por registro en el almacenamiento privado de la aplicación, sin cifrado adicional. No actives esta opción si pueden aparecer secretos.", { outputWarning = false }) {
        vm.updatePreferences(prefs.copy(saveOutput = true)); outputWarning = false
    }
}

@Composable private fun Confirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
