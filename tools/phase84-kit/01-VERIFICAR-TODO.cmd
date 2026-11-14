@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
echo ===================================================
echo  OKUBI QA84 - Verificacion Windows y Android
echo  No instala nada ni toca tus partidas.
echo ===================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" -Mode Both -KitRoot "%~dp0" -Report "%~dp0INFORME-QA84.json"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo RECHAZADO: no uses los archivos hasta resolver integridad.
) else (
  echo APROBADO: verificacion de archivos de prueba.
  echo Esto NO significa que el juego se haya probado en tu PC.
)
if not defined CI pause
exit /b %RESULT%
