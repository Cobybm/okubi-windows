# OKUBI 遊 — Fase 83 · Kit de pruebas físicas no destructivas

**Estado: PR de pruebas. La versión estable 1.0 aún NO está autorizada.**

Esta fase transforma las comprobaciones automáticas aprobadas de la Fase 82
en procedimientos que se pueden ejecutar **en el PC y Android del propietario**
sin entregar ROMs, partidas, certificados privados ni cuentas a GitHub.

## 1. Windows: verificar y probar en un entorno nuevo

Fuente de los archivos oficiales de QA: [GitHub Actions, Fase 81B, run 43615378972](https://github.com/Cobybm/okubi-windows/actions/runs/43615378972).
En la sección **Artifacts** descargar
`OKUBI-PHASE81B-WINDOWS-SOURCE-BUILT-QA-NOT-STABLE`
(retención original hasta **19/11/2026**). Extraer **solamente el ZIP
exterior descargado de GitHub**. Dentro están el ZIP de la aplicación,
`SHA256SUMS.txt` y el reporte del candidato.

El script de verificación está en
`tools/okubi_phase83_windows_local_qa.ps1`.

Desde PowerShell, con la carpeta del repositorio abierta, ejecuta
**primero la comprobación de solo lectura** (ajusta rutas):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\okubi_phase83_windows_local_qa.ps1 -Archive ".\descargas\OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip" -Checksum ".\descargas\SHA256SUMS.txt" -Report ".\reporte-qa83-windows.json"
```

Para realizar una **prueba física aislada**, con un juego **propio y nuevo
de prueba**, agrega `-Launch`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\okubi_phase83_windows_local_qa.ps1 -Archive ".\descargas\OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip" -Checksum ".\descargas\SHA256SUMS.txt" -Launch
```

El script valida el SHA-256 y la estructura del ZIP antes de extraerlo.
Al seleccionar `-Launch` crea un **nuevo** directorio aleatorio bajo el
directorio temporal de Windows, con un `LOCALAPPDATA` diferente, y ejecuta
`RUN-QA57-ISOLATED.cmd`. El lanzador usa a su vez un `APPDATA`
separado; por diseño no necesita leer la ubicación de las partidas normales.
No desinstala, migra, borra ni restaura nada. El directorio de pruebas se
conserva para evitar borrar accidentalmente los guardados generados en QA.

**Qué probar manualmente en PC:**

- [ ] Inicio / Juegos / Descubre, búsqueda, CINEMA y carátulas correctas.
- [ ] Pantalla completa, resolución y botones con teclado/mando.
- [ ] Probar con una copia propia de prueba GB, GBC, GBA y SNES: imagen y audio.
- [ ] Guardar una partida **nueva** de prueba, cerrar, abrir y recuperar.
- [ ] Confirmar que los favoritos y partidas de tu OKUBI anterior siguen iguales.

El reporte JSON de la ejecución solo marca **integridad automática y
si hubo lanzamiento**, no inventa resultados de juego real ni rutas privadas.
No es una firma del propietario.

## 2. Android: comprobar firma sin instalar

Descargar APK QA57 desde [Fase 80 Android, run 43612388104](https://github.com/Cobybm/okubi-windows/actions/runs/43612388104), artefacto
`OKUBI-Android-Migration-QA57-Side-by-Side` (retención original hasta
**28/11/2026**). Su applicationId
`bo.nexoplay.app.unified.qa57` es **distinto** del paquete existente
`bo.nexoplay.app.unified`.

En una PC con Python 3 y Android SDK build-tools (`aapt` y
`apksigner`), ejecuta sin instalar:

```powershell
python .\tools\okubi_phase83_android_local_preflight.py --qa-apk ".\descargas\OKUBI-Android-Migration-QA57.apk" --report ".\reporte-qa83-android.json"
```

Si `aapt` y `apksigner` no están en PATH, indicar rutas con `--aapt`
y `--apksigner`.

Si **ya tienes una copia privada de la APK canónica anterior**, añade
`--original-apk "ruta\OKUBI-original.apk"`. Esto observa la identidad
pública del certificado anterior pero **no demuestra** que exista aún una
actualización canónica compatible. Cuando finalmente haya una APK canónica
nueva, añadir también `--candidate-update-apk "ruta\OKUBI-nuevo.apk"`.
El script comprueba el identificador del paquete, firma y
`versionCode` creciente mediante el guard anterior del proyecto.
Un APK QA57 nunca se acepta como actualización de la aplicación normal.

**Qué probar manualmente en A05s:**

- [ ] Solo si se puede instalar en paralelo **sin perder datos**, abrir la QA.
- [ ] GB, GBC, GBA y SNES con juegos propios de ensayo, sonido y reanudación.
- [ ] Cruceta multitáctil, A/B/Start/Select/L/R, giros y transparencia.
- [ ] Nueva partida de prueba y reapertura.
- [ ] Confirmar que la app canónica y sus partidas siguen presentes.
- [ ] Guardado nuevo de prueba Android → Windows → Android y respaldo previo
  para Cross Save físico. No asumir compatibilidad de save states.

**Advertencia:** un APK de pruebas con certificado debug diferente puede
rechazar la actualización de otra QA57 anterior. **No desinstales ni borres
datos para forzarlo**. El script de firmas no abre Android ni extrae
aplicaciones del teléfono: necesita archivos APK que ya tengas de forma legítima.

## 3. Qué se conserva y qué sigue pendiente

- Los informes **no contienen** rutas personales, ROMs, partidas,
  huellas públicas de certificado ni contraseñas. Nada se transmite
  automáticamente a GitHub.
- No se modifica el repositorio original ni VIDAS.
- La CI puede comprobar estos scripts con archivos públicos y datos
  sintéticos. **No puede hacer la prueba física** de tu PC, mando o Samsung.
- El cierre de OKUBI 1.0 sigue requiriendo pruebas físicas, Cross Save,
  firma canónica compatible, auditoría completa de licencias y aprobación
  expresa del propietario. Ni una firma igual ni una CI verde equivalen
  automáticamente a permiso de publicación.

## Política de GitHub

La Fase 83 permanece en PR draft. El instalador previo y los ZIP/APK
aprobados permanecen intactos. No se fusiona a `main`, no se publican
ROMs/BIOS y no se inicia una release estable.
