# OKUBI 遊 — Hotfix Fase 75B · PORTS PC visual

**Origen:** captura de usuario de Windows 10/11 (10 de noviembre de 2026). La aplicación abre y muestra 32 ports, pero hay defectos gráficos de Windows Look & Feel.

## Problemas observados en la captura

1. Pestañas blancas de `JTabbedPane` dibujadas sobre el comienzo de la lista y cortes en las tarjetas.
2. `JComboBox` de género y requisitos con fondos casi blancos y etiquetas ilegibles.
3. Botones DESCARGAS / VINCULAR .EXE con bajo contraste o texto blanco sobre fondos claros.
4. Tarjetas de dos columnas con altura fija demasiado corta para algunas descripciones.

## Correcciones

- Reemplazado `JTabbedPane` nativo por navegación CINEMA de dos botones y `CardLayout`, aislando físicamente las dos vistas (revisados / GitHub) y eliminando el solapamiento.
- Añadido `DesktopPortsTheme`, aplicando `BasicButtonUI` y `BasicComboBoxUI` **solo al diálogo de ports**, sin modificar el tema del resto de OKUBI.
- Fondo de selección actual del combo pintado explícitamente. El Look & Feel del host anteriormente podía dibujar blanco incluso cuando el componente tenía fondo oscuro.
- Texto/acciones con alto contraste, fondo oscuro y estados seleccionados diferenciados.
- Filas algo más altas y área con desplazamiento vertical para evitar descripciones recortadas.
- Espaciado superior reducido y altura inicial del diálogo aumentada para ganar área visible.
- Prueba `DesktopPortsThemeTest` pinta botones y combos en memoria y comprueba contraste tanto en Windows como Linux, sin necesidad de iniciar una ventana en el CI.

## Lo que no cambia

La emulación, los 32 ports revisados, el explorador de GitHub, el escáner de ejecutables, la biblioteca, Android, claves, guardados y la identidad del paquete permanecen idénticos. No se fusiona `main` ni se modifica el repo antiguo.

## Prueba manual de Windows

1. Extrae la nueva versión en una carpeta independiente, verifica el ZIP y arranca con `RUN-QA57-ISOLATED.cmd`.
2. Ve a **PORTS PC / GITHUB**. Debes ver las dos pestañas CINEMA oscuras, sin tarjetas detrás de ellas.
3. Cambia entre **PORTS SELECCIONADOS** y **EXPLORAR TODO GITHUB**; los títulos de las tarjetas deben permanecer completos.
4. Abre ambos desplegables (género y requisitos), comprueba textos legibles y selecciona categorías.
5. Baja en la lista y prueba los botones DESCARGAS y VINCULAR .EXE; deben ser visibles.
6. Reduce la ventana de OKUBI al tamaño mínimo, desplázate y confirma que los controles no se superponen.
7. No desinstales versiones anteriores ni muevas partidas: es un hotfix exclusivamente visual.

**QA de GitHub + QA física pendientes hasta que termine CI y el usuario pruebe esta nueva captura.**
