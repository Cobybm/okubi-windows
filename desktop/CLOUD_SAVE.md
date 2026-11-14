# OKUBI 遊 Desktop 0.9 — CLOUD SAVE (Fase 39)

La fase 0.9 añade un **puente manual y protegido hacia una carpeta sincronizada por Google Drive para escritorio**. OKUBI no pide tu cuenta ni contraseña, no utiliza una API de Google Drive y **no puede afirmar que un archivo haya llegado a la nube**: Google Drive para escritorio (o el programa de sincronización que elijas) es quien sube y descarga los archivos de la carpeta.

En esta primera versión **no hay sincronización automática**, ni en segundo plano, ni transferencia directa entre APK y PC. Eso evita conflictos invisibles mientras se prueban partidas reales.

## Configuración una sola vez en Windows

1. Instala e inicia **Google Drive para escritorio** desde Google. Configura tu propia cuenta.
2. Desde el Explorador, crea una carpeta llamada, por ejemplo, **OKUBI-Cloud-Saves** *dentro de Mi unidad sincronizada* (o carpeta respaldada por tu cliente Drive).
3. Abre OKUBI Desktop 0.9. En la pantalla inicial elige **☁ CLOUD SAVE**.
4. Selecciona esa carpeta **existente y sincronizada** con el selector de Windows. OKUBI recuerda únicamente su ruta local en \`%APPDATA%/OKUBI/cloud-save-v1.properties\`.
5. Escoge el juego que quieres respaldar de tu biblioteca. La ROM debe ser exactamente la misma en PC y Android: identidad por SHA-256 de contenido, no por título.

OKUBI no copia ROMs, firmware, partidas antiguas de forma masiva, ni lee contraseñas/tokens de Google. No crea carpetas de nube sin tu acción.

## PC → Drive → Android

1. Guarda dentro de tu juego de PC, y **cierra la ventana de juego** para confirmar la SRAM/RTC.
2. Ve a **☁ CLOUD SAVE → SUBIR NUEVA VERSIÓN**.
3. OKUBI compara la SRAM/RTC PC con el último respaldo compatible en la carpeta. Si es idéntico, evita duplicar archivos. Si es diferente, avisa de **CONFLICTO** y requiere confirmación.
4. Al confirmar, crea un archivo nuevo e **inmutable** con formato exacto Android \`*.okubisave\`, nombre basado en consola, hash ROM, fecha UTC y un identificador único. **Nunca pisa archivos existentes**.
5. Comprueba en Google Drive para escritorio que se terminó la sincronización; OKUBI solo comprueba la copia local.
6. En el celular, desde Google Drive descarga el respaldo escogido. Abre la **misma ROM** en OKUBI Android y usa **Importar respaldo** desde su menú. Android valida el juego, tamaño y contenido y conserva un punto de recuperación antes de instalar la partida.

## Android → Drive → PC

1. Abre el juego en OKUBI Android, guarda dentro del juego, y utiliza su opción **Exportar respaldo** \`.okubisave\`.
2. Sube ese archivo a la misma carpeta en la app **Google Drive** del teléfono.
3. Comprueba que Google Drive para escritorio lo descargó a la carpeta local. Recuerda que Google sincroniza por separado de OKUBI.
4. Cierra cualquier partida abierta en el PC. Ve a **☁ CLOUD SAVE → REVISAR / RESTAURAR**.
5. OKUBI inspecciona cada respaldo \`.okubisave\` de la carpeta (hasta 250 por carpeta), comprueba consola, SHA-256 de la ROM y el SHA-256 de cada sección. Filtra otros juegos y archivos inválidos.
6. Muestra un **historial de versiones verificadas** con fecha del archivo, SRAM/RTC y avisos de estados no portables. La fecha puede variar durante la sincronización y **no demuestra cuál partida es más avanzada**.
7. Elige la versión que quieres restaurar y confirma. Si había una partida PC, OKUBI hace **un respaldo local independiente en** \`%APPDATA%/OKUBI/saves/<consola>/cross-save-recovery/\` antes de cambiar SRAM/RTC. Nunca restaura estados rápidos \`.state\` de otro núcleo.

Si ambos dispositivos tienen guardados diferentes, la app muestra **CONFLICTO**. No decide automáticamente cuál conservar. Puedes **guardar una nueva versión** en la carpeta (sin eliminar la anterior) o **elegir una versión específica para restaurar**, siempre con confirmación.

## Recuperar una versión anterior

En **CLOUD SAVE → REVISAR / RESTAURAR**, selecciona otra versión del historial. El anterior guardado de PC se protegerá otra vez.

También puedes abrir **CROSS SAVE LOCAL → VER RESPALDOS** para localizar los archivos locales creados antes de importar, y utilizarlos mediante la importación de Cross Save. La carpeta de recuperación del PC **no se sube automáticamente** a Drive, para no mezclar distintas revisiones y núcleos.

## Precauciones

- **SOLO SRAM y RTC** (guardados de cartucho). Los estados rápidos de Libretro no son intercambiables entre núcleos, revisiones y sistemas.
- El programa rechaza importaciones de partidas de otra ROM/consola, archivos dañados o tamaños incompatibles con el guardado PC existente.
- La copia de Drive no se sobrescribe ni borra automáticamente; cada nueva exportación tiene un nombre único.
- El programa rechaza seleccionar la carpeta privada de partidas o un directorio que la contenga como nube, y no sigue archivos \`*.okubisave\` simbólicos.
- Las operaciones de escaneo/copia/recuperación se ejecutan fuera del hilo de dibujo de Swing para evitar bloquear la interfaz. No se permite iniciar un juego mientras una copia está en curso.
- Las versiones del historial no están cifradas por OKUBI. **Los respaldos en tu cuenta Drive tendrán la privacidad que tú configures en Google Drive**; evita carpetas compartidas públicamente.
- Nunca se guardan ni transmiten contraseñas, cookies, credenciales OAuth o identificadores de cuentas a través de OKUBI.
- Drive puede sincronizar o descargar un archivo en otro momento. La app verifica la versión disponible **localmente**, no confirma que el remoto esté sincronizado.
- Si la carpeta no está disponible, el programa muestra un error; puedes cambiarla desde el menú. Sigue funcionando sin Drive para jugar.
- **No cambia la APK Android**, que sigue exportando/importando manualmente. En esta fase Windows requiere Google Drive para escritorio o una carpeta sincronizada por otro cliente.

## Verificación de la versión 0.9

GitHub Actions Windows ejecuta todas las pruebas anteriores y \`DesktopCloudSaveTest\`, que verifica carpetas elegidas explícitamente, persistencia, estado sin datos, conflictos, inmutabilidad de versiones, rechazo de sobrescritura, detección de backups Android de nombre libre, recuperación de SRAM, conservación de estados, rechazo de archivos corruptos/del juego equivocado, reversión y manejo de archivo cambiado tras visualizarse. La prueba no accede a cuentas reales de Google.

Se empaquetan los núcleos open source SameBoy, mGBA y Supafaust con el runtime Java, sin descargarlos ni instalarlos manualmente en el equipo del usuario.

El código sigue en PR en **borrador y sin fusionar**; valida el flujo real de Google Drive en tu Windows y Android antes de considerarlo una sincronización confiable.
