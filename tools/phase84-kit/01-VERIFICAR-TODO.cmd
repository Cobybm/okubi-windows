@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
set "OKUBI_QA84_ROOT=%~dp0"
echo ===================================================
echo  OKUBI QA84 - Verificacion Windows y Android
echo  No instala nada ni toca tus partidas.
echo ===================================================
if not exist "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" goto :EXTRAER_KIT
if not exist "%~dp0Windows\OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip" goto :EXTRAER_KIT
if not exist "%~dp0Windows\SHA256SUMS.txt" goto :EXTRAER_KIT
if not exist "%~dp0Android\OKUBI-Android-Migration-QA57.apk" goto :EXTRAER_KIT
if not exist "%~dp0Android\SHA256SUMS.txt" goto :EXTRAER_KIT
echo Carpeta del kit verificada.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\OKUBI-VERIFICAR-KIT.ps1" -Mode Both -Report "%~dp0INFORME-QA84.json"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo RECHAZADO: no uses los archivos hasta resolver integridad.
) else (
  echo APROBADO: verificacion de archivos de prueba.
  echo Esto NO significa que el juego se haya probado en tu PC.
)
if not defined CI pause
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
