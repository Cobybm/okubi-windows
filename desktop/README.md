# OKUBI 遊 Desktop 1.0.13 — Fase 65 SMART SESSION — Windows x64

**Importas tus juegos y pulsas JUGAR. No necesitas instalar emuladores externos ni elegir DLL.**

## Motores incluidos

| Consola | Motor Libretro | Licencia |
| --- | --- | --- |
| Game Boy | SameBoy | MIT |
| Game Boy Color | SameBoy | MIT |
| Game Boy Advance | mGBA | MPL-2.0 |
| Super Nintendo | Beetle Supafaust | GPL-2.0-or-later |

El programa Windows incluye los núcleos x64 oficiales en `app/cores`, junto a sus huellas SHA-256 y avisos de licencia. Si el núcleo falla una comprobación de integridad, OKUBI lo rechaza sin tocar tus partidas. Consulta [detalles de origen y licencias](BUILTIN_CORES.md).

## FASE 65 — SMART SESSION · HISTORIAL REAL DE SESIONES

- SMART PC (Ctrl+P) ahora registra **cuánto duró el proceso** externo PS1/PS2/PS3, el total de sesiones por juego y el código de salida. Es telemetría real del proceso, no una medición de FPS ni una garantía de compatibilidad.
- Tras probar un juego pulsa **¿CÓMO FUNCIONÓ ESTE JUEGO?** y, opcionalmente, indica los FPS que viste en el contador del emulador. OKUBI los distingue como **FPS introducidos por ti**, no FPS medidos automáticamente.
- El historial se guarda de forma atómica en `%APPDATA%\\OKUBI\\smart-session-history-v1.properties` usando IDs hash de juego/PC. Se separa de archivos de partidas, perfiles gráficos y biblioteca retro.
- Si cambias de PC o GPU, el historial de ese hardware no se utiliza como prueba de compatibilidad en el nuevo equipo. No altera los ajustes de PCSX2 ni corrige/eleva gráficos sin aprobación.
- Nueva prueba `DesktopSmartSessionHistoryTest`: sesiones sintéticas, FPS declarados o eliminados, hardware distinto, entradas corruptas y bytes originales de ROM, SRAM y PCSX2.ini preservados.
- [Manual y limitaciones de SMART SESSION](../docs/OKUBI_SMART_SESSION_PHASE65.md). Android y VIDAS intactos, PR draft sin fusionar.

## FASE 64 — SMART GRAPHICS 2.0 · CONFIGURACIÓN GRÁFICA PS2

- Con **SMART CONFIG activado para un juego PS2**, OKUBI prepara un perfil temporal de PCSX2 con resolución interna **1× o 2×**, efecto **FXAA** y filtrado anisotrópico **normal, 2× o 4×**. La selección se limita por el hardware, el modo elegido y el historial SMART PLAY del juego.
- Con Intel UHD / PC modesta o juego reportado lento: escala **1×**, sin FXAA ni filtrado adicional. En PC con GPU dedicada adecuada, Calidad puede subir hasta **2×**, FXAA y 4× de anisotropía. No se cambia el renderizador, no hay overclock ni se prometen FPS.
- La ficha muestra una **vista previa de los ajustes exactos**. Antes de abrir el juego, OKUBI verifica que el ejecutable PCSX2 seleccionado anuncie la opción oficial `-gamecfg` mediante `-help`. Si no es compatible, falla sin aplicar el perfil.
- Nuevos perfiles **graphics-v2**, inmutables, guardados en `%APPDATA%\\OKUBI\\smart-config-v1\\profiles`. La configuración externa de PCSX2 y las tarjetas de memoria no se modifican, y los perfiles de Fase 63 siguen intactos.
- Desactivar SMART CONFIG hace que los siguientes inicios no usen `-gamecfg`. Importante: `-gamecfg` **puede sustituir temporalmente otros ajustes propios del título**; el usuario debe tenerlo en cuenta antes de activar la función.
- Incluye la nueva suite `DesktopSmartGraphicsPhase64Test` sobre hardware Intel UHD/RTX, FXAA, anisotropía, verificación de CLI, integridad de ROMs y guardados, persistencia y reversión.
- [Guía SMART GRAPHICS 2.0](../docs/OKUBI_SMART_GRAPHICS_PHASE64.md). Windows beta, PR DRAFT, sin fusionar. Android y VIDAS intactos.

## FASE 63 — SMART CONFIG PS2 · GRÁFICOS POR JUEGO CON REVERSIÓN

- OKUBI SMART PC ahora puede **aplicar una resolución gráfica interna real de 1× o 2× a PCSX2**, exclusivamente mediante la opción oficial `-gamecfg` y **tras consentimiento por juego y PC**. Está desactivado por defecto.
- Para activarlo: selecciona un juego PS2 y pulsa **SMART CONFIG PS2 · INACTIVO (activar)**. La decisión queda guardada en `%APPDATA%\\OKUBI\\smart-config-v1\\smart-config-pcsx2-v1.properties`.
- Con Intel UHD 730, perfil ECO o juego reportado lento, se genera una configuración **1×**; en una PC con mayor capacidad y calidad seleccionada puede sugerirse **2×**. No se aplican speedhacks ni overclock.
- El archivo gráfico mínimo se crea bajo la carpeta de OKUBI; no se sobrescriben BIOS, ROMs, tarjetas de memoria ni los INI globales de PCSX2. El parámetro `-gamecfg` **puede sustituir temporalmente ajustes personalizados por juego durante esa sesión**: la interfaz avisa antes de activarlo.
- Al pulsar **desactivar**, la siguiente sesión se inicia sin `-gamecfg` y retorna a la configuración habitual de PCSX2. No se borran ni editan archivos del emulador externo.
- La nueva suite `DesktopPcsx2SmartConfigTest` comprueba consentimiento, separación por GPU, perfiles 1×/2×, rutas con espacios, rechazo de archivos alterados, desactivación e integridad de ROMs y tarjetas de memoria.
- DuckStation y RPCS3 siguen sin autoajustes internos. [Guía técnica y de seguridad Fase 63](../docs/OKUBI_SMART_CONFIG_PHASE63.md).
- **Solo Windows, PR DRAFT. Android y VIDAS intactos.**

## FASE 62 — SMART PLAY · APRENDE DE TUS JUEGOS EN ESTA PC

- En SMART PC (Ctrl+P) la biblioteca distingue **estimación de hardware** de **resultado confirmado por ti** para cada juego PS1/PS2/PS3. Al principio indica **Sin probar**, evitando afirmar que todos los títulos compatibles con una consola funcionan en tu PC.
- Después de jugar pulsa **¿CÓMO FUNCIONÓ ESTE JUEGO?** y marca si funciona bien, va lento o no inició. Los resultados se guardan en un archivo nuevo, separado de partidas y perfiles: `%APPDATA%\\OKUBI\\smart-play-history-v1.properties`.
- Si un juego tiene historial confirmado como lento/no iniciado y su modo sigue en **Automático**, OKUBI recomienda el lanzamiento **Protección/ECO** la próxima vez. Las opciones manuales siempre prevalecen y nunca aumenta los gráficos por sí solo.
- Cuando se detecta hardware diferente, la clasificación confirmada se restablece a **Sin probar** para esa PC, sin borrar las experiencias del hardware anterior.
- La función NO toca ajustes internos de DuckStation, PCSX2, RPCS3, ni copia/distribuye juegos. No inventa FPS, pruebas reales ni protección contra fallos físicos.
- Nueva prueba `DesktopSmartPlayHistoryTest`: persistencia, privacidad de rutas, cambio de PC, fallo de lectura, prioridad de perfiles manuales e integridad de bytes de ROM/partidas.
- [Manual SMART PLAY Fase 62](../docs/OKUBI_SMART_PLAY_PHASE62.md). **Solo Windows. Sin fusionar.**

## FASE 61 — SMART PC CINEMA · UI LEGIBLE (Windows)

- Arreglada la interfaz observada en PC con **Intel Core i5-11400 / UHD 730 / 15,8 GB**: botones y menú de perfiles ahora mantienen el tema oscuro con texto visible incluso cuando están desactivados.
- Al abrir SMART PC aparecen las estimaciones **PS1 / PS2 / PS3** aunque la lista esté vacía. El i5-11400 con GPU UHD 730 debería mostrar PS1 **PROBABLE**, PS2 **VARIABLE** y PS3 **NO RECOMENDADO**. No se prometen FPS ni compatibilidad individual de juegos.
- Biblioteca vacía con instrucciones de importación; los seis botones inferiores se organizan en 2 filas x 3, con barra de estado y contraste suficiente para Windows.
- Suite de regresión visual independiente `DesktopSmartPcVisualsTest`: fondo oscuro real de botones habilitados/deshabilitados, combo y los seis botones a 760 px. Sin modificar la evaluación de hardware de Fase 60 ni el formato de biblioteca.
- [Guía de contraste y prueba visual Fase 61](../docs/OKUBI_SMART_PC_UI_PHASE61.md).
- **Solo Windows**, PR draft sin fusionar. Android, VIDAS, partidas y configuración de otros emuladores siguen intactos.

## FASE 60 — SMART PERFORMANCE · PS1 / PS2 / PS3

- Accede desde el botón **SMART PC · PS1/PS2/PS3** del lateral o con **Ctrl+P**. El análisis lee la CPU, procesadores lógicos, RAM y GPU de la PC; nunca toca el registro, activa overclock ni cambia hardware.
- Cada juego de PlayStation que importes aparece con una etiqueta **Probable / Variable / No recomendado / Desconocido** basada en los requisitos generales de su consola. Es una evaluación de hardware, NO una confirmación de compatibilidad de cada título ni de FPS reales.
- Los perfiles **Auto / Protección / Equilibrado / Calidad** se guardan por juego en una biblioteca aparte (multiconsole-v1.properties). En CPU/GPU modestas el perfil Auto selecciona la apertura más conservadora y recomienda resolución nativa. Solo Calidad intenta abrir PS1/PS2 en pantalla completa.
- PlayStation 1 requiere DuckStation, PS2 requiere PCSX2 y PS3 usa RPCS3 experimental. El usuario elige el ejecutable oficial instalado. **No se incluyen** BIOS, juegos, firmwares, emuladores externos ni descargas automáticas. Se usan las opciones CLI documentadas, sin modificar los archivos de configuración de esos programas.
- La biblioteca retro GB/GBC/GBA/SNES, SAVE VAULT, SMART SAVE GUARD, Cross Save y la versión Android no se modifican. El ZIP Windows conserva los núcleos retro y el perfil QA57 aislado.
- Las pruebas de Windows incluyen **200 juegos sintéticos**, hardware básico/alto/desconocido, biblioteca independiente, persistencia, filtros de formatos, argumentos seguros y SHA-256 de los núcleos.
- Limitación actual: la **resolución interna y los ajustes avanzados** de DuckStation, PCSX2 y RPCS3 solo se sugieren. No se modifican automáticamente ni se monitorizan los FPS reales. Los juegos PS3 pueden no iniciar según versión, firmware y compatibilidad.
- [Manual de SMART PERFORMANCE y compatibilidad Windows](../docs/OKUBI_SMART_PERFORMANCE_PHASE60.md). **PR DRAFT: no fusionar ni llamar versión estable hasta pruebas físicas.**

## FASE 59 — SOLO WINDOWS (Android pospuesto)

- **Objetivo:** terminar y validar OKUBI para PC sin seguir esperando pruebas físicas Android. La APK de la Fase 58 no cambia ni se distribuirá como estable todavía.
- **Comprobación real del EXE:** OKUBI 1.0.7 admite el argumento `--verify-windows-release`, que revisa sin abrir juegos la integridad SHA-256 de las tres DLL Libretro x64, el puente JNI, los avisos de licencia y la presencia del lanzador de pruebas aislado. GitHub debe ejecutar esta comprobación contra el **EXE empaquetado**, además de la suite Java/JNI habitual.
- **Prueba aislada:** ejecuta `app/RUN-QA57-ISOLATED.cmd` para usar el perfil de laboratorio en `%LOCALAPPDATA%\\OKUBI-QA57\\Roaming\\OKUBI`, evitando sobrescribir las partidas normales. El ejecutable normal conserva `%APPDATA%\\OKUBI`.
- **Verifica la descarga:** Windows CI publica el ZIP portable 1.0.7 con `SHA256SUMS`, sin ROMs ni partidas personales; también incorpora `app/VERIFY-WINDOWS-PACKAGE.cmd` y `app/WINDOWS-PHASE59-GUIDE.md`.
- **Sin instalador MSI, sin firma Microsoft certificada, sin publicación oficial ni merge.** La versión solo podrá llamarse estable después de probar en un PC real sonido, controles, partidas temporales y licencias.
- [Guía Windows — Fase 59](../docs/OKUBI_WINDOWS_ONLY_PHASE59.md).

## FASE 58 — FINAL INTEGRATION (SOLO PREPARACIÓN)

- La rama final de integración `release/okubi-final-integration-phase58` conserva todas las fases acumuladas de Android y Windows. No se duplican commits con cherry-picks ni se fusiona `main`.
- Nueva auditoría de GitHub valida la ascendencia exacta respecto de `main`, identidad del APK normal/QA57, núcleo compartido de Cross Save, licencias y pruebas de seguridad de guardados.
- La comparación bloquea rutas de VIDAS, ROMs, partidas, APK/EXE personales y secretos de firma. Publica un reporte JSON sin leer datos privados.
- GitHub debe aprobar por separado el chequeo de integración, la suite Windows y la suite Android. La prueba real en tu PC, mando Xbox y A05s sigue PENDIENTE hasta que tú la confirmes.
- Windows continúa con la misma compilación 1.0.6 y el lanzador aislado QA57. Android mantiene la beta17 normal y la APK de prueba con identificador .qa57; ningún dato ni firma se migra automáticamente.
- Esta fase **NO FUSIONA** ni crea release: mantiene el PR en borrador hasta aprobación expresa y validación física de las partidas.
- [Guía final de integración y puertas de publicación](../docs/OKUBI_FINAL_INTEGRATION_PHASE58.md).

## FASE 57 — FINAL QA & BUG FIX (INSTALACIÓN AISLADA)

- **Windows:** la carpeta app del nuevo ZIP contiene RUN-QA57-ISOLATED.cmd.
  Ábrelo en vez del EXE normal para mantener biblioteca, preferencias y
  partidas de prueba en **%LOCALAPPDATA%\\OKUBI-QA57\\Roaming\\OKUBI**.
  La instalación y los guardados habituales permanecen fuera de ese perfil.
- **Android Samsung A05s:** GitHub genera un segundo APK con identificador
  **bo.nexoplay.app.unified.qa57**, nombre OKUBI QA57 遊 y almacenamiento
  privado propio. Puede instalarse al lado de OKUBI actual, sin borrarlo,
  porque los identificadores son diferentes. No copia partidas anteriores.
- Se conserva un APK beta17 normal e independiente. El segundo APK solo
  se obtiene mediante Gradle con la propiedad optativa okubiQaSideBySide=true.
  Ambas variantes se inspeccionan con aapt/apksigner y se validan con SHA-256.
- Nuevas pruebas: perfiles Windows separados, recuperación de SRAM en QA
  sin tocar el guardado normal, estabilidad de 150 ROMs sintéticas, nombres
  de APK, ABIs, SDK mínimo y rechazo de archivos personales.
- **No fusiones ni declares estable** la Fase 57 sin probar físicamente
  juegos, FPS, controles, guardados y sonido en PC y A05s.
- Guía: [OKUBI_FINAL_QA_PHASE57.md](../docs/OKUBI_FINAL_QA_PHASE57.md).

## FASE 56 — RELEASE CANDIDATE 1 (BETA DE VALIDACIÓN)

- **Sin migración destructiva:** RC1 es un ZIP `app-image` que se extrae en una carpeta distinta. No reemplaza una versión anterior automáticamente ni cambia rutas de `%APPDATA%/OKUBI`, favoritos, carátulas, guardados, SRAM/RTC, estados F5/F9 o SAVE VAULT.
- **Salida segura:** el menú/cierre de Windows comprueba partida activa y tareas **SMART SAVE GUARD, CLOUD SAVE y CROSS SAVE** en curso; no finaliza la JVM hasta que estas operaciones terminen. La política tiene pruebas deterministas de todas sus combinaciones.
- **Distribución comprobable:** GitHub Actions genera ZIP y archivo `OKUBI-Desktop-Windows-x64-SHA256SUMS.txt` con la huella SHA-256 real, verifica que el ZIP no contenga ROMs ni partidas personales y exige los tres avisos Libretro más enlaces de origen. Las notas de migración quedan incluidas en la app.
- **Android conserva su beta** `1.0.0-unified-beta17`, `versionCode 36` y el mismo `applicationId`. La CI crea solamente **APK de prueba con firma DEBUG**; verifica identidad, licencia y SHA-256. Para publicar/actualizar oficialmente se requiere clave del propietario y certificado fijo. **No instales sobre una APK con firma diferente**.
- **QA obligatorio:** el éxito de CI no certifica hardware, sonido, mando Xbox, escalado, restauración real, licencias de redistribución ni otra marca de celular. La versión RC1 **no está declarada estable y no se fusionará** sin aprobación.
- **Procedimiento completo y lista de verificación:** [OKUBI_RELEASE_CANDIDATE_PHASE56.md](../docs/OKUBI_RELEASE_CANDIDATE_PHASE56.md).

## FASE 55 — CINEMA FINAL DESIGN (WINDOWS)

- **GAME HUB más cómodo en laptops:** la ficha ya no obliga a desplazarse horizontalmente en la ventana mínima soportada de 800 px. Mantiene la portada real completa, estadísticas, galería de capturas y los cuatro botones de JUGAR, FAVORITOS, RESPALDOS y CERRAR.
- **Adaptación a pantalla y escala Windows:** el tamaño del diálogo respeta el monitor real, con desplazamiento vertical cuando hace falta. Las tarjetas de la biblioteca aumentan su altura únicamente si la escala/fuente lo requiere; el banner y las categorías se ocultan de forma responsiva con acceso alternativo desde el menú lateral.
- **Controles legibles con ratón y mando:** botones de CINEMA y pestañas tienen contorno de enfoque más evidente; la truncación visual utiliza un solo carácter de elipsis y nunca corta un emoji o un kanji por mitad. El texto completo permanece en accesibilidad/tooltip.
- **Pie de pantalla corregido:** incluso cuando la ventana es pequeña, los mensajes de estado conservan su espacio sin superponerse con el número de versión. Los mensajes completos aparecen también al colocar el cursor.
- **Rendimiento:** solo se usan cálculos de layout, dibujado Swing y las animaciones breves ya existentes, sin GPU dedicada ni efectos costosos. Las partidas, el mando dentro de los emuladores y la caché de carátulas no se modifican.
- **Cobertura:** dos pruebas nuevas comprueban puntos de corte 520/560/675px, escala DPI, diálogo dentro de pantalla, texto Unicode/kanji, render de botones, accesibilidad y acceso a JUGAR/FAVORITOS/RESPALDOS.
- **Sin cambios Android en esta fase.** El APK de la Fase 54 continúa siendo la última compilación Android y todavía requiere prueba visual en A05s y otros modelos. PR borrador de Fase 55 apilado sobre Fase 54; NO FUSIONAR.
- Antes de probar restauraciones reales en Windows conserva una copia externa de `%APPDATA%/OKUBI`.

### Comprobación manual de CINEMA en Windows

En una instalación de prueba, abre la app a 800×520, 1140×730 y 1920×1080, si la pantalla lo permite. Comprueba que GAME HUB no presenta barra horizontal en el tamaño pequeño, que el banner reaparece al agrandar la ventana y que el menú lateral da acceso a Favoritos/Recientes cuando la barra superior se oculta. Prueba nombres de ROM largos con acentos, kanji y emoji, navegación con flechas y mando Xbox, y que los cuatro botones de GAME HUB siguen siendo visibles. Esto **no requiere restaurar partidas**; para cualquier prueba posterior de recuperación haz antes un respaldo externo de `%APPDATA%/OKUBI`.

## FASE 54 — UNIVERSAL EXPERIENCE (CROSS SAVE SEGURO)

- Antes de importar un `.okubisave` Android a Windows, OKUBI ahora comprueba **la ROM exacta, consola, integridad SRAM/RTC y tamaño de las partidas PC**, en segundo plano, y muestra el resultado en una vista previa. Solo tras ver que la versión es compatible solicita confirmación.
- **PARTIDA IDÉNTICA:** reconoce un respaldo con SRAM/RTC idénticos al progreso existente y evita importarlo innecesariamente. No reemplaza ni crea una copia de recuperación duplicada.
- **TAMAÑO INCOMPATIBLE:** rechaza automáticamente archivos cuyo SRAM/RTC no cabe en la partida PC existente; presenta el motivo antes de solicitar la importación. La ROM y los guardados se mantienen intactos.
- **RESPALDO COMPATIBLE:** explica los componentes SRAM/RTC presentes y confirma que se creará una copia recuperable de la partida PC antes de sobrescribir. El controlador de Cross Save original vuelve a verificar el archivo al ejecutar la restauración.
- **Estados rápidos:** se valida su integridad, pero **nunca se transfiere un estado rápido Android a Windows** debido a incompatibilidades entre núcleos. Solo SRAM/RTC.
- **Interfaz fluida:** leer hashes de ROM, verificar archivos, importar y exportar se ejecuta con `SwingWorker` fuera del hilo Swing. Mientras ocurre se evitan aperturas de juegos, restauraciones en SAVE VAULT u operaciones Cloud Save/Cross Save concurrentes.
- No cambia la firma Android, APK, permisos ni rutas de datos; no instala una versión encima de otra. Se conserva el `applicationId` unificado. Todavía no hay sincronización automática bidireccional.
- Se añaden pruebas Windows para partidas idénticas, diferencias compatibles, tamaños incompatibles, ROM equivocada, copia inicial sin progreso y estados no portátiles; en Android se verifica con la **misma clase de serialización** que Windows exporta SRAM/RTC sin estados.
- **Sigue siendo versión beta:** no fusiones ramas ni instales APK debug encima de una versión firmada con otra clave. Haz una copia externa de los guardados antes de probar una recuperación real.

## FASE 53 — UNIFIED STABLE (SOLO PREPARACIÓN)

- La nueva rama `release/okubi-unified-stable-phase53` parte de la Fase 52 y conserva **toda la historia acumulada de OKUBI Android y Desktop**. No se hacen cherry-picks duplicados ni se fusiona `main`.
- El PR de preparación hacia `main` debe comprobar **dos compilaciones distintas**: Windows x64 y Android (APK DEBUG). Solo tras resultados verdes, revisión de permisos y pruebas manuales podrá considerarse fusionar.
- El script `python3 tools/okubi_unified_audit.py --root . --base origin/main` es una auditoría **solo lectura**: verifica identidad Android, compatibilidad de formato Cross Save, cobertura de CI y ausencia de ROMs, partidas y archivos VIDAS dentro de los cambios.
- La compilación Windows continúa con el Java runtime privado y los núcleos verificados. El APK de prueba Android conserva su identidad anterior: **la firma DEBUG no es una clave de distribución permanente**.
- **Ninguna ruta de guardado cambia**: ROM, SRAM/RTC, slots F5/F9, `.previous`, SAVE VAULT, favoritos, horas, mandos, Drive y las partidas Android permanecen fuera del repositorio.
- No confundir el nombre de la fase «UNIFIED STABLE» con una versión estable ya aprobada: todavía se requiere prueba real en Windows, mando físico y Android A05s. **PR DRAFT, NO FUSIONAR**.
- [Guía de auditoría completa y checklist de integración](../docs/OKUBI_UNIFIED_STABLE_PHASE53.md).

## NUEVO: SMART SAVE GUARD — Fase 52

- **Respaldo automático antes de jugar:** al pulsar JUGAR, OKUBI revisa la SRAM y RTC de ese cartucho en segundo plano. Si encuentra progreso distinto al protegido anteriormente, crea una copia verificada en SAVE VAULT **antes** de iniciar el emulador integrado o externo.
- **Sin duplicados:** compara contenido con instantáneas válidas de la misma ROM, tanto automáticas como manuales y copias de recuperación. Si ya hay una versión idéntica, no vuelve a almacenarla. Un juego nuevo sin SRAM/RTC inicia normalmente sin crear archivos vacíos.
- **Máximo 8 copias automáticas únicas por cartucho al día UTC.** Jamás borra automáticamente historial previo, ni copias manuales. Si alcanza el límite, **te pide permiso explícito** para iniciar sin otra copia.
- **Ante un error de disco o validación**, muestra el motivo y permite elegir entre cancelar o iniciar sin ese nuevo respaldo; no omite silenciosamente la protección. Mientras procesa una copia, impide otra operación de Cloud Save, Cross Save o Save Vault.
- **Activar/desactivar:** menú lateral **AUTO RESPALDO: ON/OFF**. Activado por defecto para instalaciones nuevas, pero puedes desactivarlo. La preferencia se guarda atómicamente en `%APPDATA%/OKUBI/smart-save-guard-v1.properties`. Las preferencias corruptas no impiden iniciar OKUBI: la protección queda desactivada hasta que vuelvas a activarla.
- **La copia es de solo lectura respecto a tus partidas**: no escribe ni elimina SRAM/RTC, capturas, estados F5/F9, archivos `.previous` ni ROMs al crear un respaldo. Se usa SAVE VAULT y el mismo SHA-256 de la ROM existente.
- **Pruebas offline:** juego sin SRAM, primera copia, progreso cambiado, deduplicación contra copias manuales, preferencia persistente, límite diario de ocho, y comparación de bytes de ROM/SRAM/RTC/`.previous`.
- **Windows 1.0.3 beta:** sin cambios en emulación, Android, VIDAS, núcleos, perfiles ni datos existentes. PR **DRAFT** sin fusionar. Antes de probar una restauración real, haz una copia externa de `%APPDATA%/OKUBI`.

## NUEVO: QA & STABILITY — Fase 51

- **Protección del mando al cambiar de ventana:** al volver de GAME HUB, de otro diálogo o de un juego, la biblioteca espera una lectura completa con todos los botones soltados antes de reconocer nuevas pulsaciones XInput. Evita abrir varias veces una ficha al mantener Y o activar A al cerrar un diálogo.
- La ficha GAME HUB aplica la misma regla cuando recupera el foco; los controles dentro del emulador no se modifican. El estado se limpia al cerrar el diálogo; no cambia perfiles ni mapeos previos.
- **Bloqueo de cierre de SAVE VAULT** mientras está leyendo, creando copias o restaurando: se deshabilita CERRAR y se impide salir accidentalmente con Esc o la X de la ventana mientras la tarea de disco se ejecuta. Solo se desbloquea al finalizar la operación.
- **Almacenamiento más aislado:** SAVE VAULT rechaza configuraciones en las que el historial vive dentro de los guardados reales o viceversa, rutas directas de guardado enlazadas simbólicamente y manifiestos sospechosamente grandes. Un respaldo inválido sigue en el historial, pero no permite restaurarse.
- **Pruebas de regresión nuevas:** eventos Xbox A/Y/B mantenidos al perder y recuperar foco; doble apertura de GAME HUB, corrupción de manifiestos y separación de rutas. La CI de Windows ejecuta, además, toda la batería histórica de emulación y SRAM/RTC, F5/F9, Cross Save, Cloud Save, capturas, UI, control Xbox y SAVE VAULT.
- **Sin operaciones destructivas en producción:** no se borran partidas reales, no se modifica Android, VIDAS, ROMs ni emuladores y no se fusionan los PR. Esta es una beta 1.0.2 que sigue requiriendo pruebas con mando físico y una copia externa de `%APPDATA%/OKUBI` antes de restaurar.
- **Nota:** el repositorio incluye pruebas automatizadas; el resultado de compilación no sustituye una prueba de restauración en la instalación del usuario.

## NUEVO: SAVE VAULT — Fase 50

- **Administrador visual de partidas**: abre **SAVE VAULT** desde el menú lateral o desde **RESPALDOS** en GAME HUB PRO. Muestra el historial de copias por juego, con fecha, componentes SRAM/RTC y estado de integridad SHA-256.
- **CREAR COPIA** crea una instantánea verificada y separada en `%APPDATA%/OKUBI/save-vault-v1/<CONSOLE>/<ROM_SHA256>/snap-...`. Cada instantánea usa su propio directorio y manifiesto; **nunca reemplaza una copia anterior**. No incluye ROMs ni archivos de emulador.
- **RECUPERAR** requiere seleccionar una copia válida y confirmar explícitamente. El archivo se vuelve a comprobar con SHA-256 y **la huella real de la ROM** antes de escribir. Si había SRAM/RTC en el PC, crea primero otra instantánea marcada **PROTECCIÓN PREVIA** para poder deshacer la restauración.
- Cada componente se sustituye mediante archivo temporal; tras escribir se verifica el contenido. Ante fallo se intenta restaurar los bytes originales y se mantienen las copias del Vault. Si una instantánea solo incluye SRAM o solo RTC, **el componente ausente permanece intacto**; se avisa antes de confirmar.
- **No se permite operar con un juego abierto.** El trabajo de huellas, lectura, copias y restauración se ejecuta en segundo plano con SwingWorker para no congelar la interfaz.
- Respeta la separación: **SAVE VAULT = copias locales SRAM/RTC; CROSS SAVE = transferencia Android ↔ PC; CLOUD SAVE = carpeta de Drive sincronizada por el cliente.** Los estados rápidos F5/F9, las ROMs, las capturas y `.previous` nunca se editan desde Vault.
- Historial acotado y archivos comprobados en cada acceso. Copias corruptas o pertenecientes a otra ROM se marcan como no verificadas y no se ofrecen para restaurar.
- Pruebas automáticas: crea varias versiones, recupera progreso anterior, verifica la copia de protección, restaura esa recuperación, rechaza archivos corruptos/ROM diferentes y comprueba que los bytes ROM, F5/F9 y `.previous` permanecen intactos. CI Windows conserva todas las pruebas anteriores.
- **Esta versión es una beta de respaldo; no sustituye una copia externa de las partidas.** Haz una copia manual de `%APPDATA%/OKUBI` antes de probar una restauración por primera vez.
- Rama y PR independientes de Fase 49, **DRAFT, NO FUSIONAR**, sin cambios en Android, firmas o VIDAS.

## NUEVO: CONTROLLER EXPERIENCE — Fase 49

- Desde la biblioteca, **Y** abre la ficha del juego seleccionado (una sola vez por pulsación), sin duplicar la apertura al regresar.
- La **ficha GAME HUB PRO** puede manejarse por completo mediante mando **Xbox/XInput**: cruceta **izquierda/derecha/arriba/abajo** y **LB/RB** desplazan el foco entre JUGAR, FAVORITO, RESPALDOS y CERRAR. El botón enfocado tiene un efecto visible y accesible.
- **A** confirma la acción seleccionada; **B** cierra la ficha sin modificar el juego; **X** añade/quita favorito directamente. Pulsar **Start** dentro de la ficha no alterna el modo consola ni inicia un juego inesperadamente.
- **Teclado sin ratón** en la ficha: flechas cambian foco, **Enter** activa la selección, **Esc** cierra y **Espacio** conserva el comportamiento nativo del botón seleccionado. `Ctrl+Enter` sigue iniciando la ROM seleccionada.
- **Sin conflictos de mando:** el temporizador XInput de la ficha funciona únicamente mientras el diálogo está activo; la biblioteca no toma el mando en ese momento, y el emulador conserva el control exclusivo durante la partida. Cuando se cierra la ficha, el temporizador se detiene y se limpia la entrada anterior.
- Mantiene los mapeos que ya funcionaban dentro de GB/GBC/GBA/SNES. No cambia perfiles durante el juego, JNI, vibración, DirectInput, SRAM/RTC, F5/F9, Cross Save ni Cloud Save.
- El modo Xbox, la selección y la limpieza se verifican con pruebas automáticas deterministas, además de las de Fases 37–48. **Una compilación verde no sustituye una prueba física con el mando real.**
- Sin tocar VIDAS, APK Android, ROMs ni archivos de guardados. PR **DRAFT** y **NO FUSIONAR**.

## NUEVO: GAME HUB PRO — Fase 48

- Cada ROM importada tiene una **ficha grande** con la portada de Libretro que ya funciona, sistema, disponibilidad, horas reales registradas, sesiones y última sesión. La ficha reutiliza estadísticas existentes; **no inventa horas anteriores** ni cuenta tiempo en emuladores externos.
- Desde la tarjeta pulsa el botón **i** (con nombre accesible «Ver ficha»), o pulsa **VER FICHA** en el banner. El atajo **I** abre la ficha del juego seleccionado (si no estás escribiendo en Buscar). **Esc** cierra; **Ctrl+Enter** inicia el juego desde la ficha.
- Acciones reales: **JUGAR**, **AÑADIR/QUITAR FAVORITO**, **RESPALDOS** (abre exactamente el módulo existente de Cross Save) y **CERRAR**. La carátula y las acciones conservan estética CINEMA.
- **TUS CAPTURAS:** galería de hasta **3 imágenes reales** ya guardadas por OKUBI, ordenadas por fecha. Solo se muestran capturas del juego seleccionado; las imágenes se leen con límites de tamaño y dimensiones, sin descargar capturas de la red ni crear capturas ficticias.
- **Indicadores de guardado:** detecta de forma **solo lectura** si existen SRAM y RTC para el cartucho; jamás sobrescribe/restaura los archivos al mostrar la ficha.
- El escaneo SHA-256 del cartucho y la lectura de capturas ocurren en **SwingWorker** fuera del hilo de interfaz; se ignora el resultado si cerraste la ficha.
- Protecciones: no cambia las rutas de las ROM, saves, estados rápidos F5/F9, núcleos, configuración, Cross Save, Cloud Save, Android, APK ni VIDAS. La app sigue protegiendo el cierre de partidas activas.
- Se añaden pruebas automáticas que verifican bytes y fechas de ROM/SRAM intactos, rechazan capturas de otros juegos y corruptas, prueban callbacks Swing de JUGAR/FAVORITOS/RESPALDOS y los estados vacíos. CI Windows 0.9.9 ejecuta también toda la suite anterior.
- **Pendiente:** verificar en Windows real las proporciones de la ficha, la navegación por teclado y el comportamiento del diálogo con el mando. PR **DRAFT**, **NO FUSIONAR**.

## NUEVO: CINEMA LIBRARY 2.0 — Fase 47

- **Banner dinámico de portada real:** la carátula cacheada o descargada mediante ONLINE COVERS aparece ampliada, suavizada y oscurecida detrás del nombre del juego seleccionado. La carátula original completa sigue visible a la derecha sin recortes.
- **Transición cinematográfica breve (~300 ms)** al recibir una portada, con `javax.swing.Timer` solo en el componente visible; el temporizador se detiene al quitar el banner. Sin hilos de juego, motores nuevos ni escrituras en partidas.
- **Tarjetas con brillo suave al pasar el ratón** (~140 ms), borde seleccionado de alto contraste y accesibilidad conservada. Los botones de jugar/favoritos son independientes de la animación.
- **Menús sin duplicados:** las cuatro categorías viven en las pestañas de biblioteca; el menú lateral contiene solo Importar carpeta, Portadas en Internet, Cross Save y Partidas/respaldos. Las opciones principales continúan arriba.
- **Pantallas pequeñas:** cuando se ocultan las pestañas por falta de altura, las categorías aparecen automáticamente en el menú lateral. También puedes usar `Ctrl+1/2/3/4` (Todos/Favoritos/Recientes/Más jugados), LB/RB con mando o el ratón.
- **Proporciones afinadas** en el banner y miniaturas; sin descargar las imágenes más de una vez gracias a la caché existente, sin ROMs ni arte protegido empaquetado.
- **Pruebas nuevas sin red** verifican colores del fondo real, contraste, controles existentes, fade, selección y liberación de temporizadores. La suite previa de núcleos, SRAM/RTC, F5/F9, Cross Save, Cloud Save, HDR/HUD, Smart Scaling, mandos y portadas sigue ejecutándose.
- No toca `%APPDATA%/OKUBI/saves`, rutas de ROM, APK Android, firmas ni VIDAS. **PR DRAFT, NO FUSIONAR** hasta comprobar el diseño en Windows real.

## NUEVO: ONLINE COVERS — Fase 46

- **Portadas reales automáticas por Internet** desde el servidor público de miniaturas de Libretro: https://thumbnails.libretro.com/ . Fuentes oficiales de RetroArch; no Google Images, scrapers, cuentas ni claves.
- Al mostrar el juego en el **banner** y en su **tarjeta**, OKUBI busca una imagen de su consola que coincida con el título, la descarga **en segundo plano** y la guarda en `%APPDATA%/OKUBI/cover-art-v1/` para usarla sin conexión.
- Reconoce títulos en español: **Pokemon Esmeralda** → `Pokemon - Edicion Esmeralda (Spain)`; **Pokemon Rojo Fuego** → `Pokemon - Edicion Rojo Fuego (Spain)`. No asigna portadas aproximadas a ROM hacks u otros juegos para evitar errores.
- Si no existe o falla la red, conserva la ilustración procedural de OKUBI. Busca de nuevo tras 14 días cuando el catálogo no tenga portada, o antes con **Explorar → Portadas en Internet**.
- Acepta carátulas locales `.png/.jpg/.jpeg` junto a la ROM con el mismo nombre base (sin cambiar ni leer el contenido de la ROM), que tienen preferencia sobre las descargadas.
- Sin bloqueos de interfaz: una sola cola HTTPS, límites de transferencia e imagen, no sigue redirecciones a otros dominios y escritura temporal atómica de caché.
- **Nota sobre derechos:** carátulas y material promocional pueden tener derechos de sus respectivos editores. OKUBI no las redistribuye ni incluye en el ZIP; solo las obtiene bajo demanda al equipo del usuario. Revisa los términos de uso de la fuente.

## NUEVO: CONSOLE FOCUS — Fase 45

- Las **tarjetas de juegos** tienen un estado seleccionado con iluminación original del color de GB/GBC/GBA/SNES, borde de alto contraste y descripción accesible; al pasar el ratón se resaltan sin iniciar la ROM.
- **Portada, nombre y superficie** seleccionan el juego con clic; un doble clic inicia la ROM disponible. Los botones JUGAR y FAVORITO siguen siendo independientes, evitando activaciones por accidente.
- **Pestañas** TODOS, FAVORITOS, RECIENTES y MÁS JUGADOS con estado activo claramente visible y clic real. **LB/RB** sigue cambiándolas con mando Xbox, conservando el filtro seleccionado.
- La ficha protagonista sigue al juego seleccionado después de filtrar o cambiar de categoría, corrigiendo un desfase visual del banner.
- En ventanas de poca altura se oculta primero el banner y finalmente las pestañas, dejando más espacio para los juegos; los filtros siguen disponibles en el menú y con LB/RB.
- Progreso, SRAM/RTC, F5/F9, Cross Save, Cloud Save, núcleos, controles dentro del juego y Android quedan intactos.

## NUEVO: ADAPTIVE LIBRARY — Fase 44

- Biblioteca adaptable: **una columna en ventanas estrechas**, **dos** en tamaños normales y **tres** cuando el ancho lo permite. Las tarjetas nunca se reducen por debajo del ancho mínimo a causa de añadir más columnas.
- Flechas y cruceta Xbox se desplazan según el **número real de columnas**: arriba/abajo conserva columna cuando existe y izquierda/derecha nunca salta a la siguiente fila.
- Cambiar el tamaño de la ventana o el Modo Consola recalcula las columnas automáticamente y mantiene el juego seleccionado. Las últimas filas incompletas **no crean juegos falsos**.
- Se conserva la tarjeta de progreso cuando hay una sola partida en una fila con espacio, y el estado sin juegos también se adapta.
- No toca ROMs, emulación, guardados SRAM/RTC, F5/F9, Cross Save, Cloud Save, controles dentro de los juegos ni Android.

## NUEVO: HEADER RESPONSIVE — Fase 43

- La cabecera de la biblioteca se adapta al ancho real de la ventana: **botones completos** en pantallas grandes, **nombres cortos sin truncar** en tamaño medio y **dos filas de tres controles** en ventanas pequeñas (desde 800 px).
- Se corrigieron los textos que salían cortados («MODO CON...», «Juego sor...», «ESTADÍST...» e «Importar...»). Cada botón conserva su función, descripción completa y accesibilidad.
- Al entrar o salir del **Modo Consola** la cabecera se actualiza automáticamente sin recargar la biblioteca, ROMs ni emuladores.
- **Sin cambios** en partidas, mandos, filtros, HUD, SMART SCALING, Cross Save y Cloud Save.

Consulta [HEADER RESPONSIVE — Fase 43](HEADER_RESPONSIVE_PHASE43.md) para ver cómo probar los distintos tamaños en Windows.

## NUEVO: SMART SCALING — Fase 42

- En el juego, **Esc → CINEMA CONTROL CENTER → SMART SCALING**, o tecla **F2**: elige Auto (respeta tu filtro anterior), Ajustar/FIT, Pixel Perfect (entero), Llenar/FILL (recorta) o Estirar/STRETCH (deforma).
- Los ajustes son instantáneos, independientes por GB/GBC/GBA/SNES y se conservan en `%APPDATA%/OKUBI/cinema-scaling-v1.properties`.
- Ajustar/Pixel Perfect mantienen las proporciones originales. Llenar avisa que puede recortar; Estirar avisa que puede deformar. CRT/LCD/Suave HD continúan como filtros visuales separados.
- **F11 maximiza la ventana**. El HUD 0.9.2 se oculta con F1/Esc para dar más espacio. Sin cambios en ROMs, guardados, mandos, Cross Save o Cloud Save.

Consulta [SMART SCALING — Fase 42](SMART_SCALING_PHASE42.md) para entender los modos y su resultado en 1080p.

## NUEVO: CINEMA IMMERSIVE — Fase 41

- En el juego, **ESC → CINEMA CONTROL CENTER → HUD**: modos **Automático, Completo, Compacto u Oculto**, guardados por consola.
- **Automático (por defecto)** esconde las barras superior/inferior tras ~3 segundos y las muestra de nuevo al mover el ratón; la imagen crece sin estirar el juego.
- **F1** muestra las barras temporalmente, incluso en modo Oculto; **ESC** abre siempre el menú. F5/F9, F12, M, P y F11 siguen funcionando.
- La configuración del HUD se guarda por separado en `%APPDATA%/OKUBI/cinema-hud-v1.properties`, sin afectar partidas, ROMs, Cross Save, Cloud Save ni controles.

Consulta [CINEMA IMMERSIVE — Fase 41](CINEMA_IMMERSIVE_PHASE41.md) para la guía de los cuatro modos.

## NUEVO: CLOUD SAVE — Fase 39

- **☁ CLOUD SAVE** conecta una carpeta **local sincronizada con Google Drive para escritorio**. No necesita iniciar sesión dentro de OKUBI, instalar emuladores externos ni proporcionar contraseñas.
- **Subir nueva versión** exporta el mismo archivo `.okubisave` compatible con Android, comprobado mediante SHA-256. No sobreescribe respaldos anteriores.
- **Revisar / restaurar** muestra el historial de versiones que pertenecen exactamente a tu ROM y marca los **conflictos** cuando PC y carpeta tienen guardados SRAM/RTC diferentes.
- Cada restauración exige confirmación y conserva un punto independiente de recuperación antes de reemplazar cualquier guardado.
- **Drive sincroniza el archivo, no OKUBI**. Comprueba que Google Drive para escritorio termina la subida o descarga. Android sigue usando su opción manual de Importar/Exportar respaldo.
- No transfiere estados rápidos `.state`, ni modifica ROMs, partidas existentes o preferencias de versiones anteriores.

Consulta la guía [CLOUD SAVE (Google Drive)](CLOUD_SAVE.md) para configurar una carpeta y mover partidas entre tu Android y la PC.

## NUEVO: CROSS SAVE — Fase 38

- **⇄ CROSS SAVE** permite exportar una partida de Windows a OKUBI Android o importar un archivo `.okubisave` del celular.
- **Sin instalar emuladores adicionales** ni cambiar ROMs. Se reutiliza el formato original de respaldo de OKUBI Android.
- Cada archivo se valida por consola, SHA-256 de la ROM y de los datos; la importación exige confirmación y protege el guardado PC anterior mediante una copia recuperable.
- Se transfieren **SRAM y RTC**, no estados rápidos entre núcleos. Las partidas pueden ser incompatibles si los núcleos difieren: revisar siempre la recuperación.
- No se toca Android ni se sincroniza automáticamente con Google Drive. Tú decides cuándo copiar el respaldo por USB, Drive u otro medio.

Consulta [CROSS SAVE Android ↔ Windows](CROSS_SAVE.md) para los pasos exactos y advertencias.

## NUEVO: GAME HUB — Fase 37H

- **Ficha de juego seleccionada** con portada original, consola, tiempo jugado real, número de sesiones y botón de continuar.
- **Estadísticas** locales: horas totales, sesiones, ranking visual de los 15 más utilizados y categoría «Más jugados» con mando.
- El contador funciona **solo desde 0.7** y únicamente cuando el núcleo integrado está funcionando. Pausas, configuración y tiempos anteriores NO se inventan.
- **Perfiles de mando XInput por consola**: distribución Nintendo tradicional o letras de Xbox, elegibles con **ESC** durante la partida. Mantiene todas las teclas personalizadas.
- Datos guardados de forma atómica e independiente de las ROMs/partidas en `%APPDATA%/OKUBI/gameplay-stats-v1.properties`.

Consulta la [guía GAME HUB](GAME_HUB.md) para detalles del tiempo, privacidad y perfiles de mando.

## NUEVO: CONSOLE MODE — Fase 37G

- **Modo consola a pantalla completa**: activa con el botón superior, **Start de un mando Xbox/XInput** o **F11**. Se oculta la barra lateral y el juego seleccionado se resalta con borde de neón.
- **Jugar desde el sofá**: cruceta/stick para recorrer tarjetas, **A** para iniciar, **X** para favoritos, **B** para volver y **LB/RB** para cambiar categorías. Las pulsaciones mantenidas tienen repetición moderada.
- También funciona con **flechas**, **Enter**, **F** para favoritos y **Esc** para salir de Modo Consola, sin depender del ratón.
- Mantiene el modo ventana tradicional, el menú dentro del juego con **Esc**, los cinco filtros y controles configurables por consola.
- **Conserva tus guardados y biblioteca**, sin cambiar rutas, formatos, ROMs o núcleos.

Consulta la [guía de CONSOLE MODE](CONSOLE_MODE.md) para el esquema completo de controles y sus límites.

## NUEVO: ULTRA CINEMA

- Pantalla principal con un **juego destacado y botón CONTINUAR**, tarjetas retro originales de cada consola, favoritos y recientes.
- **Juego sorpresa** para abrir al azar un juego disponible.
- Atajos globales **Ctrl+F** buscar, **Ctrl+O** importar ROM y **Ctrl+R** juego sorpresa.
- En el reproductor integrado: **F5 guarda un estado rápido**, **F9 lo restaura con confirmación**, **F12 hace una captura PNG**, **M silencia/reactiva el audio**, **P pausa** y **F11 maximiza**.
- El mando compatible con **XInput (Xbox y similares)** se detecta automáticamente cuando está conectado; se puede seguir usando el teclado.
- Guardados rápidos por ROM y huella del núcleo en `%APPDATA%/OKUBI/saves/<consola>/*.slot1.state`, con copia `.previous`. Una actualización de núcleo que cambie su hash no cargará automáticamente un estado antiguo.
- Las capturas PNG se guardan en la subcarpeta `screenshots` de la partida y nunca sustituyen SRAM ni RTC.
- Los guardados SRAM/RTC siguen usando escritura atómica y copia previa; no cambia el formato de los juegos ni tu biblioteca.

**Compatibilidad:** los mandos DirectInput de otros fabricantes podrían requerir software de compatibilidad XInput; no afirmamos compatibilidad con todos los mandos. Los estados rápidos Libretro no son portables universalmente entre núcleos o revisiones.

## Pasos

1. Descarga el artefacto **OKUBI-Desktop-UNIFIED-PREVIEW-Windows-x64-Phase53** de la ejecución verde de la rama de preparación; es una beta, no una versión final aprobada.
2. Descomprime **todo** el ZIP (se necesitan `OKUBI Desktop.exe`, `app` y `runtime`).
3. Abre `OKUBI Desktop.exe`. Es una compilación de desarrollo **sin firma Authenticode de Windows**. Verifica su origen si SmartScreen advierte.
4. Pulsa **Importar ROM** o **Importar carpeta** y selecciona tus propias copias de juegos.
5. Haz clic en **JUGAR**. La ventana interna de OKUBI utiliza automáticamente el emulador incluido correspondiente.

En **Motores incluidos** puedes consultar el estado de cada consola. Las opciones para seleccionar un núcleo DLL propio o un emulador EXE externo permanecen como alternativas avanzadas, pero **no son necesarias para jugar**.

## Controles de teclado

| Teclas | Acción |
| --- | --- |
| Flechas | Movimiento |
| Z y X | B y A |
| A y S | Y y X de SNES |
| Q y W | L y R |
| Enter | Start |
| Espacio | Select |
| P | Pausar o reanudar |
| F11 | Maximizar o restaurar |

La imagen conserva la proporción original y permite píxeles nítidos o suavizados.

## Guardados

- Las ROMs originales solo se leen y permanecen donde las guardaste.
- Guardados internos: `%APPDATA%/OKUBI/saves/<consola>/<SHA-256 ROM>.srm` y `.rtc`.
- Escritura atómica, copia `.previous` de la versión anterior y guardado automático cada ~30 segundos y al cerrar.
- Los guardados y ajustes de Android no se modifican. **Cross Save Android ↔ Windows está implementado como exportación/importación manual de archivos `.okubisave`**, y requiere ROM de idéntico hash; no hay sincronización Android automática.
- La biblioteca y sus favoritos siguen en `%APPDATA%/OKUBI/desktop-library.properties`; actualizar el ZIP no los elimina.

## Pruebas y limitaciones

GitHub Actions Windows compila el frontend y el puente nativo con MSVC, prueba vídeo/audio/mandos/RTC/SRAM con un núcleo sintético de CI, descarga los tres núcleos oficiales con hashes fijados, verifica arquitectura Windows x64 y API Libretro, y finalmente construye el EXE con Java incluido mediante jpackage.

**Falta la prueba física con tus ROMs reales en tu PC**. El hecho de que las bibliotecas carguen y superen las verificaciones no asegura que todos los juegos funcionen sin problemas. Algunos núcleos pueden necesitar configuraciones o firmware adicionales según el juego.

El binario sin firmar es de prueba privada. Antes de una distribución pública hay que completar las obligaciones de fuentes correspondientes (especialmente GPLv2+ de Supafaust), revisar la licencia y fijar revisiones y recetas reproducibles de los motores. No se incluyen juegos ni firmware protegidos.

Consulta [reproductor integrado](EMBEDDED_PLAYER.md) y [origen de los núcleos](BUILTIN_CORES.md).

**PR en borrador: no fusionar sin probar.**
