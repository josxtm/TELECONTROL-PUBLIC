# PARIDAD_GUI — TELECONTROL

**ESTADO: NO ACEPTADA / NO RESTAURADA.** Esta es una auditoría de diferencias, no una afirmación de equivalencia.

Origen **privado, solo lectura:** `josxtm/TELECONTROL`, rama `feat/mangossh-cog-integration`, commit `32b87f14086ad66482322771e78696126beddc0a`. No copiar historial Git ni recursos operativos. El SHA identifica la referencia técnica y no concede permiso para publicarla indiscriminadamente.

**Destino público:** `josxtm/TELECONTROL-PUBLIC`, rama `feat/mangossh-public-integration`.

## Superficies que deben coincidir

Portada original HTML/CSS/JS; temas; búsqueda y ficha; notas y diario; trabajos; navegación; acceso MangoSSH añadido sin mover otros componentes.

## Diferencias bloqueantes

La edición pública actual usa formularios Android simplificados; no contiene la portada ni las hojas CSS y JS aprobadas. El PR SSH contiene icono y Activity, pero no repara la GUI. Recursos gráficos de terceros y módulos que describan sistemas privados requieren revisión previa. La persistencia de la edición pública existente debe preservarse durante cualquier cambio de frontend.

## Evidencia y controles

- [ ] Inventario y saneamiento manual de todas las fuentes y recursos destinados a publicar; referencias privadas sin datos reales.
- [ ] Misma portada, iconografía, temas, geometría, rutas y estados con entradas sintéticas.
- [ ] Compatibilidad funcional, almacenamiento local y actualizaciones sin pérdida.
- [ ] Capturas comparativas reales de móvil y tablet revisadas; no incluir capturas con información confidencial.
- [ ] Pruebas Android, seguridad, firma y APK final verificadas.
- [ ] Autorización del propietario para integrar el PR en main.

`tools/gui_parity_gate.py` comprueba únicamente presencia de ficheros y marcadores; **no sustituye comparación visual ni garantiza igualdad de funciones**. Mientras falten componentes, la validación de publicación de la APK falla expresamente.

[Incidencia de paridad](https://github.com/josxtm/TELECONTROL-PUBLIC/issues/5) · [PR de trabajo](https://github.com/josxtm/TELECONTROL-PUBLIC/pull/3).

## Launcher original recuperado

Se trasladaron los cinco recursos originales de icono de la aplicación, verificados por SHA del blob Git, y se referencia el icono en el manifiesto público. El resto de la interfaz y sus funciones aún **no** tienen paridad aprobada.

## Recuperación real de código de diseño (09/11/2026)

Se han incorporado a `design/original/` los estilos CSS, controlador de seis temas e iconografía SVG originales, tras saneamiento de referencias corporativas en el CSS. Son **fuentes de diseño revisadas**, todavía **no integradas en la APK**. El aspecto actual sigue siendo diferente. La interfaz WebView, sus acciones y su persistencia deben adaptarse sin reemplazar el almacenamiento local existente.

## Portada y pantallas HTML recuperadas como fuente revisada

La estructura HTML original se conserva en `design/original/index.html`, con huecos de igual clase para logos sin permiso, etiquetas operativas neutralizadas y sin archivos JSON reales. El documento está fuera de la APK y no es todavía una interfaz ejecutable. La equivalencia visual requiere marcas autorizadas, enlace de JavaScript y pruebas en dispositivos.
