# OKUBI 遊 — Fase 81B: candidato Windows aislado con fuentes incluidas

**Estado:** experimental, solo para QA. No sustituye la Fase 80 ni autoriza una distribución pública.

## Alcance técnico

Se construyen desde revisiones exactas de sus repositorios los tres núcleos
Libretro de Windows x64, con sus pruebas de carga y API:

| Emulador | Consolas | Commit fuente |
| --- | --- | --- |
| SameBoy (MIT) | GB / GBC | `aa158a889a48b538a0302873704a34577c8eb67d` |
| mGBA (MPL-2.0) | GBA | `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` |
| Supafaust (GPL-2.0 o posterior) | SNES | `642d1d1b6684aa7e306a02a89885f3f5456a5157` |

Las compilaciones aisladas pasaron en GitHub: SameBoy y mGBA en
[43614000789](https://github.com/Cobybm/okubi-windows/actions/runs/43614000789);
Supafaust en [43614000765](https://github.com/Cobybm/okubi-windows/actions/runs/43614000765).

## Candidato 81B

Workflow: `.github/workflows/okubi-phase81b-integrated-source-qa.yml`.

1. Obtiene una copia intacta del ZIP de prueba Fase 80 de
   [43612388002](https://github.com/Cobybm/okubi-windows/actions/runs/43612388002).
2. **Verifica** su SHA-256, su estructura, sus tres DLL antiguas y los
   metadatos previamente auditados. Si no coincide, aborta sin crear un ZIP.
3. En el mismo runner Windows, compila **de nuevo** SameBoy, mGBA y Supafaust
   desde sus fuentes exactas y conserva los archivos fuente/licencia/receta.
4. `tools/okubi_phase81b_source_bundle.py` crea **otra copia de ZIP** con
   las tres DLL nuevas y un manifiesto de procedencia. Compara los hashes de
   todos los demás archivos de la aplicación y aborta si se modificaron.
5. Comprueba por separado las DLL incluidas en el ZIP final con la prueba
   nativa de Windows y API Libretro v1, sin ROMs ni datos personales.
6. Genera el artefacto temporal:
   `OKUBI-PHASE81B-WINDOWS-SOURCE-BUILT-QA-NOT-STABLE`, con ZIP, SHA256SUMS
   y reporte de integridad. **Solo está aprobado si todos los pasos de CI
   terminan con SUCCESS.**

Dentro del ZIP, el directorio
`OKUBI Desktop/app/cores/source-packages/` contiene los archivos
`*-corresponding-source.tar.gz`, los scripts exactos de compilación y un
manifiesto JSON de relación DLL → fuente. Los avisos se encuentran en
`app/cores/licenses/`.

El archivo `OKUBI Desktop/app/PHASE81-SOURCE-BUILT-QA.txt` explica que la
compilación solo sirve para pruebas y cómo usar
`RUN-QA57-ISOLATED.cmd`, sin migrar datos ni reemplazar la aplicación.

## Precauciones que se mantienen

- El ZIP original de la Fase 80 no se toca; se verifica de nuevo antes de
  producir el candidato derivado. El artefacto antiguo tiene retención limitada,
  por lo que un trabajo posterior debe producir su propio baseline verificable.
- No se modifica código Android, firma, paquete canónico, juegos, ROMs,
  BIOS, guardados, favoritos, imágenes propias ni proyecto VIDAS.
- Se documenta la fuente exacta de las tres DLL y su receta, pero
  **todavía falta** una revisión integral de licencias y obligaciones de
  redistribución que abarque toda la aplicación, dependencias y avisos.
- Los análisis automáticos no sustituyen pruebas físicas de guardados SRAM/RTC
  y rendimiento en PC real; no prueban PS2/PS3.
- Para actualizar Android en el futuro, hay que verificar localmente la
  firma **anterior** y conservar la ruta de actualización sin pérdida de
  partidas; la variante QA57 no es una actualización de producción.
- `main` y los PR anteriores permanecen sin fusionar hasta la decisión de
  publicación final.

La **Fase 81B no equivale todavía a OKUBI 1.0 estable**. Sirve para probar
por primera vez la aplicación Windows completa con tres núcleos cuya fuente
y ruta de compilación son auditablemente vinculables al binario.
