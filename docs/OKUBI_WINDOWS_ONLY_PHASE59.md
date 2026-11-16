# OKUBI 遊 — Fase 59: Windows-only Release Candidate

**Windows x64 únicamente — versión 1.0.7 RC.** Android se deja en su última
beta aprobada por CI, sin modificar APK, firma, ID, datos ni configuración.
Ninguna prueba Android física se considera aprobada.

## Incluye

- Windows Java Swing CINEMA, biblioteca con carátulas, GAME HUB, controles
  Xbox, pantalla completa, SAVE VAULT y SMART SAVE GUARD.
- Núcleos integrados SameBoy (GB/GBC), mGBA (GBA) y Supafaust (SNES),
  con licencias y un manifiesto SHA-256 de los tres DLL.
- ZIP portable x64 y suma SHA-256 externa del paquete completo.
  No es un MSI ni un actualizador automático.
- Ejecución normal: `OKUBI Desktop.exe`, con el perfil normal
  `%APPDATA%\OKUBI`, igual que en las fases anteriores.
- Ejecución de prueba aislada: `app\RUN-QA57-ISOLATED.cmd`,
  que crea un perfil aparte en `%LOCALAPPDATA%\OKUBI-QA57\Roaming\OKUBI`,
  sin importar por defecto tus partidas.
- Comprobación previa: `app\VERIFY-WINDOWS-PACKAGE.cmd`
  (o `OKUBI Desktop.exe --verify-windows-release`), que comprueba **sin
  abrir juegos ni datos personales** el JAR, DLL JNI x64, cores x64,
  hashes y avisos de licencia. Su código de salida es 0 si todo está bien.
  Detecta archivos incompletos o alterados, pero no sustituye una firma
  digital ni una auditoría legal de distribución.

## Prueba Windows sin perder progreso

1. Conserva el ZIP anterior y haz copia externa de `%APPDATA%\OKUBI`
   antes de cualquier prueba con restauración.
2. Descarga el nuevo artefacto ZIP desde GitHub Actions, **extráelo completo
   en una carpeta nueva**, sin reemplazar el ejecutable actual.
3. Verifica el SHA-256 con PowerShell:
   `Get-FileHash -Algorithm SHA256 "OKUBI-Desktop-Windows-x64.zip"`.
   Compara con el SHA256SUMS que acompaña al ZIP.
4. Ejecuta `OKUBI Desktop\app\VERIFY-WINDOWS-PACKAGE.cmd`.
   Si falla, no juegues desde esa extracción.
5. Abre `OKUBI Desktop\app\RUN-QA57-ISOLATED.cmd`. La biblioteca QA estará
   vacía. Importa solo ROMs de prueba de tu propiedad, juega con el núcleo
   integrado y comprueba sonido, mando/teclado y guardado nuevo de prueba.
6. Revisa GB/GBC/GBA/SNES y el GAME HUB si lo necesitas. No uses la
   única copia de una partida importante para probar restauraciones.
7. Después comprueba que tu app normal aún conserva favoritos y guardados.

## Pendiente para declarar versión estable y publicar

- [ ] El EXE empaquetado supera la comprobación real de arranque offline.
- [ ] La suite completa Windows (Java, JNI, emulación, datos, mandos)
      y el empaquetado ZIP terminan con SUCCESS en GitHub.
- [ ] Prueba real con sonido, mando Xbox, un juego y guardados temporales
      en tu Windows.
- [ ] Revisión final de licencias/código correspondiente, carátulas y
      condiciones de redistribución de los núcleos.
- [ ] Aprobación expresa para fusionar y publicar. No hacerlo en esta fase.

**Android se aplaza, no se declara estable.** No tocar VIDAS, sus archivos,
su repositorio, ni mezclar recursos entre ambos proyectos.
