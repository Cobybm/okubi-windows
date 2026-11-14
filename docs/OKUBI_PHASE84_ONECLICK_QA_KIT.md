# OKUBI 遊 · Fase 84 — Kit de prueba listo para doble clic

**Objetivo:** reemplazar el procedimiento manual con PowerShell por un único
artefacto de GitHub Actions que contenga la versión Windows de pruebas, la
versión Android QA57 y los verificadores, sin publicarlo como OKUBI 1.0.

## Contenido del kit

Descargar en GitHub Actions el artefacto
`OKUBI-PHASE84-WINDOWS-ANDROID-ONE-CLICK-QA-NOT-STABLE`.
Al extraerlo se encuentran:

| Archivo | Función |
| --- | --- |
| `00-LEER-PRIMERO.txt` | Instrucciones y advertencias en lenguaje sencillo |
| `01-VERIFICAR-TODO.cmd` | Doble clic: examina ZIP Windows, archivo APK Android, SHA256 y modo de aislamiento |
| `02-PROBAR-WINDOWS-AISLADO.cmd` | Doble clic, verifica de nuevo, pregunta si deseas abrir y ejecuta Windows con nuevo perfil QA |
| `03-VERIFICAR-ANDROID.cmd` | Doble clic: valida SHA256 del APK y arquitectura de emuladores, sin instalar |
| `Windows/OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip` | ZIP Windows Fase81B con SameBoy, mGBA y Supafaust compilados de fuente |
| `Windows/SHA256SUMS.txt` | Suma de integridad Windows |
| `Android/OKUBI-Android-Migration-QA57.apk` | APK de pruebas independiente, no reemplaza `bo.nexoplay.app.unified` |
| `Android/SHA256SUMS.txt` | Suma de integridad Android |
| `tools/OKUBI-VERIFICAR-KIT.ps1` | Verificador local, solo lectura, sin privilegios |
| `tools/okubi_phase83_windows_local_qa.ps1` | Inspector y arranque Windows con sandbox nuevo |

### Garantías y límites

La CI obtiene **los artefactos existentes aprobados**, no recompila aplicaciones
ni modifica APK/ZIP. Verifica que ninguno tenga contenidos personales, que
coincidan los hashes oficiales y que el lanzador sea aislado. El kit únicamente
contiene archivos públicos de QA y scripts; no incluye partidas, ROMs, BIOS,
cuentas, certificados privados ni claves.

El comando **01** no extrae ni ejecuta aplicaciones. El comando **02**
abre la aplicación únicamente después de dos verificaciones y de una
confirmación interactiva. Crea un nuevo perfil de prueba distinto de la
instalación anterior; lo conserva después del cierre, sin borrar partidas.
El comando **03** no instala Android, no necesita ADB y no toca el teléfono.

**Importante:** el comando 03 únicamente verifica la **integridad del APK**.
La identidad de paquete y firma se validaron en CI con Android SDK durante
la Fase 82; no se realiza una firma criptográfica independiente mediante
`apksigner` en Windows. Para ello se puede usar la herramienta de Fase 83.

Si ya existe una APK QA57 con partidas y firma distinta, Android puede
rechazar la instalación de la nueva. **No desinstalarla para forzar la
instalación**, ni eliminar la app normal. La compatibilidad de firma para
actualización canónica sigue siendo un requisito pendiente.

### Pruebas físicas todavía por realizar

- [ ] Windows: Inicio/Juegos/Descubre, imágenes CINEMA, sonido, mandos y
  pantalla completa usando juegos propios de prueba
- [ ] Windows: partida de prueba guardada, cierre y recuperación sin
  alterar los datos previos
- [ ] Samsung A05s: orientación, cruceta multitáctil, botones y partida
  de prueba, conservando la app instalada
- [ ] Cross Save físico de ensayo con respaldos
- [ ] Certificado canónico de Android, `versionCode`, licencia/fuentes
  de la distribución completa y autorización explícita de publicación

**No hay fusión automática a `main`, ni actualización estable.**
Este kit es una herramienta de prueba para cerrar los bloqueos finales y
evitar regresiones antes de OKUBI 1.0.

## Origen verificado

- Windows Fase81B: [Actions 43615378972](https://github.com/Cobybm/okubi-windows/actions/runs/43615378972)
- Android QA57: [Actions 43612388104](https://github.com/Cobybm/okubi-windows/actions/runs/43612388104)
- Auditoría conjunta: [Actions 43616061339](https://github.com/Cobybm/okubi-windows/actions/runs/43616061339)
- Prueba local de los verificadores Fase83: [Actions 43617696877](https://github.com/Cobybm/okubi-windows/actions/runs/43617696877)
