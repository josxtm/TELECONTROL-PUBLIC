package io.github.josxtm.mangossh.core

/** Only this allowlist can construct executable diagnostic operations. */
class DiagnosticCommand internal constructor(val id: String, val category: String, val title: String, val shell: String)

object Diagnostics {
    private fun tool(id: String, category: String, title: String, binary: String, args: String) =
        DiagnosticCommand(id, category, title, "if command -v $binary >/dev/null 2>&1; then $binary $args; else printf '%s\\n' 'Herramienta no disponible: $binary' >&2; exit 127; fi")
    private fun file(id: String, category: String, title: String, path: String) =
        DiagnosticCommand(id, category, title, "if test -r $path; then cat $path; else printf '%s\\n' 'Archivo no disponible: $path' >&2; exit 127; fi")
    val commands = listOf(
        file("firmware", "Sistema", "Firmware / OpenWrt", "/etc/openwrt_release"),
        tool("kernel", "Sistema", "Kernel", "uname", "-a"),
        tool("architecture", "Sistema", "Arquitectura", "uname", "-m"),
        file("cpu", "Sistema", "CPU", "/proc/cpuinfo"),
        tool("ram", "Sistema", "RAM", "free", ""),
        tool("disk", "Sistema", "Almacenamiento", "df", "-h"),
        tool("uptime", "Sistema", "Tiempo encendido", "uptime", ""),
        tool("interfaces", "Red", "Interfaces / Ethernet", "ip", "link"),
        tool("addresses", "Red", "Direcciones IPv4", "ip", "-4 addr"),
        tool("routes", "Red", "Rutas", "ip", "route"),
        tool("network", "Red", "Configuración LAN", "uci", "show network"),
        // Exclude wireless keys and DHCP secrets from the normal display and reports.
        DiagnosticCommand("wifi", "Red", "Wi-Fi (sin claves)", """if command -v uci >/dev/null 2>&1; then wireless="${'$'}(uci show wireless)" || exit ${'$'}?; printf '%s\n' "${'$'}wireless" | sed '/\.key=/d; /\.password=/d; /\.auth_secret=/d; /\.acct_secret=/d; /\.sae_password=/d'; else printf '%s\n' 'Herramienta no disponible: uci' >&2; exit 127; fi"""),
        tool("dhcp", "Red", "DHCP", "uci", "show dhcp"),
        tool("processes", "Diagnóstico", "Procesos", "ps", ""),
        tool("logs", "Diagnóstico", "Logs recientes", "logread", "-l 100"),
        tool("interfaceStatus", "Diagnóstico", "Estado de interfaces", "ip", "-s link"),
        DiagnosticCommand("model", "Mango", "Modelo", "if test -r /tmp/sysinfo/model; then cat /tmp/sysinfo/model; elif command -v ubus >/dev/null 2>&1; then ubus call system board; else uname -a; fi"),
        file("glFirmware", "Mango", "Firmware GL.iNet", "/etc/glversion"),
    )
    fun byId(id: String): DiagnosticCommand? = commands.firstOrNull { it.id == id }
    fun ping(ip: String): DiagnosticCommand {
        require(Validation.ipv4(ip)) { "Introduce una dirección IPv4 válida" }
        return tool("ping:$ip", "Diagnóstico", "Ping $ip", "ping", "-c 4 -W 2 $ip")
    }
    fun repeat(id: String): DiagnosticCommand? = byId(id) ?: if (id.startsWith("ping:")) {
        runCatching { ping(id.removePrefix("ping:")) }.getOrNull()
    } else null
    val audit get() = commands.filter { it.id in setOf("model", "glFirmware", "firmware", "kernel", "architecture", "cpu", "ram", "disk", "interfaces", "addresses", "routes", "wifi", "dhcp") }
    fun report(profile: Profile, results: List<Pair<DiagnosticCommand, CommandResult>>): String = buildString {
        appendLine("MangoSSH · Auditoría de consultas · ${profile.name} · ${profile.user}@${profile.endpoint}")
        appendLine("Revisar datos sensibles antes de compartir. No se modificó la configuración.")
        results.forEach { (cmd, result) -> appendLine("\n== ${cmd.title} ==\n${result.report()}") }
    }
}
