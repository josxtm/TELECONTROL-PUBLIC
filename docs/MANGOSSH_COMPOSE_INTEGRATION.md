# MangoSSH original Compose dentro de TELECONTROL-PUBLIC

## Base de referencia

- TELECONTROL público aceptado como referencia técnica: GitHub Actions #97,
  commit `eb904bdeb70616e63cf49ab694c87f1b6feb3e64`.
- GUI MangoSSH: fuente original privada `josxtm/MangoSSH`, rama
  `feat/persistent-signed-updates`, revisada sin copiar historial Git ni datos.
- Repositorio de trabajo **exclusivamente público**: `josxtm/TELECONTROL-PUBLIC`.

## Arquitectura

Se recuperan las funciones Compose de Conexión, Terminal, Diagnóstico,
Historial y Ajustes, los temas originales y el visor nativo de terminal.
MangoSSH se abre por el icono original de TELECONTROL, usando una **Activity
Compose independiente y no exportada** en el proceso `:mangossh`. TELECONTROL
sigue usando exactamente su frontend WebView y su paquete público original.

El cliente SSH incorpora verificación explícita de identidad de servidor,
perfiles cifrados en el almacén privado de Android y consola interactiva.
Las clases públicas Java antiguas **se conservan** temporalmente para evitar
eliminar preferencias o claves guardadas; no se borran datos al migrar la UI.
El nuevo ViewModel importa una sola vez el host, cuenta y puerto introducidos
en la interfaz anterior, después de validarlos y evitando duplicados. No mueve
contraseñas ni acepta automáticamente antiguas huellas de servidor.
Los datos de la pantalla antigua se mantienen intactos, incluso ante fallo.

## Seguridad y limitaciones de esta etapa

El código público no incluye datos operativos, credenciales, catálogos reales,
direcciones de instalaciones, rutas de campo ni reglas internas de topología.
La interfaz de configuración CT se mantiene visualmente, pero la planificación
y los comandos de reconfiguración de red **fallan de forma segura** hasta que
se incorpore una configuración autorizada importada localmente. No se debe
presentar esta parte como operativa ni equivalente funcionalmente todavía.
La conexión SSH normal, terminal y comandos de diagnóstico genéricos siguen
su implementación original.

No iniciar conexiones ni ejecutar comandos automáticamente. La primera
huella SSH exige comprobación manual; las modificaciones se rechazan.

## Verificación

- [ ] Compilación Android con Kotlin 2.0.21 y Compose, Room y DataStore.
- [ ] Privacidad de fuentes y recursos; no incluir configuración de redes.
- [ ] Compilación/inspección de APK con los dos componentes.
- [ ] Prueba instrumentada de apertura desde la portada y regreso al WebView.
- [ ] Prueba de perfiles antiguos y persistencia sin borrado.
- [ ] Comparación de capturas de pantallas Compose originales con datos sintéticos.
- [ ] Test SSH contra servidor de pruebas autorizado (no se acredita hardware real).
- [ ] Revisión de firma persistente antes de instalar como actualización estable.

No fusionar en `main` ni afirmar paridad completa si queda alguna casilla
pendiente. La compilación #97 se conserva inmutable como punto de recuperación.
