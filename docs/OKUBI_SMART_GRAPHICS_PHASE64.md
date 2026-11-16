# OKUBI 遊 — Fase 64: SMART GRAPHICS 2.0 (Windows 1.0.12 beta)

**Solo Windows.** No se modifica Android ni VIDAS, ni se fusiona
main. Sin cambios en BIOS, firmware, ROM/ISO, tarjetas PS2 o SAVE VAULT.

## ¿Qué mejora frente a la Fase 63?

SMART CONFIG (Fase 63) podía aplicar escala interna 1× o 2× al PCSX2.
SMART GRAPHICS 2.0 extiende el perfil con tres valores controlados
para un juego PS2 y una determinada PC:

| Perfil | Escala interna | FXAA | Filtrado anisotrópico |
|---|---:|---|---:|
| Protección/ECO, GPU integrada, hardware desconocido, juego lento | 1× | Desactivado | Normal |
| Equilibrado en GPU dedicada compatible | 1× | Desactivado | 2× |
| Calidad con GPU dedicada suficientemente potente | 2× | Activado | 4× |
| Calidad en GPU integrada | 1× | Desactivado | Normal |

Estas son **recomendaciones conservadoras**, no predicciones de FPS.
La mejora gráfica puede ser incompatible con un título específico.
Nunca se modifica speedhack/EE/CPU, BIOS, núcleo ni controlador.

## Cómo usarlo

1. Desde SMART PC (Ctrl+P), selecciona el juego de PlayStation 2.
2. Elige Automático, Protección, Equilibrado o Calidad. SMART PC mostrará
   exactamente la escala, FXAA y filtrado previstos.
3. Si SMART CONFIG PS2 está desactivado, el perfil es **solo información**.
   Pulsa el botón de activación y acepta los riesgos descritos.
4. Al pulsar JUGAR, OKUBI pide al ejecutable PCSX2 elegido su lista de
   opciones usando `-help` y comprueba `-gamecfg` antes de generar el
   archivo. Si no aparece la opción o no responde, **no inicia el juego
   con configuración desconocida**. Puedes desactivar SMART CONFIG
   para volver al arranque normal.
5. El archivo se genera solo dentro de
   `%APPDATA%\OKUBI\smart-config-v1\profiles`, con nombre nuevo
   `pcsx2-HASH-graphics-v2-s1-a0-f0.ini` o equivalente.
   **Los perfiles de la Fase 63 se conservan y NO se sobrescriben**.
6. Si PCSX2 muestra un problema o el juego va lento, desactiva SMART
   CONFIG para la siguiente ejecución. El juego se abre sin `-gamecfg`,
   volviendo a su comportamiento ordinario.

## Alcances y advertencias

- Los tres parámetros son claves de configuración de PCSX2 para
  `[EmuCore/GS]`: `upscale_multiplier`, `fxaa` y
  `MaxAnisotropy`. El perfil se aplica mediante `-gamecfg`,
  que PCSX2 documenta como sustitución temporal de los ajustes del
  juego. Esto **puede ocultar otros ajustes personalizados del título**
  durante esa ejecución. Si necesitas parches/correcciones por juego,
  mantén SMART CONFIG desactivado hasta verificar su efecto.
- La comprobación de `-help` inicia momentáneamente el ejecutable
  de PCSX2 **solo tras tu consentimiento**. Se limita a ocho segundos
  y no arranca un juego. No se puede garantizar qué operaciones
  internas hace una versión desconocida del ejecutable, por lo que
  debes usar la descarga oficial.
- No se modifican archivos internos de PCSX2 ni se monitorizan
  FPS, temperatura real, ventiladores o voltajes. El programa no
  garantiza protección contra fallos eléctricos o térmicos.
- PS1 y PS3 quedan fuera de la escritura de configuraciones;
  DuckStation y RPCS3 seguirán con sus opciones normales.
- Los archivos de consentimiento solo contienen hash de ruta/PC y
  activado/desactivado; no el contenido de tus juegos ni guardados.

## Pruebas

La CI ejecuta la suite Windows completa y un test con ficheros
sintéticos que cubre hardware Intel UHD/RTX, detección desconocida,
la correspondencia exacta de parámetros INI, el rechazo de PCSX2
que no anuncie `-gamecfg`, versiones v1 y v2 compatibles con el
lanzador, rechazo de perfiles alterados, reversión por juego,
cambio de GPU, y bytes originales de ROM, tarjetas PS2, INI global
y biblioteca retro intactos.

Aun con CI exitosa, **falta validación real en PCSX2**. La versión
continúa en PR borrador y no se fusionará sin autorización.

Fuentes oficiales:
- https://pcsx2.net/docs/advanced/cli/
- https://pcsx2.net/docs/configuration/general/
- https://github.com/PCSX2/pcsx2/blob/master/pcsx2/Config.h
