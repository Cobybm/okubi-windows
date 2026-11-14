# OKUBI 遊 — Launcher de juegos para Windows y Android

Proyecto de código fuente con biblioteca CINEMA, juegos retro, catálogo de
ports para PC, perfiles de rendimiento SMART PC, mandos y guardados protegidos.

## Estado actual: Fase 78, candidato de integración (NO ESTABLE)

La implementación más completa está en
`integration/okubi-phase78-release-candidate`. Combina el historial de
desarrollo de Windows y Android hasta la Fase 77I e incorpora auditorías de
integración y compatibilidad de guardados.

**Importante:** la rama `main` no debe considerarse una compilación de la
aplicación hasta completar el PR de integración. No existe una versión 1.0
estable publicada. Las pruebas automáticas y APK de QA no autorizan publicar.

Lee el estado y las tareas pendientes en
[Plan de integración Fase 78](docs/OKUBI_PHASE78_INTEGRATION_CANDIDATE.md).

## Arquitectura

- `desktop/`: launcher nativo de Windows, emulación con núcleos Libretro,
  biblioteca CINEMA, SMART PC, mandos y guardados.
- `android-app/`: aplicación Android con identidad de paquete canónica
  `bo.nexoplay.app.unified`.
- `retro-module/`: fuentes, integración, emulación y serializador de guardados
  compartido con Windows.
- `catalogs/`: definiciones de formatos y catálogo.
- `tools/`: verificaciones no destructivas y pruebas de seguridad.
- `.github/workflows/`: compilaciones y QA de Windows y Android.

## Pruebas, sin instalaciones destructivas

- Windows: consultar el workflow `okubi-desktop.yml`; crea un paquete
  de pruebas con runtime Java, verifica integridad y ejecuta regresiones.
- Android: consultar `okubi-android-migrated-qa.yml`; genera únicamente
  una variante QA paralela `bo.nexoplay.app.unified.qa57`, sin tocar la
  instalación canónica ni firmar con certificados privados.
- Fase 78: `okubi-phase78-integration-gate.yml` agrega auditorías de
  seguridad, fuentes, identidad y pruebas sintéticas Cross Save en Windows
  y Ubuntu.

Para ejecutar el control de fuentes (Python 3.12, en una copia Git):

```bash
python -m unittest discover -s tools/tests -p 'test_okubi_phase78_integration_gate.py' -v
python tools/okubi_phase78_integration_gate.py --root . --report build/phase78/integration-gate.json
```

Un `PASS` indica que la fuente supera el control estático, **no** que
Android se pueda actualizar sin la firma anterior, que todas las consolas
sean compatibles o que las licencias de los núcleos estén listas para distribuir.

## Datos personales y limitaciones

No se almacenan ROMs comerciales, BIOS, saves, cuentas, keystores o credenciales
en este repositorio. Las capturas oficiales se enlazan bajo identidades de
proyecto concretas; un port sin captura verificable usa fondo neutro.
Las imágenes personalizadas del usuario tienen prioridad.

Antes de publicar es indispensable revisar el código fuente correspondiente a
las DLL Libretro, cumplir avisos/licencias, comprobar la firma Android del
propietario y probar físicamente ambos dispositivos con respaldo.
No se recomienda desinstalar la aplicación anterior para instalar una prueba.

**VIDAS es otro proyecto:** no tocar ni mezclar su código. El repositorio
privado de origen tampoco se modifica.
