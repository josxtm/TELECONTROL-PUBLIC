# INFORME_CAMBIOS — inicio de migración segura

## Trabajo realizado

- Fijada referencia privada de GUI por rama y commit, con su uso limitado a lectura.
- Registradas las discrepancias actuales y los requisitos exactos de aceptación.
- Añadido comprobador estructural independiente del proyecto privado, con tests sintéticos.
- Añadido bloqueo de publicación del APK en GitHub Actions si falta la estructura mínima de la GUI aprobada.
- Preservada la fuente pública ya existente, su identificador Android, el almacenamiento y el historial.
- Ningún dato, captura real, archivo binario ni historial del repositorio privado se ha incluido.

## Pendiente

La migración de la interfaz y la equivalencia funcional están **sin completar**. No confundir un build verde con paridad. Ninguna prueba automatizada sustituye la aceptación visual y funcional.
