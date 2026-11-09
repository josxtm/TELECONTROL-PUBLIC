# Componentes de diseño original — TELECONTROL

Este directorio contiene **código visual original recuperado y revisado** para la migración al frontend público. **No forma parte de la APK todavía**: la versión pública continúa utilizando su interfaz anterior y no existe equivalencia completa.

Fuente privada de solo lectura: `josxtm/TELECONTROL`, rama `feat/mangossh-cog-integration`, commit `32b87f14086ad66482322771e78696126beddc0a`.

| Archivo público | Origen privado | Tratamiento |
| --- | --- | --- |
| `themes.css` | `app/src/main/assets/themes.css` | Geometría, temas, proporciones y colores preservados. Selector de marca renombrado a clase neutra y comentario corporativo eliminado. |
| `themes.js` | `app/src/main/assets/themes.js` | Copia exacta del controlador de los seis temas, sin valores operativos. |
| `icons.js` | `app/src/main/assets/icons.js` | Copia exacta de los SVG de iconos funcionales originales, sin recursos corporativos. |

El directorio `design/original` es un **origen revisado de diseño**, no un permiso para incluir recursos privados indiscriminadamente. No incorporar archivos JSON, fotos, manuales, capturas operativas ni logos sin derechos acreditados.

**Próxima integración:** reconstruir la portada y navegación WebView con este CSS/JS, mediante una interfaz pública de almacenamiento local compatible con los datos ya instalados. La adaptación no debe sustituir ni borrar la base local pública ni cambiar el identificador de paquete.

**No declarar paridad** hasta realizar comparación visual de las pantallas originales con datos sintéticos, pruebas de todas las funciones y firma estable.

## Estructura HTML recuperada

`index.html` reproduce la estructura original de portada, ficha, configuración, diario, trabajo y estados. Se ha **desactivado la redistribución de marcas no autorizadas** sustituyendo únicamente los elementos de imagen por espacios de igual clase/tamaño; las clases visuales permanecen enlazables al CSS original. Se han neutralizado las menciones a documentos internos, identificadores operativos y etiquetas de red en la plantilla. Las referencias antiguas a un identificador de código interno se han renombrado a `workcode` en esta fuente: cuando se conecte el JavaScript funcional, el mapeo debe actualizarse en todos los módulos correspondientes.

**No ejecutar este HTML directamente ni incluirlo en el APK aún**: los módulos JS funcionales no han sido saneados ni enlazados con la persistencia pública, y la identidad visual de marcas está legalmente pendiente. El icono MangoSSH debe integrarse en la portada sin mover sus controles originales.
