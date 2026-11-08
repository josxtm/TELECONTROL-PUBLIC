# Seguridad de la edición pública

## Política obligatoria

Este proyecto es una edición independiente de software Android. Nunca se incluirán archivos con instalaciones reales, credenciales, claves, firmas, datos de usuarios, procedimientos internos, documentación de terceros no redistribuible ni parámetros operativos específicos.

Se prohíbe reutilizar el historial Git del repositorio privado, incluso tras eliminar archivos. Las pull requests, los artefactos y los commits antiguos pueden conservar información.

## Revisiones previas a cualquier PR

1. Ejecutar `python3 tools/privacy_guard.py .`.
2. Inspeccionar manualmente cada archivo nuevo, cambio de código, recurso binario y documentación.
3. Ejecutar pruebas y compilar APK en un entorno sin secretos.
4. Examinar recursos empaquetados, logs y artefactos.
5. Verificar `applicationId`, `versionCode` y huella de firma de la APK antes de decir que actualiza otra aplicación.

**No copiar claves privadas al repositorio público.** Nunca asignar credenciales de firma privadas a un workflow ejecutado sobre una pull request externa.

El código genérico puede ser público; los complementos que requieran conocimientos internos deben permanecer en proyectos privados.
