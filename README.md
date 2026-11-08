# TELECONTROL Público

**Edición pública independiente — fase 1: base Android compilable y filtro de privacidad.**

Este repositorio fue creado desde cero, **sin importar commits, ramas ni etiquetas** de otros proyectos. No contiene bases de datos de instalaciones, direcciones operativas, credenciales ni documentación interna.

## Estado funcional

Pantalla de acceso a un archivo JSON seleccionado localmente por el usuario. La importación y búsqueda completas aún NO están migradas.

La edición pública tiene **un applicationId diferente** (es.josxtm.telecontrol.community) y por ello se instala en paralelo: **no actualiza, sustituye ni migra los datos de la edición privada instalada**. No cambies el identificador por el de la edición privada ni publiques certificados privados de firma.

## Compilar

En GitHub: Actions > **Public Android build** > Run workflow. La tarea comprueba privacidad y pruebas antes de generar una APK de desarrollo. En local, con Java 17, Android SDK 35 y Gradle 8.9: `gradle :app:assembleDebug`.

**La APK de Actions es una edición pública de pruebas**, no una actualización compatible con las instalaciones privadas. El proyecto no usa analítica, servidores de telemetría ni servicios en la nube.

## Protección

- Solo incorporar código revisado y adaptado expresamente a esta edición.
- **Nunca** copiar repositorios privados mediante fork, mirror, `git push --mirror` ni `git filter-branch`.
- No trasladar documentación interna, catálogos de instalaciones, archivos de firma, capturas de trabajos reales ni historiales Git.
- Los datos del usuario deben permanecer en su dispositivo.
- `python3 tools/privacy_guard.py .` impide publicar archivos y patrones sospechosos; **no reemplaza** la revisión manual.

Lee `SECURITY.md` y `MIGRATION.md` antes de incorporar código funcional.
