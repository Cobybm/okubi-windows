@echo off
setlocal EnableExtensions DisableDelayedExpansion
REM Phase59 read-only package check: no game saves are accessed.
set "EXE=%~dp0..\OKUBI Desktop.exe"
if not exist "%EXE%" (
    echo ERROR: OKUBI Desktop.exe is missing.
    pause
    exit /b 2
)
echo Verificando nucleos x64, SHA-256 y licencias de OKUBI...
"%EXE%" --verify-windows-release
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo ERROR: Paquete incompleto o alterado. Extrae el ZIP completo otra vez.
    echo No borres %%APPDATA%%\OKUBI.
) else (
    echo OK: el paquete paso las comprobaciones; no se tocaron partidas.
)
pause
exit /b %RESULT%
