# COMPILACIONES — TELECONTROL

**Referencia CI pública:** [última APK anterior comprobada](https://github.com/josxtm/TELECONTROL-PUBLIC/actions/runs/42525696021). Corresponde al diseño público antiguo y **no cumple** la GUI privada aprobada.

El control `tools/gui_parity_gate.py` se ejecuta **después** de la compilación y pruebas, pero **antes** de publicar una APK. Así CI seguirá dando diagnósticos Android; si no existe la GUI aprobada, no se publicará una candidata engañosa.

No existe todavía una APK verificada de migración con paridad real. Las ejecuciones de la nueva rama deben reportarse como pendientes o bloqueadas hasta completar la restauración.

**Advertencia:** la firma Debug efímera no garantiza actualizaciones seguras; la identidad y firma permanente requieren validación aparte.
