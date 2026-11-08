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
