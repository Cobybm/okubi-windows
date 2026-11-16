# OKUBI 遊 · Fase 78 — Candidato integrado Windows + Android

**Estado:** rama de integración preparada para revisión, NO fusionada, NO publicada, NO estable.

## ¿Qué se integró en esta rama?

La rama `integration/okubi-phase78-release-candidate` nació directamente de
`feat/okubi-phase77i-xash3d-verified-artwork`. Esta última desciende linealmente
de la migración Windows/Android y del conjunto de 19 PR apilados sobre `main`.

En la inspección inicial (14 de noviembre de 2026):
- `main` seguía en `e852aa066354c7c69ee65832bf4204ecbaa2d348`, con solo su README.
- La Fase 77I tenía SHA `de2d321f7b3ac947721ee7aa34a15d85bbd4ae68`.
- GitHub Compare: 127 commits por delante de `main`, 0 por detrás.
- Fase 77I Windows x64: Actions `42915446478` SUCCESS, pero eso no certifica Android ni licencias.
- Migración Android QA57: Actions `42867125796` SUCCESS sobre rama de migración.
- Cross Save sintético entre plataformas: Actions `42875325617` SUCCESS en su rama original.

No se han reimplementado las fases ni copiado archivos desde el repositorio privado:
esta rama parte del árbol integrado existente. Ningún PR anterior fue fusionado
durante la Fase 78.

## Tres controles técnicos agregados

1. **Auditoría agregada offline:** `tools/okubi_phase78_integration_gate.py` llama
   a las auditorías ya existentes de Fase 70 (archivos, paquete, credenciales),
   Fase 72 (fuente/licencias/firma) y auditoría unificada (Windows, Android,
   guardados). Emite JSON sin rutas privadas ni contenido personal. El resultado
   puede ser PASS para **fuentes listas para revisión**, pero
   `public_release_authorized` siempre es **false**.
2. **Cross Save sintético obligatorio:** workflow
   `.github/workflows/okubi-phase78-integration-gate.yml` prueba el
   serializador Android compartido y la importación/exportación Windows en
   Ubuntu y Windows, usando datos de prueba. No abre partidas reales.
3. **QA Android dentro de la propuesta de integración:** el workflow
   `okubi-android-migrated-qa.yml` ahora se ejecuta también contra PR hacia
   `main` o cambios relevantes en esta rama. Genera una **APK QA57 paralela**
   (`bo.nexoplay.app.unified.qa57`), nunca reemplaza una APK canónica existente.
   No se cambia el ID, versión, firma o código Android para conseguir esto.

La CI de escritorio ya cubre PR con cambios de `desktop/` y ensambla EXE/ZIP
Windows aislado. Estas comprobaciones pueden fallar por compilación, origen o
infraestructura: revisar el último commit, no reutilizar un SUCCESS antiguo.

## Qué se conserva intocado

- Directorios Android originales del usuario, APK canónica y certificado previo.
- ROMs, juegos comerciales, BIOS, partidas SRAM/RTC/estados y archivos de saves.
- Favoritos, carátulas manuales, estadísticas, configuraciones y caché de las
  instalaciones existentes; esta rama solo edita fuente y QA.
- Proyecto VIDAS y repositorio `barrioseduardoe-ship-it/nexo-play.` (privado).
- No se ejecutan instaladores, no se firman APK de propietario, no hay merge.

## Bloqueos obligatorios antes de la versión 1.0 estable

- [ ] Fase 78: Windows x64 CI y APK QA57 CI **SUCCESS** en commit final.
- [ ] Fase 78: auditoría offline + Cross Save sintético Windows y Ubuntu
      **SUCCESS** en commit final.
- [ ] Validar en PC real la nueva vista CINEMA, capturas, emuladores incluidos,
      mandos, cierre/reanudación y respaldo sin pérdida de datos.
- [ ] Probar en A05s el APK QA57 de esta revisión sin reemplazar la app anterior.
- [ ] Probar Cross Save físico Android ↔ PC con un guardado **de prueba** y
      copias locales previas; los estados rápidos no son portables por defecto.
- [ ] Correlacionar **cada** DLL Windows Libretro concreta con fuente, parches,
      licencia, atribuciones, recetas y avisos reproducibles (el directorio
      `nightly/latest` no certifica procedencia); resolver obligaciones GPL/MPL.
- [ ] Verificar localmente el certificado Android canónico existente. Sin la misma
      clave, mantener paquete paralelo; jamás pedir borrar la instalación anterior.
- [ ] Revisión final del PR, aprobación expresa para fusionar a `main` y
      decisión explícita de publicar artefactos distribuidos.

## Estrategia de revisión

La propuesta Fase 78 compara la rama completa contra `main`, en **un PR borrador
de integración**. Los 19 PR originales quedan visibles como historial técnico
hasta que se revise y autorice la consolidación. No aprobarlos ni fusionarlos
automáticamente; evitar así desplegar una aplicación incompleta por fusionar
solo el primer tramo del stack.

**La meta es una versión 1.0 estable y segura, no 100 % de capturas de ports
ni emulación PS2/PS3 universal.** Los títulos no verificados conservan el fondo
CINEMA y la opción de imagen manual.

## Incidencia detectada y reparada en Fase 78 (14 de noviembre de 2026)

El primer trabajo Windows del PR de integración (#20), Actions
[43604720738](https://github.com/Cobybm/okubi-windows/actions/runs/43604720738),
**compiló Java/JNI y pasó las regresiones**, pero detuvo el empaquetado:
el servidor mutable `nightly/latest` de Libretro ofrecía nuevas huellas ZIP
para SameBoy, mGBA y Supafaust. El rechazo SHA-256 fue el comportamiento
seguro esperado. No se desactivó la verificación.

Para confirmar los archivos, se ejecutó la auditoría cruzada
[43604912900](https://github.com/Cobybm/okubi-windows/actions/runs/43604912900)
con dos runners independientes (Ubuntu y Windows). Ambos obtuvieron bytes
idénticos, verificaron ZIP CRC, único miembro DLL, tamaño y PE Windows x64:

| Core Windows | SHA-256 ZIP nuevo, comprobado independientemente |
| --- | --- |
| SameBoy | `02d848a55db54c1760d9cdff53941c41473e47646711bc8a0d7b6f2130f2b447` |
| mGBA | `0a98416e81417da065a5f0f2fa526f5f11baeb0a537063bbe5b02908fde92d87` |
| Supafaust | `3796e4f56291e08d3d966a1aceb32e41af88eb280e094f61cc8a43cd41c0aced` |

Solo esas tres huellas adicionales se autorizaron en
`desktop/build-bundled-cores.ps1`; se conservan las huellas anteriores y
**cualquier otra huella sigue fallando cerrado**. Es obligatorio confirmar
el resultado del **nuevo** empaquetado sobre el commit final.

**No resuelto:** estas pruebas certifican integridad y consistencia de los
archivos descargados, NO el código fuente exacto ni sus obligaciones GPL/MPL
en una publicación redistribuible. La Fase 79 debe resolver la procedencia
de fuente de cada DLL concreta o reemplazarlas por binarios reproducibles.
