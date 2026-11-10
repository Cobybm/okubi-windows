# OKUBI 遊 — Windows · Fase 69

Repositorio independiente para la versión **Windows** de OKUBI 遊.

## Estado de la migración

Esta copia migra de forma selectiva el código de la rama de desarrollo **Fase 69 SMART LIBRARY**. Incluye el módulo `desktop/`, sus pruebas, documentación de las Fases 67–69, el serializador común `.okubisave` y CI Java de Fase 69.

**Estado:** prototipo en validación. No se afirma que el paquete EXE/ZIP esté verificado o probado en una PC real.

## Qué NO contiene

No se incluyen ROMs comerciales, BIOS, firmware, partidas, claves de firma, credenciales, carpetas de usuario, archivos de emuladores externos, binarios de compilación ni la aplicación Android completa. No se importa el historial de commits privado.

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
