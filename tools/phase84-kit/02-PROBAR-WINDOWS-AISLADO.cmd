@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
set "OKUBI_QA84_ROOT=%~dp0"
echo ===================================================
echo  OKUBI QA84 - Prueba Windows CON PERFIL NUEVO
echo ===================================================
echo NO desinstala OKUBI, no sustituye tus partidas.
echo Usa solamente juegos propios y guardados NUEVOS.
echo Las carpetas de esta prueba se conservan al terminar.
echo.
if not exist "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" goto :EXTRAER_KIT
if not exist "%~dp0tools\okubi_phase83_windows_local_qa.ps1" goto :EXTRAER_KIT
if not exist "%~dp0Windows\OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip" goto :EXTRAER_KIT
if not exist "%~dp0Windows\SHA256SUMS.txt" goto :EXTRAER_KIT
echo Carpeta del kit verificada.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" -Mode Windows
set "QA84_PRECHECK=%ERRORLEVEL%"
if not "%QA84_PRECHECK%"=="0" (
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

:EXTRAER_KIT
echo.
echo ERROR: El kit OKUBI QA84 no esta extraido completamente.
echo Posible causa: abriste un archivo .cmd dentro de WinRAR o 7-Zip.
echo.
echo SOLUCION: cierra esta ventana y usa "Extraer en..." sobre el ZIP EXTERIOR.
echo Abre los .cmd desde la carpeta extraida, nunca desde WinRAR.
echo Deben aparecer juntos: 01/02/03 .cmd, tools, Windows y Android.
echo NO desinstales OKUBI ni borres partidas. No extraias el ZIP interno de Windows.
if not defined CI pause
exit /b 2
