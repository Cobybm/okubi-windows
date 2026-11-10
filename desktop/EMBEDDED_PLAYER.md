# OKUBI Desktop 0.2 — Reproductor Libretro integrado (Fase 37C)

Esta fase permite **abrir un juego en una ventana de OKUBI Desktop**, con imagen, sonido, teclado y batería SRAM/RTC. Ya no es obligatorio lanzar un emulador externo, pero **cada usuario debe proporcionar su propia DLL de núcleo Libretro para Windows x64**. El ZIP incluye únicamente el puente nativo de OKUBI y el entorno Java.

## Diferencia entre EXE y DLL

En **Emuladores** verás dos opciones independientes por consola:
- **Elegir núcleo .dll:** un núcleo Libretro Windows de **64 bits** compatible con ese sistema, por ejemplo una DLL Libretro de SameBoy (GB/GBC) o mGBA (GBA). OKUBI emula directamente en su ventana.
- **Elegir .exe:** programa externo, como antes. Si no has configurado ninguna DLL, el botón JUGAR sigue abriendo el EXE externo. Esto protege tu configuración existente.

Los ejecutables normales de SameBoy y mGBA **no son necesariamente núcleos Libretro**. Una DLL de Windows descargada desde una fuente desconocida podría ejecutar código malicioso, así que utiliza solamente núcleos de confianza. No incluimos ni descargamos ninguno automáticamente.

Puedes encontrar núcleos Libretro Windows en los recursos del proyecto Libretro:
- https://www.libretro.com/
- https://buildbot.libretro.com/nightly/windows/x86_64/latest/

**Comprueba siempre que el núcleo corresponda a Windows x86_64 y al sistema elegido** y que su licencia y distribución sean adecuadas.

## Teclado dentro de OKUBI

| Tecla | Acción |
|---|---|
| Flechas | Mover personaje |
| Z | Botón B |
| X | Botón A |
| A | Botón Y (SNES) |
| S | Botón X (SNES) |
| Q / W | L / R |
| Enter | Start |
| Espacio | Select |
| P | Pausar / reanudar |
| F11 | Maximizar / restaurar |

El juego se pausa al perder el foco de la ventana para evitar pulsaciones involuntarias. Vuelve a OKUBI y pulsa **P** para continuar.

El selector de filtro del reproductor permite píxeles nítidos o suavizado bilineal; no es un shader xBRZ real. El juego se escala manteniendo la relación de aspecto original.

## Guardados: no perder partidas

- El núcleo integrado guarda archivos en `%APPDATA%/OKUBI/saves/<consola>/<SHA-256 de la ROM>.srm` y, si corresponde, `.rtc`.
- Guarda automáticamente aproximadamente cada 30 segundos y al cerrar el juego, en escritura atómica. Si el contenido cambia, conserva el archivo anterior con extensión `.previous`.
- Los guardados de emuladores externos **no se tocan**. No se cambia nada de tus partidas Android.
- Si un SRAM/RTC existente tiene tamaño incompatible con el núcleo, OKUBI se detiene en vez de sobrescribirlo.
- Los snapshots `.state` todavía **no se importan ni sincronizan automáticamente**, aunque el puente nativo ya implementa operaciones de serialización para futuras funciones.
- Haz una copia externa de la carpeta `saves` antes de instalar otra beta. La **Fase 38 Cross Save** podrá implementar importaciones verificadas de Android, sin sobrescribir destinos existentes.

## Qué hay dentro del ZIP

La compilación de GitHub Actions crea un paquete `OKUBI Desktop.exe` + carpeta `app` con JAR y `okubi_libretro_jni.dll` + carpeta `runtime`. No saques el EXE de esa estructura.

El compilador Windows usa MSVC x64 para construir el puente JNI. En CI también se crea una **DLL falsa de prueba**, no distribuida con el ZIP, para probar el ciclo completo de Libretro (carga del juego, fotogramas 160x144, audio estéreo, botones, SRAM, RTC, snapshot y restauración tras cerrar).

**Limitaciones conocidas:**
- Windows 10/11 de 64 bits; todavía sin paquetes Windows ARM64/x86, Linux o macOS.
- No se distribuyen motores de emulación ni ROMs. No todos los núcleos Libretro de terceros ofrecen las mismas opciones, y algunos pueden exigir BIOS opcionales.
- Primera beta interna: sin compatibilidad garantizada con mandos físicos en el reproductor; teclado incluido. Los mandos de PC siguen funcionando si se utiliza un emulador externo que los admita.
- Núcleos Libretro se ejecutan en el mismo proceso que OKUBI; prueba solo DLL de confianza.
- La comprobación automática de CI usa un núcleo sintético; las pruebas con SameBoy/mGBA/SNES reales y tus juegos en tu PC siguen siendo necesarias.

No cambia `bo.nexoplay.app.unified` ni versión, firma o partidas del Android. **El PR permanece en borrador y no se fusiona.**
