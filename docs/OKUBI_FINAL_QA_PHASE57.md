# OKUBI 遊 — FASE 57 · FINAL QA & BUG FIX

**Estado: versión de pruebas, NO estable, NO publicada y NO fusionada.**
Fase 57 apilada sobre RC1 / Fase 56 / PR #88. El proyecto VIDAS se mantiene separado.

## Objetivo: pruebas sin alterar la instalación actual

| Plataforma | Paquete | Aislamiento |
|---|---|---|
| Windows | OKUBI-Desktop-FINAL-QA57-ISOLATED-Windows-x64 | SOLO al arrancar con app/RUN-QA57-ISOLATED.cmd utiliza %LOCALAPPDATA%\OKUBI-QA57\Roaming\OKUBI |
| Android | OKUBI-A05S-QA57-SIDE-BY-SIDE-APK | ID bo.nexoplay.app.unified.qa57, etiqueta OKUBI QA57 遊 y datos privados nuevos |
| Instalación normal | No se reemplaza | Windows: %APPDATA%\OKUBI; Android: bo.nexoplay.app.unified |

La app Android de QA **no puede acceder automáticamente a los datos privados
de la aplicación anterior**. No desinstales ni borres datos de la app actual.
Este aislamiento es deliberado; tus juegos y partidas personales no se copian
automáticamente. No promete compartir estados rápidos entre plataformas.

## Windows — prueba reversible

1. Conserva la versión anterior y haz una copia externa de %APPDATA%\OKUBI.
2. Descarga el ZIP de GitHub Actions / Artifacts y descomprímelo en una carpeta
   NUEVA (no sobreescribas el ejecutable anterior).
3. Abre la subcarpeta OKUBI Desktop\app y ejecuta RUN-QA57-ISOLATED.cmd.
   **No abras directamente OKUBI Desktop.exe para esta prueba aislada.**
   El script usa una variable APPDATA SOLO para este proceso; nunca modifica
   las variables globales de Windows ni el directorio de la instalación normal.
4. La biblioteca QA estará inicialmente vacía. Importa ROMs propias de prueba
   desde sus ubicaciones habituales, sin moverlas. La biblioteca registra
   referencias, no copia sus bytes.
5. Usa los núcleos **integrados** de GB/GBC/GBA/SNES; comprueba sonido,
   FPS, pantalla completa, mando Xbox, teclado, estados F5/F9 y reanudación.
6. Prueba SAVE VAULT con datos de prueba: copia, cambia, recupera, verifica
   existencia de PROTECCIÓN PREVIA. Nunca pruebes por primera vez con tu única
   partida valiosa.
7. Cierra la app y comprueba que el OKUBI normal mantiene biblioteca,
   favoritos, estadísticas, partidas y configuraciones.

Importante: un emulador EXTERNO que configures tú podría guardar junto a la ROM;
para un aislamiento estricto, usa solamente los núcleos integrados de OKUBI.

## Samsung Galaxy A05s — instalación paralela

1. **No desinstales** la app actual.
2. En GitHub Actions descarga el artifact OKUBI-A05S-QA57-SIDE-BY-SIDE-APK.
   Contiene OKUBI-QA57-Android.apk, un manifiesto SHA-256 y reporte de verificación.
3. Instala la APK con el nombre OKUBI QA57 遊. Android debe mantener separado
   el icono anterior OKUBI 遊. La APK QA tiene applicationId diferente:
   bo.nexoplay.app.unified.qa57.
4. QA57 arranca sin partidas anteriores. Importa solo ROMs de prueba a las que
   tengas permiso de acceso. Crea progreso NUEVO, sin modificar tu único respaldo.
5. Prueba Pokémon Esmeralda/Rojo Fuego: cruceta multitáctil, A/B/L/R,
   START/SELECT, sonido, estados 1/2/3, velocidad y orientación vertical/horizontal.
6. Prueba SNES con un juego propio, revisa estabilidad, temperatura y consumo.
7. Si QA57 falla, cierra solo esa app; la aplicación original debe permanecer
   intacta.

La beta canónica mantiene bo.nexoplay.app.unified, versionCode 36 y la
identidad original. La variante QA añade el sufijo .qa57 SOLO cuando Gradle
recibe la propiedad de compilación okubiQaSideBySide=true. La firma debug
NO pretende actualizar el APK original ni sustituir el certificado del dueño.

## Comprobaciones obligatorias antes de publicar

- [ ] Windows QA usa exclusivamente la ruta aislada.
- [ ] El paquete Windows contiene lanzador y guía QA57 y verifica SHA-256.
- [ ] Tests Windows de seguridad SRAM/RTC, F5/F9, Cross Save, Cloud Save,
      SAVE VAULT, mandos y biblioteca sintética (150 ROMs).
- [ ] Android CI compila primero el APK habitual y luego APK QA57 diferente,
      comprobando paquete real/versión/etiqueta/firmas y ABIs con aapt/apksigner.
- [ ] A05s: instalación paralela, juego, guardados, sonido, cruceta y giro,
      probados físicamente.
- [ ] Windows: mouse, Xbox y juegos reales en pantalla pequeña y grande.
- [ ] Licencias, código correspondiente y certificado del propietario revisados.
- [ ] PR Draft aprobado expresamente antes de cualquier merge.

Una CI verde NO demuestra que las pruebas de teléfono o Windows físico
se hayan completado. Las pruebas automatizadas usan datos sintéticos.
