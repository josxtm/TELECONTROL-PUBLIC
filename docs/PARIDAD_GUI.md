# Paridad de GUI original — TELECONTROL público

**Estado: EN RESTAURACIÓN, NO ACEPTADA.** La presencia de archivos originales no equivale a conformidad visual y funcional.

- Fuente privada de referencia, lectura exclusivamente: `josxtm/TELECONTROL`, rama `feat/mangossh-cog-integration`, commit `32b87f14086ad66482322771e78696126beddc0a`.
- Destino público de desarrollo: `josxtm/TELECONTROL-PUBLIC`, rama `feat/restore-original-gui-v1-public`.
- GUI original recuperada: pantalla principal HTML/WebView con iconos y barra original, temas CSS, navegación, búsqueda, fichas, diario, trabajo semanal y ajustes.
- MangoSSH: único cambio de GUI autorizado, icono original con gafas dentro del botón existente. Abre Activity separada y no inicia SSH automáticamente.
- Se conservó el nombre Android público y la política de no copiar historiales privados.

## Diferencias bloqueantes de aceptación

- [x] Los dos gráficos originales de la portada se han restaurado por hash idéntico al origen privado y se han eliminado los sustitutos vectoriales. Sus archivos PNG solo contienen datos de imagen, sin metadatos adicionales. Las marcas siguen siendo propiedad de sus titulares; respetar las condiciones de uso y distribución.
- [ ] Módulo de referencia de red: la tabla operativa interna fue excluida. Debe sustituirse por configuración importada únicamente en el dispositivo del técnico; actualmente se muestra un aviso sin datos.
- [ ] Recuperación de los datos ya guardados por la primera edición pública: se ha conservado su código y almacenamiento, pero no existe migración reversible y probada hacia el modelo WebView de origen.
- [x] Recuperado el **código fuente** de la GUI MangoSSH Compose original (páginas, navegación, temas y terminal) en una Activity aislada; la comparación visual en dispositivos continúa pendiente.
- [ ] Capturas de comparación en móvil y tablet, con datos sintéticos, aprobadas por el propietario.
- [ ] Firma persistente y actualización comprobada sin pérdida de datos.

## Comprobaciones de integridad

El filtro público solo admite el conjunto de recursos de frontend revisado e identificado por hash de contenido. Se bloquean binarios desconocidos, credenciales, direcciones de redes incrustadas y módulos de topología no autorizados. Los modelos OCR son materiales públicos verificados por SHA-256.

`tools/gui_parity_gate.py` exige la presencia de la GUI original **y de sus recursos gráficos aún pendientes** antes de permitir publicar una APK como válida; compilar Android no sustituye esa validación.

Relacionado con los PR #3 y #6 y con la incidencia #5.

## Paridad MangoSSH en PR #7

La clase Java simplificada se conserva únicamente para compatibilidad de almacenamiento; la portada ahora abre el Activity Compose original. Los colores, distribución responsiva, navegación, terminal y sus pantallas proceden del trabajo privado aprobado, auditados sin incluir datos de instalaciones. La función de planificación CT permanece **deshabilitada** para no publicar reglas internas; requiere una configuración autorizada importada en el dispositivo. Por ello no se puede certificar aún la paridad funcional completa.
