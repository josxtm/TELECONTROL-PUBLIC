# Informe de cambios — Recuperación GUI de TELECONTROL

## Implementado en el repositorio público

1. Copia auditada de HTML y estilos de la GUI privada aprobada, sin copiar historiales, catálogos ni datos de usuario.
2. Portada, menús, configuración, fichas, notas, diario, trabajos, avisos y sincronización de la interfaz original.
3. Restauración de la Activity WebView y de las clases Android originales necesarias para los módulos previamente desarrollados.
4. Integración con MangoSSH en otra Activity, tarea y proceso, conservando la navegación principal.
5. Icono de MangoSSH restaurado desde el repositorio público de ese proyecto y validado byte por byte.
6. Conservación de identificador público y formato `v.1.0 public - comp. X`.
7. Auditoría pública de fuentes/activos por hash y rechazo de contenido operacional privado; OCR público por hash.
8. Instrumentación de prueba para abrir el launcher WebView original, además de las pruebas anteriores.
9. Controles CI que bloquean un artefacto anunciado como equivalente cuando los elementos originales aún faltan.

## No completado

- Logotipos originales restaurados y auditados byte a byte. Derechos de las marcas pertenecen a sus titulares; su utilización debe ajustarse a los permisos aplicables.
- Reglas privadas de red, sustituidas temporalmente por un adaptador neutro.
- Migración probada de datos guardados por otras variantes de la aplicación.
- Pruebas visuales del diseño Compose original de MangoSSH, ahora incorporado en PR #7.
- Verificación visual real en móvil y tablet y firma estable.

**No afirmar equivalencia completa antes de superar estas pendientes.** Los repositorios privados permanecen intactos.

## PR #7 — Recuperación del diseño original MangoSSH

- Integradas las pantallas de conexión, terminal, diagnóstico, historial y configuración de Compose, con temas claro/oscuro y terminal nativa.
- Enlace del icono original de TELECONTROL a la nueva Activity, aislada en tarea/proceso.
- Cliente SSH original, huellas comprobadas y almacenamiento local cifrado, sin transferencia automática de claves confiables anteriores.
- Migración conservadora de los parámetros de conexión de la interfaz pública anterior, sin borrar datos.
- No se han trasladado configuraciones privadas de redes de campo; las operaciones que las requieren están bloqueadas hasta importar una configuración local válida.
- Nueva dependencia de Kotlin/Compose, Room y DataStore; requiere pruebas de compilación Android y de navegación en emulador.
