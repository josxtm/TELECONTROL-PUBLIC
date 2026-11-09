# Material-inspired motion — TELECONTROL TABLET

## Alcance
Rama `feat/tablet-material3-motion`, basada en el estado validado de `feat/tablet-mobile-split-industrial-ui` (CI 42565595059, commit b899d868d5773b0e05704a5a72676b9b49ef9af6). Mobile carga el frontend original y no incluye animaciones activas. No se modifica ninguna fuente MangoSSH.

Las animaciones pertenecen a la capa WebView de presentación; no dependen de Compose. El controlador `window.TabletMotionController` se carga exclusivamente después de `tablet-network.js` en el flavor Tablet. Solo observa cambios de visibilidad de las pantallas existentes; no toca bases de datos ni funciones de negocio.

## Efectos elegidos

| Nombre | Navegación | Duración |
| --- | --- | --- |
| 06 Fade Through | Inicio, trabajos y diario | 310 ms (salida 110 ms, entrada posterior) |
| 09 Shared Axis X | Ficha CT ↔ Ping / Red | 280 ms, desplazamiento máximo 130 px |
| 11 Shared Axis Z | Configurar y pantallas auxiliares | 255 ms, escala de 0,94 a 1 |
| 12 Container Transform | Buscar CT → ficha; tarjeta trabajo → detalle | 330 ms, geometría de origen/destino tomada de la pantalla |

Container Transform utiliza un contenedor superpuesto dibujado temporalmente según `getBoundingClientRect` del botón/tarjeta y de la pantalla de destino; no es únicamente escalado de pantalla. Si falta el elemento de origen, el sistema aplica desplazamiento y fundido discreto. Los controles originales continúan ejecutando la navegación, incluida la validación de cambios sin guardar.

## Accesibilidad
`prefers-reduced-motion: reduce` desactiva las transiciones. Se cancelan al ocultar la aplicación, al abandonar la página y al rotar. Se usa `transform` y `opacity` para el contenido; solo la capa temporal de Container Transform interpola tamaño. Los efectos no guardan datos ni realizan sondas de red.

## Versiones y validaciones
Mobile conserva `v.1.0 public - comp. X`, `versionCode=10000+X`. Tablet pasa a `v.1.1 tablet - comp. X`, `versionCode=30000+X`. Paquetes independientes. La actualización de una APK instalada exige una firma coincidente; las compilaciones debug de CI usan firma efímera.

La CI ejecuta privacidad, pruebas Python, unitarias y lint de ambas variantes, verificación del contenido APK y pruebas Android en emulador. Se añaden aserciones instrumentadas de que el controlador existe únicamente en Tablet, expone los cuatro efectos y usa las rutas correctas.

## Limitaciones
La vista de diagnóstico visual todavía necesita un adaptador autorizado de pings reales y preparación SSH. Los marcadores y animaciones de red no realizan mediciones. La estética y la continuidad real de los efectos requieren revisión humana en la tablet física de 13,4 pulgadas. No se garantiza 60 FPS en todos los dispositivos.
