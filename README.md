# TELECONTROL Público

Edición Android pública independiente, con consulta local de catálogos aportados por el usuario. Paquete `es.josxtm.telecontrol.community`, versión visible **`v.1.0 public - comp. X`**, donde **X** es el número de ejecución de GitHub Actions (o el valor pasado mediante `-PpublicCompilationNumber=X`). `versionCode = 10000 + X`. Se instala en paralelo con las ediciones privadas.

## Funciones

- Importar JSON con el selector de documentos del sistema. Lectura incremental, inserción transaccional en SQLite e índice por identificador; sin cargar el catálogo completo en memoria.
- Buscar por identificador exacto configurable, ver la ficha y elegir sus campos visibles.
- Guardar notas por registro y entradas de un diario independiente, con páginas de 25 entradas.
- Exportar/restaurar catálogo, campos visibles, notas y diario mediante archivos locales. Una importación o restauración inválida revierte todos sus cambios.
- Temas sistema, claro y oscuro; interfaz desplazable con ancho limitado en tablet, texto escalable y márgenes del sistema.
- Dictado exclusivamente con el reconocedor **en el dispositivo** de Android 12 o posterior cuando esté disponible. No se utiliza reconocimiento remoto como alternativa. Solo se pide micrófono al pulsar dictar.

No contiene catálogos reales, analítica ni servicios propios. El permiso `INTERNET` se utiliza exclusivamente por el cliente SSH integrado bajo autorización del usuario; Android no limita técnicamente dicho permiso a una red local. Los datos se guardan en el almacenamiento privado de esta aplicación. Se bloquean capturas de pantalla y copias automáticas del sistema. Los teclados y proveedores de documentos elegidos por el usuario tienen sus propias políticas: elige servicios locales autorizados.

## MangoSSH en otra instancia

- La única modificación de la portada es **un icono de mango con gafas** que abre MangoSSH.
- MangoSSH se incorpora a la misma APK, pero se ejecuta en **otra Activity, tarea y proceso Android** (`:mangossh`), no como una aplicación instalada por separado. Volver no reinicia TELECONTROL.
- El cliente SSH procede de código genérico público de `josxtm/MANGOSSH-PUBLIC`: introducir host, puerto, usuario y contraseña de sesión, comprobar huella SHA-256, diagnósticos permitidos y terminal interactiva previa confirmación.
- No transfiere automáticamente catálogos, notas o datos de fichas al cliente SSH; no guarda la contraseña ni se conecta sin que el usuario lo solicite.
- El acceso a MangoSSH requiere **Android 8/API 26**; TELECONTROL conserva soporte desde API 23 y muestra aviso en sistemas más antiguos.
- Los identificadores, catálogos y contenidos locales no se incluyen en el código fuente ni se transmiten a servidores de la aplicación.

## Formato de importación

Lista JSON raíz de objetos con campos simples: cadenas, números, booleanos o `null`. Ejemplo **inventado exclusivamente para documentación**:

```text
[{"id":"SYN-001","label":"Synthetic example","active":true}]
```

Escribe `id` como campo identificador para ese ejemplo. La búsqueda es exacta, distingue mayúsculas y conserva espacios y ceros iniciales de las cadenas. Los valores se guardan y muestran como texto; `null` se muestra vacío. No se admiten objetos ni listas anidados, claves repetidas, identificadores nulos, vacíos o duplicados, ni contenido después de la lista.

Límites: 512 Mi de caracteres de entrada, un millón de registros, 100 campos distintos, nombres de campo de 128 caracteres, identificador de 256, valores/notas de 16000 y 262144 caracteres acumulados por registro. Una importación correcta sustituye el catálogo y elimina sus notas con confirmación previa; conserva el diario. Haz una copia antes de sustituir datos.

## Copias locales

La copia JSON `telecontrol-community`, esquema 1, solo restaura datos de esta edición pública. Incluye todos los datos locales de trabajo, aunque algunos campos estén ocultos en la ficha. Es texto legible, sin cifrado propio: guárdala en una ubicación local protegida. El selector Android también puede ofrecer proveedores remotos; esta aplicación no puede garantizar que el proveedor elegido sea local.

Restaurar requiere confirmación y sustituye el conjunto completo en una transacción. Se validan esquema, límites, identificadores, referencias de notas y final del archivo. La versión actual no transforma copias de otras aplicaciones. No hay migración desde ninguna edición privada.

## Validación y APK

CI **Public Android build** ejecuta filtro de privacidad, pruebas Python, pruebas unitarias JVM, Android Lint, compilación y pruebas en Android API 35 (incluyen importación sintética incremental de 20000 registros, notas, diario, recuperación transaccional, persistencia y arranque/búsqueda desde la interfaz). Solo sube la APK cuando pasan todos los pasos. El artefacto incluye `apk-verification.txt` con identidad, versión, huella SHA-256 del certificado y hash SHA-256 de la APK; se verifican firma, permisos y recursos empaquetados.

En local: JDK 17 completo, Android SDK 35 y Gradle 8.9:

```sh
python3 tools/privacy_guard.py .
python3 -m unittest discover -s tests -v
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 tools/inspect_apk.py app/build/outputs/apk/debug/app-debug.apk "$ANDROID_HOME/build-tools/35.0.0"
gradle :app:connectedDebugAndroidTest
```

Las APK de CI son de pruebas, firmadas con una clave debug efímera. No garantizan actualizaciones entre ejecuciones y **no son actualizaciones de aplicaciones privadas**. Lee [SIGNING.md](SIGNING.md) para el canal público permanente y [MIGRATION.md](MIGRATION.md) para límites y verificaciones pendientes.
