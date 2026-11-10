# OKUBI 遊 — Fase 70 / Preparación de publicación segura

**Estado:** trabajo de validación sobre una rama nueva. NO fusionado y NO estable.

## Lo que verifica automáticamente

- No se añaden ROMs, BIOS, partidas, estados, APK, EXE, DLL, keystores, archivos de entorno o rutas de datos personales dentro del árbol Git.
- Búsqueda heurística de formatos conocidos de credenciales en el texto versionado. Esto no reemplaza una revisión de seguridad externa.
- Se conserva el paquete Android \`bo.nexoplay.app.unified\`, con prueba paralela \`.qa57\`, versión y SDK mínimo, firma del propietario independiente y \`allowBackup=false\`.
- Se siguen verificando el APK QA con apksigner/SHA256, el ejecutable Windows con \`--verify-windows-release\`, el ZIP y el lanzador de pruebas con datos aislados.
- No se habilitan flujos privilegiados \`pull_request_target\` sobre los workflows activos de compilación.

## Lo que NO puede certificar automáticamente

1. Que funcionan sonido, mandos, giros de pantalla, rendimiento o juegos concretos en el Samsung A05s y en tu PC Windows.
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
- [ ] QA funcional real de Windows en la PC (sonido, controles, interfaz y guardados con perfil aislado).
- [ ] Comprobar específicamente todas las consolas por separado y cambios de aplicación/pausa, si se desea cobertura completa.
- [ ] Verificar transferencia Android ↔ Windows de SRAM/RTC con archivos **sintéticos** y copia de seguridad; no asumir compatibilidad de estados rápidos.
- [ ] Revisar el cumplimiento de licencias y fuente correspondiente de Libretro antes de publicar una release de distribución.
- [ ] Validar certificado y estrategia de firma permanente Android sin reemplazar ni desinstalar la instalación existente.
- [ ] Revisar PR #1, #2 y #3 y obtener aprobación expresa antes de integrar o publicar.

El gate automatizado de Fase 70 puede seguir marcando las validaciones manuales como `pending`: no consume ni certifica esta declaración humana. `public_release_authorized=false` sigue siendo la política de publicación.
