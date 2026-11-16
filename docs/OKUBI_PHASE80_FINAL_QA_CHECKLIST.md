# OKUBI 遊 — Fase 80 · Candidato final de pruebas (NO versión estable)

**Estado:** preparación de paquetes verificables y pruebas de aceptación. Código de desarrollo en `release/okubi-phase80-final-qa` sobre Fase 79. Sin merge a `main` y sin release público.

## Resultado esperado de la CI

La compilación Windows y Android debe finalizar en SUCCESS y entregar **artefactos QA**:

| Plataforma | Archivo de salida | Comprobación adicional Fase 80 |
| --- | --- | --- |
| Windows x64 | `OKUBI-Desktop-Windows-x64.zip` + SHA256SUMS | Integridad ZIP, manifiesto/huellas de los tres DLL, licencias, perfil de pruebas aislado, ausencia de ROM/partidas |
| Android | `OKUBI-Android-Migration-QA57.apk` + SHA256SUMS | Integridad APK, 4 ABI, 3 emuladores por ABI, licencias, `bo.nexoplay.app.unified.qa57`, SDK 26, **certificado debug**, no sustituye versión canónica |

La herramienta `tools/okubi_phase80_rc_preflight.py` funciona **sin instalar nada**; examina archivos generados dentro de los runners. Rechaza cambios en SHA-256, identidades erróneas, rutas de escape, archivos personales, DLL corruptas o APK con firma no apropiada para QA.

El reporte JSON incluye hashes y metadatos genéricos, **no ROMs, rutas privadas, claves de firma o partidas**. No certifica una licencia de redistribución pública ni pruebas físicas. Sigue siendo `public_release_authorized=false`.

## Pruebas de aceptación que requieren dispositivos físicos

Los usuarios pueden marcar cada una localmente después de realizar respaldos:

- [ ] **Windows, aislamiento:** copiar `%APPDATA%\OKUBI` a un respaldo fuera de OKUBI, descomprimir el ZIP en una carpeta nueva, verificar SHA256SUMS, iniciar **`OKUBI Desktop\app\RUN-QA57-ISOLATED.cmd`**. Comprobar que los favoritos y partidas de la instalación habitual no cambian.
- [ ] **Windows, biblioteca:** Inicio, Juegos y Descubre; pantalla completa y ventana; listas, búsqueda, miniaturas CINEMA y galería. Para 14 ports sin captura verificada debe mostrarse fondo neutro o imagen manual, nunca una captura ajena.
- [ ] **Windows, juego:** importar solo un juego propio de prueba GB/GBC/GBA/SNES; comprobar imagen y audio, teclado/mando, controles, cierre/reapertura y guardados **nuevos**. No usar como primera prueba la única partida valiosa.
- [ ] **Windows, SMART PC:** comprobar que el perfil de hardware se visualiza y que no cambia configuraciones de emuladores externos sin autorización. PS2 y PS3 no se consideran validados universalmente.
- [ ] **Android, aislamiento:** conservar `bo.nexoplay.app.unified`; verificar que la APK nueva tiene paquete `bo.nexoplay.app.unified.qa57`. Una variante debug efímera podría **no actualizar la QA57 anterior** porque su certificado cambia; no intentar resolverlo desinstalando aplicaciones con partidas sin respaldo privado.
- [ ] **Android, controles:** probar con Samsung A05s u otro teléfono compatible, usando juegos propios de prueba: pantalla vertical/horizontal, cruceta sin fantasmas, A/B/Start/Select/L/R, sonido, pantalla completa y transparencia.
- [ ] **Android, estabilidad:** probar cambio de app, pausa, reanudación y guardado nuevo, sin tocar los archivos de la instalación canónica.
- [ ] **Cross Save físico:** exportar a archivo `.okubisave` sintético/de prueba, transferir Android ↔ Windows aislados, comprobar SRAM/RTC y restaurar **sin reemplazar** partidas anteriores. Los estados rápidos no se presuponen compatibles.
- [ ] **Offline:** después de generar caché de carátulas, desconectar internet y comprobar fallbacks; iniciar el juego y asegurar que no dependa de una descarga de imágenes.

## Bloqueos que NO resuelve un CI verde

- [ ] Asociar cada DLL **exacta** Windows a su código fuente correspondiente, parches, receta de compilación y avisos MIT/MPL/GPL. El archivo JSON de Fase 79 registra huellas, **no el origen del código que produjo esas DLL**. La ruta mutable `nightly/latest` no es prueba suficiente.
- [ ] Revisar obligaciones de código fuente y atribución de toda la distribución, incluidos los núcleos Android.
- [ ] Identificar localmente la **firma original** de la APK `bo.nexoplay.app.unified` para una actualización in-place con `versionCode` superior; jamás subir keystore ni contraseñas.
- [ ] Obtener aprobación explícita de fusión a `main` y publicación estable como dos pasos distintos.

## Política de cierre

La Fase 80 solo produce un **candidato de QA con evidencias reproducibles**. Su ejecución puede aprobarse sin que se autorice una release pública. Prohibido alterar Android instalado, borrar partidas, colocar ROMs en GitHub, cambiar repositorio original o tocar VIDAS.

**Fase 81** solo podrá llamarse `OKUBI 1.0 estable` después de aprobar todos los bloqueos y las pruebas físicas.
