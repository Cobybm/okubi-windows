# OKUBI 遊 — Fase 65: SMART SESSION · registros de rendimiento Windows

**Windows 1.0.13 beta.** Solo Windows, PR DRAFT, sin fusionar.
Android y VIDAS NO se modifican. Las partidas y configuraciones previas
de OKUBI y PCSX2 permanecen intactas.

## Qué registra OKUBI automáticamente

Cada vez que abres un juego PS1, PS2 o PS3 desde SMART PC y termina
el proceso externo de DuckStation, PCSX2 o RPCS3, OKUBI anota:

- Número total de sesiones de ese juego **en esa computadora**.
- Tiempo acumulado transcurrido entre pulsar JUGAR y terminar el proceso.
- Código de salida del proceso (solo diagnóstico; no representa
  necesariamente un error ni confirma que el juego haya funcionado).

El contador se guarda en `%APPDATA%\OKUBI\smart-session-history-v1.properties`
junto a identificadores SHA-256 para la combinación PC + juego + consola.
No se abren ROMs/ISOs para calcular hashes ni se accede a tarjetas de
memoria o partidas guardadas.

**Límites de la telemetría:** la duración es de la sesión/proceso completo,
no necesariamente tiempo de juego activo; puede incluir arranque,
pantallas de menú y pausas. Un proceso que termina con código cero
no certifica compatibilidad. El historial de sesiones se mantiene
separado del resultado explícito SMART PLAY ("funciona bien",
"lento", "no inició") y no modifica por sí solo los perfiles.

## FPS reales vs. FPS informados

Al pulsar **¿CÓMO FUNCIONÓ ESTE JUEGO?**, puedes registrar un
resultado cualitativo. Después, si quieres, introducir un número
de FPS que **tú hayas observado** mediante el contador del emulador
(entre 1 y 240). Es opcional: puedes dejar el campo vacío.
SMART PC lo mostrará como **FPS introducidos por ti**, nunca como
FPS medidos automáticamente por OKUBI.

No se supone que 60 FPS sea la meta para todos los juegos: existen
juegos/regiones con distintos objetivos de refresco y comportamiento.
No se ajusta la calidad automáticamente basándose en FPS autodeclarados
sin suficiente contexto. Un resultado marcado "lento" sí mantiene
la recomendación ECO de Fase 62.

## Protección de datos y rollback

- Archivo de sesiones propio, independiente de los datos GAME HUB,
  biblioteca retro, SMART CONFIG y perfil de cada emulador.
- Escritura atómica con archivo temporal; rechazo de vínculos simbólicos
  directos, tamaños excesivos y datos corruptos.
- Si el hardware detectado cambia, el historial del equipo anterior
  no se muestra como si fuese el del equipo nuevo.
- El modo QA57 aislado funciona con `%LOCALAPPDATA%\OKUBI-QA57`
  y guarda el historial separadamente del perfil normal.
- No se modifica firmware, GPU, registro de Windows, ROMs, INIs
  de PCSX2, tarjetas de memoria, ni guardados GB/GBC/GBA/SNES.
- No se hacen descargas de ROMs ni BIOS.

## Comprobación en la PC

1. Descarga el artefacto Windows 1.0.13 de GitHub Actions y extráelo
   en una carpeta **nueva**, sin reemplazar la versión 1.0.12.
2. Ejecuta `app\VERIFY-WINDOWS-PACKAGE.cmd` y abre
   `app\RUN-QA57-ISOLATED.cmd` si quieres pruebas con perfil separado.
3. Abre SMART PC (Ctrl+P), selecciona un juego propio PS1 o PS2 e inicia
   su emulador oficial.
4. Tras cerrar el emulador, verás el número de sesiones, tiempo y código
   de salida. **No se inventa un rendimiento numérico.**
5. Pulsa ¿CÓMO FUNCIONÓ ESTE JUEGO? y, opcionalmente, introduce los
   FPS que observaste. Comprueba que aparezcan etiquetados como manuales.
6. Cierra y vuelve a abrir OKUBI. El historial de prueba debe
   conservarse y tus partidas anteriores permanecer intactas.

Las pruebas CI utilizan archivos sintéticos y verifican el tiempo,
las cuentas, código de salida, FPS introducidos/retirados por el usuario,
aislamiento por GPU, corrupción, duración limitada e integridad
byte a byte de ROM, SRAM y PCSX2.ini. **No sustituyen una prueba
física con un juego real y un emulador instalado.**


## Bloqueo externo de GitHub Actions y alternativa local

**Estado verificado el 9 de noviembre de 2026:** los jobs Windows
y Ubuntu fallaron antes de que GitHub asignara un ejecutor:
`runner_id = 0`, `steps = []`, sin pruebas ni logs. Hubo incidencias
públicas de GitHub Actions ese día, pero no se pudo demostrar una
causa específica para la cuenta privada. **No hay ZIP 1.0.13
aprobado** y la versión Windows 1.0.12 permanece como la última
CI verificada.

Para comprobar si el bloqueo es del límite mensual o del presupuesto
de Actions, el propietario puede revisar en GitHub sus ajustes de
**Billing and licensing → Usage and budgets**. Un repositorio privado
consume minutos de computación incluidos del plan; si se agotan o el
presupuesto bloquea uso adicional, GitHub puede impedir el arranque
de los jobs. No habilitar pagos ni aumentar presupuesto sin una
decisión explícita del propietario.

Si necesitas generar una versión de prueba sin GitHub Actions, el
repositorio incluye `desktop/BUILD_LOCAL_WINDOWS_PHASE65.ps1`.
Usa los mismos comandos que el CI Windows para compilar puente
JNI, clases Java, **todas las regresiones**, comprobar los tres
núcleos x64 por SHA-256 y ABI, empaquetar con jpackage, verificar
el EXE y calcular SHA-256 del ZIP. **No salta ninguna prueba.**

Prerrequisitos en una PC Windows x64: JDK 21 con javac/java/jar/jpackage,
herramientas de compilación Microsoft Visual C++ x64 (cl.exe),
PowerShell y acceso para descargar los núcleos oficiales verificados.
Clona el repositorio privado en una carpeta de nombre válido para
Windows, por ejemplo `C:\okubi-source` (el nombre original del repo
termina en punto y no debe usarse como nombre local de carpeta).
Desde una terminal x64 Native Tools con Java 21:

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\desktop\BUILD_LOCAL_WINDOWS_PHASE65.ps1 -TestsOnly`

Para empaquetar también el ZIP, retira `-TestsOnly`. El resultado,
si TODAS las comprobaciones pasan, queda en `build\desktop`
con un archivo SHA256SUMS. Es una compilación local de prueba sin
firma comercial, no una distribución estable; no la anuncies como
validada por GitHub. No ejecutes el script desde un repositorio
sin verificar su origen.

**La versión actual del repositorio no fue probada físicamente en la
PC del usuario**, ni pudo ejecutarse dentro de GitHub Actions debido
al fallo previo a la asignación del runner.
