@echo off
setlocal EnableExtensions DisableDelayedExpansion
REM OKUBI QA57 uses a separate APPDATA tree. This does not uninstall,
REM replace, migrate, read or delete production OKUBI saves/settings.
REM Run from the Windows ZIP's OKUBI Desktop\app subfolder.

if not defined LOCALAPPDATA (
  echo ERROR: Windows LOCALAPPDATA is not available. QA launcher cancelled.
  exit /b 1
)
set "QA_ROOT=%LOCALAPPDATA%\OKUBI-QA57"
set "APPDATA=%QA_ROOT%\Roaming"
set "OKUBI_QA57_DATA=%APPDATA%\OKUBI"
if not exist "%OKUBI_QA57_DATA%" mkdir "%OKUBI_QA57_DATA%"
if errorlevel 1 (
  echo ERROR: Could not create isolated QA data folder.
  exit /b 1
)
set "OKUBI_QA57_EXE=%~dp0..\OKUBI Desktop.exe"
if not exist "%OKUBI_QA57_EXE%" (
  echo ERROR: The OKUBI Desktop.exe next to app\ is missing.
  exit /b 1
)
echo ===========================================================
echo OKUBI QA57 - isolated test library and SRAM/RTC location
echo %OKUBI_QA57_DATA%
echo Normal %%APPDATA%%\OKUBI data is NOT accessed by this run.
echo Import test cartridges separately; do not restore original saves.
echo ===========================================================
start "" /wait "%OKUBI_QA57_EXE%"
exit /b %errorlevel%
