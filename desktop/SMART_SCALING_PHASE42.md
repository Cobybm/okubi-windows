# OKUBI 遊 — Fase 42 SMART SCALING (Windows Desktop 0.9.3)

Después de CINEMA IMMERSIVE 0.9.2, ahora puedes elegir cómo encajar los fotogramas de GB, GBC, GBA y SNES en monitores 16:9, 16:10, 4:3 y ventanas redimensionadas. Los ajustes se aplican **en vivo**; no reinician la ROM ni modifican guardados.

## Acceso

Dentro del juego, pulsa **Esc → CINEMA CONTROL CENTER → SMART SCALING** y selecciona la escala. También puedes cambiar al siguiente modo pulsando **F2** fuera del menú. F2 se reserva para OKUBI y no se puede asignar a un botón del juego.

| Modo | Resultado | Precaución |
| --- | --- | --- |
| **Automático (predeterminado)** | Mantiene el comportamiento de 0.9.2. Si tenías el filtro Pixel Perfect, conserva el escalado entero; si no, usa Ajustar | Compatibilidad con configuración anterior |
| **Ajustar / FIT** | Muestra el juego completo, centrado, con proporciones originales | Puede dejar bandas negras |
| **Pixel Perfect / INTEGER** | Escala los píxeles a múltiplos enteros al ampliar (2x, 3x, 4x, 5x, 6x...). Si la ventana es menor que la resolución original, reduce proporcionalmente para evitar cortes | Puede dejar bandas negras más grandes |
| **Llenar / FILL** | Ocupa toda el área útil sin deformar, haciendo un recorte central de los bordes sobrantes | **Puede ocultar elementos en los bordes del juego** |
| **Estirar / STRETCH** | Llena completamente el área del juego | **Deforma las proporciones originales** si el monitor y el juego tienen relaciones de aspecto diferentes |

Por defecto se utiliza **AUTO**, precisamente para no alterar las preferencias guardadas de versiones anteriores (incluido el filtro Pixel Perfect antiguo). Una selección explícita en el nuevo menú prevalece sobre el modo de escalado heredado sin borrar tus filtros CRT, LCD, Suave HD o Nítido. El filtro sigue controlando la interpolación/efecto; SMART SCALING controla la geometría.

## Ejemplo real: Pokémon Esmeralda (GBA, 240×160)

En una pantalla de 1920×1080 con el HUD de 0.9.2 oculto, los resultados matemáticos serán aproximadamente los siguientes (el tamaño final depende del área real de la ventana y de Windows):

- **Ajustar:** fotograma **1620×1080**, con 150 px de banda negra a cada lado.
- **Pixel Perfect:** fotograma **1440×960** (6x exacto), con 240 px de banda negra lateral y 60 px arriba y abajo.
- **Llenar:** fotograma **1920×1280** centrado; se recortan aproximadamente 100 px de imagen escalada arriba y abajo, sin alterar las proporciones.
- **Estirar:** fotograma **1920×1080**, ocupando toda la vista pero cambiando 3:2 original a 16:9.

Estos resultados son de la **zona de vídeo** del reproductor; no garantizan que la ventana sea realmente 1920×1080 cuando tiene barras y bordes de Windows. F11 sigue maximizando/restaurando la ventana y CINEMA IMMERSIVE puede ocultar las barras para dar más espacio. No se cambia la resolución del sistema operativo.

## Partidas y compatibilidad

Los cambios se recuerdan **por consola** en `%APPDATA%/OKUBI/cinema-scaling-v1.properties`. Es un archivo separado de las rutas de ROMs, biblioteca, perfiles de mando, HUD, estadísticas y partidas. Se escribe atómicamente y los valores dañados se ignoran usando AUTO.

No se cambia:

- El motor Libretro (SameBoy, mGBA, Supafaust), la velocidad, el audio ni los controles del juego.
- Los fotogramas originales de GB/GBC/GBA/SNES ni la captura F12 (PNG original sin reescalar).
- SRAM/RTC, guardados F5/F9, puntos de recuperación ni partidas.
- Cross Save Android ↔ PC y Cloud Save por carpeta.
- OKUBI Android, VIDAS o cualquier proyecto ajeno.

El área de vídeo siempre se recorta al propio panel de Swing, de manera que el modo **Llenar** no invade botones ni marcos. Los efectos CRT y LCD respetan el clip.

## Verificaciones

Las pruebas automáticas Windows realizan cálculos determinísticos de 1080p, laptop 1366×768, 1024×768, 240×160 GBA, 160×144 GB, 256×224 SNES, ventanas diminutas, fotogramas inválidos y legado Pixel Perfect. Prueban píxeles renderizados de Ajustar/Llenar/Estirar, ciclos F2, advertencias de recorte/deformación, preferencias por consola, reinicio y aislamiento SRAM. Se ejecutan junto con todas las pruebas anteriores de núcleos, guardados, controles, estadísticas, Cross Save y Cloud Save.

**La compilación automática no sustituye la prueba de visualización física en el PC**. Si el usuario no quiere perder nada del campo visible, elegir **Ajustar**. Si desea píxel 100% entero, elegir **Pixel Perfect**. FILL/STRETCH son deliberadamente opcionales.

PR en borrador apilado sobre la Fase 41; no fusionar.
