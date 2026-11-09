package io.github.josxtm.mangossh.core

/** UI-compatible data contracts. Operational CT routing data is deliberately
 * excluded from public source: it must come from an explicitly imported,
 * user-owned, on-device configuration before CT probing can be enabled.
 */
data class CtTarget(val name: String, val offset: Int, val ip: String)
data class CtPlan(
    val matricula: String, val lanBase: String, val netmask: String,
    val prefix: Int, val mangoIp: String, val targets: List<CtTarget>
)

object CtPlanning {
    fun build(matricula: String, lanBase: String, netmask: String): CtPlan {
        require(matricula.isNotBlank() && matricula.length <= 80) { "Identificador inválido" }
        require(Validation.ipv4(lanBase) && Validation.ipv4(netmask)) { "Direcciones locales inválidas" }
        // Deliberately fail closed. Never infer a site topology or allocate an
        // interface address from a publicly embedded rule.
        throw IllegalStateException("Importa una configuración privada autorizada para habilitar este plan")
    }
}

/** No network-changing operation without a verified, local topology profile.
 * General SSH terminal and read-only diagnostics work independently.
 */
object CtCommands {
    private fun disabled(): Nothing =
        throw IllegalStateException("Operación de red deshabilitada sin configuración local verificada")
    fun preflight(): DiagnosticCommand = disabled()
    fun apply(plan: CtPlan): DiagnosticCommand = disabled()
    fun verify(plan: CtPlan): DiagnosticCommand = disabled()
    fun ping(plan: CtPlan, target: CtTarget): DiagnosticCommand = disabled()
    fun restore(): DiagnosticCommand = disabled()
}
