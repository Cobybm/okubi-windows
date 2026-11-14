# OKUBI 遊 — Fase 56 · RELEASE CANDIDATE 1 (RC1)

**Estado: candidato de pruebas, NO ESTABLE y NO PUBLICADO.**  No fusionar ramas ni `main` sin aprobación expresa y sin pruebas en dispositivos físicos.

## Alcance e identidades

| Plataforma | Versión / paquete | Qué significa |
|---|---|---|
| Windows x64 | **1.0.6 / RC1** · ZIP con aplicación portátil y Java privado | Candidato para instalación en una carpeta separada, sin sobrescribir carpetas anteriores |
| Android | **1.0.0-unified-beta17 / versionCode 36** · `bo.nexoplay.app.unified` | Última beta de Android. No cambiar `applicationId`, `versionCode` ni firma para aparentar un release |
| Repositorio de migración Windows | `Cobybm/okubi-windows` | Copia independiente; el repositorio privado de origen permanece intacto |

El RC1 **no** es una actualización automática Android y no incluye un instalador Windows `MSI`. GitHub Actions construye un archivo ZIP `app-image`. Ninguna compilación de PR puede generar un APK de producción firmado con clave efímera. El paso de firma del propietario solo puede ejecutarse **manualmente desde `main`** cuando existan todos los secretos y la huella SHA-256 pública fijada del certificado.

## Migración segura y recuperación

**Windows (prueba reversible sin pérdida de datos):**
1. Cierra el juego y termina Smart Save Guard, Cross Save y Cloud Save antes de cerrar la aplicación.
2. Haz una **copia EXTERNA** de `%APPDATA%\OKUBI` (biblioteca, favoritos, configuración, `saves`, `save-vault-v1`, portadas, estadísticas). Guarda aparte tus ROMs originales donde estén.
3. Extrae el ZIP RC1 en una carpeta NUEVA. No sustituyas la carpeta del ejecutable anterior. No borres ni cambies `%APPDATA%\OKUBI`.
4. Ejecuta `OKUBI Desktop.exe` desde la nueva carpeta. Comprueba que se ve la biblioteca, aparece la portada cacheada sin red, y abre ROMs de prueba con sus partidas.
5. Para volver atrás, cierra todos los juegos y operaciones, abre tu ejecutable anterior y comprueba tus datos. **Retroceder de versión no revierte automáticamente cambios ya hechos a las partidas**; restaura desde tu copia externa solo si resulta necesario y tras revisarla.

**Android (prueba sin reinstalación destructiva):**
1. **No desinstales OKUBI** para cambiar una APK de prueba: Android puede eliminar datos de la app al desinstalar.
2. Exporta manualmente las partidas y ajustes desde la versión instalada, conserva el archivo fuera del teléfono y verifica que se puede leer.
3. Compara el `applicationId`, `versionCode` **estrictamente superior** y **la misma huella SHA-256 de certificado** con el APK instalado. La herramienta `tools/okubi_release_guard.py` permite comparar dos APKs; no instala ni modifica nada.
4. Si los certificados no coinciden o no tienes la APK antigua, **no sobrescribas ni desinstales** la instalación existente. No hay una migración garantizada desde versiones con distinta identidad.
5. La app mantiene `android:allowBackup="false"`: no asumir que Google restaurará automáticamente los guardados.

**Cross Save:** únicamente SRAM/RTC portátiles de `.okubisave` con ROM SHA-256 idéntica; estados rápidos F5/F9 entre emuladores no son portátiles. SAVE VAULT solo restaura con confirmación y copia previa del guardado PC.

## Integridad de paquetes y licencias

- Windows incluye SameBoy (MIT), mGBA (MPL-2.0) y Beetle Supafaust (GPL-2.0-or-later) desde artefactos oficiales Libretro con ZIP SHA-256 fijado en CI. Debe contener los **tres** avisos íntegros de licencia, las URL de repositorios fuente, `CORE-SHA256.properties` y esta guía.
- Las obligaciones de licencias pueden incluir **oferta/acceso al código fuente correspondiente y avisos de modificaciones**. La presencia de un archivo LICENSE por sí sola **no acredita cumplimiento pleno**; antes de distribución pública se exige revisión de licencias, versiones de código fuente y buildbot.
- La compilación Windows publica un `SHA256SUMS` externo para verificar el ZIP descargado. `Get-FileHash` en PowerShell permite comparar el hash calculado con la línea del manifiesto.
- Android debe contener licencias mGBA y Supafaust más el manifiesto de procedencia; la identidad y firma se verifican mediante `aapt` y `apksigner`. El APK debug es solo para evaluación: no representa ni sustituye la firma del propietario.
- Ningún paquete debe incluir `.gba`, `.gbc`, `.gb`, `.srm`, `.rtc`, `.sav`, `.state`, `.okubisave`, firmware, BIOS, partidas ni ROMs personales. Las bibliotecas nativas/archivos de licencia no se eliminan de un paquete para reducir tamaño.

## Puertas para declarar versión estable

- [ ] CI **Windows RC1**: compilación Java/JNI, toda la suite de regresión, 3 núcleos verificados, app-image x64, hash SHA-256 y escaneo del ZIP sin datos personales.
- [ ] CI **Android beta17**: Python/JVM, ABIs nativas, APK verificada, licencias, versión y firma debug. Validar identidad del propietario en un proceso manual separado.
- [ ] Prueba real en Windows: pantalla 800×520 y normal, biblioteca, controles Xbox, GBA/GB/GBC/SNES, sonido y pantalla completa; cerrar con copia Smart Guard/Cloud/Cross activa debe bloquearse.
- [ ] Prueba real en Android A05s y, cuando sea posible, otro Android: controles táctiles, orientación, juegos, sonido, rendimiento y continuidad de partidas.
- [ ] Prueba Cross Save con ROM de mismo SHA-256 y **partidas de prueba**; rechazo de ROM distinta, tamaño diferente, copia corrupta y archivos duplicados.
- [ ] SAVE VAULT: copia externa previa, prueba de recuperar SRAM/RTC y recuperación de esa copia; nunca probar primero sobre la única partida valiosa.
- [ ] Validar en un APK anterior la **misma firma** y aumento de `versionCode`; conservar claves solo en Secrets, con certificado público fijado.
- [ ] Auditoría legal de licencia/código correspondiente, carátulas de proveedores externos, marcas y procedencia antes de distribución.
- [ ] Aprobación expresa del propietario para fusionar y publicar. **No dar por cumplida ninguna prueba física solo porque CI esté verde.**

## Separación de proyectos

OKUBI no debe importar archivos del proyecto **VIDAS**, alterar su repositorio o mezclar recursos ni ramas. Todos los tests de RC se ejecutan con datos sintéticos. No se han subido archivos de partidas reales, certificados privados ni credenciales.
