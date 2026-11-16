# OKUBI Desktop 0.3 — Emuladores integrados de fábrica

**Ahora no hay que descargar, seleccionar ni configurar los emuladores para GB/GBC/GBA/SNES.** La carpeta descargable de Windows x64 contiene los núcleos de código abierto precompilados y comprobados. Basta con importar una ROM de tu propiedad y pulsar **JUGAR**; se abrirá en la ventana interna de OKUBI.

| Consola | Núcleo incorporado | Licencia |
|---|---|---|
| Game Boy / Game Boy Color | SameBoy Libretro | MIT |
| Game Boy Advance | mGBA Libretro | MPL 2.0 |
| Super Nintendo | Beetle Supafaust Libretro | GPL 2.0 o posterior |

Los binarios proceden del [compilador oficial de Libretro](https://buildbot.libretro.com/nightly/windows/x86_64/latest/). Durante la compilación, OKUBI descarga solo esos tres archivos .dll.zip, verifica la integridad ZIP, el tamaño, la arquitectura PE Windows x64 y la presencia de la API Libretro. Después genera un manifiesto `app/cores/CORE-SHA256.properties` con las huellas SHA-256 de las tres DLL.

Al iniciar un juego, OKUBI vuelve a verificar la huella del núcleo correspondiente. Si el binario se altera, **no lo carga**; muestra un aviso. Si no están los núcleos, se puede conservar la selección manual antigua como respaldo, pero el ZIP oficial no requiere pasos extras. No se descargan archivos durante el uso de la aplicación.

## Instalación

1. Extraer el ZIP entero (nunca separar el .exe de sus carpetas `app` y `runtime`).
2. Abrir `OKUBI Desktop.exe`.
3. Seleccionar **Importar ROM** o **Importar carpeta**, localizar las copias personales en el PC y pulsar **JUGAR**.
4. La primera vez, verificar audio, botones y guardados con una copia de seguridad externa de tus partidas.

La sección **Motores incluidos** indica «INCLUIDO Y LISTO» por consola. La selección avanzada de DLL externa queda disponible únicamente como alternativa.

## Código abierto y licencias

OKUBI no incluye juegos, BIOS protegidas ni ROMs. Se conservan los avisos de licencia completos junto a los archivos binarios en `app/cores/licenses/`. Proyectos y código fuente oficial:

- SameBoy, MIT: https://github.com/libretro/SameBoy
- mGBA, MPL 2.0: https://github.com/libretro/mgba
- Beetle Supafaust, GPLv2+: https://github.com/libretro/supafaust
- ABI Libretro, MIT: https://github.com/libretro/libretro-common

**Nota de cumplimiento**: Supafaust requiere poner a disposición su código fuente correspondiente y respetar GPLv2+ si distribuyes la aplicación fuera de tu uso privado. Los archivos de origen y receta de compilación públicos deben acompañar cualquier distribución de terceros; un enlace general al repositorio no sustituye necesariamente una copia del código fuente exacto usado en el binario. La compilación actual descarga binarios precompilados del buildbot oficial y guarda sus huellas y procedencia, no recompila los núcleos Windows desde el código fuente. Antes de publicar una versión pública necesitamos fijar una revisión exacta y preparar su paquete de código fuente correspondiente. Esto también afecta otras obligaciones de licencias de los núcleos. No afirmamos que disponer de un binario público permita ignorar las licencias.

## Conservación de partidas

- Android, PC y emuladores externos guardan en ubicaciones distintas. No se modifican datos Android al compilar Windows.
- Los núcleos internos de Windows guardan SRAM/RTC en `%APPDATA%/OKUBI/saves/<consola>/<SHA256>.srm/.rtc`.
- El guardado interno es atómico y conserva `.previous` cuando cambia la memoria.
- Si cambias de núcleo y la memoria es incompatible, el programa debe **detenerse sin sobrescribir**.
- La transferencia Android ↔ PC (Fase 38) sigue pendiente.

## Pruebas

CI Windows compila el puente JNI de OKUBI con MSVC, comprueba la app, ejecuta un núcleo sintético para vídeo/audio/teclado/guardado/RTC/snapshot, descarga los núcleos reales y verifica sus exports y ABI nativa. La prueba automatizada con núcleo sintético **no demuestra aún que todos los juegos comerciales funcionen correctamente**; validar con tus ROMs y tu PC es la siguiente prueba.

La compilación Windows x64 no está firmada como editor de Windows ni modifica la clave de firma Android. No fusionar PR sin validar funcionamiento real.
