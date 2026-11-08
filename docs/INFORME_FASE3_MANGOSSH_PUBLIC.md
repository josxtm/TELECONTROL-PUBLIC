# Informe de integración — TELECONTROL Público + MangoSSH

**Fecha:** 08/11/2026  
**Repositorio:** `josxtm/TELECONTROL-PUBLIC` (público)  
**Rama:** `feat/mangossh-public-integration`  
**Objetivo de versión:** **`v.1.0 public - comp. X`**, X = `GITHUB_RUN_NUMBER` por cada ejecución de CI (o `-PpublicCompilationNumber=X` en local); `versionCode = 10000 + X`.

## 1. Requisito de interfaz respetado

**No se ha reconstruido ni rediseñado la interfaz TELECONTROL.** Todos los controles de búsqueda, ficha, notas, diario, ajustes de tema, importación y copia permanecen en su ubicación y funcionalidad anteriores. La única adición visible en la pantalla inicial es **un botón con el icono vectorial original del mango con gafas**, etiqueta accesible `Abrir MangoSSH`.

El botón abre el módulo SSH en **otra Activity y otra tarea** con afinidad propia, y la actividad corre en un **proceso Android independiente** (`:mangossh`). MangoSSH conserva su propia pantalla y controles de conexión, no sustituye la Activity principal ni requiere instalar la APK MangoSSH independiente. No se copian los datos locales al abrirla; volver a TELECONTROL mantiene su Activity.

## 2. Código incorporado y origen

Desde el **repositorio público** `josxtm/MANGOSSH-PUBLIC` se reutilizaron las clases genéricas:

- `EndpointPolicy.java`: validación de host/puerto/usuario, comandos permitidos y cambio de huella.
- `SshTransport.java`: JSch mantenido, SSH con comprobación estricta de huella, aceptación inicial explícita, shell PTY, diagnósticos y desconexión.
- `MainActivity.java`: pantalla SSH nativa, terminal simple, confirmación de privilegios, historial de sesión y perfil sin contraseña.

Se sustituyeron algunas API Java modernas (`Set.of`, `isBlank`, `String.join`) para no introducir fallos evitables de compatibilidad con versiones Android antiguas. El módulo se habilita desde **API 26**; TELECONTROL mantiene `minSdk 23` con aviso si se pulsa el icono en versiones anteriores.

## 3. Cambios técnicos

- `AndroidManifest.xml`: permiso `INTERNET` para sesiones SSH iniciadas por el técnico; Activity SSH `exported=false`, proceso y tarea independientes. `RECORD_AUDIO` y bloqueo de copias automáticas no cambian.
- `app/build.gradle`: JSch `com.github.mwiede:jsch:2.28.8`; versión pública basada en número de ejecución; **`applicationId es.josxtm.telecontrol.community` inalterado**.
- `ic_mangossh.xml`: icono vectorial con mango y gafas, sin recursos externos.
- `tools/privacy_guard.py`: excepción muy restringida para el permiso de Internet solo cuando la Activity SSH interna no se exporta y usa el proceso aislado. Todo lo demás continúa bloqueado.
- `tools/inspect_apk.py`: comprobación de versión pública numerada, lista cerrada de permisos y presencia de la Activity SSH.
- Tests: reglas de validación de SSH y comprobación Android del nuevo icono/registro de la Activity; casos negativos de seguridad y versión.

## 4. Alcance y garantías de privacidad

Sin copia ni importación de código, historial, credenciales o catálogo desde repositorios privados. No se incorporan datos reales de usuarios, hosts concretos, direcciones operativas, topologías ni documentos internos. El permiso INTERNET es global desde el punto de vista de Android; el componente implementado realiza solo conexiones al host introducido por el usuario. **El terminal interactivo sí puede ejecutar comandos con los privilegios del servidor; requiere confirmación expresa y debe usarse solo en equipos autorizados.** La huella nueva debe cotejarse fuera de banda; una huella cambiada se rechaza.

La APK pública es **independiente** de las ediciones privadas y no migra sus datos. Se conserva el almacén local público, sin migraciones destructivas. La pantalla SSH tiene sus propias preferencias dentro de la instalación pública.

## 5. Validación y APK

- Validación de privacidad estática, pruebas Python, JVM, Lint, ensamblado y Android instrumentado: **pendientes de resultado final GitHub Actions** en el momento de redactar este informe.
- La evidencia final del workflow y del artefacto debe incorporarse aquí al terminar la ejecución; no declarar APK funcional por tener únicamente código fuente.
- El workflow `Public Android build` se ejecuta en runners GitHub estándar. El artefacto solo se publica después de pasar las verificaciones obligatorias.
- La firma debug efímera de GitHub Actions **no garantiza actualización sin pérdida de datos entre ejecuciones**. Para una distribución estable se requiere un certificado persistente protegido y probado, conforme a `SIGNING.md`.
- Falta prueba física de conexión SSH a un servidor controlado por el propietario. El emulador puede verificar interfaz, navegación y seguridad, pero no acredita la compatibilidad con todos los servidores SSH reales.

## 6. Revisión y seguimiento

Guardar cambios en PR hacia `main`. No fusionar automáticamente antes de revisión. Si CI falla, corregir en esta misma rama y repetir las comprobaciones sin publicar datos internos.
