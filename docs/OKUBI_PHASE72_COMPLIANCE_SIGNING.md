# OKUBI 遊 — Fase 72: licencias, fuentes exactas y firma Android

**Estado: revisión técnica automática; publicación estable BLOQUEADA.**
Este documento no reemplaza asesoría jurídica ni confirma cumplimiento de licencias por sí mismo.

## Núcleos incluidos

| Núcleo | Plataforma | Licencia conocida | Procedencia actual | Estado para distribución pública |
| --- | --- | --- | --- | --- |
| SameBoy | Android | MIT | Fuente Git fijado en `retro-module/build_sameboy.py` | Revisar atribución y avisos |
| mGBA | Android | MPL-2.0 | Fuente Git fijado en `retro-module/build_mgba.py` | Revisar obligaciones de fuente y modificaciones |
| Beetle Supafaust | Android | GPL-2.0-or-later | Fuente Git fijado y parche Bionic en `retro-module/build_supafaust.py` | Preparar código fuente correspondiente, parche y compilación |
| SameBoy / mGBA / Supafaust | Windows | MIT / MPL-2.0 / GPL-2.0+ respectivamente | DLL desde `buildbot.libretro.com/nightly/windows/x86_64/latest/`, con SHA-256 de ZIP autorizados | **BLOQUEADO:** falta vincular cada binario exacto al código fuente correspondiente |

Referencias oficiales de licencias:
- https://docs.libretro.com/development/licenses/
- https://docs.libretro.com/library/mgba/
- https://docs.libretro.com/library/mednafen_supafaust/
- https://www.gnu.org/licenses/gpl-faq.en.html

### Qué prueba Fase 72

- Coincidencia de los SHA Git de los tres constructores Android con los enlaces de avisos utilizados en el paquete Windows.
- Presencia de checkout por revisión exacta y manejo de licencias en Android.
- Verificación SHA-256 de los DLL Windows y validación del ejecutable empaquetado.
- Existencia de `okubi_release_guard.py`, que rechaza certificados diferentes, versiones que no aumentan y releases sin firma fijada.
- Variante de prueba Android `.qa57` aislada; sin utilizar ni regenerar la clave de firma normal.

**Lo anterior no prueba que la revisión Git de Android sea la que produjo los DLL Windows del buildbot.** Su directorio `latest` cambia con el tiempo. Los SHA en el repositorio son un control de integridad, no una certificación de procedencia de código fuente. No afirmar que las obligaciones GPL/MPL están cumplidas hasta disponer del código exacto correspondiente a los binarios efectivamente distribuidos (y su receta y parches aplicables).

### Firma Android: decisión no destructiva

1. Mantener la versión normal `bo.nexoplay.app.unified` instalada y conservar una copia privada de sus datos. La APK de pruebas `.qa57` tiene una identidad distinta y firma debug efímera.
2. **NO crear una nueva keystore para actualizar una app que ya tiene instalada una firma de propietario anterior.** Primero identificar el certificado del APK canónico instalado o la APK de distribución anterior, y localizar su keystore existente en privado.
3. Comprobar **localmente** ambas APK con `apksigner verify --print-certs --verbose archivo.apk` (Android SDK Build Tools), sin publicar APK originales. Para una actualización sin desinstalación: misma identidad de paquete, mismo certificado firmante compatible y `versionCode` superior.
4. Si no está la clave anterior, NO pedir desinstalar ni reemplazar los datos del usuario. Mantener variante independiente mientras se decide una migración respaldada.
5. No subir `.jks`, `.p12`, contraseñas, carpetas de datos o partidas a GitHub ni compartir claves por chat. El script `create_okubi_release_key.ps1` es solo para crear una identidad **nueva**, no para actualizar una identidad firmada ya existente.

### Puertas restantes

- [x] Android QA57 probado en Samsung Galaxy A05s: 10/10 reportado por el usuario
- [x] Windows Fase69 probado en PC: 8/8 reportado por el usuario
- [x] Cross Save sintético con CI dual en Windows/Linux: [run 42875325617](https://github.com/Cobybm/okubi-windows/actions/runs/42875325617)
- [ ] Cross Save **físico**, Android QA57 ↔ Windows aislado, con archivo de prueba propio
- [ ] Paquete de fuentes y licencias correcto para cada DLL Windows concreta
- [ ] Revisión de avisos, correspondencia de fuente y redistribución Android/Windows
- [ ] Certificado de actualización de APK canónica verificado por el propietario
- [ ] Aprobación expresa del merge en orden y revisión final del repositorio público

**No hay autorización para release estable ni para fusionar**; `public_release_authorized=false`. El repositorio privado original y los proyectos VIDAS/SigmaOS permanecen intactos.
