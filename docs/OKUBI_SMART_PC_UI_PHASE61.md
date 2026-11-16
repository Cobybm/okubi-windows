# OKUBI 遊 — Fase 61 SMART PC · corrección visual Windows

**Referencia:** captura real con Intel Core i5-11400, 12 hilos, 15,8 GiB RAM
y gráficos Intel UHD 730. La detección de CPU/GPU/RAM funciona, pero la
pantalla tenía botones blancos casi ilegibles, un selector blanco, borde
demasiado brillante y una biblioteca vacía sin instrucciones visibles.

## Cambios de interfaz

- Botones con fondo púrpura oscuro, texto de alto contraste y enfoque
  accesible, tanto activos como desactivados; no dependen del tema Windows.
- Menú desplegable oscuro para Automático / Protección / Equilibrado /
  Calidad, conservando la selección persistida por juego.
- **PS1, PS2 y PS3 tienen su evaluación visible desde el inicio**, incluso
  antes de importar títulos. El estado se actualiza al detectar el PC.
- En biblioteca vacía, OKUBI invita a importar juegos propios y deja
  claro que no se inventan ni descargan juegos.
- Las seis acciones ahora se organizan en **dos filas de tres**, para
  evitar botones apretados y barra inferior oculta.

## Estimación para la PC de la captura

| Consola | Resultado inicial orientativo |
|---|---|
| PS1 | PROBABLE |
| PS2 | VARIABLE, probar escala nativa 1x |
| PS3 | NO RECOMENDADO con la GPU UHD 730 integrada |

La compatibilidad por juego sigue sin comprobarse por ID o versión.
Depende de BIOS/firmware legítimo, driver, emulador, juego y FPS reales.
**SMART PC no acelera ni sobrecalienta la PC automáticamente**: no realiza
overclock, no cambia el registro y aún no escribe resoluciones internas
de emuladores externos.

## Prueba segura en Windows

1. Descarga el ZIP 1.0.9 desde la ejecución SUCCESS en Actions y extráelo
   **en una carpeta nueva**.
2. Ejecuta `app\VERIFY-WINDOWS-PACKAGE.cmd`, luego
   `app\RUN-QA57-ISOLATED.cmd` si deseas un perfil sin tus partidas reales.
3. Abre SMART PC con Ctrl+P. Deben verse tres clasificaciones aunque la
   biblioteca PS1/PS2/PS3 todavía esté vacía.
4. Importa un juego de prueba propio, revisa clasificación y perfil.
5. Reduce la ventana a ~900x630 px y luego a ~760 px de ancho: los seis
   botones deben seguir siendo legibles, con barra de estado visible.
6. Verifica que la biblioteca y partidas GB/GBC/GBA/SNES permanecen intactas.

Los tests Java verifican contraste del botón habilitado/deshabilitado,
selector oscuro, la clasificación del hardware Intel de la captura y
geometría de 2x3 a 760 píxeles. El resultado no reemplaza la prueba
visual real con los drivers de tu Windows.

**Solo Windows: Android, VIDAS y main intactos. PR DRAFT, sin merge.**
