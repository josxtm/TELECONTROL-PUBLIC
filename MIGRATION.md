# Migración controlada — fase 2

Implementación independiente a partir de requisitos públicos e issue #1. No se han consultado repositorios privados ni copiado código, datos, ramas, firmas o historial de ellos. Se conserva la base Android Java existente; se han añadido módulos separados `JsonCodec` y `LocalStore` sin dependencias de producción nuevas.

## Implementado

Importación incremental con selector Android, índice SQLite por campo configurable, ficha y selección persistente de campos, notas por registro, diario separado y paginado, copia/restauración local transaccional, dictado en dispositivo cuando esté disponible y temas claro/oscuro/sistema. El esquema de almacenamiento público comienza en 1; la base pública anterior no almacenaba datos de trabajo. La persistencia y la reapertura del esquema actual se prueban sin rutas de acceso a otras aplicaciones.

## Exclusiones de seguridad

No se incorporan catálogos, configuración de instalaciones, direcciones operativas, documentos, topologías, capturas, servicios remotos ni materiales internos. No hay permiso de Internet, copias automáticas ni lectura de almacenamiento de otras apps. Los errores no imprimen importaciones. No se registran contenidos. Las pruebas generan datos sintéticos explícitos. El filtro de privacidad conserva sus patrones originales y amplía protección a datos empaquetados, enlaces y permisos de red.

## Pruebas y límites

El workflow exige filtro y sus pruebas, pruebas unitarias JVM, Android Lint, compilación, inspección de paquete/versión/firma/permisos/recursos y pruebas instrumentadas en Android API 35 antes de subir la APK. Incluye datos inválidos y truncados, claves repetidas, índices exactos, límites, 20000 filas generadas incrementalmente, reversión de importaciones/restauraciones, notas, paginación del diario, reapertura y pruebas de arranque/búsqueda/guardado desde la interfaz.

Pendientes de validación manual en dispositivos físicos: selector y permisos de distintos proveedores, disponibilidad y calidad del modelo de voz local, rotación durante operaciones largas, accesibilidad con TalkBack, tablet real y combinaciones de teclado. Las pruebas automáticas no establecen equivalencia completa con aplicaciones privadas. No se ha medido el rendimiento en el límite de un millón de filas.

Las copias son legibles y requieren custodia del usuario. Los catálogos anidados no se admiten en esta versión. El catálogo se sustituye como conjunto y sus notas se eliminan para evitar asociarlas a otro catálogo; no hay fusión de conjuntos. La interfaz conserva borradores al recrearse, pero no garantiza borradores tras cierre forzado. No hay cancelación de importación iniciada; se procesa fuera del hilo de interfaz y la base revierte operaciones fallidas. El formato y los límites están en README.

No hay migración del esquema público hacia una versión futura todavía: los cambios de esquema deberán aportar migraciones explícitas con pruebas antes de incrementar su versión; nunca borrar datos como alternativa a una migración fallida. Firma permanente y pruebas de actualización entre releases públicos pendientes según SIGNING.md.
