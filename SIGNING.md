# Canal permanente de firma pública

La APK de PR/CI es una preview debug. No se reutilizan ni consultan firmas privadas originales.

Antes de distribuir una primera versión pública estable:

1. Crear una clave de firma nueva exclusiva para `es.josxtm.telecontrol.community`, fuera de Git, con custodia y copia cifrada controladas por el propietario.
2. Establecer un canal release separado, activado mediante etiquetas y un entorno GitHub protegido. Guardar material de firma en el gestor de secretos del entorno; nunca exponerlo a jobs de pull requests. La configuración actual no solicita ni publica claves.
3. Fijar y publicar la huella SHA-256 del certificado público y conservar esa misma clave en todas las versiones release; comprobar la huella antes de subir cada APK.
4. Incrementar `versionCode` en cada distribución, también al volver a compilar una versión ya publicada. La versión pública nueva utiliza `versionCode = 10000 + X` y `versionName = v.1.0 public - comp. X`, con `X` como número de ejecución de GitHub Actions. Antes de activar un canal release permanente debe garantizarse monotonía del código entre todas sus entregas. Rechazar códigos repetidos o menores que el último release del canal.
5. Validar privacidad, pruebas, Lint, firma, permisos y recursos antes de distribuir. Ensayar actualización solo entre versiones públicas del mismo canal y comprobar conservación del almacenamiento.

Una instalación de preview firmada con otra clave deberá desinstalarse antes de instalar el canal estable: exportar primero una copia local y restaurarla después. La firma permanente y el ensayo entre dos releases quedan pendientes hasta que el propietario configure esa custodia; no se crea una identidad de producción efímera durante la migración.
