# OKUBI 遊 — Fase 62: SMART PLAY LEARNING (Windows)

**Windows 1.0.10, versión de pruebas.** Android y VIDAS no se modifican.
No se fusiona main, no se incluyen ROMs, BIOS ni juegos.

## Diferencia clave: compatibilidad estimada vs. probada

Desde SMART PC (Ctrl+P) se muestra en cada juego:

- **COMPATIBILIDAD ESTIMADA DEL HARDWARE:** recomendación general PS1/PS2/PS3 calculada con CPU, RAM y GPU. No implica que el juego haya sido ejecutado o que sus FPS sean buenos.
- **RESULTADO EN ESTA PC:** inicialmente **Sin probar**. Tras jugar, pulsa **¿CÓMO FUNCIONÓ ESTE JUEGO?** y registra el resultado real: **Funcionó bien**, **Funcionó lento** o **No inició**.
- Cada resultado se muestra en la biblioteca y en la ficha. No se inventan FPS ni se confunde un proceso que cerró rápido con un fallo del juego.

## Cómo aprende sin poner en riesgo tus datos

Con el perfil por juego **Automático**:
- Si el resultado confirmado es **Funcionó lento** o **No inició**, OKUBI elige un modo de apertura más conservador (**Protección/ECO**) en la próxima sesión y avisa antes de reintentar.
- Si el resultado confirmado es **Funcionó bien**, mantiene las reglas previas de hardware: no aumenta por sí solo la resolución ni fuerza Calidad.
- Si seleccionas manualmente Protección, Equilibrado o Calidad, ese ajuste explícito **tiene prioridad** sobre el historial automático.
- Si cambias de hardware, las observaciones del equipo anterior no se presentan como resultados validados en la nueva PC.

Los resultados se guardan atómicamente en un archivo separado:

`%APPDATA%\OKUBI\smart-play-history-v1.properties`

Contiene identificadores SHA-256 derivados de la ruta del juego, consola y características del hardware, y únicamente el resultado/fecha. No copia ni modifica ROMs, ISOs, firmware, BIOS, tarjetas de memoria, SRAM, RTC, SAVE VAULT o las configuraciones internas de los emuladores externos.

**Límite actual:** el cambio ECO afecta al modo de apertura recomendado por OKUBI. No modifica aún la escala de renderizado dentro de DuckStation, PCSX2 o RPCS3, no monitoriza temperatura/FPS real y no puede garantizar que un juego funcione ni prevenir una falla de hardware. La ficha distingue explícitamente estas limitaciones.

## Procedimiento de prueba

1. Descarga el ZIP Windows 1.0.10 desde GitHub Actions de la Fase 62 y extráelo en otra carpeta.
2. Ejecuta `app\VERIFY-WINDOWS-PACKAGE.cmd`; para perfil separado usa `app\RUN-QA57-ISOLATED.cmd`.
3. En SMART PC (Ctrl+P) importa un juego legítimo de PS1 o PS2. Verás **Sin probar** hasta haber registrado una experiencia real.
4. Abre el juego mediante su emulador externo oficial. Tras salir, pulsa **¿CÓMO FUNCIONÓ ESTE JUEGO?**.
5. Marca **Funcionó lento** y comprueba que la próxima recomendación automática sea **Protección/ECO**.
6. Elige **Calidad** manualmente y comprueba que se respete tu elección; después puedes volver a Automático.
7. Comprueba que tus guardados anteriores de GB/GBC/GBA/SNES y las partidas de los emuladores externos no se hayan modificado por el registro de opinión.

### CI y privacidad

Los tests con datos sintéticos verifican persistencia, aislamiento entre PC distintas, ajustes manuales prioritarios, recuperación segura de archivos corruptos, historial sin rutas explícitas y bytes de ROM/SRAM preservados. Los tests de Windows existentes continúan en GitHub Actions.

**No afirmar que se está optimizando gráficamente cada juego de manera autónoma hasta integrar y probar opciones oficiales de configuración por versión de emulador.** No fusionar ni publicar como estable sin aprobación y pruebas físicas.

