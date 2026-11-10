# OKUBI 遊 · Fase 75 — SMART PORTS, detección de instalaciones

## Mejora implementada

En **PORTS PC · GITHUB** aparece **DETECTAR INSTALADOS**. Elige una carpeta que contenga ports o juegos ya instalados en Windows. OKUBI buscará únicamente nombres de archivos ejecutables conocidos entre las **32 fichas revisadas** y comprobará que sus cabeceras sean de ejecutables Windows PE x86/x64.

La búsqueda en vivo de GitHub de Fase 74 permanece igual: proyectos adicionales siguen siendo candidatos, sin compatibilidad ni seguridad garantizadas.

### Límites y seguridad

- Solo funciona después de elegir **una carpeta** y confirmar la revisión.
- Máximo **4 niveles**, **4.000 archivos** y **60 coincidencias** en cada exploración; sin escaneo automático del disco completo.
- Excluye subcarpetas `saves`, `savegames`, `savestates`, `roms`, `bios`, `firmware`, `userdata`, `.git`, `node_modules` y similares.
- No sigue enlaces simbólicos, no ejecuta ningún archivo y no descarga nada.
- Las coincidencias solo se presentan como **posibles ports**. El usuario debe seleccionar manualmente cuáles vincular. No se marca un juego como instalado automáticamente.
- Si ya existe un vínculo distinto, se pide autorización explícita antes de sustituirlo.
- La operación guarda únicamente la ruta local del EXE en el perfil aislado/normal de OKUBI, según cómo se haya abierto la aplicación. No modifica juegos, partidas, ROMs, BIOS ni carpetas personales.
- Nombres y cabeceras PE no prueban que un ejecutable sea seguro o compatible. Confirma su origen y firma antes de usar **JUGAR**.

### Prueba en Windows

1. Descomprime el paquete QA de la Fase 75 en una carpeta nueva y ejecuta `VERIFY-WINDOWS-PACKAGE.cmd` y `RUN-QA57-ISOLATED.cmd`.
2. Entra en **PORTS PC · GITHUB → DETECTAR INSTALADOS**.
3. Selecciona tu carpeta de juegos de PC, no la unidad `C:\` completa. OKUBI no pide privilegios de administrador.
4. Comprueba que aparezcan solamente los títulos cuyo ejecutable conocido esté dentro de esa carpeta. Selecciona uno que reconozcas.
5. Pulsa **JUGAR** en el catálogo revisado y confirma la ruta visible; comprueba que se inicia de forma normal.
6. Cierra y reabre OKUBI para verificar el vínculo. Si lo quitas, el juego instalado debe permanecer intacto.
7. Verifica que la biblioteca retro, los mandos y tus guardados siguen funcionando.

### Qué falta aún

- Prueba física del escáner de ports en la PC del propietario.
- Diagnóstico de GPU/RAM/controladores y comparación con **requisitos oficiales por port** antes de presentar recomendaciones de compatibilidad. Los rótulos Ligero/Medio/Variable siguen siendo orientativos; no equivalen a un test de rendimiento.
- Cross Save Android ↔ Windows entre equipos físicos, correspondencia de código fuente de DLL Windows, firma Android canónica y aprobación explícita de PR en orden.
- No fusionar a `main` ni publicar como estable; no tocar el repositorio privado original.
