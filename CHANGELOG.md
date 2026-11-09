# CHANGELOG

## v.1.0 public — restauración de interfaz original (en curso)

- Reconstruido el frontend original TELECONTROL HTML/CSS/JavaScript a partir de una referencia privada de lectura.
- Importadas las clases Android de apoyo para cámara, recordatorios, trabajo semanal y sincronización de carpetas.
- Restaurado el icono original de MangoSSH en la portada, sin cambiar el resto de controles.
- Se mantiene paquete Android público y numeración de compilaciones con GitHub Actions.
- Recursos con derechos no verificados y reglas internas de red quedan excluidos; no hay datos operativos incorporados.
- Comprobaciones de seguridad, test de carga WebView y bloqueos de GUI incompleta en CI.
- **Paridad visual y funcional todavía NO aprobada.**

## Fase MangoSSH Compose original — PR #7 (en validación)

- Recuperada la interfaz Compose original dentro de TELECONTROL, sin cambios en la portada WebView.
- Temas claro/oscuro, panel de perfiles, consola, diagnósticos, historial y ajustes.
- Actividad Android aislada en proceso propio; mantenimiento temporal de interfaz Java anterior para conservar preferencias.
- Migración no destructiva de conexiones guardadas; confianza SSH nunca aceptada automáticamente.
- Reglas de reconfiguración de redes de campo excluidas del código público; operaciones dependientes de ellas bloqueadas.
- Revisión de APK, seguridad, pruebas visuales y firma estable aún pendiente.
