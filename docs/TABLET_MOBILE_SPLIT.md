# TELECONTROL-PUBLIC: variantes TABLET y MOBILE

Base inicial: PR #7, commit `7c5cf167d072a053bcbe010f9faead557438a776`. No fusionar PR pendientes automáticamente.

## APK y compatibilidad

| Variante | Paquete | Versión | Orientación |
| --- | --- | --- | --- |
| MOBILE | `es.josxtm.telecontrol.community` | `v.1.0 public - comp. X` | Sistema / actual |
| TABLET | `es.josxtm.telecontrol.community.tablet` | `v.1.0 tablet - comp. X` | Horizontal preferida, redimensionable |

La variante MOBILE conserva la misma identidad de paquete y los recursos WebView originales. TABLET tiene almacenamiento privado independiente y utiliza un tema industrial exclusivo cargado dinámicamente. El cambio de identificador de TABLET implica que sus datos no se migran automáticamente desde otra aplicación.

Las dos variantes empaquetan el componente MangoSSH existente por compatibilidad, sin modificar sus fuentes ni su interfaz. Comparten implementación de lógica, pero no datos de almacenamiento de cada paquete. No se configura transferencia automática entre variantes.

## Diseño TABLET

- Negro grafito, superficies blancas y acentos rojos, tipografía de sistema condensada cuando esté disponible, con Arial/Roboto como alternativas.
- Barra superior para navegación horizontal, fichas con columnas y tarjetas de trabajo, diario y configuración.
- Panel PING / RED con disposición TABLET → MANGO amarillo al 50% → ROUTER DEL CT gris → CC T1, CC T2, FFAA1, RTU, SABT T1, SABT T2.
- Líneas de enlaces verdes para éxitos verificados y rojas para fallos. Los estados aún no verificados muestran **NO COMPROBADO**, nunca resultados inventados.
- Los paquetes animados son solo metáforas visuales; no ejecutan transferencias. Se desactivan cuando el usuario solicita reducir movimiento.

**Limitación funcional:** La vista nueva proporciona el esquema y un adaptador de presentación `window.TCTabletView.renderMeasurements(data)`, sin ejecutar sondas de red ni autenticación SSH. Conectar resultados reales requiere un adaptador de diagnóstico autorizado, independiente del código de MangoSSH, que deberá verificarse antes de mostrar estados verdes o RTT. Una conexión TCP al puerto SSH no acredita una sesión autenticada.

## Seguridad

El frontend aprobado de MOBILE conserva su hash de revisión. Los dos archivos adicionales de presentación se validan mediante hash exacto, y la inspección APK verifica el empaquetado. No introducir direcciones de redes reales, inventarios, IP de instalaciones, credenciales ni datos privados en los archivos públicos.

## Compilar

```bash
python3 tools/privacy_guard.py .
python3 -m unittest discover -s tests -p 'test_*.py'
gradle :app:testMobileDebugUnitTest :app:testTabletDebugUnitTest :app:lintMobileDebug :app:lintTabletDebug :app:assembleMobileDebug :app:assembleTabletDebug
python3 tools/inspect_apk.py app/build/outputs/apk/mobile/debug/app-mobile-debug.apk "$ANDROID_HOME/build-tools/35.0.0" --flavor mobile
python3 tools/inspect_apk.py app/build/outputs/apk/tablet/debug/app-tablet-debug.apk "$ANDROID_HOME/build-tools/35.0.0" --flavor tablet
```

La distribución debug de CI sigue usando firma efímera: exportar copia antes de reemplazar instalaciones. No fusionar hasta validar en dispositivo físico, revisar paridad de MOBILE y aprobar visualmente TABLET.
