@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ===================================================
echo  OKUBI QA84 - Prueba Windows CON PERFIL NUEVO
echo ===================================================
echo NO desinstala OKUBI, no sustituye tus partidas.
echo Usa solamente juegos propios y guardados NUEVOS.
echo Las carpetas de esta prueba se conservan al terminar.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" -Mode Windows
if errorlevel 1 (
  echo RECHAZADO: la comprobacion de integridad fallo.
  if not defined CI pause
  exit /b 1
)
if defined CI (
  echo MODO CI: no se abre la aplicacion automaticamente.
  exit /b 0
)
choice /C SN /N /M "Abrir OKUBI en el perfil aislado? [S/N] "
if errorlevel 2 exit /b 0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\okubi_phase83_windows_local_qa.ps1" -Archive "%~dp0Windows\OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip" -Checksum "%~dp0Windows\SHA256SUMS.txt" -Launch
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" echo ERROR: la aplicacion o el entorno de prueba no se pudo iniciar.
echo No se ha instalado ni reemplazado el OKUBI anterior.
pause
exit /b %RESULT%
