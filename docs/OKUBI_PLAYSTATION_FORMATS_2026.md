# OKUBI 遊 · Matriz verificada de imágenes PlayStation (16 nov 2026)

**Objetivo**: aceptar archivos que el usuario ya posee, identificar su consola real y sugerir un motor apropiado, sin prometer compatibilidad ni ejecutar documentos desconocidos.

| Consola | Motor recomendado | Archivos de juego | Comentario de OKUBI |
|---|---|---|---|
| PS1 | DuckStation externo / Libretro PCSX ReARMed o Beetle PSX como base posible | BIN/CUE, IMG, CCD, MDS, ISO, CHD, ECM, PBP, M3U | CUE requiere las pistas BIN/WAV; SBI es auxiliar |
| PS2 | PCSX2 externo Windows / ARMSX2 en ARM Android; Play! alternativo | ISO, BIN/CUE, MDF, CHD, CSO, ZSO, GZ, ELF | BIOS propia puede ser necesaria; juegos 3D exigentes |
| PSP | PPSSPP Windows/Android | ISO, CSO, CHD, EBOOT.PBP | PSP PBP no es necesariamente PS1 PBP |
| PS3 | RPCS3 Windows | PS3_GAME/USRDIR/EBOOT.BIN, carpeta PS3_GAME, PKG instalable, algunas ISO preparadas | No asociar todos los archivos ISO a PS3; firmware/instalación oficial necesarios |
| PS Vita | Vita3K Windows/Android | VPK, ZIP, VCI, PKG, carpeta desencriptada | PKG puede requerir licencia legítima; ZIP es paquete instalable, no ROM directa |
| Contenedores | No son consolas | ZIP / 7Z / RAR | No abrir ni extraer automáticamente; riesgo de bombas ZIP, contenido arbitrario |

### Motores y licencias
- DuckStation: ideal como **ejecutable externo** con vínculo oficial: licencia **CC BY-NC-ND 4.0**, no se debe crear un fork modificado ni empaquetar una distribución adaptada bajo esa licencia.
- PCSX ReARMed y Beetle PSX: núcleos Libretro **GPLv2**, potencial base para integración PS1 respetando avisos, fuentes y compatibilidad.
- PCSX2 y ARMSX2: **GPLv3**, integración PS2 requiere CPU, GPU, BIOS legítima, licencias y pruebas por arquitectura.
- PPSSPP: **GPLv2 o posterior**, candidato para la arquitectura Windows + Android y formatos ISO/CSO/CHD.
- Play! core de Libretro: **MIT**, posible alternativa PS2, pero hay que validar compatibilidad por juego.
- Vita3K: **GPLv2**, emulación experimental.
- RPCS3: **GPLv2**, no debe ofrecer PS3 Android como jugable en todos los teléfonos.

### Reglas de importación implementadas en esta rama
1. En Windows el importador del panel SMART PC reconoce PS1/PS2 con más extensiones oficiales. ISO, CHD, BIN y CUE con posibilidad de varias consolas requieren elección manual; los escaneos de carpetas no asignan automáticamente ISO/CHD/PBP. PSP y Vita se identifican para orientación, pero no se promete arranque.
2. El analizador Windows abre **solo referencias a archivos reales**, no los modifica y advierte por pistas CUE o M3U faltantes; rechaza rutas externas en las listas.
3. En Android, Descubre > «ANALIZAR ARCHIVO PLAYSTATION» permite seleccionar un documento mediante el selector de Android y ver consola(s) posibles, formato, motor sugerido y requisitos. La app no instala emuladores, no descomprime contenedores ni lee juegos completos por su cuenta.
4. El identificador del APK, firmas y partidas existentes no se alteran. VIDAS es otro proyecto y no se toca.

### Verificación pública de formatos
- DuckStation: https://github.com/stenzek/duckstation
- DuckStation license: https://github.com/stenzek/duckstation/blob/master/LICENSE
- PCSX2 selector: https://github.com/PCSX2/pcsx2/blob/master/pcsx2-qt/MainWindow.cpp
- ARMSX2 Android: https://github.com/ARMSX2/ARMSX2
- Play! core: https://docs.libretro.com/library/play/
- PPSSPP: https://www.ppsspp.org/docs/getting-started/dumping-games/
- RPCS3: https://rpcs3.net/quickstart
- Vita3K: https://vita3k.org/quickstart
- PCSX ReARMed: https://docs.libretro.com/library/pcsx_rearmed/
- Beetle PSX: https://docs.libretro.com/library/beetle_psx/

**Estado**: analizador/ruteador documental integrado en rama de revisión, no un lanzamiento de núcleos PS1/PS2/PSP. Las pruebas de emulación real y APK físico son requisitos antes de publicar funcionalidad como lista.
