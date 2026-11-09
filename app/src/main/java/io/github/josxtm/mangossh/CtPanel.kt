package io.github.josxtm.mangossh

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.josxtm.mangossh.core.CtPlanning
import io.github.josxtm.mangossh.core.mangoDefaultHost

/** Reusable CT workflow for future AutoControL SNIFFER integration. */
@Composable
fun CtPanel(vm: MangoViewModel) {
    val catalog by vm.ctCatalog.collectAsStateWithLifecycle()
    val importState by vm.ctImport.collectAsStateWithLifecycle()
    val session by vm.ctSession.collectAsStateWithLifecycle()
    val status by vm.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf("") }
    var mask by rememberSaveable { mutableStateOf("") }
    var previousAutoMatricula by rememberSaveable { mutableStateOf("") }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCtCatalog(uri)
    }
    val normalizedSearch = CtCatalogParser.normalizeMatricula(search)
    val selectedRecord = catalog.firstOrNull { it.matricula == normalizedSearch }
    LaunchedEffect(catalog, normalizedSearch) {
        // Exact matrícula fills LAN automatically, including when JSON is imported AFTER typing.
        val record = catalog.firstOrNull { it.matricula == normalizedSearch }
        if (record != null) {
            base = record.ipLan
            if (previousAutoMatricula != record.matricula || record.netmask.isNotBlank())
                mask = record.netmask
            previousAutoMatricula = record.matricula
        } else if (previousAutoMatricula.isNotBlank()) {
            // Do not accidentally probe a previous CT when the matricula changes.
            base = ""
            mask = ""
            previousAutoMatricula = ""
        }
    }
    val matched = if (normalizedSearch.isBlank()) emptyList() else catalog.filter {
        it.matricula.contains(normalizedSearch) ||
            it.name.contains(search.trim(), ignoreCase = true)
    }.take(8)
    val plan = remember(search, base, mask) {
        runCatching { CtPlanning.build(search, base, mask) }.getOrNull()
    }
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("CT · Red local", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("Mango WAN · ping desde el Mango",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ocultar" else "Mostrar")
                }
            }
            if (expanded) {
                Text("1. Conecta la tablet al Wi-Fi del Mango (${mangoDefaultHost()}). " +
                    "2. Conecta el puerto WAN del Mango a la LAN del CT autorizado. " +
                    "3. Verifica la máscara y que dirección reservada no esté ocupada.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    enabled = importState.phase != "loading",
                    onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                ) { Text("Importar catálogo JSON local") }
                if (importState.phase == "loading") LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(importState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (importState.phase == "error" || importState.phase == "empty")
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (importState.fileName.isNotBlank()) {
                    Text("Archivo: " + importState.fileName,
                        style = MaterialTheme.typography.labelSmall)
                }
                Text("${catalog.size} CT disponibles en esta sesión (JSON local).",
                    style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(search,
                    { search = CtCatalogParser.normalizeMatricula(it) },
                    label = { Text("Matrícula del CT") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (selectedRecord != null) {
                    Text(
                        if (selectedRecord.ipLan.isBlank())
                            "CT encontrado · ${selectedRecord.name} · SIN IP\u0020LAN en el JSON. " +
                                "Comprueba la IP real antes de introducirla manualmente."
                        else
                            "CT encontrado · ${selectedRecord.name} · IP\u0020LAN cargada: ${selectedRecord.ipLan}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedRecord.ipLan.isBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                } else if (normalizedSearch.isNotBlank() && catalog.isNotEmpty() && matched.isEmpty()) {
                    Text("CT no encontrado en el JSON importado. Comprueba la matrícula.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (search.isNotBlank() && matched.isNotEmpty()) {
                    Text("Selecciona CT del catálogo:", style = MaterialTheme.typography.labelSmall)
                    matched.forEach { record ->
                        OutlinedButton(onClick = {
                            search = record.matricula
                            base = record.ipLan
                            mask = record.netmask
                            previousAutoMatricula = record.matricula
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("${record.matricula} · ${record.name} · " +
                                if (record.ipLan.isBlank()) "SIN IP\u0020LAN" else "LAN ${record.ipLan}",
                                maxLines = 1)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(base, { base = it.trim() }, label = { Text("IP\u0020LAN base") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(mask, { mask = it.trim() },
                        label = { Text("Máscara real") }, placeholder = { Text("${listOf(255, 255, 255, 0).joinToString(".")}") },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("La máscara de ejemplo NO se aplica por defecto. Introduce la real; " +
                    "las subredes pueden variar entre CT.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan != null) {
                    Text("Plan CT ${plan.matricula} · WAN ${plan.mangoIp}/${plan.prefix}",
                        style = MaterialTheme.typography.titleMedium)
                    plan.targets.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { t ->
                                Text("${t.name}: ${t.ip}", modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else Text("Introduce matrícula, IP\u0020LAN y máscara válidas para calcular los destinos autorizados.",
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = plan != null && !status.busy && !session.working,
                    onClick = { confirmApply = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Configurar WAN y comprobar los destinos autorizados")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(enabled = session.configured && !status.busy && !session.working,
                        onClick = vm::repeatCtPings) { Text("Repetir ping") }
                    OutlinedButton(enabled = !status.busy && !session.working,
                        onClick = { confirmRestore = true }) { Text("Restaurar WAN original") }
                }
                Text("Estado: ${session.stage}", style = MaterialTheme.typography.bodyMedium,
                    color = if (session.stage.startsWith("ERROR")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
                if (session.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (session.probes.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Resultados · 4 paquetes ICMP por equipo", style = MaterialTheme.typography.titleSmall)
                    session.probes.forEach { p ->
                        Column(Modifier.fillMaxWidth()) {
                            Text("${p.name} · ${p.ip} · ${p.status}",
                                color = if (p.status == "OK" || p.status == "PARCIAL") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium)
                            if (p.detail.isNotBlank()) Text(p.detail,
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }
                }
                if (session.lastOutput.isNotBlank())
                    SelectionContainer { Text(session.lastOutput.take(2000),
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                Text("Seguridad: solo cambia la WAN del GL-MT300N-V2, nunca la configuración del router del CT. " +
                    "Los ping no prueban por sí solos el estado funcional de un equipo. " +
                    "La copia de restauración en /tmp se pierde si reinicias el Mango.",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (confirmApply && plan != null) {
        AlertDialog(onDismissRequest = { confirmApply = false },
            title = { Text("Confirmar conexión al CT") },
            text = { Text("Matrícula: ${plan.matricula}\nIP\u0020LAN base: ${plan.lanBase}\n" +
                "WAN Mango: ${plan.mangoIp}/${plan.prefix}\n" +
                "Máscara: ${plan.netmask}\n\n" +
                "Confirmo que estoy en una red autorizada, la máscara es correcta, " +
                "y que dirección reservada está libre. El programa NO puede garantizar ausencia de conflictos " +
                "por ping. Solo cambiará la WAN del Mango.") },
            confirmButton = { TextButton(onClick = {
                confirmApply = false
                vm.setupAndProbeCt(plan.matricula, plan.lanBase, plan.netmask, true)
            }) { Text("Aplicar y comprobar") } },
            dismissButton = { TextButton(onClick = { confirmApply = false }) { Text("Cancelar") } })
    }
    if (confirmRestore) {
        AlertDialog(onDismissRequest = { confirmRestore = false },
            title = { Text("Restablecer WAN original del Mango") },
            text = { Text("Se recuperará la copia original del archivo de red creada durante esta sesión del Mango. " +
                "No modifica ningún equipo del CT. Si reiniciaste el Mango, la copia temporal no estará disponible.") },
            confirmButton = { TextButton(onClick = {
                confirmRestore = false; vm.restoreCtWan(true)
            }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } })
    }
}
