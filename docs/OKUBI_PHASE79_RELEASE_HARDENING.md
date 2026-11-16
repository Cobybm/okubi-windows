# OKUBI 遊 — Fase 79 · Estabilidad, procedencia y bloqueo de release

**Estado: auditoría sobre PR apilado encima de Fase 78. NO fusionado, NO estable, NO publicado.**

## Base verificada (14 de noviembre de 2026)
La Fase 78 — commit `838228bb1fbf5bd6c7e54be56f138f84609a48ca` — concluyó con éxito en las siguientes comprobaciones del propio GitHub:
- [Windows x64](https://github.com/Cobybm/okubi-windows/actions/runs/43605014642): Java/JNI, regresiones, tres núcleos, validación Libretro ABI, empaquetado y ZIP; artifact QA hasta el 19/11/2026.
- [Android QA57](https://github.com/Cobybm/okubi-windows/actions/runs/43605014630): prueba retro y APK paralela `bo.nexoplay.app.unified.qa57`, sin firma canónica; artifact hasta el 28/11/2026.
- [Integración](https://github.com/Cobybm/okubi-windows/actions/runs/43605014667): auditoría de fuente/seguridad y Cross Save **sintético** en Windows y Ubuntu.

Estos éxitos **no** demuestran una instalación final, transferencia de partidas reales o permisos para distribuir las DLL.

## Aporte técnico de la Fase 79
1. `desktop/build-bundled-cores.ps1` mantiene rechazo cerrado de hashes ZIP no autorizados y, para los tres pares auditados el 14/11/2026, también **compara el SHA-256 de la DLL extraída**. No se aceptan automáticamente versiones nuevas del directorio móvil `nightly/latest`.
2. `tools/okubi_phase79_windows_core_evidence.json` registra exactamente las identidades, huellas ZIP, huellas DLL y la auditoría independiente [43604912900](https://github.com/Cobybm/okubi-windows/actions/runs/43604912900).
3. `tools/okubi_phase79_supply_chain.py` coteja ese registro con las huellas ejecutables que aplica PowerShell y exige la advertencia **procedencia de código fuente Windows no verificada**. Por diseño, `--require-public-release` devuelve error y no autoriza una publicación.
4. `.github/workflows/okubi-phase79-provenance.yml` ejecuta los tests del control en **Windows y Ubuntu**, sin descargar juegos ni abrir directorios personales.

### Lo que la auditoría sí y no prueba
- **Comprobado:** ambos runners independientes observaron archivos ZIP/DLL idénticos, con formato ZIP válido, CRC y PE x64. Las nuevas huellas fueron auditadas; los archivos de compilación no se mezclan con una URL de otra consola.
- **No comprobado:** la revisión Git exacta, receta de compilación, parches y código fuente correspondiente **de esos binarios específicos de Windows**. Los SHA Git de las compilaciones Android no prueban qué código construyó las DLL del buildbot.
- **Pendiente de revisión:** obligaciones de atribución, divulgación y distribución de código fuente para MIT (SameBoy), MPL-2.0 (mGBA) y GPL-2.0 o posterior (Supafaust), aplicables según la distribución concreta. Esta auditoría no es dictamen legal.

## Firma Android: comprobación local no destructiva
La aplicación canónica existente usa `bo.nexoplay.app.unified`; la APK QA usa `bo.nexoplay.app.unified.qa57` con datos independientes. **No instalar una APK con otra firma sobre el paquete existente ni borrar datos para lograrlo.**

Para estudiar una futura actualización canónica, ejecutar las herramientas de Android SDK **en la PC del propietario**, usando copias de APK anteriores y futuras, sin subirlas al repositorio:
```bash
apksigner verify --verbose --print-certs OKUBI-anterior.apk
apksigner verify --verbose --print-certs OKUBI-nuevo.apk
aapt dump badging OKUBI-anterior.apk
aapt dump badging OKUBI-nuevo.apk
```
La firma y el identificador del paquete de actualización deben ser compatibles y el `versionCode` nuevo debe ser superior. Existe el verificador de sólo lectura:
```bash
python tools/okubi_release_guard.py --kind release --apk OKUBI-nuevo.apk --previous-apk OKUBI-anterior.apk --pinned-sha256 HUELLA_PUBLICA_SHA256
```
`HUELLA_PUBLICA_SHA256` es una huella pública, **nunca la clave de firma**. La firma antigua real no se conoce desde GitHub, y la APK QA57 no puede usarla como sustituto. No crear claves nuevas para suplantar la identidad anterior.

## Pruebas físicas pendientes para cerrar 1.0
- [ ] En Windows, hacer copia privada de `%APPDATA%\OKUBI`, ejecutar ZIP de QA en directorio aislado y confirmar entrada a Inicio/Juegos/PORTS, visor CINEMA, mandos, controles, biblioteca y guardados sintéticos.
- [ ] En Samsung A05s, conservar app canónica y probar **QA57 en paralelo**, sin borrar la anterior: GB, GBC, GBA, SNES, orientación, sonido, botones y reanudación.
- [ ] Exportar una partida **recién creada para prueba**, transferir SRAM/RTC Android ↔ Windows y restaurar en instalación aislada. No asumir portabilidad de estados rápidos.
- [ ] Revisar obligaciones exactas de licencias y fuentes correspondientes de **todos** los ejecutables redistribuidos.
- [ ] Verificar el certificado de la APK canónica previa con una copia privada y documentar la estrategia de actualización.
- [ ] Obtener aprobación expresa para fusionar y una aprobación separada para publicar la versión estable.

## Protección
No cambia ubicación de partidas, aplicación Android, ajustes, preferencias ni archivos de usuario. No toca el repositorio privado original ni VIDAS. La Fase 79 sólo modifica PowerShell de compilación, auditoría automatizada, pruebas y documentación.

**Conclusión:** podemos terminar el QA integrado, pero no afirmar todavía que OKUBI 1.0 sea distribuible sin resolver los bloqueos anteriores.
