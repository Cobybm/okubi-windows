# OKUBI 遊 — Fase 60 · SMART PERFORMANCE & MULTICONSOLE (Windows beta)

**Objetivo:** indicar qué juegos PS1, PS2 y PS3 del usuario podrían funcionar
en su PC, con diagnóstico del equipo y perfiles de ejecución conservadores
por juego. **Android y VIDAS no se modifican.**

## Funciones implementadas

Abre **SMART PC · PS1/PS2/PS3** desde el lateral o con Ctrl+P.

OKUBI detecta CPU, procesadores lógicos, RAM y GPU mediante Java y
PowerShell estándar. Es un análisis **solo lectura**, sin instalar drivers,
modificar el registro, overclockear, borrar partidas o exigir administrador.

Importa referencias a juegos de carpetas o archivos individuales. La lista
muestra una clasificación para cada juego de acuerdo con su consola y el
hardware detectado:

- **Probable con ajustes conservadores**: el hardware parece apropiado
  para ese tipo de consola, no garantiza compatibilidad del título.
- **Rendimiento variable**: podría funcionar a resolución nativa;
  requiere probar el juego.
- **No recomendado**: ordenador limitado para esa consola; OKUBI advierte
  antes de iniciar y exige confirmación.
- **Hardware desconocido**: RAM o GPU no detectada, sin asumir potencia.

Los perfiles **Automático, Protección, Equilibrado y Calidad** se guardan
para cada juego de manera independiente en
`%APPDATA%\OKUBI\multiconsole-v1.properties`.

El modo **Automático** selecciona preferentemente **ECO** cuando se
detecta GPU integrada, CPU limitada o consola exigente. Los emuladores
PS1/PS2 se abren con los argumentos oficiales de ventana segura
`-batch -nofullscreen -- juego`, o `-fullscreen` cuando se selecciona
Calidad; RPCS3 usa `--no-gui EBOOT.BIN` en forma experimental.

**Límites actuales:** el perfil NO escribe la resolución ni el
renderizador internos de DuckStation/PCSX2/RPCS3; únicamente aplica el
modo de apertura y sugiere una escala 1x o 2x para ajustar manualmente.
No modifica archivos INI internos, BIOS, firmware o tarjetas de memoria.
Tampoco predice FPS reales ni consulta automáticamente una base
verificada por ID de título. No promete que una PC pueda ejecutar
todos los juegos de una consola.

## Emuladores externos oficiales y formatos

| Sistema | Emulador | Archivos | Fuente oficial |
|---|---|---|---|
| PS1 | DuckStation | .cue, .chd, .pbp, .m3u, .iso | https://github.com/stenzek/duckstation/releases/tag/latest |
| PS2 | PCSX2 | .iso, .cso, .chd | https://pcsx2.net/downloads/ |
| PS3 experimental | RPCS3 | EBOOT.BIN | https://rpcs3.net/download |

Los .iso y .chd son ambiguos, por lo que al añadirlos uno por uno se
pregunta a qué consola pertenecen. En importación masiva se usa una
consola inicial y se puede cambiar reimportando el archivo.

**OKUBI no descarga, agrupa ni distribuye estos emuladores**. El usuario
elige desde la interfaz el ejecutable Windows instalado legítimamente;
se comprueba el nombre esperado y se confirma antes de utilizarlo, sin
pretender comprobar la firma digital del archivo. Utiliza solo los
enlaces oficiales. No se proporcionan ROMs, BIOS, firmware, claves ni
juegos comerciales.

Los juegos PS3 deben estar previamente preparados para RPCS3, no
archivos .pkg/ISO arbitrarios. Los guardados de las tres consolas
pertenecen a cada emulador externo y no se mezclan con SAVE VAULT
GB/GBC/GBA/SNES ni Cross Save.

## Hardware básico: ejemplo Ryzen 3 2200G / Vega / RAM 16 GB

| Consola | Recomendación inicial |
|---|---|
| PS1 | Probable, resolución nativa o mejora modesta si va fluido |
| PS2 | Variable, empezar con 1x y filtros mínimos |
| PS3 | No recomendado en esa PC, sin promesas de ejecución |

Si actualizas CPU o GPU, usa **Volver a analizar PC**. Se recalculan las
recomendaciones con las nuevas características, sin eliminar tu
biblioteca ni tus partidas. La detección automática puede fallar si el
sistema restringe PowerShell/WMI o si la GPU activa no se identifica
correctamente: en ese caso muestra desconocido.

## Seguridad y regresiones

- Catálogo separado en multiconsole-v1.properties; el catálogo retro
  existente y sus partidas no se modifican.
- Solo se guardan rutas de ROM/ISO; nunca se copian o editan sus bytes.
  Se ignoran vínculos simbólicos directos y no se escanean más de 4
  niveles de profundidad ni 1000 referencias.
- Los comandos externos se construyen como argumentos separados para
  ProcessBuilder, nunca se usan títulos con cmd.exe o PowerShell.
- En caso de clasificación desfavorable se solicita confirmación; el
  usuario puede cancelar sin lanzar nada.
- Las licencias de DuckStation pueden restringir redistribución de
  paquetes modificados; los emuladores son instalaciones independientes.
- Windows CI debe probar hardware inyectado, fallos de detección,
  clasificador PS1/PS2/PS3, escaneo de 200 ROMs sintéticas, modo
  independiente y persistente por juego, ausencia de modificación de
  partidas previas, y comandos válidos.
- La versión sigue en PR DRAFT; no se fusiona main ni se toca VIDAS.

## Prueba en Windows

1. Descarga el ZIP de la Fase 60 desde Actions e instálalo en carpeta nueva.
2. Ejecuta `app\VERIFY-WINDOWS-PACKAGE.cmd`; para pruebas en un
   perfil nuevo usa `app\RUN-QA57-ISOLATED.cmd`.
3. Abre SMART PC (Ctrl+P), confirma CPU/RAM/GPU y el estado del sistema.
4. Importa juegos legales y configura DuckStation/PCSX2/RPCS3
   instalados desde sus páginas oficiales.
5. Abre un juego PS1 o PS2 con perfil Auto; comprueba la advertencia
   si no se recomienda la consola. Prueba modos ECO/Calidad solo cuando
   el hardware sea adecuado.
6. Comprueba que tus partidas retro anteriores permanecen intactas.
   Los guardados nuevos PS1/PS2/PS3 se administran en cada emulador.

**Seguridad física:** si Windows aún se apaga jugando o el gabinete
produce descargas eléctricas, no utilices emulación exigente hasta
revisar instalación eléctrica, fuente y hardware. Ningún perfil
de software evita fallos físicos o sobrecalentamiento severo.
