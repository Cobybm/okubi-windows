# OKUBI Desktop 1.0.4 — UNIVERSAL CROSS SAVE entre Android y Windows

Esta fase implementa la transferencia de **partidas de cartucho** entre tu OKUBI Android y tu OKUBI Desktop, usando **exactamente el mismo formato** `.okubisave` de Android (serializador Java compartido, no reimplementado).

## Mejoras de compatibilidad de la Fase 54

La importación ahora tiene una **verificación previa de solo lectura**, antes de pedir confirmar, que distingue entre progreso idéntico, SRAM/RTC del mismo tamaño y una diferencia de tamaño incompatible. Además, todas las lecturas de ROM y operaciones de transferencia se ejecutan fuera del hilo gráfico de Windows.

La verificación no instala guardados: solo permite la restauración si el archivo pertenece a la ROM exacta y contiene SRAM/RTC compatibles. El importador original **vuelve a verificar** antes de escribir, guarda una recuperación del progreso anterior y mantiene los estados rápidos separados. Android y Windows usan el mismo formato `.okubisave` versión 1, pero no prometen intercambiar estados rápidos de emulación ni sincronización automática.

## Así pasas Pokémon Esmeralda del celular al PC

1. En **OKUBI Android**, inicia tu copia de Pokémon Esmeralda, guarda normalmente **dentro del juego**, abre el menú de respaldos y usa **Exportar respaldo** para crear `*.okubisave`.
2. Copia el archivo a la PC por USB o Google Drive. **No necesitas mover ni volver a descargar la ROM**.
3. En **OKUBI Desktop 0.8**, importa tu misma copia de `Pokémon Esmeralda.gba` si todavía no aparece en tu biblioteca.
4. **Cierra el juego**. En la pantalla principal pulsa **⇄ CROSS SAVE** (o entra por Partidas y respaldos).
5. Selecciona Pokémon Esmeralda y **IMPORTAR DEL CELULAR**. Elige el archivo `.okubisave` y revisa la vista previa: sistema, tamaño SRAM/RTC y aviso de estados rápidos.
6. Confirma únicamente si el juego mostrado es correcto. OKUBI verifica **SHA-256 exacto de los bytes de la ROM**, consola y SHA-256 de cada parte del respaldo.
7. Si ya había una partida PC, guarda primero una copia independiente en `%APPDATA%/OKUBI/saves/gba/<hash>/cross-save-recovery` (en realidad la carpeta de consola es `gba` y los respaldos se guardan en `gba/cross-save-recovery`). Solo entonces instala SRAM/RTC. Después abre la ROM y comprueba su progreso.

## Así la regresas al celular

1. Guarda dentro del juego en OKUBI PC, **cierra completamente la ventana del juego** para que se confirme el SRAM/RTC.
2. Abre **⇄ CROSS SAVE → EXPORTAR AL CELULAR** y guarda `*.okubisave` en una carpeta (Downloads, pendrive, Drive, etc.).
3. Copia el archivo al teléfono.
4. En OKUBI Android inicia **la misma ROM**, entra al menú de respaldos y pulsa **Importar respaldo**. Android valida el cartucho y el tamaño SRAM/RTC, crea una copia privada de recuperación y luego importa. No hace falta desinstalar ni modificar OKUBI Android.

## Por qué no copiamos los estados rápidos

Los archivos de estado rápido de emulación `.state`/F5 pueden contener datos internos específicos del núcleo, la versión y el procesador. Un estado de la app Android puede ser incompatible con Windows incluso si es el mismo juego. **OKUBI 0.8 importa y exporta solamente SRAM y RTC**, que son los guardados de cartucho de mayor compatibilidad. Si un respaldo Android contiene un estado rápido, se reconoce, se comprueba su hash y se **omite expresamente** durante la importación a Windows.

No se prometen todas las partidas: algunos núcleos o revisiones usan distintos formatos/tamaños de SRAM o RTC. OKUBI compara tamaños cuando hay una partida PC existente y rechaza tamaños diferentes sin reemplazar los datos. La importación en Android también exige coincidencia del tamaño y un núcleo compatible.

## Seguridad de tus partidas

- No modifica ROMs originales ni los archivos de guardado de emuladores externos.
- **Nunca importa ni exporta mientras una sesión interna esté activa**.
- Los archivos de respaldo se verifican antes de usarse: firma `OKBS`, versión, consola, hash SHA-256 de ROM, SHA-256 por sección, límites estrictos y ausencia de datos extra.
- Importar exige confirmación. Si ya tienes SRAM/RTC en PC se crea un **archivo de recuperación .okubisave nuevo**, no se sobreescribe uno anterior.
- Los cambios se escriben de forma atómica y se comprueban después. En caso de error se intenta recuperar el estado anterior y se conserva el respaldo de recuperación.
- **No sobreescribe archivos .okubisave exportados que ya existan**: elige otro nombre.
- Para restaurar una partida anterior de PC: abre **CROSS SAVE → VER RESPALDOS**, busca el archivo en `cross-save-recovery` y selecciónalo desde **IMPORTAR DEL CELULAR**. Se creará otro punto de recuperación antes del cambio.
- Sin cuentas ni sincronización automática, ni descargas de juegos: copias tú el archivo cuando quieras.
- Los datos Android siguen alojados exclusivamente en Android; las partidas Windows siguen en `%APPDATA%/OKUBI/saves`.

## Formato exacto compartido

La misma clase `retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java` de la app Android se **compila directamente** en OKUBI Desktop y se incluye en su JAR. Es el formato versión 1:

```
OKBS (4 bytes) | versión (4 bytes) | sistema (UTF Java) | ROM SHA-256 (UTF Java)
SRAM: longitud, SHA-256 de 32 bytes, contenido
RTC:  longitud, SHA-256 de 32 bytes, contenido
STATE: longitud, SHA-256 de 32 bytes, contenido (solo se verifica; NO se transfiere)
```

No hay rutas dentro del respaldo ni archivos ROM.

Las pruebas Java comprueban interoperabilidad real leyendo un archivo escrito por la misma clase de Android, archivo de recuperación, SRAM+RTC, importación inversa, rechazos por otra ROM, tamaño incompatible, archivo alterado, archivo solo con estados, aislamiento de estados Windows y rechazo de sobrescrituras.

**IMPORTANTE:** La transferencia de partidas es **manual y protegida**, no sincronización automática ni Google Drive conectado. Los estados rápidos nunca se convierten entre plataformas. Conserva tus respaldos hasta verificar el progreso de cada juego.

**PR en borrador, no fusionar.** No cambia el código APK Android, los emuladores incluidos, tu biblioteca, ni las firmas existentes.
