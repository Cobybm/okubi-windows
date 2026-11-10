# OKUBI 遊 — Windows + Android (migración)

Repositorio independiente para las versiones **Windows y Android** de OKUBI 遊.\n\n> Android se incorpora en la rama de migración y todavía no está fusionado a `main`.

## Estado de la migración

Esta copia migra de forma selectiva el código de la rama de desarrollo **Fase 69 SMART LIBRARY**. Incluye el módulo `desktop/`, sus pruebas, documentación de las Fases 67–69, el serializador común `.okubisave` y CI Java de Fase 69.

**Estado:** prototipo en validación. No se afirma que el paquete EXE/ZIP esté verificado o probado en una PC real.

## Qué NO contiene

No se incluyen ROMs comerciales, BIOS, firmware, partidas, claves de firma, credenciales, carpetas de usuario, archivos de emuladores externos, binarios de compilación ni los archivos personales de Android. No se importa el historial de commits privado.

## Pruebas Java (JDK 21)

```bash
mkdir -p build/phase69/classes build/phase69/tests
find desktop/src -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d build/phase69/classes retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java
javac -encoding UTF-8 -cp build/phase69/classes -d build/phase69/tests desktop/tests/bo/okubi/desktop/DesktopLibraryBatchTest.java desktop/tests/bo/okubi/desktop/DesktopSearchRefreshTest.java desktop/tests/bo/okubi/desktop/DesktopMaintenanceServiceTest.java
java -ea -cp build/phase69/classes:build/phase69/tests bo.okubi.desktop.DesktopLibraryBatchTest
java -ea -cp build/phase69/classes:build/phase69/tests bo.okubi.desktop.DesktopSearchRefreshTest
java -ea -cp build/phase69/classes:build/phase69/tests bo.okubi.desktop.DesktopMaintenanceServiceTest
```

En Windows, para el empaquetado completo, consulta `desktop/BUILD_LOCAL_WINDOWS_PHASE65.ps1` y los requisitos de JDK 21, compilador MSVC x64 y licencias de núcleos Libretro.

## Publicación segura

Antes de una versión estable: confirmar compilación, probar mandos y navegación, verificar guardados con datos sintéticos, revisar licencias y ejecutar pruebas en Windows real. No subir juegos, claves ni archivos personales. Si un archivo secreto se publicó alguna vez, ignorarlo después no elimina su historial: hay que rotar la credencial y limpiar el historial.

La rama de migración está separada de `main` hasta su revisión.
\n## Android\n\nEl código fuente de Android está en `android-app/`; la integración retro en `retro-module/`. La compilación de pruebas se ejecuta con `.github/workflows/okubi-android-migrated-qa.yml` y mantiene un identificador de paquete paralelo para no sustituir tu instalación original. Consulta `docs/OKUBI_ANDROID_MIGRATION_COBYBM.md`.\n