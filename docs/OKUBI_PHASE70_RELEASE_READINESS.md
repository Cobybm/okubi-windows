# OKUBI 遊 — Fase 70 / Preparación de publicación segura

**Estado:** trabajo de validación sobre una rama nueva. NO fusionado y NO estable.

## Lo que verifica automáticamente

- No se añaden ROMs, BIOS, partidas, estados, APK, EXE, DLL, keystores, archivos de entorno o rutas de datos personales dentro del árbol Git.
- Búsqueda heurística de formatos conocidos de credenciales en el texto versionado. Esto no reemplaza una revisión de seguridad externa.
- Se conserva el paquete Android \`bo.nexoplay.app.unified\`, con prueba paralela \`.qa57\`, versión y SDK mínimo, firma del propietario independiente y \`allowBackup=false\`.
- Se siguen verificando el APK QA con apksigner/SHA256, el ejecutable Windows con \`--verify-windows-release\`, el ZIP y el lanzador de pruebas con datos aislados.
- No se habilitan flujos privilegiados \`pull_request_target\` sobre los workflows activos de compilación.

## Lo que NO puede certificar automáticamente

1. La compatibilidad general con otros teléfonos/PC, consolas no probadas, rendimiento medido o juegos concretos más allá de los que el propietario comprobó en A05s y Windows.
2. Que la firma de una nueva APK puede actualizar tu instalación Android existente. Sin el mismo certificado, usa la variante paralela y NO desinstales la aplicación normal.
3. Que puede redistribuirse el conjunto de núcleos Libretro sin revisar las obligaciones exactas MPL/GPL y la fuente correspondiente.
4. Que la versión está lista para llamarse pública, estable o para fusionarse a \`main\`.

Las pruebas automatizadas NO modifican tus partidas: trabajan con el código y datos sintéticos. El reporte deja explícitamente \`public_release_authorized=false\`.

## Orden recomendado

Primero completar la auditoría automatizada de Fase 70. Después probar ambos paquetes aislados en dispositivos reales y conservar una copia externa de los datos de cada instalación. Tras revisar licencias y firma, evaluar el PR #1 (Windows), luego el PR #2 (Android) y por último este PR, sin fusionarlos automáticamente.

**Repositorio original privado:** no se modifica. **VIDAS/SigmaOS:** no se tocan.


## QA manual Android — Samsung Galaxy A05s (10 de noviembre de 2026)

**Resultado reportado por el usuario: 10/10 comprobaciones aprobadas**, sobre la APK aislada **OKUBI Android QA57**. Este resultado es una prueba en un dispositivo físico realizada por el propietario; no es un resultado medido por GitHub Actions ni una certificación universal de compatibilidad.

| Verificación | Resultado informado |
| --- | --- |
| Instalación APK sin error de paquete | OK |
| Inicio de OKUBI QA57 sin cierre inesperado | OK |
| Inicio, Juegos y Descubre | OK |
| Juego GBA con imagen y sonido | OK |
| Al menos una prueba de GB/GBC o SNES | OK (el reporte no especifica cuál) |
| Cruceta sin direcciones fantasma | OK |
| A, B, Start, Select, L y R | OK |
| Rotación vertical y horizontal | OK |
| Pantalla completa y transparencia | OK |
| Guardar y recuperar una partida **nueva de prueba** | OK |

**No se reportaron fallos Android que requieran modificar código.** Se conserva el snapshot funcional y la instalación canónica anterior; la firma efímera de QA57 no se considera una firma estable.

### Pendientes tras esta validación

- [x] QA funcional de Android en Samsung Galaxy A05s (10 controles informados por el propietario).
- [x] QA funcional real de Windows en la PC — 8/8 controles aprobados según el reporte del propietario, sin mediciones independientes de FPS ni pruebas de todos los sistemas.
- [ ] Comprobar específicamente todas las consolas por separado y cambios de aplicación/pausa, si se desea cobertura completa.
- [ ] Verificar transferencia Android ↔ Windows de SRAM/RTC con archivos **sintéticos** y copia de seguridad; no asumir compatibilidad de estados rápidos.
- [ ] Revisar el cumplimiento de licencias y fuente correspondiente de Libretro antes de publicar una release de distribución.
- [ ] Validar certificado y estrategia de firma permanente Android sin reemplazar ni desinstalar la instalación existente.
- [ ] Revisar PR #1, #2 y #3 y obtener aprobación expresa antes de integrar o publicar.

El gate automatizado de Fase 70 puede seguir marcando las validaciones manuales como `pending`: no consume ni certifica esta declaración humana. `public_release_authorized=false` sigue siendo la política de publicación.

## QA manual Windows — PC del propietario (10 de noviembre de 2026)

**Resultado reportado: 8/8 comprobaciones aprobadas** en la compilación aislada **OKUBI Desktop Windows Fase 69, 1.0.13**. Este registro refleja una prueba de usuario en equipo físico; no es una medición de rendimiento de GitHub Actions.

| Prueba Windows | Resultado informado |
| --- | --- |
| Abre sin pantalla negra ni error | OK |
| Inicio, biblioteca y fichas responden | OK |
| Importa un juego de prueba de GB/GBC/GBA **o** SNES | OK (sin especificar la consola exacta) |
| El juego abre con imagen y sonido | OK |
| Teclado o mando sin botones fantasma | OK (no se indicó cuál periférico se utilizó) |
| Pantalla completa y regreso a ventana | OK |
| Guardado nuevo de prueba, cierre y recuperación | OK |
| SMART PC presenta información de hardware sin bloquearse | OK (no se infieren FPS, PS2/PS3 ni compatibilidad general) |

**Sin errores funcionales reportados para Windows.** No se justifican parches a los controles, a la biblioteca o al motor retro por esta batería de pruebas. La suite Windows existente ya terminó con SUCCESS en [GitHub Actions #42863459667](https://github.com/Cobybm/okubi-windows/actions/runs/42863459667); se conserva el estado candidato de la Fase 69.

### Estado combinado (Android y Windows)

| Dispositivo/plataforma | QA en hardware informada | CI | Incidencias informadas |
| --- | --- | --- | --- |
| Samsung Galaxy A05s / Android QA57 | 10/10 | SUCCESS, run #42867125796 | 0 |
| PC Windows / Fase 69, 1.0.13 | 8/8 | SUCCESS, run #42863459667 | 0 |

**Ambas baterías manuales aprobadas, pero no equivalen a una release estable.** Sigue pendiente comprobar Cross Save Android ↔ Windows con un archivo sintético y respaldos; verificar específicamente las consolas no probadas; revisar avisos y distribución GPL/MPL; confirmar firma Android del propietario si se quiere actualizar la app canónica; y aprobar integración de PR #1 → PR #2 → PR #3.

**Política:** no reemplazar instalaciones previas, no subir partidas al repositorio público y mantener `public_release_authorized=false` hasta superar las puertas pendientes.
