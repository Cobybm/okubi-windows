# OKUBI 遊 — Fase 87: Instalador Windows y arranque confiable

**Estado:** instalador de pruebas. No es una publicación estable.

## Objetivo

Mejorar el funcionamiento del **instalador Windows de doble clic** en lugar
de entregar nuevos archivos `.cmd`. El paquete base es el mismo OKUBI
Windows con núcleos SameBoy, mGBA y Supafaust compilados desde fuentes,
cuya integridad y licencias se incluyeron en Fase81B.

El instalador nuevo se llama **`OKUBI-Windows-Instalar-QA87.exe`**.
Lo genera el workflow
`.github/workflows/okubi-phase86-direct-installers.yml`.

## Correcciones

1. **Continúa el perfil de QA86**, exactamente en
   `%LOCALAPPDATA%\OKUBI-QA86-Perfil\`; la instalación nueva conserva
   las partidas y preferencias de pruebas si ya se utilizó QA86.
2. Mantiene el mismo **AppId** de Inno Setup y la misma carpeta de
   instalación para actualizar sin duplicar dos instalaciones de prueba
   incompatibles.
3. El acceso directo `OKUBI QA86` sigue funcionando. Si el ejecutable
   del juego no puede iniciarse o se cierra inmediatamente, el lanzador
   muestra un mensaje entendible en vez de fallar sin feedback.
4. El argumento `--verify-installation` del **lanzador instalado**
   ejecuta la autocomprobación del exe original con `APPDATA` y
   `LOCALAPPDATA` aislados. Este argumento es **solo de CI**;
   el usuario abre OKUBI con su icono habitual, sin consola ni scripts.
5. La CI instala el **Setup EXE real** en una ubicación temporal, prueba
   el lanzador real, cambia un byte de la **copia temporal** de un núcleo
   y confirma que la prueba rechaza esa corrupción, luego restaura la
   DLL y confirma que el inicio vuelve a pasar. Por último desinstala
   en el runner sin acceder a partidas del propietario.

## Android

La versión Android continúa siendo el APK de pruebas **QA57**;
no se recompila ni cambia la firma de tu instalación actual.
La APK se entrega por separado en el archivo
`OKUBI-Android-QA57.apk`. No desinstalar versiones anteriores con
partidas para forzar la instalación.

## Límites y controles pendientes

La compilación CI puede verificar la instalación y el **modo de
diagnóstico del ejecutable**, pero no puede certificar que la pantalla
del usuario, controles, audio o guardados funcionen en la PC física.
El instalador no está firmado por un editor comercial; Windows podría
mostrar una advertencia de procedencia. No se modifica ni se publica
ningún ROM, BIOS o partida.

OKUBI 1.0 requiere aún: pruebas físicas Windows/Android, Cross Save real,
certificado de actualización Android original, auditoría integral de
licencias y autorización expresa para publicar.

No se fusiona a `main`; no se altera el proyecto independiente VIDAS.
