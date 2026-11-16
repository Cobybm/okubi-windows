# OKUBI — Fase 71 · Prueba física Cross Save (solo datos de prueba)

**Estado:** pendiente de prueba en dispositivos. Esto NO activa sincronización automática ni modifica los guardados normales. Mantener PR #1, #2 y #3 sin fusionar.

## Preparación segura

- Usar **OKUBI QA57 遊**, aplicación Android separada, y **RUN-QA57-ISOLATED.cmd** en Windows.
- Elegir **la misma copia exacta de ROM** legalmente obtenida, preferiblemente homebrew o archivo de prueba; comprobar que ambos equipos usan idénticos bytes. No usar la única partida importante ni un estado F5/F9 como prueba.
- Crear un guardado NUEVO en la partida de prueba dentro del juego, salir/cerrar el juego y comprobar que el guardado es persistente en cada equipo por separado.
- Guardar una copia independiente de cualquier .okubisave de prueba antes de importar; **nunca sobrescribir una copia de respaldo**.

## Prueba 1 — Android → Windows

1. En la app **Android QA57** abrir la ROM de prueba, guardar dentro del juego y cerrar la sesión.
2. Usar el menú de partidas/respaldos para **Exportar respaldo .okubisave**.
3. Copiar **solo** ese archivo por USB u otro medio privado a una carpeta temporal de Windows, fuera del repositorio GitHub.
4. Abrir Windows con **RUN-QA57-ISOLATED.cmd**, importar la **misma ROM**, cerrar la sesión de juego y entrar a **⇄ CROSS SAVE → IMPORTAR DEL CELULAR**.
5. En la vista previa comprobar ROM y consola correctas, tamaño SRAM/RTC compatible y mensaje de estados no portátiles. Confirmar solo el respaldo de prueba.
6. Abrir el juego y comprobar progreso exacto; verificar que se mantuvo la copia de recuperación y no se tocaron partidas habituales.

## Prueba 2 — Windows → Android

1. Seguir desde el perfil Windows aislado. Guardar NUEVO progreso dentro del juego, cerrar el juego.
2. Usar **⇄ CROSS SAVE → EXPORTAR AL CELULAR** y elegir **un nombre nuevo** de archivo .okubisave.
3. Copiar el respaldo al teléfono. En Android **QA57** abrir la misma ROM de prueba, salir del juego y seleccionar la función **Importar respaldo**.
4. Confirmar coincidencia de ROM/consola y vista previa. Hacer copia de recuperación si la app la ofrece, importar y volver a abrir.
5. Verificar progreso y que Android normal (no QA57) conserva intactas sus partidas antiguas.

## Rechazos que deben protegerte

- Una ROM distinta, aunque tenga nombre parecido, **debe rechazarse**.
- Un archivo de respaldo alterado debe rechazarse **antes** de escribir datos.
- Los estados rápidos/savestates no son transferibles entre emuladores; solo SRAM y RTC (si existen y son compatibles).
- Si aparece error de tamaño, firma, hash, formato o perfil, **cancelar**. No intentar forzar importación ni sobrescribir manualmente carpetas de guardado.

## Resultado esperado

- [ ] Android → Windows: progreso de prueba correcto
- [ ] Windows → Android: progreso de prueba correcto
- [ ] Archivos incorrectos/corruptos rechazados sin pérdida
- [ ] Partidas y apps normales intactas

**QA automática existente:** Windows [run #42863459667](https://github.com/Cobybm/okubi-windows/actions/runs/42863459667) ejecuta `DesktopCrossSaveTest`; Android [run #42867125796](https://github.com/Cobybm/okubi-windows/actions/runs/42867125796) ejecuta `OkubiPortableSaveContractTest`. Ambas suites completaron SUCCESS, pero aún falta esta prueba real bidireccional.

**No publiques ROMs, archivos de guardados ni capturas que revelen rutas privadas en issues públicos.** Para reportar un fallo basta la consola, acción, texto de error y si el progreso sintético quedó intacto.

## Auditoría automática de Fase 71 (10 de noviembre de 2026)

- ✅ Ejecución independiente en **Windows** y **Ubuntu**: [GitHub Actions #42875325617](https://github.com/Cobybm/okubi-windows/actions/runs/42875325617) — ambas matrices `SUCCESS`.
- ✅ Compilación de `desktop/src/` junto con el serializador **original y compartido** `OkubiSaveArchive.java` de Android.
- ✅ `OkubiSaveArchiveTest`: integridad de secciones y archivo.
- ✅ `OkubiPortableSaveContractTest`: SRAM/RTC Android ↔ Windows; no transferir estados rápidos; rechazar ROM o sistema diferente.
- ✅ `DesktopCrossSaveTest`: importación/exportación Windows, vista previa, copia de recuperación, rechazo por tamaños distintos y archivos alterados, sin sobreescribir un respaldo.
- ✅ Comprobación adicional de que Git no rastrea ROM, guardados o claves privadas de prueba.

**Límites:** este CI no ejecuta la interfaz en un teléfono y no ha movido una partida real entre dispositivos. La comprobación **Android QA57 ↔ Windows QA57** sobre el mismo archivo homebrew/de prueba sigue pendiente. Los datos personales no deben cargarse a GitHub.

**Próximas puertas para versión estable:** comprobar la transferencia física bidireccional, realizar la revisión de código fuente correspondiente y obligaciones MIT/MPL/GPL de los núcleos distribuidos, verificar la firma permanente del propietario para actualizar la APK canónica, y obtener autorización para integrar los PR por orden. No cambiar `main`, la instalación anterior ni el repositorio privado original mientras quede alguna de esas puertas.
