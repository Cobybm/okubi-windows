# OKUBI 遊 Desktop 0.7 — GAME HUB (Fase 37H)

## Una biblioteca con historia real de juego

Al seleccionar un juego con la cruceta, teclado o ratón, el banner superior se convierte en su **ficha** con:

- Nombre y consola, arte retro original generado localmente y botón **JUGAR**.
- Tiempo real jugado y número de sesiones desde OKUBI Desktop 0.7.
- Categorías **Todos / Favoritos / Recientes / Más jugados**.
- Nuevo apartado **Mis estadísticas** en la barra lateral o botón **ESTADÍSTICAS** arriba: total de horas, sesiones y clasificación visual de los 15 juegos con más actividad.

El ranking usa las horas medidas por OKUBI; no genera estadísticas ficticias ni lee información de internet, videojuegos, cuentas o mandos.

## ¿Cómo mide las horas?

- El contador empieza cuando **la ROM y la SRAM/RTC cargan correctamente** en el motor integrado Libretro.
- Usa reloj monotónico de Java (`System.nanoTime`), no la fecha del sistema, y suma únicamente el tiempo cuando **la emulación está activa**.
- **No cuenta** pausas con P, el menú de configuración ESC, pérdida de foco, interrupciones largas de Windows, ni emuladores externos.
- Guarda avances cada 15 segundos y al cerrar, mediante escritura atómica. Si falla guardar la estadística, **el juego sigue funcionando y las partidas siguen guardándose**.
- Si el juego ya estaba en tu biblioteca desde 0.6, **empieza en 0 minutos**. No inventa tiempo anterior.
- Identifica cada juego mediante hash de la ruta absoluta + consola. Si mueves la ROM a otra carpeta, su contador aparece como nuevo; la información anterior permanece en el archivo de estadísticas.
- El tiempo se redondea a segundos completos; sesiones de menos de un segundo pueden reflejar 0 segundos con 1 inicio.

Los datos quedan solo en este PC:

`%APPDATA%\OKUBI\gameplay-stats-v1.properties`

No se recopila telemetría, no hay cuentas, ni subida automática a la nube, ni descarga de portadas.

## Perfiles de mando por consola

En un juego, pulsa **ESC → CINEMA CONTROL CENTER → PERFIL MANDO XBOX** y elige entre:

- **Distribución Nintendo (predeterminada)**: mantiene exactamente el mapeo XInput que tenía OKUBI 0.6, sin modificar partidas.
- **Letras Xbox**: el botón físico A ejecuta A, B ejecuta B, X ejecuta X e Y ejecuta Y. Mantiene cruceta, stick, Start, Select, L y R.

Puedes elegir un perfil diferente para GB, GBC, GBA y SNES. Se conserva el teclado personalizable de 0.5 y las preferencias se guardan, de forma compatible, en `cinema-controls-v1.properties`. No afecta la navegación del mando por la biblioteca.

## Conservación

Sin cambios de formato ni ubicación para:
- ROMs del usuario (solo lectura);
- SRAM y RTC, guardado automático, snapshots F5/F9 y capturas F12;
- perfiles de teclado y filtros gráficos anteriores;
- biblioteca, favoritos, fechas de último juego y modo consola;
- aplicación Android, partidas Android, firmas APK o código de otros proyectos.

Los emuladores incluidos SameBoy GB/GBC, mGBA GBA y Beetle Supafaust SNES siguen dentro del ZIP Windows, sin descargas manuales.

## Verificación

GitHub Actions ejecuta las pruebas anteriores de compilación Windows y núcleos Libretro, más `DesktopGameHubTest`: monotonicidad, pausa, sesiones, recuperación de preferencias inválidas, persistencia al reiniciar, filtros de estadísticas y dos distribuciones de mando por consola. La prueba del contador utiliza tiempo simulado determinísticamente; **no afirma que se haya medido físicamente una partida real en el PC del usuario**.

Windows 10/11 x64; mando Xbox/XInput o teclado. El ejecutable no está firmado como editor Windows. Extrae siempre el ZIP completo sin borrar `%APPDATA%\OKUBI`, y conserva respaldos externos.

**PR en borrador sin fusionar**. Pruebas visuales y de tiempo real en PC físico pendientes.
