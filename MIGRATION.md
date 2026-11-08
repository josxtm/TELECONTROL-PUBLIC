# Migración controlada — TELECONTROL Público

**Fase 1 completada:** repo creado desde cero, base Android independiente, escáner preventivo, CI de construcción y versión pública de pruebas.

**Fase 2 pendiente (para Codex):** comparar exclusivamente el código actual del repositorio privado autorizado, clasificar archivos y dependencias, portar módulos genéricos revisados manteniendo la aplicación privada intacta, eliminar toda dependencia de material interno, ejecutar pruebas y validar comportamiento.

**Fase 3 pendiente:** pruebas de usuario con APK pública instalada en paralelo. La equivalencia de funcionalidades, firma y actualización de la aplicación privada **no está demostrada**.

No renombrar el paquete público (es.josxtm.telecontrol.community) para imitar el privado. Las versiones públicas no deben acceder a datos de aplicaciones privadas por rutas compartidas.

No trasladar bitácoras internas, ficheros reales, direcciones o topologías operativas, plantillas no redistribuibles, credenciales, certificados, keystores ni versiones históricas.
