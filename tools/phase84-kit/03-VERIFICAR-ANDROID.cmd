@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ===================================================
echo  OKUBI QA84 - Verificar archivo APK Android
echo ===================================================
echo Solo verifica integridad. No instala ni desinstala.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" -Mode Android
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo RECHAZADO: archivo APK invalido.
) else (
  echo OK: archivo descargado sin modificaciones.
  echo NO desinstales tu app Android actual para instalar otra QA57.
  echo Si la firma no coincide, conserva la version con tus partidas.
)
if not defined CI pause
exit /b %RESULT%
