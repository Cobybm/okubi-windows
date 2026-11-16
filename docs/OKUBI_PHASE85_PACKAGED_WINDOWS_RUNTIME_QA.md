# OKUBI 遊 — Fase 85 · Ejecutar el EXE real en Windows sin ROMs

**Objetivo:** mejorar el QA automático más allá del ZIP y la carga de DLL:
ejecutar el `OKUBI Desktop.exe` **real**, extraído de la distribución
Windows 81B ya aprobada, usando `--verify-windows-release`.

El modo `--verify-windows-release` que ya existe dentro de
`OkubiDesktop.main()` llama a `DesktopWindowsReleaseVerifier.runCli()`
antes de crear una ventana o cargar juegos. Examina:

- `okubi-desktop.jar` y los archivos obligatorios de la app.
- La DLL puente JNI de 64 bits y los tres núcleos GB/GBC/GBA/SNES.
- Cada DLL contra el SHA-256 de `CORE-SHA256.properties`.
- Las licencias de SameBoy, mGBA y Supafaust y el lanzador QA aislado.

## Protección y prueba de manipulación

Workflow: `.github/workflows/okubi-phase85-windows-runtime-smoke.yml`.

1. Descarga el ZIP exacto de Windows QA del run
   [43615378972](https://github.com/Cobybm/okubi-windows/actions/runs/43615378972).
2. Verifica SHA-256 del ZIP y la estructura con la herramienta Fase 83.
3. Extrae una **copia** en directorio aleatorio del runner.
4. Establece nuevos `APPDATA` y `LOCALAPPDATA` vacíos, sin acceso deliberado
   a partidas normales.
5. Ejecuta el EXE de Windows con la bandera de autoverificación de **solo
   lectura, sin interfaz, sin cargar juegos**. Debe salir con código 0.
6. Modifica **un solo byte de una copia temporal** de mGBA; el EXE debe
   detectar el SHA incorrecto y salir con código distinto de 0.
7. Restaura la DLL temporal y comprueba que el EXE vuelve a salir con 0.
8. Publica un JSON **sin rutas privadas, ROMs, BIOS ni partidas**. No
   publica otro instalador ni reemplaza ningún ZIP existente.

## Lo que este resultado NO prueba

Un PASS de Fase 85 **no demuestra** que se haya abierto la ventana de OKUBI
en el PC del propietario, ni que funcione un mando, sonido, resolución
completa, ROM, guardado SRAM/RTC, PSP/PS2/PS3 o Cross Save físico. Es una
prueba del binario Windows real y su autoverificador de integridad.

Para la prueba gráfica real hay que usar en el PC del propietario el
[kit QA84](https://github.com/Cobybm/okubi-windows/actions/runs/43619812198),
elegir `01-VERIFICAR-TODO.cmd` y después
`02-PROBAR-WINDOWS-AISLADO.cmd`. No desinstalar la app anterior.

**OKUBI 1.0 estable aún no se autoriza** sin pruebas físicas, firma canónica
Android original, revisión de licencias y autorización expresa de publicación.
El proyecto VIDAS y la aplicación Android normal no se tocan.
