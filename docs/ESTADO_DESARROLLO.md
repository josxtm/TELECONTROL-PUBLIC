# Estado de desarrollo — TELECONTROL público

## Recuperación original en curso

**Realizado:** recuperación de fuentes WebView/HTML/CSS/JS, 21+ recursos de frontend, clases nativas de cámara, sincronización y recordatorios, integración SSH desde portada, icono original de MangoSSH y versiones públicas numeradas.

**Pendiente:** verificar condiciones de distribución de los logotipos originales restaurados, módulo de red local sin tabla interna publicable, migración segura de almacenes públicos antiguos, comprobación visual y funcional final de la GUI Compose original de MangoSSH, configuración de redes locales importada por el usuario, comparaciones visuales y firma persistente.

## Identidad de distribución

- `applicationId`: `es.josxtm.telecontrol.community` (no cambia).
- Formato de versión: `v.1.0 public - comp. X`.
- Los datos de una APK privada instalada no son automáticamente accesibles desde el paquete público.
- Los datos almacenados por la edición pública previa no se han eliminado, pero todavía no se han convertido para la interfaz nueva.
- No instalar una APK de depuración sobre otra con firma distinta sin realizar copia de seguridad.

**Estado de aceptación:** pendiente, PR #6 en borrador, no fusionado en `main`.

## Integración MangoSSH Compose — PR #7

La interfaz Java simplificada deja de abrirse desde TELECONTROL; el botón MangoSSH ahora dirige a la **Activity Compose original**, en proceso y tarea separados. Se han recuperado páginas y temas con su lógica SSH, terminal y almacenamiento privado. Conservamos la Activity Java previa y migramos de forma no destructiva su host, usuario y puerto, sin trasladar automáticamente la confianza SSH.

**No completado:** configuración de equipos de campo, firma persistente, prueba SSH física y comparación visual de terminal en tablet/móvil. El interfaz WebView de TELECONTROL está sin cambios en esta rama.
