# OKUBI 遊 — Fase 81: fuentes correspondientes para Windows (laboratorio)

**Estado inicial:** experimento de compilación desde código fuente. No fusionado, no es una distribución estable.

## Objetivo prioritario

Sustituir los binarios Libretro de terceros descargados desde una URL móvil
`nightly/latest` —cuya revisión exacta no se puede reconstruir del SHA-256— por
**DLL creadas dentro del proyecto desde revisiones Git concretas**, con código
fuente completo, parches, receta, avisos de licencia y comprobación real de API.

Esto evita afirmar que los commits fijados para Android también son los que
produjeron las DLL Windows: son cadenas de suministro independientes.

## Primera prueba: Supafaust, SNES (GPL-2.0 o posterior)

- Repositorio: `https://github.com/libretro/supafaust.git`
- Commit: `642d1d1b6684aa7e306a02a89885f3f5456a5157`
- Sistema: Windows x64 + MSYS2 MinGW64
- Compilación: `make clean platform=win && make -j2 platform=win CC=gcc CXX='g++ -static'`
- Controles: `git rev-parse HEAD` debe coincidir con el commit, `COPYING` presente,
  DLL Windows PE x64, símbolos `retro_*`, DLL cargable con API Libretro v1.
- Archivo de prueba: DLL, `supafaust-corresponding-source.tar.gz` producido
  desde **ese mismo commit**, licencia, receta y SHA256SUMS **juntos**.
- Workflow: `.github/workflows/okubi-phase81-source-windows.yml` (QA, no release).
- Script: `tools/phase81_build_windows_supafaust.sh`.
- ABI: `tools/phase81_core_abi_probe.c`.

Los resultados de GitHub Actions deben quedar en **SUCCESS** antes de considerar
este experimento validado. Un fallo no afecta el proyecto Windows de Fase 80.

## Próximos pasos de esta misma fase

1. Construir y verificar el Supafaust fuente-nativa Windows x64 y asegurar que
   no necesite DLL adicionales sin declarar. Si aparecen, incluir sus fuentes/licencias
   o construirlas de forma estática.
2. Repetir en SameBoy (MIT), fijando revisión y herramientas.
3. Repetir en mGBA (MPL-2.0), fijando revisión y modificaciones.
4. Revisar condiciones y obligaciones reales de MIT/MPL/GPL del conjunto.
5. Cambiar la compilación QA Windows para usar estas DLL solo después de superar
   pruebas ABI, emulación, guardados y preservación. Mantener el modo de QA previo
   disponible durante la transición.
6. Verificar firma original de la aplicación Android **localmente** y probar
   ambos dispositivos con guardados de ensayo y copia de seguridad.

**Bloqueos para OKUBI 1.0 estable:** licencias/fuentes de las tres DLL Windows,
firma Android original, pruebas físicas, y autorización expresa para fusionar
y publicar. La Fase 80 aprobada proporciona el ZIP y APK de **pruebas**, no un
certificado legal para distribuir como versión estable.

**Separación estricta:** no tocar repositorio original privado,
VIDAS, instalaciones de Android, ROMs, saves, favoritos o certificados.

## Primer hallazgo de QA (14/11/2026)

La compilación inicial desde el commit exacto de Supafaust se completó en el workflow
[43613155581](https://github.com/Cobybm/okubi-windows/actions/runs/43613155581),
pero falló la prueba de carga del DLL en Windows (error de sistema **126**,
posible dependencia de runtime MinGW no empaquetada). La rama experimental
ahora exige enlace estático del runtime y registra las DLL importadas antes de
aceptar el resultado. No se han sustituido los binarios de la Fase 80.
