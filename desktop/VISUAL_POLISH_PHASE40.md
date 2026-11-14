# OKUBI Desktop 0.9.1 — Phase 40 · Screenshot-based visual polish

This update is driven by real screenshots from Windows running Pokémon Esmeralda in OKUBI Desktop 0.9. All changes are **visual, navigational and accessibility improvements**; emulation cores, ROMs, SRAM/RTC, save states, Cross Save, Cloud Save, statistics and controller mappings stay unchanged.

## Correcciones comprobables

### 1. Iconos que parecían cuadrados

Los símbolos de los botones, por ejemplo controles, favoritos, nube, pantalla completa y menú del jugador, aparecían como pequeños rectángulos en un Windows sin fuentes compatibles con los emojis usados originalmente.

Ahora **CinemaButton pinta iconos vectoriales Java2D**, sin descargar fuentes ni depender de emojis. Están incluidos los botones de biblioteca, barra lateral, favoritos, nube, Cross Save, pantalla completa y menú dentro del juego. Los botones conservan su comportamiento, textos accesibles y navegación por teclado.

Los caracteres japoneses de la marca **OKUBI 遊** siguen usando la detección anterior de fuentes CJK instaladas; nunca se incluyen ni distribuyen archivos de fuentes externas.

### 2. Menú lateral recortado

La captura muestra que "Cross Save local", "Cloud Save Drive" y "Partidas y respaldos" quedaban abajo de la ventana y se cortaban. La barra lateral ahora tiene su **propio desplazamiento vertical**, incluso en una ventana reducida de portátil. Se conserva Modo Consola, que oculta el lateral como antes.

### 3. Media biblioteca vacía

Cuando solo hay un juego o el último renglón tiene un título, en vez de presentar un cuadro vacío se muestra una **tarjeta complementaria** con el nombre del título, consola, tiempo registrado y acceso rápido a **Cross Save** y **Cloud Save**. Esa tarjeta es informativa: no se agrega como juego ni altera el índice de selección del mando.

Si la biblioteca está vacía, la otra columna muestra una guía breve de control y un acceso para añadir juegos. La búsqueda tiene un texto de ayuda legible cuando no escribes nada.

### 4. Barra de juego más legible

La ventana interna mantiene la pantalla GBA con su proporción correcta (240×160 de base, sin estirar ni recortar). Se aumentó ligeramente el tamaño del título y la ayuda inferior. El título negro de copyrights de Pokémon Esmeralda que aparece en la captura corresponde al **inicio del juego**, no a un fallo de OKUBI: no se altera la emulación.

## Cómo probar

1. Cierra el juego y deja que termine de guardar la partida.
2. Cierra OKUBI Desktop 0.9 y **extrae el ZIP 0.9.1 completo en otra carpeta**. No borres \`%APPDATA%\OKUBI\`.
3. Ejecuta \`OKUBI Desktop.exe\`.
4. Comprueba la barra lateral hasta el fondo y la tarjeta del único Pokémon en la biblioteca.
5. Abre Pokémon Esmeralda y comprueba que los iconos de la cabecera (menú / pantalla completa) estén dibujados con trazos, no cuadrados.
6. Prueba favoritos, Modo Consola, Cross Save y Cloud Save sin importar ni restaurar archivos de partida en la prueba inicial.

GitHub Actions Windows ejecuta la suite anterior y un nuevo test que comprueba que los botones reemplazaron los emojis por iconos dibujados, mantienen nombres accesibles y siguen disparando sus callbacks.

**Limitaciones:** la prueba automatizada no sustituye la verificación visual de Windows en tu monitor real. Los cambios no corrigen incompatibilidades de núcleos ni estados rápidos entre versiones. La rama permanece en PR draft sin fusionar.
