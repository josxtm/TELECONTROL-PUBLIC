# Compilaciones de recuperación — TELECONTROL

Repositorio público: `josxtm/TELECONTROL-PUBLIC`  
Rama: `feat/restore-original-gui-v1-public`

**CI:** https://github.com/josxtm/TELECONTROL-PUBLIC/actions

La primera fase ha conseguido ensamblar Android en runners estándar con la GUI WebView original incorporada. Las ejecuciones posteriores revisan instrumentación, seguridad de activos y pruebas reales de emulador.

Un resultado de Gradle correcto **no implica** que la restauración ya haya sido aceptada. El gate de GUI debe impedir publicar una APK como definitiva mientras los recursos originales y funciones privadas pendientes no estén restaurados.

- Prueba de referencia de compilación intermedia: https://github.com/josxtm/TELECONTROL-PUBLIC/actions/runs/42533005509
- PR de recuperación: https://github.com/josxtm/TELECONTROL-PUBLIC/pull/6

**Firma:** las APK de depuración de GitHub Actions pueden tener certificados distintos entre ejecuciones; no garantizar actualización directa. Se requiere firma persistente protegida para distribuir versiones estables.

## Punto de referencia verificado y restauración Compose

- **#97 SUCCESS:** https://github.com/josxtm/TELECONTROL-PUBLIC/actions/runs/42542228665 — portada WebView original validada técnicamente; punto inmutable de recuperación.
- Rama Compose: `feat/mangossh-original-compose-in-telecontrol`, PR #7. Cada nueva ejecución se revisará por separado y se documentarán sus fallos reales.
- Los APK de esta rama no serán equivalentes a la aplicación privada hasta completar las pruebas visuales, de almacenamiento y SSH con hardware real.
