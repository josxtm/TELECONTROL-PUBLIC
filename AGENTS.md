# CONTRATO OBLIGATORIO DE GUI Y DESARROLLO PÚBLICO — Codex

> **Regla permanente del propietario. Prioridad máxima.** Una aplicación pública debe mantener **exactamente la GUI aprobada y probada del repositorio privado equivalente**, antes de aceptar cualquier nueva función. Esta instrucción se aplica a todos los cambios y revisiones presentes y futuros. Si existen diferencias, son defectos bloqueantes, incluso si la APK compila.

## Origen de verdad

- **Repositorio privado de referencia (lectura exclusivamente):** `josxtm/TELECONTROL`.
- **Repositorio público, único destino autorizado de cambios y desarrollo nuevo:** `josxtm/TELECONTROL-PUBLIC`.
- **Identidad GUI:** La interfaz aprobada de TELECONTROL v2.1, incluyendo la portada HTML/CSS/JS, sus temas, logotipos autorizados, navegación, iconos y disposición. Referencia técnica histórica: `feat/mangossh-cog-integration` (o el commit privado aprobado más reciente). El prototipo público de formularios genéricos NO cumple la paridad.
- **Advertencia concreta:** El PR #3 existente queda bloqueado hasta que se demuestre paridad visual y funcional; conservar únicamente el icono adicional de MangoSSH autorizado.

## Reglas no negociables

1. **Nunca escribir, editar, fusionar PR, versionar, generar una rama de desarrollo ni ejecutar cambios en el repositorio privado.** Su código y GUI actúan como referencia de lectura; el desarrollo continúa únicamente en el público.
2. **La GUI pública debe ser visual y funcionalmente equivalente a la GUI privada aprobada**, no solo similar: conservar portada, elementos visuales, iconografía, estilo, fuentes, proporciones, menús, navegación, disposición, colores, temas, tamaños adaptativos y comportamiento de todas las pantallas existentes.
3. **Prohibido reconstruir, sustituir o simplificar** la GUI con plantillas Android genéricas, formularios de prueba, una pantalla provisional o un rediseño no solicitado. No usar `main` privado automáticamente como fuente de GUI si la versión aprobada está en otra rama; localizarla y verificarla.
4. **Solo modificar la interfaz cuando el usuario autorice explícitamente ese cambio específico.** Si solicita añadir un icono, se añade exclusivamente el icono en la posición indicada; el resto se conserva.
5. Las nuevas funciones, correcciones, documentación, pruebas, releases, ramas, PR y compilaciones APK se desarrollan exclusivamente en el **repositorio público**.
6. **Privacidad obligatoria:** jamás publicar datos de clientes/CT, matrículas reales, IP operativas, credenciales, claves, JSON privados, documentación interna, historial Git confidencial, capturas de instalaciones reales ni otros secretos. No incorporar assets de terceros sin permiso adecuado. Replicar la GUI con recursos legítimos y sintéticos; nunca copiar a ciegas todo un repositorio privado.
7. Los cambios que puedan alterar la identidad Android, el almacenamiento, la firma, las migraciones o la compatibilidad de actualización deben documentarse y probarse explícitamente; conservar datos existentes. Una APK de otro `applicationId` o firma **no actualiza** la privada aunque la GUI coincida.
8. **Validación antes de aceptar:** comparación de las pantallas clave contra capturas de la versión privada aprobada (con datos sintéticos), pruebas de navegación, restauración de estado, persistencia, accesibilidad básica, escalado móvil/tablet, pruebas unitarias y GitHub Actions con APK. La compilación verde por sí sola **NO** demuestra paridad GUI.
9. Si falta alguna imagen de referencia o recurso necesario, documentar la carencia y no afirmar que hay paridad total. Una GUI distinta es fallo de aceptación que bloquea la fusión.
10. Entregas mediante rama, PR e informe de diferencias/capturas. No fusionar automáticamente sin autorización del propietario.

## Criterios de aceptación

- [ ] Mismo diseño y mismas funciones previas (salvo cambios expresamente autorizados).
- [ ] Comparación visual aprobada con la referencia privada real.
- [ ] Datos confidenciales excluidos de commits, historial, APK y artefactos públicos.
- [ ] GitHub Actions y pruebas técnicas verdes; APK disponible.
- [ ] Desarrollo y cambios contenidos exclusivamente en el repositorio público.

**Una solicitud futura de nuevas funciones no autoriza, por sí sola, ningún cambio a la GUI existente.**
