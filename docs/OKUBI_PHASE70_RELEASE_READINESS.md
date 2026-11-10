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
